import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Thread de atendimento de um cliente no servidor. Existe uma instancia
 * desta classe para cada cliente conectado. Recebe as linhas enviadas,
 * interpreta os comandos do protocolo e produz as respostas.
 */
public class ThreadCliente extends Thread {

    private final Socket socket;
    private final Servidor servidor;
    private BufferedReader entrada;
    private PrintWriter saida;

    private volatile String apelido = null;   // definido pelo comando LOGIN
    private volatile Sala sala = null;        // sala atual do cliente

    public ThreadCliente(Socket socket, Servidor servidor) {
        this.socket = socket;
        this.servidor = servidor;
    }

    public String getApelido() {
        return (apelido == null) ? "(anonimo)" : apelido;
    }

    public String getNomeSala() {
        Sala s = sala;
        return (s == null) ? "-" : s.getNome();
    }

    public String getEndereco() {
        return socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
    }

    /** Envia uma linha ao cliente. Sincronizado: outras threads tambem escrevem aqui. */
    public synchronized void enviar(String linha) {
        if (saida != null) {
            saida.println(linha);
            saida.flush();
        }
    }

    /** Fecha o socket; a leitura bloqueada em run() termina e a sessao e' encerrada. */
    public void desconectar() {
        try { socket.close(); } catch (IOException e) { }
    }

    @Override
    public void run() {
        servidor.adicionarConexao(this);
        try {
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            saida   = new PrintWriter(new java.io.OutputStreamWriter(socket.getOutputStream(), "UTF-8"));

            enviar(Protocolo.montar("CMD", "OK", "INFO", "Conectado. Use LOGIN para se identificar."));

            String linha;
            while ((linha = entrada.readLine()) != null) {   // bloqueia ate chegar mensagem
                Map<String, String> campos = Protocolo.analisar(linha);
                String cmd = Protocolo.campo(campos, "CMD", "").toUpperCase();

                if (cmd.equals("LOGIN"))       tratarLogin(campos);
                else if (cmd.equals("JOIN"))   tratarJoin(campos);
                else if (cmd.equals("MSG"))    tratarMsg(campos);
                else if (cmd.equals("PRIV"))   tratarPriv(campos);
                else if (cmd.equals("USERS"))  tratarUsers();
                else if (cmd.equals("ROOMS"))  tratarRooms();
                else if (cmd.equals("HIST"))   tratarHist(campos);
                else if (cmd.equals("FILE"))   tratarFile(campos);
                else if (cmd.equals("GET"))    tratarGet(campos);
                else if (cmd.equals("QUIT"))   break;
                else erro("01", "Comando desconhecido: " + cmd);
            }
        } catch (IOException e) {
            servidor.log("Conexao encerrada (" + getApelido() + "): " + e.getMessage());
        } finally {
            encerrar();
        }
    }

    // ---------- tratamento dos comandos ----------

    private void tratarLogin(Map<String, String> campos) {
        String nome = Protocolo.campo(campos, "USER", "").trim();
        if (apelido != null)      { erro("03", "Login ja realizado."); return; }
        if (nome.isEmpty())       { erro("04", "Apelido nao informado."); return; }
        if (!servidor.reservarApelido(nome, this)) { erro("02", "Apelido em uso: " + nome); return; }

        apelido = nome;
        servidor.log("LOGIN: " + apelido);
        enviar(Protocolo.montar("CMD", "OK", "INFO", "Bem-vindo, " + apelido + "! Use JOIN para entrar numa sala."));
    }

    private void tratarJoin(Map<String, String> campos) {
        if (apelido == null)      { erro("05", "Faca LOGIN antes de entrar numa sala."); return; }
        String nomeSala = Protocolo.campo(campos, "ROOM", "").trim();
        if (nomeSala.isEmpty())   { erro("06", "Nome da sala nao informado."); return; }

        if (sala != null) {                              // sai da sala anterior
            sala.difundir(Protocolo.montar("CMD", "NOTIF", "INFO", apelido + " saiu da sala."), this);
            sala.sair(this);
            servidor.removerSalaSeVazia(sala);
        }

        sala = servidor.obterSala(nomeSala);
        sala.entrar(this);
        servidor.log(apelido + " entrou na sala " + nomeSala);
        enviar(Protocolo.montar("CMD", "OK", "INFO", "Voce entrou na sala " + nomeSala + "."));
        sala.difundir(Protocolo.montar("CMD", "NOTIF", "INFO", apelido + " entrou na sala."), this);
    }

