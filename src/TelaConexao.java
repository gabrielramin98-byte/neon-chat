import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * Tela de entrada do cliente grafico: coleta servidor, porta, apelido e
 * sala inicial, abre o socket TCP e realiza o LOGIN.
 *
 * A conexao e o LOGIN sao feitos em uma thread separada para que a janela
 * nao congele enquanto aguarda o servidor. Se o servidor recusar (ex.: erro
 * 02 - apelido em uso), a mensagem aparece aqui mesmo e o usuario tenta de novo.
 */
public class TelaConexao extends JFrame {

    private final JTextField campoHost = new Estilo.CampoTexto("ex.: 192.168.0.10");
    private final JTextField campoPorta = new Estilo.CampoTexto("5050");
    private final JTextField campoApelido = new Estilo.CampoTexto("como você quer ser chamado");
    private final JTextField campoSala = new Estilo.CampoTexto("geral");
    private final JButton botaoEntrar = new Estilo.Botao("Entrar no chat", Estilo.Botao.PRIMARIO);
    private final JLabel mensagem = new JLabel(" ");

    public TelaConexao(String host, int porta) {
        super("Entrar no chat");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        campoHost.setText(host);
        campoPorta.setText(String.valueOf(porta));
        campoSala.setText("geral");

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Estilo.PAINEL);
        form.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.gridx = 0;
        c.gridy = 0;

        adicionarCampo(form, c, "Servidor (IP ou nome)", campoHost);
        adicionarCampo(form, c, "Porta", campoPorta);
        adicionarCampo(form, c, "Seu apelido", campoApelido);
        adicionarCampo(form, c, "Sala para entrar", campoSala);

        mensagem.setForeground(Estilo.ERRO);
        c.insets = new Insets(4, 0, 8, 0);
        form.add(mensagem, c);
        c.gridy++;

        botaoEntrar.setFont(Estilo.fonte(Font.BOLD, 14f));
        botaoEntrar.setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 18));
        c.insets = new Insets(0, 0, 0, 0);
        form.add(botaoEntrar, c);

        setLayout(new BorderLayout());
        getContentPane().setBackground(Estilo.PAINEL);
        add(Estilo.cabecalho(new JLabel("Entrar no chat"),
                new JLabel("conecte-se a um servidor via TCP"), null), BorderLayout.NORTH);
        add(form, BorderLayout.CENTER);

        botaoEntrar.addActionListener(e -> conectar());
        campoApelido.addActionListener(e -> conectar());
        campoSala.addActionListener(e -> conectar());
        getRootPane().setDefaultButton(botaoEntrar);

        pack();
        setSize(400, getHeight());
        setResizable(false);
        setLocationByPlatform(true);
    }

    private static void adicionarCampo(JPanel form, GridBagConstraints c, String rotulo, JTextField campo) {
        JLabel l = new JLabel(rotulo.toUpperCase());
        l.setFont(Estilo.mono(Font.BOLD, 10.5f));
        l.setForeground(Estilo.ROTULO);
        c.insets = new Insets(0, 2, 5, 0);
        form.add(l, c);
        c.gridy++;
        c.insets = new Insets(0, 0, 14, 0);
        form.add(campo, c);
        c.gridy++;
    }

    private void conectar() {
        if (!botaoEntrar.isEnabled()) return;

        final String host = campoHost.getText().trim();
        final String apelido = campoApelido.getText().trim();
        final String sala = campoSala.getText().trim();
        final int porta;
        try {
            porta = Integer.parseInt(campoPorta.getText().trim());
        } catch (NumberFormatException e) {
            mostrarErro("Porta inválida.");
            return;
        }
        if (host.isEmpty())    { mostrarErro("Informe o endereço do servidor."); return; }
        if (apelido.isEmpty()) { mostrarErro("Informe um apelido."); return; }
        if (sala.isEmpty())    { mostrarErro("Informe o nome da sala."); return; }

        botaoEntrar.setEnabled(false);
        botaoEntrar.setText("Conectando...");
        mensagem.setText(" ");

        // thread de conexao: nao bloqueia a interface
        new Thread(() -> {
            Socket socket = new Socket();
            try {
                socket.connect(new InetSocketAddress(host, porta), 5000);
                socket.setSoTimeout(5000);   // limite de espera durante o LOGIN
                BufferedReader entrada = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), "UTF-8"));
                PrintWriter saida = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));

                aguardarResposta(entrada);   // saudacao inicial (CMD=OK)

                saida.println(Protocolo.montar("CMD", "LOGIN", "USER", apelido));
                saida.flush();
                Map<String, String> resposta = aguardarResposta(entrada);

                if (Protocolo.campo(resposta, "CMD", "").equalsIgnoreCase("ERR")) {
                    socket.close();
                    String erro = "Erro " + Protocolo.campo(resposta, "COD", "??") + ": "
                            + Protocolo.campo(resposta, "INFO", "");
                    SwingUtilities.invokeLater(() -> falhou(erro));
                    return;
                }

                socket.setSoTimeout(0);   // a partir daqui a leitura pode bloquear indefinidamente
                SwingUtilities.invokeLater(() -> {
                    new TelaChat(socket, entrada, saida, apelido, host + ":" + porta, sala).setVisible(true);
                    dispose();
                });
            } catch (IOException e) {
                try { socket.close(); } catch (IOException ignorada) { }
                String motivo = (e.getMessage() == null) ? e.getClass().getSimpleName() : e.getMessage();
                SwingUtilities.invokeLater(() -> falhou("Não foi possível conectar: " + motivo));
            }
        }, "Cliente-conexao").start();
    }

    /** Le linhas ate chegar a resposta direta (OK ou ERR), ignorando notificacoes. */
    private static Map<String, String> aguardarResposta(BufferedReader entrada) throws IOException {
        String linha;
        while ((linha = entrada.readLine()) != null) {
            Map<String, String> campos = Protocolo.analisar(linha);
            String cmd = Protocolo.campo(campos, "CMD", "").toUpperCase();
            if (cmd.equals("OK") || cmd.equals("ERR")) return campos;
        }
        throw new IOException("conexão encerrada pelo servidor");
    }

    private void falhou(String texto) {
        botaoEntrar.setEnabled(true);
        botaoEntrar.setText("Entrar no chat");
        mostrarErro(texto);
    }

    private void mostrarErro(String texto) {
        mensagem.setText("<html>" + texto.replace("&", "&amp;").replace("<", "&lt;") + "</html>");
        pack();
        setSize(400, getHeight());
    }

    public static void main(String[] args) {
        String host = (args.length > 0) ? args[0] : "127.0.0.1";
        int porta = Servidor.PORTA_PADRAO;
        if (args.length > 1) {
            try { porta = Integer.parseInt(args[1]); } catch (NumberFormatException e) { }
        }
        final int p = porta;
        SwingUtilities.invokeLater(() -> {
            Estilo.aplicarLookAndFeel();
            new TelaConexao(host, p).setVisible(true);
        });
    }
}
