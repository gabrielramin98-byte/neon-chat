import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

/**
 * Thread secundaria do cliente. Permanece bloqueada lendo o socket e
 * exibe na tela, de forma legivel, as mensagens recebidas do servidor.
 */
public class ThreadRecebimento extends Thread {

    private final Socket socket;

    public ThreadRecebimento(Socket socket) {
        this.socket = socket;
        setDaemon(true);   // nao impede o encerramento da aplicacao
    }

    @Override
    public void run() {
        try {
            BufferedReader entrada = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8"));

            String linha;
            while ((linha = entrada.readLine()) != null) {
                exibir(Protocolo.analisar(linha));
            }
            System.out.println("[!] Conexao encerrada pelo servidor.");
        } catch (IOException e) {
            System.out.println("[!] Conexao perdida: " + e.getMessage());
        }
    }

    /** Formata a mensagem recebida conforme o comando. */
    private void exibir(Map<String, String> campos) {
        String cmd = Protocolo.campo(campos, "CMD", "").toUpperCase();

        if (cmd.equals("MSG")) {
            System.out.println("[" + Protocolo.campo(campos, "TS", "--:--") + "] "
                    + Protocolo.campo(campos, "FROM", "?") + ": "
                    + Protocolo.campo(campos, "TEXT", ""));

        } else if (cmd.equals("PRIV")) {
            System.out.println("[" + Protocolo.campo(campos, "TS", "--:--") + "] (privado) "
                    + Protocolo.campo(campos, "FROM", "?") + ": "
                    + Protocolo.campo(campos, "TEXT", ""));

        } else if (cmd.equals("FILE")) {
            String para = campos.containsKey("TO") ? " (privado)" : "";
            String id = Protocolo.campo(campos, "ID", "?");
            System.out.println("[" + Protocolo.campo(campos, "TS", "--:--") + "]" + para + " "
                    + Protocolo.campo(campos, "FROM", "?") + " enviou o arquivo #" + id + ": "
                    + Protocolo.campo(campos, "NAME", "?") + " (" + tamanho(campos) + ")"
                    + "  -> /baixar " + id);

        } else if (cmd.equals("FILEDATA")) {
            try {
                byte[] dados = Base64.getDecoder().decode(Protocolo.campo(campos, "DATA", ""));
                Path salvo = Arquivo.salvarSemSobrescrever(Arquivo.pastaRecebidos(),
                        Protocolo.campo(campos, "NAME", "arquivo"), dados);
                System.out.println("[*] Arquivo #" + Protocolo.campo(campos, "ID", "?") + " salvo em "
                        + salvo.toAbsolutePath());
            } catch (IllegalArgumentException | IOException e) {
                System.out.println("[!] Nao foi possivel salvar o arquivo: " + e.getMessage());
            }

        } else if (cmd.equals("ROOMS")) {
            String lista = Protocolo.campo(campos, "LIST", "");
            System.out.println("[*] Salas ativas: " + (lista.isEmpty() ? "(nenhuma)" : lista));

        } else if (cmd.equals("USERS")) {
            System.out.println("[*] Sala " + Protocolo.campo(campos, "ROOM", "?")
                    + " - usuarios: " + Protocolo.campo(campos, "LIST", ""));

        } else if (cmd.equals("NOTIF")) {
            System.out.println("[*] " + Protocolo.campo(campos, "INFO", ""));

        } else if (cmd.equals("ERR")) {
            System.out.println("[ERRO " + Protocolo.campo(campos, "COD", "??") + "] "
                    + Protocolo.campo(campos, "INFO", ""));

        } else if (cmd.equals("OK")) {
            System.out.println("[ok] " + Protocolo.campo(campos, "INFO", ""));

        } else {
            System.out.println("[?] " + campos);
        }
    }

    private static String tamanho(Map<String, String> campos) {
        try {
            return Arquivo.tamanhoLegivel(Long.parseLong(Protocolo.campo(campos, "SIZE", "0")));
        } catch (NumberFormatException e) {
            return "?";
        }
    }
}