    private void tratarMsg(Map<String, String> campos) {
        if (apelido == null)      { erro("05", "Faca LOGIN antes de enviar mensagens."); return; }
        if (sala == null)         { erro("07", "Entre numa sala antes de enviar mensagens."); return; }
        String texto = Protocolo.campo(campos, "TEXT", "");
        if (texto.isEmpty())      { erro("08", "Mensagem vazia."); return; }

        String hora = new SimpleDateFormat("HH:mm").format(new Date());
        String difusao = Protocolo.montar("CMD", "MSG", "FROM", apelido, "TS", hora, "TEXT", texto);

        sala.registrarHistorico(difusao);   // repositorio de dados comuns
        sala.difundir(difusao, this);       // mediacao: o servidor repassa
        enviar(Protocolo.montar("CMD", "OK", "INFO", "enviada " + hora));
    }

    /** Mensagem privada: o servidor localiza o destinatario e repassa somente a ele. */
    private void tratarPriv(Map<String, String> campos) {
        if (apelido == null)      { erro("05", "Faca LOGIN antes de enviar mensagens."); return; }
        String destino = Protocolo.campo(campos, "TO", "").trim();
        String texto = Protocolo.campo(campos, "TEXT", "");
        if (destino.isEmpty())    { erro("10", "Destinatario nao informado."); return; }
        if (texto.isEmpty())      { erro("08", "Mensagem vazia."); return; }

        ThreadCliente alvo = servidor.buscarUsuario(destino);
        if (alvo == null)         { erro("11", "Usuario nao encontrado: " + destino); return; }

        String hora = new SimpleDateFormat("HH:mm").format(new Date());
        alvo.enviar(Protocolo.montar("CMD", "PRIV", "FROM", apelido, "TO", destino, "TS", hora, "TEXT", texto));
        enviar(Protocolo.montar("CMD", "OK", "INFO", "privada para " + destino + " enviada " + hora));
    }

    /**
     * Arquivo: sem TO vai para a sala atual, com TO vai so para o destinatario.
     * O servidor guarda o conteudo e repassa apenas o aviso (ID, nome, tamanho).
     */
    private void tratarFile(Map<String, String> campos) {
        if (apelido == null)      { erro("05", "Faca LOGIN antes de enviar arquivos."); return; }
        String destino = Protocolo.campo(campos, "TO", "").trim();
        String nome = Arquivo.nomeSeguro(Protocolo.campo(campos, "NAME", ""));
        byte[] dados;
        try {
            dados = Base64.getDecoder().decode(Protocolo.campo(campos, "DATA", ""));
        } catch (IllegalArgumentException e) {
            erro("12", "Conteudo do arquivo invalido."); return;
        }
        if (dados.length == 0)    { erro("12", "Arquivo vazio."); return; }
        if (dados.length > Arquivo.TAMANHO_MAXIMO) {
            erro("13", "Arquivo maior que " + Arquivo.tamanhoLegivel(Arquivo.TAMANHO_MAXIMO) + "."); return;
        }

        ThreadCliente alvo = null;
        if (destino.isEmpty()) {
            if (sala == null)     { erro("07", "Entre numa sala antes de enviar arquivos."); return; }
        } else {
            alvo = servidor.buscarUsuario(destino);
            if (alvo == null)     { erro("11", "Usuario nao encontrado: " + destino); return; }
        }

        Arquivo a = servidor.guardarArquivo(apelido, alvo == null ? null : destino, nome, dados);
        String id = String.valueOf(a.getId());
        String tamanho = String.valueOf(a.getTamanho());
        String hora = new SimpleDateFormat("HH:mm").format(new Date());

        if (alvo == null) {
            String difusao = Protocolo.montar("CMD", "FILE", "ID", id, "FROM", apelido, "TS", hora,
                                              "NAME", nome, "SIZE", tamanho);
            sala.registrarHistorico(difusao);
            sala.difundir(difusao, this);
            servidor.log("ARQUIVO #" + id + ": " + apelido + " -> sala " + sala.getNome() + ": "
                         + nome + " (" + Arquivo.tamanhoLegivel(a.getTamanho()) + ")");
        } else {
            alvo.enviar(Protocolo.montar("CMD", "FILE", "ID", id, "FROM", apelido, "TO", destino, "TS", hora,
                                         "NAME", nome, "SIZE", tamanho));
            servidor.log("ARQUIVO #" + id + ": " + apelido + " -> " + destino + " (privado): "
                         + nome + " (" + Arquivo.tamanhoLegivel(a.getTamanho()) + ")");
        }
        enviar(Protocolo.montar("CMD", "OK", "INFO", "arquivo #" + id + " enviado " + hora,
                                "ID", id, "SIZE", tamanho));
    }

