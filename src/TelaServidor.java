import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.table.DefaultTableModel;

/**
 * Painel grafico do servidor: inicia/para o Servidor, exibe o log, os
 * usuarios conectados e as salas ativas (repositorio de dados comuns).
 *
 * A thread da interface (EDT) nunca fica bloqueada: o laco de accept()
 * roda em uma thread propria, e o log chega pelo Ouvinte, sendo repassado
 * a EDT com SwingUtilities.invokeLater.
 */
public class TelaServidor extends JFrame {

    private Servidor servidor;

    private final JSpinner campoPorta = new JSpinner(new SpinnerNumberModel(Servidor.PORTA_PADRAO, 1, 65535, 1));
    private final Estilo.Botao botaoIniciar = new Estilo.Botao("Iniciar servidor", Estilo.Botao.PRIMARIO);
    private final Estilo.Led led = new Estilo.Led();
    private final JLabel status = new JLabel();
    private final JLabel subtitulo = new JLabel();
    private final JTextArea log = new JTextArea();
    private final Estilo.CampoTexto campoAviso = new Estilo.CampoTexto("Aviso para todos os clientes conectados...");
    private final JButton botaoAviso = new Estilo.Botao("Enviar aviso", Estilo.Botao.PRIMARIO);
    private final JLabel contadores = new JLabel();

    private final DefaultTableModel modeloUsuarios = tabelaSomenteLeitura("Apelido", "Sala", "Endereço");
    private final DefaultTableModel modeloSalas = tabelaSomenteLeitura("Sala", "Participantes", "Histórico");

    private final Timer atualizador = new Timer(1000, e -> atualizarTabelas());

