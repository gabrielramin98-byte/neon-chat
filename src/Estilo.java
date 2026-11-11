import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;

/**
 * Cores, fontes e componentes visuais compartilhados pelas telas.
 * Nao participa da comunicacao: e' apenas apresentacao.
 *
 * Tema escuro "neon": fundo azul quase preto, destaques em ciano e violeta,
 * grade sutil ao fundo e brilho nos componentes em foco. Usa o look and
 * feel Metal (multiplataforma) com um tema proprio, para que as cores sejam
 * respeitadas tambem no macOS; botoes e campos sao desenhados aqui mesmo.
 */
public class Estilo {

    public static final Color FUNDO         = new Color(0x070B14);
    public static final Color PAINEL        = new Color(0x0C1222);
    public static final Color SUPERFICIE    = new Color(0x111A2E);
    public static final Color LISTRA        = new Color(0x0E1528);   // linha alternada das tabelas
    public static final Color GRADE         = new Color(0x0D1525);   // grade do fundo do chat
    public static final Color BORDA         = new Color(0x1A2440);
    public static final Color BORDA_FORTE   = new Color(0x2A3A60);
    public static final Color DESTAQUE      = new Color(0x0E2A3C);   // item selecionado
    public static final Color PRIMARIA      = new Color(0x00E5FF);   // ciano neon
    public static final Color SECUNDARIA    = new Color(0xB45CFF);   // violeta neon
    public static final Color CABECALHO     = new Color(0x0A1020);
    public static final Color CABECALHO_TXT = new Color(0x6FA8C8);
    public static final Color ROTULO        = new Color(0x4F9DC0);
    public static final Color TEXTO         = new Color(0xE4ECFF);
    public static final Color TEXTO_SUAVE   = new Color(0x6F7FA3);
    public static final Color TERMINAL      = new Color(0x7CFFCB);   // texto do log do servidor
    public static final Color SUCESSO       = new Color(0x2BFFA8);
    public static final Color ERRO          = new Color(0xFF4D73);
    public static final Color PRIVADO       = new Color(0xFF5FD2);

    private static final Color[] CORES_USUARIO = {
        new Color(0xFF8A3D), new Color(0x3DFF9A), new Color(0x4DA3FF), new Color(0xD07CFF),
        new Color(0xFF5C8A), new Color(0x2EE6D6), new Color(0xFFD23D), new Color(0x9B8CFF)
    };

    /** Fonte do sistema (lida do look and feel nativo antes de trocar para o Metal). */
    private static Font fonteBase = null;
    private static String familiaMono = null;

