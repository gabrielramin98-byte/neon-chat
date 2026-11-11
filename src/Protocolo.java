import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tratamento das mensagens textuais trocadas entre cliente e servidor.
 *
 * FORMATO: uma mensagem e' uma unica linha de texto, terminada por '\n',
 * composta por pares campo=valor separados por ';'.
 *
 *     CMD=MSG;ROOM=geral;TEXT=ola pessoal
 *
 * BYTE STUFFING: os caracteres reservados ';', '=' e '\' podem aparecer
 * dentro do conteudo. Para que nao sejam confundidos com delimitadores,
 * sao precedidos por '\' na montagem (escape) e restaurados na analise
 * (unescape). O analisador percorre a linha caractere a caractere e
 * desconsidera como delimitador todo simbolo escapado.
 */
public class Protocolo {

    public static final char SEP_CAMPO = ';';
    public static final char SEP_VALOR = '=';
    public static final char ESCAPE    = '\\';

    /** Monta a linha de texto a partir dos pares campo=valor. */
    public static String montar(Map<String, String> campos) {
        StringBuilder sb = new StringBuilder();
        boolean primeiro = true;
        for (Map.Entry<String, String> e : campos.entrySet()) {
            if (!primeiro) sb.append(SEP_CAMPO);
            sb.append(escapar(e.getKey()));
            sb.append(SEP_VALOR);
            sb.append(escapar(e.getValue()));
            primeiro = false;
        }
        return sb.toString();
    }

    /** Analisa a linha recebida e devolve os pares campo=valor. */
    public static Map<String, String> analisar(String linha) {
        Map<String, String> campos = new LinkedHashMap<String, String>();
        StringBuilder token = new StringBuilder();
        String chave = null;
        boolean escapado = false;

        for (int i = 0; i < linha.length(); i++) {
            char c = linha.charAt(i);

            if (escapado) {                 // caractere anterior foi '\':
                token.append(c);            // este e' conteudo literal
                escapado = false;
            } else if (c == ESCAPE) {
                escapado = true;            // proximo caractere e' literal
            } else if (c == SEP_VALOR && chave == null) {
                chave = token.toString();   // fim do nome do campo
                token.setLength(0);
            } else if (c == SEP_CAMPO) {
                gravar(campos, chave, token.toString());
                chave = null;
                token.setLength(0);
            } else {
                token.append(c);
            }
        }
        gravar(campos, chave, token.toString());
        return campos;
    }

    private static void gravar(Map<String, String> campos, String chave, String valor) {
        if (chave != null) {
            campos.put(chave.trim().toUpperCase(), valor);
        } else if (valor.trim().length() > 0) {
            campos.put(valor.trim().toUpperCase(), "");
        }
    }

    /** Insere o caractere de escape antes de cada caractere reservado. */
    private static String escapar(String texto) {
        if (texto == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c == SEP_CAMPO || c == SEP_VALOR || c == ESCAPE) {
                sb.append(ESCAPE);
            }
            if (c == '\n' || c == '\r') continue;  // a linha nao pode ser quebrada
            sb.append(c);
        }
        return sb.toString();
    }

    /** Atalho para montar mensagens: montar("CMD","MSG","TEXT","ola") */
    public static String montar(String... paresOrdenados) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        for (int i = 0; i + 1 < paresOrdenados.length; i += 2) {
            m.put(paresOrdenados[i], paresOrdenados[i + 1]);
        }
        return montar(m);
    }

    /** Devolve o valor do campo ou o padrao informado, se ausente. */
    public static String campo(Map<String, String> campos, String nome, String padrao) {
        String v = campos.get(nome.toUpperCase());
        return (v == null) ? padrao : v;
    }
}
