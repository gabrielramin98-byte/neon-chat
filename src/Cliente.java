import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Aplicacao cliente do chat.
 *
 * Utiliza duas threads:
 *   - a thread principal (esta) le os comandos digitados no teclado e os
 *     envia ao servidor;
 *   - a ThreadRecebimento fica bloqueada na leitura do socket e exibe as
 *     mensagens que chegam.
 *
 * A separacao e' necessaria: com uma unica thread, a aplicacao ficaria
 * bloqueada esperando o teclado e nao conseguiria receber mensagens dos
 * demais participantes.
 *
 * Uso: java Cliente [ip] [porta]
 */
public class Cliente {

    public static void main(String[] args) {
        String ip = (args.length > 0) ? args[0] : "127.0.0.1";
        int porta = Servidor.PORTA_PADRAO;
        if (args.length > 1) {
            try {
                porta = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.out.println("Porta invalida. Usando " + Servidor.PORTA_PADRAO + ".");
            }
        }

        Socket socket = null;
        try {
            socket = new Socket(ip, porta);
            PrintWriter saida = new PrintWriter(
                    new java.io.OutputStreamWriter(socket.getOutputStream(), "UTF-8"));

            System.out.println("Conectado a " + ip + ":" + porta);
            System.out.println("Comandos: /login <apelido>  /join <sala>  /users  /salas  /hist <n>");
            System.out.println("          /msg <apelido> <texto>  /sair");
            System.out.println("          /arquivo <caminho>  /arqmsg <apelido> <caminho>  /baixar <id>  /emojis");
            System.out.println("Qualquer outro texto e' enviado como mensagem para a sala atual.");
            System.out.println("--------------------------------------------------------------");

            new ThreadRecebimento(socket).start();   // thread de escuta do socket

            BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));
            String linha;
            while ((linha = teclado.readLine()) != null) {
                linha = linha.trim();
                if (linha.isEmpty()) continue;

                String envio = traduzir(linha);
                if (envio == null) continue;

                saida.println(envio);
                saida.flush();

                if (envio.startsWith("CMD=QUIT")) break;
            }
        } catch (IOException e) {
            System.out.println("Erro de conexao: " + e.getMessage());
        } finally {
            try { if (socket != null) socket.close(); } catch (IOException e) { }
            System.out.println("Cliente encerrado.");
            System.exit(0);
        }
    }

    /** Converte o que foi digitado em uma mensagem do protocolo. */
    private static String traduzir(String linha) {
        if (!linha.startsWith("/")) {
            return Protocolo.montar("CMD", "MSG", "TEXT", Emoticons.converter(linha));
        }

        String[] partes = linha.split("\\s+", 2);
        String comando = partes[0].toLowerCase();
        String argumento = (partes.length > 1) ? partes[1].trim() : "";

        if (comando.equals("/login")) {
            if (argumento.isEmpty()) { System.out.println("Uso: /login <apelido>"); return null; }
            return Protocolo.montar("CMD", "LOGIN", "USER", argumento);
        }
        if (comando.equals("/join")) {
            if (argumento.isEmpty()) { System.out.println("Uso: /join <sala>"); return null; }
            return Protocolo.montar("CMD", "JOIN", "ROOM", argumento);
        }
        if (comando.equals("/users")) {
            return Protocolo.montar("CMD", "USERS", "");
        }
        if (comando.equals("/salas")) {
            return Protocolo.montar("CMD", "ROOMS", "");
        }
        if (comando.equals("/msg")) {
            String[] destTexto = argumento.split("\\s+", 2);
            if (destTexto.length < 2) { System.out.println("Uso: /msg <apelido> <texto>"); return null; }
            return Protocolo.montar("CMD", "PRIV", "TO", destTexto[0], "TEXT", Emoticons.converter(destTexto[1]));
        }
        if (comando.equals("/arquivo")) {
            if (argumento.isEmpty()) { System.out.println("Uso: /arquivo <caminho>"); return null; }
            return montarArquivo(null, argumento);
        }
        if (comando.equals("/arqmsg")) {
            String[] destCaminho = argumento.split("\\s+", 2);
            if (destCaminho.length < 2) { System.out.println("Uso: /arqmsg <apelido> <caminho>"); return null; }
            return montarArquivo(destCaminho[0], destCaminho[1]);
        }
        if (comando.equals("/baixar")) {
            if (argumento.isEmpty()) { System.out.println("Uso: /baixar <id>"); return null; }
            return Protocolo.montar("CMD", "GET", "ID", argumento);
        }
        if (comando.equals("/emojis")) {
            System.out.println("Atalhos: " + Emoticons.listaAtalhos());
            return null;
        }
        if (comando.equals("/hist")) {
            String n = argumento.isEmpty() ? "10" : argumento;
            return Protocolo.montar("CMD", "HIST", "N", n);
        }
        if (comando.equals("/sair")) {
            return Protocolo.montar("CMD", "QUIT", "");
        }

        System.out.println("Comando desconhecido. Use /login /join /users /salas /msg /hist "
                           + "/arquivo /arqmsg /baixar /emojis /sair");
        return null;
    }

    /** Le o arquivo do disco e monta o comando FILE (destino null = sala atual). */
    private static String montarArquivo(String destino, String caminho) {
        File f = new File(caminho.replaceAll("^[\"']|[\"']$", ""));   // aceita caminho entre aspas
        try {
            String dados = Arquivo.lerBase64(f);
            System.out.println("[*] Enviando " + f.getName() + " (" + Arquivo.tamanhoLegivel(f.length()) + ")...");
            if (destino == null) return Protocolo.montar("CMD", "FILE", "NAME", f.getName(), "DATA", dados);
            return Protocolo.montar("CMD", "FILE", "TO", destino, "NAME", f.getName(), "DATA", dados);
        } catch (IOException e) {
            System.out.println("Nao foi possivel ler o arquivo: " + e.getMessage());
            return null;
        }
    }
}
