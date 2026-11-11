import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Emoticons do chat.
 *
 * Os emojis sao caracteres Unicode comuns: viajam dentro do campo TEXT
 * como qualquer outro texto (o socket usa UTF-8), sem mudar o protocolo.
 * Os clientes apenas convertem os atalhos digitados (":)", "<3", ...) no
 * emoji correspondente antes de enviar a mensagem.
 */
public class Emoticons {

    /** Atalho digitado -> emoji. */
    public static final Map<String, String> ATALHOS = new LinkedHashMap<String, String>();
    static {
        ATALHOS.put(":)",  "🙂");
        ATALHOS.put(":-)", "🙂");
        ATALHOS.put(":D",  "😀");
        ATALHOS.put("xD",  "😆");
        ATALHOS.put(";)",  "😉");
        ATALHOS.put(":(",  "🙁");
        ATALHOS.put(":'(", "😢");
        ATALHOS.put(":P",  "😛");
        ATALHOS.put(":O",  "😮");
        ATALHOS.put(":*",  "😘");
        ATALHOS.put("B)",  "😎");
        ATALHOS.put(":|",  "😐");
        ATALHOS.put("<3",  "💖");
        ATALHOS.put("(y)", "👍");
        ATALHOS.put("(n)", "👎");
    }

    /** Emojis oferecidos no seletor da tela de chat. */
    public static final String[] SELETOR = {
        "😀", "😂", "🙂", "😉", "😍", "😘", "😎", "🤔",
        "😮", "😢", "😭", "😡", "😴", "🤯", "🥳", "🙃",
        "👍", "👎", "👏", "🙏", "👋", "💪", "🤝", "🙌",
        "💖", "🔥", "🎉", "⭐", "✅", "❌", "💡", "☕"
    };

    private static final Pattern PADRAO;
    static {
        StringBuilder alternativas = new StringBuilder();
        for (String atalho : ATALHOS.keySet()) {
            if (alternativas.length() > 0) alternativas.append('|');
            alternativas.append(Pattern.quote(atalho));
        }
        // so troca o atalho isolado (entre espacos), para nao mexer em "http://" ou "a:b"
        PADRAO = Pattern.compile("(?<!\\S)(" + alternativas + ")(?!\\S)");
    }

    /** Troca os atalhos do texto pelos emojis correspondentes. */
    public static String converter(String texto) {
        Matcher m = PADRAO.matcher(texto);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(ATALHOS.get(m.group(1))));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Lista legivel dos atalhos, para o comando de ajuda. */
    public static String listaAtalhos() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : ATALHOS.entrySet()) {
            if (sb.length() > 0) sb.append("  ");
            sb.append(e.getKey()).append(' ').append(e.getValue());
        }
        return sb.toString();
    }
}
