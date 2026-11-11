import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aplicacao servidora do chat.
 *
 * A thread principal permanece bloqueada em accept(), aguardando novas
 * conexoes. Para cada cliente aceito e' criada uma ThreadCliente, de modo
 * que o servidor atende varios clientes simultaneamente (multithreaded).
 *
 * O servidor e' a entidade mediadora (nenhum cliente fala com outro
 * diretamente) e o repositorio dos dados comuns: apelidos em uso e salas
 * ativas com seus participantes e historicos, e arquivos enviados.
 *
 * Pode ser executado pelo terminal ou controlado pela TelaServidor
 * (interface grafica), que recebe o log atraves de um Ouvinte.
 *
 * Uso: java Servidor [porta]
 */
public class Servidor {

    public static final int PORTA_PADRAO = 5050;
    private static final int MAX_ARQUIVOS = 40;   // arquivos mais antigos sao descartados

    /** Recebe as linhas de log do servidor (usado pela interface grafica). */
    public interface Ouvinte {
        void registrar(String linha);
    }

    private final Map<String, Sala> salas = new HashMap<String, Sala>();
    private final Map<String, ThreadCliente> usuarios = new HashMap<String, ThreadCliente>();
    private final Set<ThreadCliente> conexoes = new LinkedHashSet<ThreadCliente>();
    private final Map<Integer, Arquivo> arquivos = new LinkedHashMap<Integer, Arquivo>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Arquivo> maisAntigo) {
            return size() > MAX_ARQUIVOS;
        }
    };
    private int proximoIdArquivo = 1;

    private ServerSocket servidor;
    private volatile boolean rodando = false;
    private volatile Ouvinte ouvinte;

    public static void main(String[] args) {
        int porta = PORTA_PADRAO;
        if (args.length > 0) {
            try {
                porta = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Porta invalida. Usando " + PORTA_PADRAO + ".");
            }
        }
        Servidor s = new Servidor();
        try {
            s.abrir(porta);
            s.aceitarConexoes();
        } catch (IOException e) {
            System.out.println("[SERVIDOR] Erro: " + e.getMessage());
        }
    }

    public void setOuvinte(Ouvinte ouvinte) {
        this.ouvinte = ouvinte;
    }

    public boolean isRodando() {
        return rodando;
    }

    /** Abre o socket servidor na porta. Lanca excecao se a porta estiver ocupada. */
    public void abrir(int porta) throws IOException {
        servidor = new ServerSocket(porta);
        rodando = true;
        log("Escutando na porta " + porta + ".");
    }

    /** Laco principal: bloqueia em accept() ate o servidor ser parado. */
    public void aceitarConexoes() {
        try {
            while (rodando) {
                Socket socket = servidor.accept();   // bloqueia ate chegar conexao
                log("Conexao de " + socket.getInetAddress().getHostAddress());
                ThreadCliente t = new ThreadCliente(socket, this);
                t.start();                            // atendimento em thread propria
            }
        } catch (IOException e) {
            if (rodando) log("Erro: " + e.getMessage());
        } finally {
            parar();
        }
    }

    /** Fecha o socket servidor e desconecta todos os clientes. */
    public void parar() {
        List<ThreadCliente> ativos;
        synchronized (this) {
            if (!rodando) return;
            rodando = false;
            ativos = new ArrayList<ThreadCliente>(conexoes);
        }
        try { if (servidor != null) servidor.close(); } catch (IOException e) { }
        for (ThreadCliente c : ativos) c.desconectar();
        log("Servidor parado.");
    }

    /** Escreve no console e repassa ao ouvinte (se houver). */
    public void log(String texto) {
        System.out.println("[SERVIDOR] " + texto);
        Ouvinte o = ouvinte;
        if (o != null) {
            o.registrar("[" + new SimpleDateFormat("HH:mm:ss").format(new Date()) + "] " + texto);
        }
    }

    // ---------- controle de conexoes ----------

    public synchronized void adicionarConexao(ThreadCliente c) {
        conexoes.add(c);
    }

    public synchronized void removerConexao(ThreadCliente c) {
        conexoes.remove(c);
    }

    /** Envia uma notificacao a todos os clientes conectados. */
    public void avisarTodos(String texto) {
        List<ThreadCliente> ativos;
        synchronized (this) {
            ativos = new ArrayList<ThreadCliente>(conexoes);
        }
        String linha = Protocolo.montar("CMD", "NOTIF", "INFO", "[Aviso do servidor] " + texto);
        for (ThreadCliente c : ativos) c.enviar(linha);
        log("Aviso enviado a " + ativos.size() + " cliente(s): " + texto);
    }

    // ---------- controle de apelidos (dados comuns) ----------

    /** Reserva o apelido. Devolve false se ja estiver em uso. */
    public synchronized boolean reservarApelido(String apelido, ThreadCliente c) {
        if (usuarios.containsKey(apelido)) return false;
        usuarios.put(apelido, c);
        return true;
    }

    public synchronized void liberarApelido(String apelido) {
        if (apelido != null) usuarios.remove(apelido);
    }

    /** Devolve a conexao do usuario com o apelido, ou null. */
    public synchronized ThreadCliente buscarUsuario(String apelido) {
        return usuarios.get(apelido);
    }

    // ---------- arquivos (dados comuns) ----------

    /** Guarda o arquivo recebido e devolve-o ja numerado. */
    public synchronized Arquivo guardarArquivo(String remetente, String destino, String nome, byte[] dados) {
        Arquivo a = new Arquivo(proximoIdArquivo++, remetente, destino, nome, dados);
        arquivos.put(a.getId(), a);
        return a;
    }

    /** Devolve o arquivo pelo ID, ou null se nao existir (ou ja descartado). */
    public synchronized Arquivo buscarArquivo(int id) {
        return arquivos.get(id);
    }

    // ---------- controle de salas (dados comuns) ----------

    /** Devolve a sala pelo nome, criando-a caso ainda nao exista. */
    public synchronized Sala obterSala(String nome) {
        Sala s = salas.get(nome);
        if (s == null) {
            s = new Sala(nome);
            salas.put(nome, s);
            log("Sala criada: " + nome);
        }
        return s;
    }

    /** Remove a sala caso tenha ficado sem participantes. */
    public synchronized void removerSalaSeVazia(Sala s) {
        if (s != null && s.vazia()) {
            salas.remove(s.getNome());
            log("Sala removida: " + s.getNome());
        }
    }

    /** Nomes das salas ativas, em ordem alfabetica, separados por virgula. */
    public synchronized String listaSalas() {
        List<String> nomes = new ArrayList<String>(salas.keySet());
        Collections.sort(nomes, String.CASE_INSENSITIVE_ORDER);
        return String.join(",", nomes);
    }

    // ---------- consultas para o painel grafico ----------

    /** Linhas {apelido, sala, endereco} dos usuarios identificados. */
    public synchronized List<String[]> resumoUsuarios() {
        List<String[]> linhas = new ArrayList<String[]>();
        for (ThreadCliente c : usuarios.values()) {
            linhas.add(new String[] { c.getApelido(), c.getNomeSala(), c.getEndereco() });
        }
        linhas.sort(Comparator.comparing((String[] l) -> l[0], String.CASE_INSENSITIVE_ORDER));
        return linhas;
    }

    /** Linhas {sala, participantes, mensagens no historico}. */
    public synchronized List<String[]> resumoSalas() {
        List<String[]> linhas = new ArrayList<String[]>();
        for (Sala s : salas.values()) {
            linhas.add(new String[] { s.getNome(), String.valueOf(s.quantidade()),
                                      String.valueOf(s.tamanhoHistorico()) });
        }
        linhas.sort(Comparator.comparing((String[] l) -> l[0], String.CASE_INSENSITIVE_ORDER));
        return linhas;
    }

    public synchronized int totalConexoes() {
        return conexoes.size();
    }
}