    public static void aplicarLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            fonteBase = UIManager.getFont("Label.font");
        } catch (Exception e) {
            // segue com a fonte padrao
        }
        try {
            MetalLookAndFeel.setCurrentTheme(new TemaNeon());
            UIManager.setLookAndFeel(new MetalLookAndFeel());
        } catch (Exception e) {
            return;   // mantem o look and feel nativo
        }
        UIManager.put("ScrollBarUI", BarraRolagemUI.class.getName());
        UIManager.put("TextField.caretForeground", new ColorUIResource(PRIMARIA));
        UIManager.put("FormattedTextField.caretForeground", new ColorUIResource(PRIMARIA));
        UIManager.put("ToolTip.background", new ColorUIResource(SUPERFICIE));
        UIManager.put("ToolTip.foreground", new ColorUIResource(TEXTO));
        UIManager.put("ToolTip.border", BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDA_FORTE), BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(BORDA_FORTE));
        UIManager.put("List.focusCellHighlightBorder", BorderFactory.createEmptyBorder());
        UIManager.put("Table.focusCellHighlightBorder", BorderFactory.createEmptyBorder());
        UIManager.put("SplitPane.dividerSize", 12);
        UIManager.put("OptionPane.questionIcon",    new IconeAlerta("?", PRIMARIA));
        UIManager.put("OptionPane.informationIcon", new IconeAlerta("i", PRIMARIA));
        UIManager.put("OptionPane.warningIcon",     new IconeAlerta("!", new Color(0xFFD23D)));
        UIManager.put("OptionPane.errorIcon",       new IconeAlerta("×", ERRO));
        traduzirDialogos();
    }

    /** Textos dos dialogos padrao (o Metal nao traz traducao para o portugues). */
    private static void traduzirDialogos() {
        String[][] textos = {
            { "OptionPane.yesButtonText", "Sim" },              { "OptionPane.noButtonText", "Não" },
            { "OptionPane.cancelButtonText", "Cancelar" },      { "OptionPane.okButtonText", "OK" },
            { "FileChooser.openButtonText", "Abrir" },          { "FileChooser.saveButtonText", "Salvar" },
            { "FileChooser.cancelButtonText", "Cancelar" },     { "FileChooser.updateButtonText", "Atualizar" },
            { "FileChooser.helpButtonText", "Ajuda" },          { "FileChooser.directoryOpenButtonText", "Abrir" },
            { "FileChooser.openDialogTitleText", "Abrir" },     { "FileChooser.saveDialogTitleText", "Salvar" },
            { "FileChooser.lookInLabelText", "Procurar em:" },  { "FileChooser.saveInLabelText", "Salvar em:" },
            { "FileChooser.fileNameLabelText", "Nome do arquivo:" },
            { "FileChooser.folderNameLabelText", "Nome da pasta:" },
            { "FileChooser.filesOfTypeLabelText", "Tipo:" },
            { "FileChooser.acceptAllFileFilterText", "Todos os arquivos" },
            { "FileChooser.upFolderToolTipText", "Pasta acima" },
            { "FileChooser.homeFolderToolTipText", "Pasta pessoal" },
            { "FileChooser.newFolderToolTipText", "Nova pasta" },
            { "FileChooser.listViewButtonToolTipText", "Lista" },
            { "FileChooser.detailsViewButtonToolTipText", "Detalhes" },
            { "FileChooser.fileNameHeaderText", "Nome" },       { "FileChooser.fileSizeHeaderText", "Tamanho" },
            { "FileChooser.fileTypeHeaderText", "Tipo" },       { "FileChooser.fileDateHeaderText", "Modificado" },
            { "FileChooser.openButtonToolTipText", "Abrir o arquivo selecionado" },
            { "FileChooser.saveButtonToolTipText", "Salvar o arquivo" },
            { "FileChooser.cancelButtonToolTipText", "Fechar sem escolher" },
        };
        for (String[] t : textos) UIManager.put(t[0], t[1]);
        // sem letras sublinhadas (os atalhos originais apontam para letras do ingles)
        for (String chave : new String[] { "OptionPane.yesButtonMnemonic", "OptionPane.noButtonMnemonic",
                "OptionPane.cancelButtonMnemonic", "OptionPane.okButtonMnemonic", "FileChooser.openButtonMnemonic",
                "FileChooser.saveButtonMnemonic", "FileChooser.cancelButtonMnemonic", "FileChooser.lookInLabelMnemonic",
                "FileChooser.saveInLabelMnemonic", "FileChooser.fileNameLabelMnemonic",
                "FileChooser.filesOfTypeLabelMnemonic", "FileChooser.folderNameLabelMnemonic" }) {
            UIManager.put(chave, "0");
        }
    }

    /** Icone dos dialogos: circulo neon com um simbolo no centro. */
    private static class IconeAlerta implements Icon {
        private final String simbolo;
        private final Color cor;

        IconeAlerta(String simbolo, Color cor) {
            this.simbolo = simbolo;
            this.cor = cor;
        }

        @Override public int getIconWidth()  { return 40; }
        @Override public int getIconHeight() { return 40; }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = suavizar(g);
            Ellipse2D circulo = new Ellipse2D.Float(x + 5, y + 5, 30, 30);
            brilho(g2, circulo, cor, 3);
            g2.setColor(comAlfa(cor, 30));
            g2.fill(circulo);
            g2.setColor(cor);
            g2.setStroke(new BasicStroke(1.6f));
            g2.draw(circulo);
            g2.setFont(mono(Font.BOLD, 17f));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(simbolo, x + 20 - fm.stringWidth(simbolo) / 2f, y + 20 + (fm.getAscent() - fm.getDescent()) / 2f);
            g2.dispose();
        }
    }

    /** Tema do Metal: tons escuros, texto claro e ciano como cor de foco. */
    private static class TemaNeon extends DefaultMetalTheme {
        @Override public String getName() { return "Neon"; }
        @Override protected ColorUIResource getPrimary1()   { return new ColorUIResource(PRIMARIA); }
        @Override protected ColorUIResource getPrimary2()   { return new ColorUIResource(BORDA_FORTE); }
        @Override protected ColorUIResource getPrimary3()   { return new ColorUIResource(DESTAQUE); }
        @Override protected ColorUIResource getSecondary1() { return new ColorUIResource(BORDA_FORTE); }
        @Override protected ColorUIResource getSecondary2() { return new ColorUIResource(BORDA); }
        @Override protected ColorUIResource getSecondary3() { return new ColorUIResource(PAINEL); }
        @Override protected ColorUIResource getWhite()      { return new ColorUIResource(SUPERFICIE); }
        @Override protected ColorUIResource getBlack()      { return new ColorUIResource(TEXTO); }
        @Override public ColorUIResource getInactiveControlTextColor() { return new ColorUIResource(TEXTO_SUAVE); }
        @Override public ColorUIResource getInactiveSystemTextColor()  { return new ColorUIResource(TEXTO_SUAVE); }
        @Override public FontUIResource getControlTextFont() { return new FontUIResource(fonte(Font.PLAIN, 13f)); }
        @Override public FontUIResource getSystemTextFont()  { return new FontUIResource(fonte(Font.PLAIN, 13f)); }
        @Override public FontUIResource getUserTextFont()    { return new FontUIResource(fonte(Font.PLAIN, 13f)); }
        @Override public FontUIResource getMenuTextFont()    { return new FontUIResource(fonte(Font.PLAIN, 13f)); }
        @Override public FontUIResource getWindowTitleFont() { return new FontUIResource(fonte(Font.BOLD, 13f)); }
        @Override public FontUIResource getSubTextFont()     { return new FontUIResource(fonte(Font.PLAIN, 11f)); }
    }

    public static Font fonte(int estilo, float tamanho) {
        Font base = (fonteBase != null) ? fonteBase : new JLabel().getFont();
        return base.deriveFont(estilo, tamanho);
    }

    /** Fonte monoespacada, usada nos rotulos, horarios e no log ("visual de terminal"). */
    public static Font mono(int estilo, float tamanho) {
        return new Font(familiaMono(), estilo, 12).deriveFont(tamanho);
    }

    public static synchronized String familiaMono() {
        if (familiaMono == null) {
            List<String> instaladas = Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
            familiaMono = Font.MONOSPACED;
            for (String f : new String[] { "JetBrains Mono", "SF Mono", "Menlo", "Cascadia Mono", "Consolas", "DejaVu Sans Mono" }) {
                if (instaladas.contains(f)) { familiaMono = f; break; }
            }
        }
        return familiaMono;
    }

    /** Cor fixa para cada apelido, para facilitar a leitura do chat. */
    public static Color corDoUsuario(String apelido) {
        return CORES_USUARIO[Math.abs(apelido.hashCode() % CORES_USUARIO.length)];
    }

    public static Color comAlfa(Color c, int alfa) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alfa)));
    }

    /** Cor no formato #rrggbb, para uso em textos HTML dos componentes. */
    public static String hex(Color c) {
        return String.format("#%06x", c.getRGB() & 0xFFFFFF);
    }

    /** Copia do Graphics com antisserrilhamento ligado (lembrar de dispose()). */
    public static Graphics2D suavizar(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }

    /** Halo de "neon" em volta de uma forma: tracos cada vez mais finos e fortes. */
    public static void brilho(Graphics2D g2, Shape forma, Color cor, int camadas) {
        Stroke antes = g2.getStroke();
        for (int i = camadas; i >= 1; i--) {
            g2.setStroke(new BasicStroke(i * 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(comAlfa(cor, 14 + (camadas - i) * 12));
            g2.draw(forma);
        }
        g2.setStroke(antes);
    }

    // ---------- emojis ----------

    private static final Map<String, java.awt.Image> IMAGENS_EMOJI = new HashMap<String, java.awt.Image>();

    /**
     * Fim do emoji que comeca na posicao i do texto (ou o proprio i, se ali
     * nao houver emoji). Inclui seletor de variacao, tom de pele, bandeiras
     * e sequencias unidas por ZWJ (ex.: familia, profissoes).
     */
    public static int fimDoEmoji(String texto, int i) {
        int cp = texto.codePointAt(i);
        if (!ehEmoji(cp)) return i;
        int j = i + Character.charCount(cp);
        if (ehIndicadorRegional(cp) && j < texto.length() && ehIndicadorRegional(texto.codePointAt(j))) {
            return j + 2;   // bandeira: par de indicadores regionais
        }
        while (j < texto.length()) {
            int c = texto.codePointAt(j);
            if (c == 0xFE0F || (c >= 0x1F3FB && c <= 0x1F3FF)) {
                j += Character.charCount(c);
            } else if (c == 0x200D && j + 1 < texto.length() && ehEmoji(texto.codePointAt(j + 1))) {
                j += 1 + Character.charCount(texto.codePointAt(j + 1));
            } else {
                break;
            }
        }
        return j;
    }

    private static boolean ehEmoji(int cp) {
        return (cp >= 0x1F000 && cp <= 0x1FAFF) || (cp >= 0x2600 && cp <= 0x27BF)
            || (cp >= 0x2300 && cp <= 0x23FF) || cp == 0x2B50 || cp == 0x2B55;
    }

    private static boolean ehIndicadorRegional(int cp) {
        return cp >= 0x1F1E6 && cp <= 0x1F1FF;
    }

    /**
     * Emoji desenhado como icone. No macOS o Java informa para os emojis
     * coloridos uma largura menor que a do desenho, e emojis vizinhos se
     * sobrepoem no texto; como icone a largura e' exata. Cada chamada devolve
     * um icone novo (so a imagem fica em cache) para o JTextPane nao fundir
     * emojis iguais e seguidos num unico elemento.
     *
     * O JTextPane apoia o icone na linha de base; a altura informada vai so
     * ate a base e a parte de baixo do emoji e' desenhada abaixo dela, como
     * a perna de um "g", para o emoji ficar alinhado com o texto.
     */
    public static Icon iconeEmoji(String emoji, float tamanho) {
        String chave = emoji + "@" + tamanho;
        final BufferedImage x1;
        final java.awt.Image img;
        synchronized (IMAGENS_EMOJI) {
            if (!IMAGENS_EMOJI.containsKey(chave)) {
                IMAGENS_EMOJI.put(chave, new BaseMultiResolutionImage(desenharEmoji(emoji, tamanho, 1),
                                                                      desenharEmoji(emoji, tamanho, 2)));   // Retina
            }
            img = IMAGENS_EMOJI.get(chave);
            x1 = (BufferedImage) ((BaseMultiResolutionImage) img).getResolutionVariants().get(0);
        }
        final int largura = x1.getWidth(), altura = x1.getHeight();
        final int acimaDaBase = Math.round(altura * 0.95f / 1.15f);
        return new Icon() {
            @Override public int getIconWidth()  { return largura; }
            @Override public int getIconHeight() { return acimaDaBase; }
            @Override public void paintIcon(java.awt.Component c, Graphics g, int x, int y) {
                g.drawImage(img, x, y, largura, altura, null);
            }
        };
    }

    private static BufferedImage desenharEmoji(String emoji, float tamanho, int escala) {
        float px = tamanho * 1.15f * escala;
        int lado = Math.round(px * 2);
        BufferedImage tela = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = tela.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(fonte(Font.PLAIN, px));
        int base = Math.round(px * 1.3f);
        g.drawString(emoji, Math.round(px * 0.2f), base);
        g.dispose();

        // recorta a area desenhada na horizontal; na vertical vai do topo da
        // linha ate um pouco abaixo da base, para alinhar com o texto
        int esq = lado, dir = -1;
        for (int x = 0; x < lado; x++) {
            for (int y = 0; y < lado; y++) {
                if ((tela.getRGB(x, y) >>> 24) != 0) { esq = Math.min(esq, x); dir = x; break; }
            }
        }
        if (dir < 0) { esq = 0; dir = Math.round(px) - 1; }
        int margem = escala;
        int topo = Math.max(0, base - Math.round(px * 0.95f));
        int altura = Math.round(px * 1.15f);
        BufferedImage icone = new BufferedImage(dir - esq + 1 + 2 * margem, altura, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = icone.createGraphics();
        g2.drawImage(tela, margem - esq, -topo, null);
        g2.dispose();
        return icone;
    }

    // ---------- blocos de tela ----------

    /** Faixa do topo das janelas: degrade escuro, grade sutil e linha neon embaixo. */
    public static JPanel cabecalho(JLabel titulo, JLabel subtitulo, JComponent direita) {
        return cabecalho(null, titulo, subtitulo, direita);
    }

    /** Cabecalho com um indicador (LED) a esquerda do titulo. */
    public static JPanel cabecalho(Led led, JLabel titulo, JLabel subtitulo, JComponent direita) {
        JPanel p = new JPanel(new BorderLayout(12, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = suavizar(g);
                int w = getWidth(), h = getHeight();
                g2.setPaint(new GradientPaint(0, 0, new Color(0x0B1226), w, 0, new Color(0x140F30)));
                g2.fillRect(0, 0, w, h);
                g2.setColor(new Color(255, 255, 255, 7));
                for (int x = 0; x < w; x += 22) g2.drawLine(x, 0, x, h);
                for (int y = 0; y < h; y += 22) g2.drawLine(0, y, w, y);
                for (int i = 0; i < 6; i++) {   // brilho que "escorre" da linha neon
                    g2.setPaint(new GradientPaint(0, 0, comAlfa(PRIMARIA, 30 - i * 5), w, 0, comAlfa(SECUNDARIA, 30 - i * 5)));
                    g2.fillRect(0, h - 2 - i - 1, w, 1);
                }
                g2.setPaint(new GradientPaint(0, 0, PRIMARIA, w, 0, SECUNDARIA));
                g2.fillRect(0, h - 2, w, 2);
                g2.dispose();
            }
        };
        p.setBackground(CABECALHO);
        p.setBorder(BorderFactory.createEmptyBorder(16, 20, 18, 20));

        titulo.setForeground(Color.WHITE);
        titulo.setFont(fonte(Font.BOLD, 20f));
        subtitulo.setForeground(CABECALHO_TXT);
        subtitulo.setFont(mono(Font.PLAIN, 11.5f));

        JPanel textos = new JPanel(new BorderLayout(0, 4));
        textos.setOpaque(false);
        textos.add(titulo, BorderLayout.NORTH);
        textos.add(subtitulo, BorderLayout.SOUTH);
        p.add(textos, BorderLayout.CENTER);
        if (led != null) p.add(led, BorderLayout.WEST);
        if (direita != null) {
            JPanel centralizar = new JPanel(new GridBagLayout());   // nao estica o componente na vertical
            centralizar.setOpaque(false);
            centralizar.add(direita);
            p.add(centralizar, BorderLayout.EAST);
        }
        return p;
    }

    public static JLabel rotuloSecao(String texto) {
        JLabel l = new JLabel(texto.toUpperCase());
        l.setFont(mono(Font.BOLD, 10.5f));
        l.setForeground(ROTULO);
        l.setBorder(BorderFactory.createEmptyBorder(14, 14, 8, 10));
        return l;
    }

    /** Area de rolagem sem borda, com o fundo indicado e a barra fina do tema. */
    public static JScrollPane rolagem(Component conteudo, Color fundo) {
        JScrollPane s = new JScrollPane(conteudo);
        s.setBorder(BorderFactory.createEmptyBorder());
        s.setBackground(fundo);
        s.getViewport().setBackground(fundo);
        JPanel canto = new JPanel();
        canto.setBackground(fundo);
        s.setCorner(JScrollPane.UPPER_RIGHT_CORNER, canto);
        return s;
    }

    /** Tabela escura: linhas alternadas, sem grade vertical e cabecalho em fonte mono. */
    public static void estilizarTabela(JTable t) {
        t.setBackground(PAINEL);
        t.setForeground(TEXTO);
        t.setFont(fonte(Font.PLAIN, 13f));
        t.setGridColor(BORDA);
        t.setShowVerticalLines(false);
        t.setShowHorizontalLines(true);
        t.setIntercellSpacing(new Dimension(0, 1));
        t.setRowHeight(30);
        t.setSelectionBackground(DESTAQUE);
        t.setSelectionForeground(Color.WHITE);
        t.setFillsViewportHeight(true);
        t.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tab, Object valor, boolean sel,
                                                           boolean foco, int linha, int coluna) {
                super.getTableCellRendererComponent(tab, valor, sel, false, linha, coluna);
                setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));
                if (!sel) {
                    setBackground(linha % 2 == 0 ? PAINEL : LISTRA);
                    setForeground(coluna == 0 ? TEXTO : TEXTO_SUAVE);
                }
                setFont(coluna == 0 ? fonte(Font.BOLD, 13f) : mono(Font.PLAIN, 12f));
                return this;
            }
        });
        JTableHeader cab = t.getTableHeader();
        cab.setReorderingAllowed(false);
        cab.setBackground(PAINEL);
        cab.setDefaultRenderer((tab, valor, sel, foco, linha, coluna) -> {
            JLabel l = new JLabel(String.valueOf(valor).toUpperCase());
            l.setFont(mono(Font.BOLD, 10.5f));
            l.setForeground(ROTULO);
            l.setOpaque(true);
            l.setBackground(PAINEL);
            l.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, BORDA_FORTE),
                    BorderFactory.createEmptyBorder(8, 14, 8, 14)));
            return l;
        });
    }

    /** Enderecos IPv4 desta maquina (para informar aos clientes da rede). */
    public static List<String> enderecosLocais() {
        List<String> ips = new ArrayList<String>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address) ips.add(a.getHostAddress());
                }
            }
        } catch (Exception e) {
            // sem acesso as interfaces: lista fica vazia
        }
        ips.add("127.0.0.1");
        return ips;
    }

    // ---------- componentes ----------

    /** Botao arredondado desenhado pelo tema (preenchido, contorno ou de perigo). */
    public static class Botao extends JButton {
        public static final int PRIMARIO = 0;   // degrade ciano -> violeta
        public static final int FANTASMA = 1;   // so contorno; acende ao passar o mouse
        public static final int PERIGO   = 2;   // contorno vermelho (sair, parar)

        private int variante;

        public Botao(String texto, int variante) {
            super(texto);
            configurar(variante);
        }

        public Botao(Icon icone, int variante) {
            super(icone);
            configurar(variante);
            setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
        }

        private void configurar(int variante) {
            this.variante = variante;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setOpaque(false);
            setFont(fonte(Font.BOLD, 13f));
            setBorder(BorderFactory.createEmptyBorder(9, 18, 9, 18));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        public void setVariante(int variante) {
            this.variante = variante;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = suavizar(g);
            int w = getWidth(), h = getHeight();
            boolean ativo = isEnabled();
            boolean sobre = ativo && getModel().isRollover();
            boolean apertado = ativo && getModel().isPressed();
            if (!ativo) g2.setComposite(AlphaComposite.SrcOver.derive(0.4f));

            RoundRectangle2D forma = new RoundRectangle2D.Float(2, 2, w - 4, h - 4, 12, 12);
            Color cor = (variante == PERIGO) ? ERRO : PRIMARIA;
            Color corTexto;
            if (variante == PRIMARIO) {
                if (sobre) brilho(g2, forma, PRIMARIA, 3);
                g2.setPaint(new GradientPaint(0, 0, apertado ? PRIMARIA.darker() : PRIMARIA,
                                              w, h, apertado ? SECUNDARIA.darker() : SECUNDARIA));
                g2.fill(forma);
                corTexto = new Color(0x04121C);
            } else {
                if (sobre) brilho(g2, forma, cor, 2);
                g2.setColor(apertado ? comAlfa(cor, 60) : sobre ? comAlfa(cor, 26) : SUPERFICIE);
                g2.fill(forma);
                g2.setColor(sobre ? cor : (variante == PERIGO) ? comAlfa(ERRO, 150) : BORDA_FORTE);
                g2.setStroke(new BasicStroke(1.2f));
                g2.draw(forma);
                corTexto = (sobre || variante == PERIGO) ? cor : TEXTO;
            }

            String texto = (getText() == null) ? "" : getText();
            Icon icone = getIcon();
            FontMetrics fm = g2.getFontMetrics(getFont());
            int larguraTexto = texto.isEmpty() ? 0 : fm.stringWidth(texto);
            int larguraIcone = (icone == null) ? 0 : icone.getIconWidth();
            int vao = (icone != null && larguraTexto > 0) ? 6 : 0;
            int x = (w - larguraIcone - vao - larguraTexto) / 2;
            if (icone != null) {
                icone.paintIcon(this, g2, x, (h - icone.getIconHeight()) / 2 - 1);
                x += larguraIcone + vao;
            }
            if (larguraTexto > 0) {
                g2.setFont(getFont());
                g2.setColor(corTexto);
                g2.drawString(texto, x, (h + fm.getAscent() - fm.getDescent()) / 2);
            }
            g2.dispose();
        }
    }

    /** Campo de texto arredondado, com brilho ao receber o foco e dica enquanto vazio. */
    public static class CampoTexto extends JTextField {
        private final String dica;

        public CampoTexto(String dica) {
            this.dica = dica;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(9, 12, 9, 12));
            setFont(fonte(Font.PLAIN, 13f));
            setForeground(TEXTO);
            setDisabledTextColor(TEXTO_SUAVE);
            setCaretColor(PRIMARIA);
            setSelectionColor(new Color(0x13456A));
            setSelectedTextColor(Color.WHITE);
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { repaint(); }
                @Override public void focusLost(FocusEvent e)   { repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = suavizar(g);
            RoundRectangle2D forma = new RoundRectangle2D.Float(2, 2, getWidth() - 4, getHeight() - 4, 12, 12);
            g2.setColor(isEnabled() ? SUPERFICIE : PAINEL);
            g2.fill(forma);
            if (isFocusOwner()) {
                brilho(g2, forma, PRIMARIA, 2);
                g2.setColor(PRIMARIA);
            } else {
                g2.setColor(BORDA_FORTE);
            }
            g2.setStroke(new BasicStroke(1.2f));
            g2.draw(forma);
            g2.dispose();

            super.paintComponent(g);

            if (getText().isEmpty() && dica != null) {
                Graphics2D g3 = suavizar(g);
                g3.setColor(TEXTO_SUAVE);
                g3.setFont(getFont());
                int y = (getHeight() + g3.getFontMetrics().getAscent()
                         - g3.getFontMetrics().getDescent()) / 2;
                g3.drawString(dica, getInsets().left + 1, y);
                g3.dispose();
            }
        }
    }

    /** Area de texto do chat com uma grade sutil ao fundo. */
    public static class TextoComGrade extends JTextPane {
        public TextoComGrade() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Rectangle r = g2.getClipBounds();
            if (r == null) r = new Rectangle(0, 0, getWidth(), getHeight());
            g2.setColor(FUNDO);
            g2.fill(r);
            g2.setColor(GRADE);
            int passo = 28;
            for (int x = (r.x / passo) * passo; x <= r.x + r.width; x += passo) g2.drawLine(x, r.y, x, r.y + r.height);
            for (int y = (r.y / passo) * passo; y <= r.y + r.height; y += passo) g2.drawLine(r.x, y, r.x + r.width, y);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Indicador luminoso de estado; quando ativo, pulsa suavemente. */
    public static class Led extends JComponent {
        private Color cor = TEXTO_SUAVE;
        private boolean pulsando = false;
        private float fase = 0f;
        private final Timer animacao = new Timer(40, e -> { fase += 0.08f; repaint(); });

        public Led() {
            setOpaque(false);
            setPreferredSize(new Dimension(20, 20));
        }

        public void setEstado(Color cor, boolean pulsando) {
            this.cor = cor;
            this.pulsando = pulsando;
            if (pulsando && isDisplayable()) animacao.start();
            else animacao.stop();
            repaint();
        }

        // a animacao so roda com a janela aberta (um Timer ativo impediria o
        // encerramento automatico da aplicacao quando todas as janelas fecham)
        @Override public void addNotify()    { super.addNotify(); if (pulsando) animacao.start(); }
        @Override public void removeNotify() { animacao.stop(); super.removeNotify(); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = suavizar(g);
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float intensidade = pulsando ? (float) (0.55 + 0.45 * Math.sin(fase)) : 0.35f;
            for (int r = 10; r > 4; r--) {
                g2.setColor(comAlfa(cor, Math.round(intensidade * (11 - r) * 9)));
                g2.fill(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r));
            }
            g2.setColor(cor);
            g2.fill(new Ellipse2D.Float(cx - 4, cy - 4, 8, 8));
            g2.setColor(new Color(255, 255, 255, 140));
            g2.fill(new Ellipse2D.Float(cx - 2.5f, cy - 2.5f, 2.5f, 2.5f));
            g2.dispose();
        }
    }

    /** Barra de rolagem fina, sem setas, com o cursor acendendo em ciano. */
    public static class BarraRolagemUI extends BasicScrollBarUI {
        public static ComponentUI createUI(JComponent c) {
            return new BarraRolagemUI();
        }

        @Override
        protected void installDefaults() {
            super.installDefaults();
            scrollBarWidth = 10;
            minimumThumbSize = new Dimension(10, 30);
            scrollbar.setOpaque(false);
            scrollbar.setBorder(new EmptyBorder(0, 0, 0, 0));
        }

        @Override protected JButton createDecreaseButton(int orientacao) { return botaoVazio(); }
        @Override protected JButton createIncreaseButton(int orientacao) { return botaoVazio(); }

        private static JButton botaoVazio() {
            JButton b = new JButton();
            Dimension zero = new Dimension(0, 0);
            b.setPreferredSize(zero);
            b.setMinimumSize(zero);
            b.setMaximumSize(zero);
            return b;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            // trilho transparente
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g2 = suavizar(g);
            g2.setColor((isThumbRollover() || isDragging) ? comAlfa(PRIMARIA, 170) : BORDA_FORTE);
            g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 6, 6);
            g2.dispose();
        }
    }
}