    /** Download sob demanda: devolve o conteudo do arquivo guardado no servidor. */
    private void tratarGet(Map<String, String> campos) {
        if (apelido == null)      { erro("05", "Faca LOGIN antes de baixar arquivos."); return; }
        Arquivo a = null;
        try {
            a = servidor.buscarArquivo(Integer.parseInt(Protocolo.campo(campos, "ID", "").trim()));
        } catch (NumberFormatException e) { }
        if (a == null || !a.podeBaixar(apelido)) { erro("14", "Arquivo nao encontrado."); return; }

        enviar(Protocolo.montar("CMD", "FILEDATA", "ID", String.valueOf(a.getId()),
                                "NAME", a.getNome(), "DATA", a.getBase64()));
        servidor.log(apelido + " baixou o arquivo #" + a.getId() + " (" + a.getNome() + ")");
    }

    private void tratarUsers() {
        if (sala == null)         { erro("07", "Entre numa sala primeiro."); return; }
        enviar(Protocolo.montar("CMD", "USERS", "ROOM", sala.getNome(), "LIST", sala.listaUsuarios()));
    }

    private void tratarRooms() {
        enviar(Protocolo.montar("CMD", "ROOMS", "LIST", servidor.listaSalas()));
    }

    private void tratarHist(Map<String, String> campos) {
        if (sala == null)         { erro("07", "Entre numa sala primeiro."); return; }
        int n;
        try {
            n = Integer.parseInt(Protocolo.campo(campos, "N", "10").trim());
        } catch (NumberFormatException e) {
            erro("09", "Quantidade invalida."); return;
        }
        if (n < 1) n = 1;

        List<String> ultimas = sala.ultimas(n);
        // o bloqueio garante que nenhuma difusao se intercale no meio do historico
        synchronized (this) {
            enviar(Protocolo.montar("CMD", "OK", "INFO", "Historico (" + ultimas.size() + " mensagens):",
                                    "N", String.valueOf(ultimas.size())));
            for (String m : ultimas) enviar(m);
        }
    }

    private void erro(String codigo, String info) {
        enviar(Protocolo.montar("CMD", "ERR", "COD", codigo, "INFO", info));
    }

    /** Libera os recursos e avisa a sala quando o cliente se desconecta. */
    private void encerrar() {
        if (sala != null) {
            sala.difundir(Protocolo.montar("CMD", "NOTIF", "INFO", getApelido() + " desconectou."), this);
            sala.sair(this);
            servidor.removerSalaSeVazia(sala);
        }
        servidor.liberarApelido(apelido);
        servidor.removerConexao(this);
        try { socket.close(); } catch (IOException e) { }
        servidor.log("Sessao encerrada: " + getApelido());
    }
}
