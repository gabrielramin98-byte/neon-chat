import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedList;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * Tela de chat do cliente grafico.
 *
 * Threads envolvidas (cliente multithreaded):
 *   - EDT (thread da interface): trata cliques/teclas e envia os comandos
 *     pelo socket, sempre montados com Protocolo.montar();
 *   - ThreadEscuta: fica bloqueada em readLine() no socket, analisa cada
 *     linha com Protocolo.analisar() e entrega o resultado a EDT via
 *     SwingUtilities.invokeLater (componentes Swing so podem ser
 *     alterados pela EDT).
 *
 * Correlacao de respostas: o servidor responde os comandos de um cliente
 * na mesma ordem em que os recebe, com exatamente uma resposta direta
 * (OK, ERR, USERS, ROOMS ou FILEDATA). Por isso cada comando enviado entra
 * numa fila de pendentes e cada resposta direta retira o primeiro da fila.
 * MSG, PRIV, FILE e NOTIF sao assincronos (vindos de outros clientes) e
 * nao usam a fila.
 */
public class TelaChat extends JFrame {

    /** Comando enviado que aguarda resposta direta do servidor. */
    private static class Pendente {
        final String cmd;       // JOIN, MSG, PRIV, USERS, ROOMS, HIST, FILE, GET
        final String argumento; // sala do JOIN ou destinatario do PRIV/FILE
        final String texto;     // texto da MSG/PRIV ou nome do FILE (exibido apos o OK)
        final boolean exibir;   // pedido pelo usuario (true) ou automatico (false)

        Pendente(String cmd, String argumento, String texto, boolean exibir) {
            this.cmd = cmd;
            this.argumento = argumento;
            this.texto = texto;
            this.exibir = exibir;
        }
    }

    /** Atributo que marca, no texto do chat, o trecho clicavel de um arquivo. */
    private static final String ATRIBUTO_ARQUIVO = "arquivo-id";

    private final Socket socket;
    private final BufferedReader entrada;
    private final PrintWriter saida;
    private final String apelido;
    private final String descricaoServidor;

    private final LinkedList<Pendente> pendentes = new LinkedList<Pendente>();
    private boolean conectado = true;
    private String salaAtual = null;
    private int historicoRestante = 0;
    private boolean anunciarEntrada = false;
    private String ultimoCabecalho = null;   // agrupa mensagens seguidas do mesmo autor

    // componentes
    private final JLabel titulo = new JLabel();
    private final JLabel subtitulo = new JLabel();
    private final JTextPane painelMensagens = new Estilo.TextoComGrade();
    private final StyledDocument doc = painelMensagens.getStyledDocument();
    private final DefaultListModel<String> modeloSalas = new DefaultListModel<String>();
    private final DefaultListModel<String> modeloUsuarios = new DefaultListModel<String>();
    private final JList<String> listaSalas = new JList<String>(modeloSalas);
    private final JList<String> listaUsuarios = new JList<String>(modeloUsuarios);
    private final JLabel rotuloUsuarios = Estilo.rotuloSecao("Na sala");
    private final Estilo.CampoTexto campoMensagem = new Estilo.CampoTexto("Escreva uma mensagem…  (/ajuda)");
    private final Estilo.CampoTexto campoNovaSala = new Estilo.CampoTexto("nova sala");
    private final JButton botaoEnviar = new Estilo.Botao("Enviar", Estilo.Botao.PRIMARIO);
    private final JButton botaoEmoji = new Estilo.Botao(Estilo.iconeEmoji("😀", 16f), Estilo.Botao.FANTASMA);
    private final JButton botaoArquivo = new Estilo.Botao(Estilo.iconeEmoji("📎", 16f), Estilo.Botao.FANTASMA);
    private final JButton botaoNovaSala = new Estilo.Botao("+", Estilo.Botao.FANTASMA);
    private final JButton botaoSair = new Estilo.Botao("Sair", Estilo.Botao.PERIGO);
    private final Estilo.Led led = new Estilo.Led();

    private final Timer atualizador = new Timer(4000, e -> pedirSalas(false));