    public TelaServidor() {
        super("Servidor do Chat");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                fecharJanela();
            }
        });

        getContentPane().setBackground(Estilo.FUNDO);
        setLayout(new BorderLayout());
        add(montarCabecalho(), BorderLayout.NORTH);
        add(montarCentro(), BorderLayout.CENTER);
        add(montarRodape(), BorderLayout.SOUTH);

        botaoIniciar.addActionListener(e -> {
            if (servidor != null && servidor.isRodando()) pararServidor();
            else iniciarServidor();
        });
        botaoAviso.addActionListener(e -> enviarAviso());
        campoAviso.addActionListener(e -> enviarAviso());

        atualizarEstado();
        setSize(960, 620);
        setMinimumSize(new java.awt.Dimension(760, 480));
        setLocationRelativeTo(null);
    }

    // ---------- montagem da tela ----------

    private JPanel montarCabecalho() {
        JPanel direita = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        direita.setOpaque(false);
        JLabel rotuloPorta = new JLabel("PORTA");
        rotuloPorta.setFont(Estilo.mono(Font.BOLD, 10.5f));
        rotuloPorta.setForeground(Estilo.CABECALHO_TXT);
        campoPorta.setEditor(new JSpinner.NumberEditor(campoPorta, "#"));
        JTextField editorPorta = ((JSpinner.DefaultEditor) campoPorta.getEditor()).getTextField();
        editorPorta.setFont(Estilo.mono(Font.BOLD, 13f));
        editorPorta.setColumns(5);
        editorPorta.setBackground(Estilo.SUPERFICIE);
        editorPorta.setForeground(Estilo.PRIMARIA);
        editorPorta.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 4));
        campoPorta.setBorder(BorderFactory.createLineBorder(Estilo.BORDA_FORTE));
        direita.add(rotuloPorta);
        direita.add(campoPorta);
        direita.add(botaoIniciar);

        JPanel topo = Estilo.cabecalho(new JLabel("Servidor do Chat"), subtitulo, direita);

        JPanel faixa = new JPanel(new BorderLayout());
        faixa.setBackground(Estilo.PAINEL);
        faixa.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Estilo.BORDA),
                BorderFactory.createEmptyBorder(8, 14, 8, 20)));
        status.setFont(Estilo.mono(Font.BOLD, 12f));
        contadores.setFont(Estilo.mono(Font.PLAIN, 11.5f));
        contadores.setForeground(Estilo.TEXTO_SUAVE);
        JPanel estado = new JPanel(new BorderLayout(8, 0));
        estado.setOpaque(false);
        estado.add(led, BorderLayout.WEST);
        estado.add(status, BorderLayout.CENTER);
        faixa.add(estado, BorderLayout.WEST);
        faixa.add(contadores, BorderLayout.EAST);

        JPanel p = new JPanel(new BorderLayout());
        p.add(topo, BorderLayout.NORTH);
        p.add(faixa, BorderLayout.SOUTH);
        return p;
    }

    private JSplitPane montarCentro() {
        log.setEditable(false);
        log.setFont(Estilo.mono(Font.PLAIN, 12f));
        log.setBackground(new Color(0x050811));
        log.setForeground(Estilo.TERMINAL);
        log.setSelectionColor(new Color(0x13456A));
        log.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        JPanel painelLog = painelComTitulo("Log de eventos", Estilo.rolagem(log, log.getBackground()));

        JPanel direita = new JPanel(new GridLayout(2, 1, 0, 12));
        direita.setOpaque(false);
        direita.add(painelComTitulo("Usuários conectados", tabela(modeloUsuarios)));
        direita.add(painelComTitulo("Salas ativas", tabela(modeloSalas)));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, painelLog, direita);
        split.setUI(new BasicSplitPaneUI());   // divisor liso, sem o relevo do Metal
        ((BasicSplitPaneUI) split.getUI()).getDivider().setBorder(null);
        split.setResizeWeight(0.55);
        split.setBorder(BorderFactory.createEmptyBorder(14, 14, 8, 14));
        split.setOpaque(false);
        return split;
    }

    private JPanel montarRodape() {
        JButton abrirChat = new Estilo.Botao("Abrir um chat", Estilo.Botao.FANTASMA);
        abrirChat.setToolTipText("Abre uma janela de cliente já apontando para este servidor");
        abrirChat.addActionListener(e ->
                new TelaConexao("127.0.0.1", (Integer) campoPorta.getValue()).setVisible(true));

        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(6, 14, 14, 14));
        p.add(campoAviso, BorderLayout.CENTER);
        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        botoes.setOpaque(false);
        botoes.add(botaoAviso);
        botoes.add(abrirChat);
        p.add(botoes, BorderLayout.EAST);
        return p;
    }

    private static JPanel painelComTitulo(String titulo, java.awt.Component conteudo) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Estilo.PAINEL);
        p.setBorder(BorderFactory.createLineBorder(Estilo.BORDA));
        p.add(Estilo.rotuloSecao(titulo), BorderLayout.NORTH);
        if (conteudo instanceof JScrollPane) {
            ((JScrollPane) conteudo).setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Estilo.BORDA));
        }
        p.add(conteudo, BorderLayout.CENTER);
        return p;
    }

    private static JScrollPane tabela(DefaultTableModel modelo) {
        JTable t = new JTable(modelo);
        Estilo.estilizarTabela(t);
        return Estilo.rolagem(t, Estilo.PAINEL);
    }

    private static DefaultTableModel tabelaSomenteLeitura(String... colunas) {
        return new DefaultTableModel(colunas, 0) {
            @Override
            public boolean isCellEditable(int linha, int coluna) {
                return false;
            }
        };
    }

    // ---------- acoes ----------

    private void iniciarServidor() {
        int porta = (Integer) campoPorta.getValue();
        servidor = new Servidor();
        servidor.setOuvinte(linha -> SwingUtilities.invokeLater(() -> {
            log.append(linha + "\n");
            log.setCaretPosition(log.getDocument().getLength());
            atualizarEstado();
        }));
        try {
            servidor.abrir(porta);
        } catch (IOException e) {
            servidor = null;
            JOptionPane.showMessageDialog(this,
                    "Não foi possível abrir a porta " + porta + ":\n" + e.getMessage(),
                    "Erro ao iniciar", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Thread aceitacao = new Thread(servidor::aceitarConexoes, "Servidor-accept");
        aceitacao.start();
        atualizador.start();
        atualizarEstado();
    }

    private void pararServidor() {
        if (servidor != null) servidor.parar();
        atualizador.stop();
        atualizarTabelas();
        atualizarEstado();
    }

    private void enviarAviso() {
        String texto = campoAviso.getText().trim();
        if (texto.isEmpty() || servidor == null || !servidor.isRodando()) return;
        servidor.avisarTodos(texto);
        campoAviso.setText("");
    }

    private void fecharJanela() {
        if (servidor != null && servidor.isRodando()) {
            int r = JOptionPane.showConfirmDialog(this,
                    "O servidor está rodando. Parar o servidor e fechar?",
                    "Fechar servidor", JOptionPane.YES_NO_OPTION);
            if (r != JOptionPane.YES_OPTION) return;
            pararServidor();
        }
        dispose();
    }

    // ---------- atualizacao da tela ----------

    private void atualizarEstado() {
        boolean rodando = servidor != null && servidor.isRodando();
        campoPorta.setEnabled(!rodando);
        botaoIniciar.setText(rodando ? "Parar servidor" : "Iniciar servidor");
        botaoIniciar.setVariante(rodando ? Estilo.Botao.PERIGO : Estilo.Botao.PRIMARIO);
        led.setEstado(rodando ? Estilo.SUCESSO : Estilo.TEXTO_SUAVE, rodando);
        campoAviso.setEnabled(rodando);
        botaoAviso.setEnabled(rodando);

        if (rodando) {
            status.setText("ONLINE  ·  porta " + campoPorta.getValue());
            status.setForeground(Estilo.SUCESSO);
            subtitulo.setText("Clientes conectam em: " + String.join("  ·  ", Estilo.enderecosLocais())
                    + "  (porta " + campoPorta.getValue() + ")");
        } else {
            status.setText("OFFLINE  ·  servidor parado");
            status.setForeground(Estilo.TEXTO_SUAVE);
            subtitulo.setText("mediador e repositório de dados comuns · TCP multithread");
            atualizador.stop();
        }
        atualizarContadores();
    }

    private void atualizarTabelas() {
        if (servidor == null) return;
        preencher(modeloUsuarios, servidor.resumoUsuarios());
        preencher(modeloSalas, servidor.resumoSalas());
        atualizarContadores();
    }

    private void atualizarContadores() {
        if (servidor == null) {
            contadores.setText(" ");
            return;
        }
        contadores.setText(servidor.totalConexoes() + " conexão(ões)  ·  "
                + modeloUsuarios.getRowCount() + " usuário(s)  ·  "
                + modeloSalas.getRowCount() + " sala(s)");
    }

    private static void preencher(DefaultTableModel modelo, List<String[]> linhas) {
        modelo.setRowCount(0);
        for (String[] l : linhas) modelo.addRow(l);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Estilo.aplicarLookAndFeel();
            new TelaServidor().setVisible(true);
        });
    }
}
