import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * Arquivo enviado pelo chat.
 *
 * TRANSPORTE: o protocolo e' textual (uma mensagem por linha), entao o
 * conteudo binario do arquivo e' codificado em Base64 e vai no campo DATA.
 * O '=' de preenchimento do Base64 e' um caractere reservado, mas o byte
 * stuffing do Protocolo ja cuida dele.
 *
 * REPOSITORIO: o servidor guarda cada arquivo recebido com um numero (ID)
 * e difunde apenas o aviso (ID, nome, tamanho). Cada cliente baixa o
 * conteudo sob demanda com GET, assim ninguem recebe megabytes que nao
 * pediu e quem entra depois na sala ainda consegue baixar pelo historico.
 *
 * Os metodos estaticos sao utilitarios usados pelos clientes.
 */
public class Arquivo {

    public static final int TAMANHO_MAXIMO = 5 * 1024 * 1024;   // 5 MB por arquivo

    private final int id;
    private final String remetente;
    private final String destino;     // null = arquivo enviado para a sala
    private final String nome;
    private final byte[] dados;

    public Arquivo(int id, String remetente, String destino, String nome, byte[] dados) {
        this.id = id;
        this.remetente = remetente;
        this.destino = destino;
        this.nome = nome;
        this.dados = dados;
    }

    public int getId()          { return id; }
    public String getNome()     { return nome; }
    public int getTamanho()     { return dados.length; }
    public String getBase64()   { return Base64.getEncoder().encodeToString(dados); }

    /** Arquivo de sala: qualquer um baixa. Privado: so remetente e destinatario. */
    public boolean podeBaixar(String apelido) {
        return destino == null || remetente.equals(apelido) || destino.equals(apelido);
    }

    // ---------- utilitarios ----------

    /** Le o arquivo do disco e devolve o conteudo em Base64. */
    public static String lerBase64(File f) throws IOException {
        if (!f.isFile()) throw new IOException("arquivo nao encontrado: " + f);
        if (f.length() == 0) throw new IOException("arquivo vazio");
        if (f.length() > TAMANHO_MAXIMO) {
            throw new IOException("arquivo maior que " + tamanhoLegivel(TAMANHO_MAXIMO));
        }
        return Base64.getEncoder().encodeToString(Files.readAllBytes(f.toPath()));
    }

    /** Remove caminhos do nome recebido (evita gravar fora da pasta escolhida). */
    public static String nomeSeguro(String nome) {
        String n = nome.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).trim();
        return (n.isEmpty() || n.equals(".") || n.equals("..")) ? "arquivo" : n;
    }

    /** Grava na pasta sem sobrescrever: foto.png, foto (1).png, foto (2).png... */
    public static Path salvarSemSobrescrever(Path pasta, String nome, byte[] dados) throws IOException {
        Files.createDirectories(pasta);
        String n = nomeSeguro(nome);
        int ponto = n.lastIndexOf('.');
        String base = (ponto > 0) ? n.substring(0, ponto) : n;
        String ext  = (ponto > 0) ? n.substring(ponto) : "";
        Path destino = pasta.resolve(n);
        for (int i = 1; Files.exists(destino); i++) {
            destino = pasta.resolve(base + " (" + i + ")" + ext);
        }
        return Files.write(destino, dados);
    }

    public static Path pastaRecebidos() {
        return Paths.get("recebidos");
    }

    public static String tamanhoLegivel(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