    public TelaChat(Socket socket, BufferedReader entrada, PrintWriter saida,
                    String apelido, String descricaoServidor, String salaInicial) {
        super("Chat — " + apelido);
        this.socket = socket;
        this.entrada = entrada;
        this.saida = saida;
        this.apelido = apelido;
        this.descricaoServidor = descricaoServidor;

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                sair();
            }
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                campoMensagem.requestFocusInWindow();
            }
        });

        montarTela();
        titulo.setText("Conectando...");
        led.setEstado(Estilo.SUCESSO, true);
        subtitulo.setText(apelido + "  ·  " + descricaoServidor);

        new ThreadEscuta().start();   // thread de escuta do socket
        atualizador.start();

        sistema("Conectado a " + descricaoServidor + " como " + apelido + ".");
        entrarNaSala(salaInicial);
    }

    // =====================================================================
    // Montagem da tela
    // =====================================================================

    private void montarTela() {
        setLayout(new BorderLayout());
        getContentPane().setBackground(Estilo.FUNDO);

        add(Estilo.cabecalho(led, titulo, subtitulo, botaoSair), BorderLayout.NORTH);
        add(montarPainelSalas(), BorderLayout.WEST);
        add(montarPainelUsuarios(), BorderLayout.EAST);
        add(montarPainelCentral(), BorderLayout.CENTER);

        botaoSair.addActionListener(e -> sair());
        botaoEnviar.addActionListener(e -> processarEntrada());
        campoMensagem.addActionListener(e -> processarEntrada());
        botaoNovaSala.addActionListener(e -> criarSala());
        botaoEmoji.addActionListener(e -> mostrarEmojis());
        botaoArquivo.addActionListener(e -> escolherArquivo(null));
        campoNovaSala.addActionListener(e -> criarSala());

        setSize(980, 640);
        setMinimumSize(new Dimension(720, 440));
        setLocationByPlatform(true);
    }

    private JPanel montarPainelSalas() {
        listaSalas.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listaSalas.setFixedCellHeight(34);
        listaSalas.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object valor, int i,
                                                          boolean sel, boolean foco) {
                JLabel c = (JLabel) super.getListCellRendererComponent(l, valor, i, false, false);
                boolean atual = valor.equals(salaAtual);
                c.setText("<html><font color='" + Estilo.hex(atual ? Estilo.PRIMARIA : Estilo.BORDA_FORTE)
                        + "'>#</font>&nbsp;&nbsp;" + html(String.valueOf(valor)) + "</html>");
                c.setBorder(atual
                        ? BorderFactory.createCompoundBorder(
                              BorderFactory.createMatteBorder(0, 3, 0, 0, Estilo.PRIMARIA),
                              BorderFactory.createEmptyBorder(0, 11, 0, 8))
                        : BorderFactory.createEmptyBorder(0, 14, 0, 8));
                c.setFont(Estilo.fonte(atual ? Font.BOLD : Font.PLAIN, 13f));
                c.setForeground(atual ? Color.WHITE : Estilo.TEXTO_SUAVE);
                c.setBackground(atual ? Estilo.DESTAQUE : Estilo.PAINEL);
                return c;
            }
        });
        listaSalas.setToolTipText("Clique em uma sala para entrar");
        listaSalas.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                String sala = itemClicado(listaSalas, e);
                if (sala != null && !sala.equals(salaAtual)) entrarNaSala(sala);
            }
        });

        JPanel criar = new JPanel(new BorderLayout(4, 0));
        criar.setOpaque(false);
        criar.setBorder(BorderFactory.createEmptyBorder(10, 10, 12, 10));
        botaoNovaSala.setBorder(BorderFactory.createEmptyBorder(7, 13, 7, 13));
        botaoNovaSala.setToolTipText("Criar e entrar na sala");
        criar.add(campoNovaSala, BorderLayout.CENTER);
        criar.add(botaoNovaSala, BorderLayout.EAST);

        return painelLateral("Salas", listaSalas, criar, 0, 1);
    }

    private JPanel montarPainelUsuarios() {
        listaUsuarios.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listaUsuarios.setFixedCellHeight(30);
        listaUsuarios.setSelectionBackground(Estilo.DESTAQUE);
        listaUsuarios.setSelectionForeground(Color.WHITE);
        listaUsuarios.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object valor, int i,
                                                          boolean sel, boolean foco) {
                JLabel c = (JLabel) super.getListCellRendererComponent(l, valor, i, sel, false);
                String nome = String.valueOf(valor);
                boolean eu = nome.equals(apelido);
                String cor = Estilo.hex(eu ? Estilo.PRIMARIA : Estilo.corDoUsuario(nome));
                c.setText("<html><font color='" + cor + "'>●</font>&nbsp; " + html(nome)
                        + (eu ? " <font color='" + Estilo.hex(Estilo.TEXTO_SUAVE) + "'>(você)</font>" : "") + "</html>");
                c.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 8));
                c.setFont(Estilo.fonte(Font.PLAIN, 13f));
                if (!sel) {
                    c.setBackground(Estilo.PAINEL);
                    c.setForeground(Estilo.TEXTO);
                }
                return c;
            }
        });
        listaUsuarios.setToolTipText("Duplo clique para mensagem privada");
        listaUsuarios.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                String nome = itemClicado(listaUsuarios, e);
                if (e.getClickCount() == 2 && nome != null && !nome.equals(apelido)) {
                    campoMensagem.setText("/msg " + nome + " ");
                    campoMensagem.requestFocusInWindow();
                }
            }
        });

        JLabel dica = new JLabel("<html>Duplo clique em um usuário<br>para mensagem privada</html>");
        dica.setFont(Estilo.mono(Font.PLAIN, 10f));
        dica.setForeground(Estilo.TEXTO_SUAVE);
        dica.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Estilo.BORDA),
                BorderFactory.createEmptyBorder(10, 14, 12, 8)));

        JPanel p = painelLateral(null, listaUsuarios, dica, 1, 0);
        p.add(rotuloUsuarios, BorderLayout.NORTH);
        return p;
    }

    private JPanel montarPainelCentral() {
        painelMensagens.setEditable(false);
        painelMensagens.setBorder(BorderFactory.createEmptyBorder(14, 20, 14, 20));
        painelMensagens.setFont(Estilo.fonte(Font.PLAIN, 14f));
        painelMensagens.setForeground(Estilo.TEXTO);
        painelMensagens.setSelectionColor(new Color(0x13456A));
        painelMensagens.setSelectedTextColor(Color.WHITE);
        painelMensagens.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                String id = arquivoNoPonto(e.getPoint());
                if (id != null) baixarArquivo(id);
            }
        });
        painelMensagens.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                painelMensagens.setCursor(arquivoNoPonto(e.getPoint()) != null
                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            }
        });

        JScrollPane rolagem = Estilo.rolagem(painelMensagens, Estilo.FUNDO);

        JPanel entradaTexto = new JPanel(new BorderLayout(8, 0));
        entradaTexto.setBackground(Estilo.PAINEL);
        entradaTexto.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Estilo.BORDA),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));
        campoMensagem.setFont(Estilo.fonte(Font.PLAIN, 14f));
        entradaTexto.add(campoMensagem, BorderLayout.CENTER);

        botaoEmoji.setToolTipText("Emoticons");
        botaoArquivo.setToolTipText("Enviar arquivo para a sala  (/arquivo <apelido> para privado)");
        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        botoes.setOpaque(false);
        botoes.add(botaoEmoji);
        botoes.add(botaoArquivo);
        botoes.add(botaoEnviar);
        entradaTexto.add(botoes, BorderLayout.EAST);

        JPanel p = new JPanel(new BorderLayout());
        p.add(rolagem, BorderLayout.CENTER);
        p.add(entradaTexto, BorderLayout.SOUTH);
        return p;
    }

    private static JPanel painelLateral(String tituloSecao, JList<String> lista, Component rodape,
                                        int bordaEsq, int bordaDir) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Estilo.PAINEL);
        p.setPreferredSize(new Dimension(210, 0));
        p.setBorder(BorderFactory.createMatteBorder(0, bordaEsq, 0, bordaDir, Estilo.BORDA));
        if (tituloSecao != null) p.add(Estilo.rotuloSecao(tituloSecao), BorderLayout.NORTH);
        lista.setBackground(Estilo.PAINEL);
        lista.setForeground(Estilo.TEXTO);
        p.add(Estilo.rolagem(lista, Estilo.PAINEL), BorderLayout.CENTER);
        p.add(rodape, BorderLayout.SOUTH);
        return p;
    }

    private static String itemClicado(JList<String> lista, MouseEvent e) {
        int i = lista.locationToIndex(e.getPoint());
        if (i < 0 || !lista.getCellBounds(i, i).contains(e.getPoint())) return null;
        return lista.getModel().getElementAt(i);
    }

    // =====================================================================
    // Envio de comandos (executado na EDT)
    // =====================================================================

    private void enviar(Pendente p, String linha) {
        if (!conectado) return;
        pendentes.addLast(p);
        saida.println(linha);
        saida.flush();
    }

    private void entrarNaSala(String sala) {
        enviar(new Pendente("JOIN", sala, null, true), Protocolo.montar("CMD", "JOIN", "ROOM", sala));
    }

    private void pedirUsuarios(boolean exibir) {
        enviar(new Pendente("USERS", null, null, exibir), Protocolo.montar("CMD", "USERS"));
    }

    private void pedirSalas(boolean exibir) {
        enviar(new Pendente("ROOMS", null, null, exibir), Protocolo.montar("CMD", "ROOMS"));
    }

    private void pedirHistorico(int n, boolean exibir) {
        enviar(new Pendente("HIST", null, null, exibir),
               Protocolo.montar("CMD", "HIST", "N", String.valueOf(n)));
    }

    private void criarSala() {
        String sala = campoNovaSala.getText().trim();
        if (sala.isEmpty()) return;
        campoNovaSala.setText("");
        entrarNaSala(sala);
    }

    /** Seletor de emoticons: o emoji escolhido e' inserido no campo de mensagem. */
    private void mostrarEmojis() {
        JPopupMenu popup = new JPopupMenu();
        popup.setBackground(Estilo.SUPERFICIE);
        JPanel grade = new JPanel(new GridLayout(0, 8, 2, 2));
        grade.setBackground(Estilo.SUPERFICIE);
        grade.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        for (String emoji : Emoticons.SELETOR) {
            JButton b = new JButton(Estilo.iconeEmoji(emoji, 22f));
            b.setToolTipText(emoji);
            b.setBorderPainted(false);
            b.setContentAreaFilled(false);
            b.setFocusable(false);
            b.setMargin(new java.awt.Insets(2, 2, 2, 2));
            b.addActionListener(e -> {
                campoMensagem.replaceSelection(emoji);
                popup.setVisible(false);
                campoMensagem.requestFocusInWindow();
            });
            grade.add(b);
        }
        popup.add(grade);
        popup.show(botaoEmoji, 0, -popup.getPreferredSize().height);
    }

    /** Escolhe um arquivo do disco e envia (destino null = sala atual). */
    private void escolherArquivo(String destino) {
        if (!conectado) return;
        if (destino == null && salaAtual == null) { erroLocal("Entre em uma sala antes de enviar arquivos."); return; }

        JFileChooser escolha = new JFileChooser();
        escolha.setDialogTitle(destino == null ? "Enviar arquivo para #" + salaAtual
                                               : "Enviar arquivo para " + destino + " (privado)");
        if (escolha.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File f = escolha.getSelectedFile();
        String dados;
        try {
            dados = Arquivo.lerBase64(f);
        } catch (IOException e) {
            erroLocal("Não foi possível enviar " + f.getName() + ": " + e.getMessage());
            return;
        }
        if (destino == null) {
            enviar(new Pendente("FILE", null, f.getName(), true),
                   Protocolo.montar("CMD", "FILE", "NAME", f.getName(), "DATA", dados));
        } else {
            enviar(new Pendente("FILE", destino, f.getName(), true),
                   Protocolo.montar("CMD", "FILE", "TO", destino, "NAME", f.getName(), "DATA", dados));
        }
    }

    private void baixarArquivo(String id) {
        enviar(new Pendente("GET", id, null, true), Protocolo.montar("CMD", "GET", "ID", id));
    }

    /** Interpreta o que foi digitado: texto comum ou comando iniciado por '/'. */
    private void processarEntrada() {
        String linha = campoMensagem.getText().trim();
        if (linha.isEmpty() || !conectado) return;
        campoMensagem.setText("");

        if (!linha.startsWith("/")) {
            if (salaAtual == null) { erroLocal("Entre em uma sala antes de enviar mensagens."); return; }
            String texto = Emoticons.converter(linha);
            enviar(new Pendente("MSG", null, texto, true), Protocolo.montar("CMD", "MSG", "TEXT", texto));
            return;
        }

        String[] partes = linha.split("\\s+", 2);
        String comando = partes[0].toLowerCase();
        String argumento = (partes.length > 1) ? partes[1].trim() : "";

        switch (comando) {
            case "/join":
                if (argumento.isEmpty()) erroLocal("Uso: /join <sala>");
                else entrarNaSala(argumento);
                break;
            case "/users":
                pedirUsuarios(true);
                break;
            case "/salas":
                pedirSalas(true);
                break;
            case "/hist":
                try {
                    pedirHistorico(argumento.isEmpty() ? 10 : Integer.parseInt(argumento), true);
                } catch (NumberFormatException e) {
                    erroLocal("Uso: /hist <n>");
                }
                break;
            case "/msg":
                String[] destTexto = argumento.split("\\s+", 2);
                if (destTexto.length < 2 || destTexto[1].trim().isEmpty()) {
                    erroLocal("Uso: /msg <apelido> <texto>");
                } else {
                    String texto = Emoticons.converter(destTexto[1]);
                    enviar(new Pendente("PRIV", destTexto[0], texto, true),
                           Protocolo.montar("CMD", "PRIV", "TO", destTexto[0], "TEXT", texto));
                }
                break;
            case "/arquivo":
                escolherArquivo(argumento.isEmpty() ? null : argumento);
                break;
            case "/emojis":
                sistema("Atalhos: " + Emoticons.listaAtalhos());
                break;
            case "/sair":
                sair();
                break;
            case "/ajuda":
                sistema("Comandos: /join <sala>  ·  /users  ·  /salas  ·  /hist <n>  ·  "
                        + "/msg <apelido> <texto>  ·  /arquivo [apelido]  ·  /emojis  ·  /sair");
                break;
            default:
                erroLocal("Comando desconhecido: " + comando + "  (use /ajuda)");
        }
    }

    private void sair() {
        if (conectado) {
            conectado = false;
            saida.println(Protocolo.montar("CMD", "QUIT"));
            saida.flush();
            try { socket.close(); } catch (IOException e) { }
        }
        atualizador.stop();
        dispose();
    }

    // =====================================================================
    // Recebimento
    // =====================================================================

    /** Thread secundaria: bloqueada lendo o socket enquanto a interface segue livre. */
    private class ThreadEscuta extends Thread {
        ThreadEscuta() {
            super("Cliente-escuta-" + apelido);
            setDaemon(true);
        }

        @Override
        public void run() {
            String motivo = "conexão encerrada pelo servidor";
            try {
                String linha;
                while ((linha = entrada.readLine()) != null) {
                    final Map<String, String> campos = Protocolo.analisar(linha);
                    SwingUtilities.invokeLater(() -> tratar(campos));
                }
            } catch (IOException e) {
                motivo = e.getMessage();
            }
            final String m = motivo;
            SwingUtilities.invokeLater(() -> conexaoPerdida(m));
        }
    }

    /** Trata uma mensagem recebida do servidor (executado na EDT). */
    private void tratar(Map<String, String> campos) {
        String cmd = Protocolo.campo(campos, "CMD", "").toUpperCase();
        Pendente p;

        switch (cmd) {
            case "MSG":
                boolean doHistorico = historicoRestante > 0;
                mensagem(Protocolo.campo(campos, "FROM", "?"), Protocolo.campo(campos, "TS", "--:--"),
                         Protocolo.campo(campos, "TEXT", ""), doHistorico);
                if (doHistorico && --historicoRestante == 0) fimDoHistorico();
                break;

            case "PRIV":
                privada(Protocolo.campo(campos, "FROM", "?") + " → você",
                        Protocolo.campo(campos, "TS", "--:--"), Protocolo.campo(campos, "TEXT", ""));
                Toolkit.getDefaultToolkit().beep();
                break;

            case "FILE": {
                String id = Protocolo.campo(campos, "ID", "?");
                String nome = Protocolo.campo(campos, "NAME", "arquivo");
                String tamanho = Protocolo.campo(campos, "SIZE", "0");
                if (campos.containsKey("TO")) {          // arquivo privado
                    arquivoPrivado(Protocolo.campo(campos, "FROM", "?") + " → você",
                                   Protocolo.campo(campos, "TS", "--:--"), id, nome, tamanho);
                    Toolkit.getDefaultToolkit().beep();
                } else {                                 // arquivo da sala (pode vir do historico)
                    boolean historico = historicoRestante > 0;
                    arquivo(Protocolo.campo(campos, "FROM", "?"), Protocolo.campo(campos, "TS", "--:--"),
                            id, nome, tamanho, historico);
                    if (historico && --historicoRestante == 0) fimDoHistorico();
                }
                break;
            }

            case "FILEDATA":
                pendentes.pollFirst();
                receberArquivo(Protocolo.campo(campos, "NAME", "arquivo"), Protocolo.campo(campos, "DATA", ""));
                break;

            case "NOTIF":
                sistema(Protocolo.campo(campos, "INFO", ""));
                if (salaAtual != null) pedirUsuarios(false);   // alguem entrou/saiu
                pedirSalas(false);
                break;

            case "USERS":
                p = pendentes.pollFirst();
                atualizarUsuarios(Protocolo.campo(campos, "LIST", ""));
                if (p != null && p.exibir) {
                    sistema("Usuários em " + Protocolo.campo(campos, "ROOM", "?") + ": "
                            + Protocolo.campo(campos, "LIST", "").replace(",", ", "));
                }
                break;

            case "ROOMS":
                p = pendentes.pollFirst();
                atualizarSalas(Protocolo.campo(campos, "LIST", ""));
                if (p != null && p.exibir) {
                    String lista = Protocolo.campo(campos, "LIST", "");
                    sistema("Salas ativas: " + (lista.isEmpty() ? "(nenhuma)" : lista.replace(",", ", ")));
                }
                break;

            case "OK":
                tratarOk(pendentes.pollFirst(), campos);
                break;

            case "ERR":
                p = pendentes.pollFirst();
                if (p != null && p.cmd.equals("HIST")) fimDoHistorico();
                if (p != null && !p.exibir) break;   // falha de consulta automatica: silenciosa
                erroLocal("Erro " + Protocolo.campo(campos, "COD", "??") + ": "
                          + Protocolo.campo(campos, "INFO", ""));
                break;

            default:
                sistema("Mensagem não reconhecida: " + campos);
        }
    }

    private void tratarOk(Pendente p, Map<String, String> campos) {
        String info = Protocolo.campo(campos, "INFO", "");
        if (p == null) { sistema(info); return; }

        switch (p.cmd) {
            case "JOIN":
                salaAtual = p.argumento;
                limparMensagens();
                titulo.setText("# " + salaAtual);
                modeloUsuarios.clear();
                listaSalas.repaint();
                anunciarEntrada = true;
                pedirHistorico(20, false);   // mostra a conversa recente da sala
                pedirUsuarios(false);
                pedirSalas(false);
                break;

            case "MSG":
                mensagem(apelido, hora(), p.texto, false);
                break;

            case "PRIV":
                privada("você → " + p.argumento, hora(), p.texto);
                break;

            case "FILE":
                String id = Protocolo.campo(campos, "ID", "?");
                String tamanho = Protocolo.campo(campos, "SIZE", "0");
                if (p.argumento == null) arquivo(apelido, hora(), id, p.texto, tamanho, false);
                else arquivoPrivado("você → " + p.argumento, hora(), id, p.texto, tamanho);
                break;

            case "HIST":
                int n;
                try {
                    n = Integer.parseInt(Protocolo.campo(campos, "N", "0"));
                } catch (NumberFormatException e) {
                    n = 0;
                }
                if (p.exibir) separador(n == 0 ? "histórico vazio" : "últimas " + n + " mensagens");
                else if (n > 0) separador("mensagens anteriores");
                historicoRestante = n;
                if (n == 0) fimDoHistorico();
                break;

            default:
                if (p.exibir) sistema(info);
        }
    }

    /** Conteudo recebido do servidor (resposta ao GET): mostra imagens e oferece salvar. */
    private void receberArquivo(String nome, String base64) {
        byte[] dados;
        try {
            dados = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            erroLocal("Arquivo recebido corrompido: " + nome);
            return;
        }

        BufferedImage imagem = null;
        try {
            imagem = ImageIO.read(new ByteArrayInputStream(dados));
        } catch (IOException e) { }
        if (imagem != null) {   // imagem: pre-visualizacao antes de salvar
            double escala = Math.min(1.0, Math.min(520.0 / imagem.getWidth(), 380.0 / imagem.getHeight()));
            Image miniatura = imagem.getScaledInstance((int) Math.max(1, imagem.getWidth() * escala),
                    (int) Math.max(1, imagem.getHeight() * escala), Image.SCALE_SMOOTH);
            String[] opcoes = { "Salvar...", "Fechar" };
            int op = JOptionPane.showOptionDialog(this, new JLabel(new ImageIcon(miniatura)), nome,
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, opcoes, opcoes[0]);
            if (op != 0) return;
        }

        JFileChooser escolha = new JFileChooser();
        escolha.setDialogTitle("Salvar " + nome);
        escolha.setSelectedFile(new File(Arquivo.nomeSeguro(nome)));
        if (escolha.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File destino = escolha.getSelectedFile();
        if (destino.exists() && JOptionPane.showConfirmDialog(this, "Substituir " + destino.getName() + "?",
                "Arquivo existente", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            Files.write(destino.toPath(), dados);
            sistema("Arquivo salvo em " + destino.getAbsolutePath());
        } catch (IOException e) {
            erroLocal("Não foi possível salvar: " + e.getMessage());
        }
    }

    private void fimDoHistorico() {
        historicoRestante = 0;
        if (anunciarEntrada) {
            anunciarEntrada = false;
            separador("você entrou na sala " + salaAtual);
        }
    }

    private void conexaoPerdida(String motivo) {
        if (!conectado) return;   // saida solicitada pelo proprio usuario
        conectado = false;
        atualizador.stop();
        erroLocal("Desconectado: " + motivo);
        titulo.setText("Desconectado");
        led.setEstado(Estilo.ERRO, false);
        modeloUsuarios.clear();
        rotuloUsuarios.setText("NA SALA");
        campoMensagem.setEnabled(false);
        botaoEnviar.setEnabled(false);
        campoNovaSala.setEnabled(false);
        botaoNovaSala.setEnabled(false);
        botaoSair.setText("Fechar");
        botaoEmoji.setEnabled(false);
        botaoArquivo.setEnabled(false);
        try { socket.close(); } catch (IOException e) { }
    }

    private void atualizarUsuarios(String lista) {
        modeloUsuarios.clear();
        for (String u : lista.split(",")) {
            if (!u.trim().isEmpty()) modeloUsuarios.addElement(u.trim());
        }
        rotuloUsuarios.setText("NA SALA (" + modeloUsuarios.size() + ")");
    }

    private void atualizarSalas(String lista) {
        modeloSalas.clear();
        for (String s : lista.split(",")) {
            if (!s.trim().isEmpty()) modeloSalas.addElement(s.trim());
        }
    }

    // =====================================================================
    // Exibicao das mensagens
    // =====================================================================

    private void mensagem(String autor, String ts, String texto, boolean historico) {
        mensagemDeAutor(autor, ts, historico,
                texto, attr(historico ? Estilo.TEXTO_SUAVE : Estilo.TEXTO, false, false, 14f));
    }

    private void arquivo(String autor, String ts, String id, String nome, String tamanho, boolean historico) {
        mensagemDeAutor(autor, ts, historico, trechosArquivo(id, nome, tamanho));
    }

    /** Cabecalho (autor e hora, omitido em mensagens seguidas) e corpo formado por pares (texto, atributos). */
    private void mensagemDeAutor(String autor, String ts, boolean historico, Object... corpo) {
        boolean minha = autor.equals(apelido);
        int alinhamento = minha ? StyleConstants.ALIGN_RIGHT : StyleConstants.ALIGN_LEFT;
        Color corNome = minha ? Estilo.PRIMARIA : Estilo.corDoUsuario(autor);

        String cabecalho = autor + "|" + ts + "|" + historico;
        if (!cabecalho.equals(ultimoCabecalho)) {
            paragrafo(alinhamento, 8f, minha,
                    minha ? "Você" : autor, attr(corNome, true, false, 13f),
                    "   " + ts, mono(Estilo.TEXTO_SUAVE, 11f));
        }
        paragrafo(alinhamento, 1f, minha, corpo);
        ultimoCabecalho = cabecalho;
    }

    private void privada(String rotulo, String ts, String texto) {
        mensagemPrivada(rotulo, ts, texto, attr(Estilo.PRIVADO, false, true, 14f));
    }

    private void arquivoPrivado(String rotulo, String ts, String id, String nome, String tamanho) {
        mensagemPrivada(rotulo, ts, trechosArquivo(id, nome, tamanho));
    }

    private void mensagemPrivada(String rotulo, String ts, Object... corpo) {
        paragrafo(StyleConstants.ALIGN_LEFT, 8f, false,
                "◆ Privado · " + rotulo, attr(Estilo.PRIVADO, true, false, 13f),
                "   " + ts, mono(Estilo.TEXTO_SUAVE, 11f));
        paragrafo(StyleConstants.ALIGN_LEFT, 1f, false, corpo);
        ultimoCabecalho = null;
    }

    /** "📎 nome" clicavel (marcado com o ID do arquivo) seguido do tamanho. */
    private static Object[] trechosArquivo(String id, String nome, String tamanho) {
        SimpleAttributeSet clipe = attr(Estilo.PRIMARIA, true, false, 14f);
        clipe.addAttribute(ATRIBUTO_ARQUIVO, id);
        SimpleAttributeSet link = new SimpleAttributeSet(clipe);
        StyleConstants.setUnderline(link, true);
        String legivel;
        try {
            legivel = Arquivo.tamanhoLegivel(Long.parseLong(tamanho));
        } catch (NumberFormatException e) {
            legivel = "?";
        }
        return new Object[] {
            "📎 ", clipe,
            nome, link,
            "   " + legivel + " · clique para baixar", mono(Estilo.TEXTO_SUAVE, 11f)
        };
    }

    /** ID do arquivo cujo link esta sob o ponteiro do mouse, ou null. */
    private String arquivoNoPonto(Point ponto) {
        int pos = painelMensagens.viewToModel2D(ponto);
        for (int i = pos; i >= Math.max(0, pos - 1); i--) {   // o ponto pode estar no fim do trecho
            Element el = doc.getCharacterElement(i);
            Object id = el.getAttributes().getAttribute(ATRIBUTO_ARQUIVO);
            if (id == null) continue;
            try {
                Rectangle2D ini = painelMensagens.modelToView2D(el.getStartOffset());
                Rectangle2D fim = painelMensagens.modelToView2D(el.getEndOffset());
                if (ponto.y >= ini.getY() && ponto.y <= ini.getMaxY()
                        && ponto.x >= ini.getX() && ponto.x <= fim.getX()) return (String) id;
            } catch (BadLocationException e) { }
        }
        return null;
    }

    private void sistema(String texto) {
        paragrafo(StyleConstants.ALIGN_CENTER, 6f, false, "›  " + texto, attr(Estilo.TEXTO_SUAVE, false, true, 12f));
        ultimoCabecalho = null;
    }

    private void separador(String texto) {
        paragrafo(StyleConstants.ALIGN_CENTER, 10f, false,
                "─────  " + texto + "  ─────", mono(Estilo.ROTULO, 11f));
        ultimoCabecalho = null;
    }

    private void erroLocal(String texto) {
        paragrafo(StyleConstants.ALIGN_CENTER, 6f, false, "⚠  " + texto, attr(Estilo.ERRO, true, false, 12f));
        ultimoCabecalho = null;
    }

    private void limparMensagens() {
        try {
            doc.remove(0, doc.getLength());
        } catch (BadLocationException e) { }
        ultimoCabecalho = null;
    }

    /** Insere um paragrafo formado por pares (texto, atributos). */
    private void paragrafo(int alinhamento, float espacoAcima, boolean recuoEsquerdo, Object... trechos) {
        try {
            int inicio = doc.getLength();
            for (int i = 0; i + 1 < trechos.length; i += 2) {
                inserirComEmojis((String) trechos[i], (SimpleAttributeSet) trechos[i + 1]);
            }
            doc.insertString(doc.getLength(), "\n", null);

            SimpleAttributeSet par = new SimpleAttributeSet();
            StyleConstants.setAlignment(par, alinhamento);
            StyleConstants.setSpaceAbove(par, espacoAcima);
            StyleConstants.setLeftIndent(par, recuoEsquerdo ? 120f : 0f);
            StyleConstants.setRightIndent(par, (!recuoEsquerdo && alinhamento == StyleConstants.ALIGN_LEFT) ? 120f : 0f);
            doc.setParagraphAttributes(inicio, doc.getLength() - inicio, par, false);
        } catch (BadLocationException e) { }
        painelMensagens.setCaretPosition(doc.getLength());
    }

    /** Insere o texto trocando cada emoji por um icone (ver Estilo.iconeEmoji). */
    private void inserirComEmojis(String texto, SimpleAttributeSet a) throws BadLocationException {
        int inicio = 0;
        for (int i = 0; i < texto.length(); ) {
            int fim = Estilo.fimDoEmoji(texto, i);
            if (fim == i) { i += Character.charCount(texto.codePointAt(i)); continue; }
            doc.insertString(doc.getLength(), texto.substring(inicio, i), a);
            SimpleAttributeSet comIcone = new SimpleAttributeSet(a);
            StyleConstants.setIcon(comIcone, Estilo.iconeEmoji(texto.substring(i, fim), StyleConstants.getFontSize(a)));
            doc.insertString(doc.getLength(), texto.substring(i, fim), comIcone);
            inicio = i = fim;
        }
        doc.insertString(doc.getLength(), texto.substring(inicio), a);
    }

    private static SimpleAttributeSet attr(Color cor, boolean negrito, boolean italico, float tamanho) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setForeground(a, cor);
        StyleConstants.setBold(a, negrito);
        StyleConstants.setItalic(a, italico);
        StyleConstants.setFontSize(a, Math.round(tamanho));
        return a;
    }

    /** Atributos em fonte monoespacada (horarios, separadores). */
    private static SimpleAttributeSet mono(Color cor, float tamanho) {
        SimpleAttributeSet a = attr(cor, false, false, tamanho);
        StyleConstants.setFontFamily(a, Estilo.familiaMono());
        return a;
    }

    private static String hora() {
        return new SimpleDateFormat("HH:mm").format(new Date());
    }

    private static String html(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
