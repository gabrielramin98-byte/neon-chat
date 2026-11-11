import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * Tela inicial da versao grafica. Permite abrir o painel do servidor e
 * quantas janelas de cliente forem necessarias (util para demonstrar
 * varios usuarios na mesma maquina).
 *
 * Uso: java ChatApp            (abre o menu)
 *      java ChatApp servidor   (abre direto o painel do servidor)
 *      java ChatApp cliente    (abre direto a tela de conexao)
 */
public class ChatApp extends JFrame {

    public ChatApp() {
        super("Chat Distribuído");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel opcoes = new JPanel(new GridLayout(1, 2, 16, 0));
        opcoes.setBackground(Estilo.FUNDO);
        opcoes.setBorder(BorderFactory.createEmptyBorder(22, 22, 10, 22));
        opcoes.add(cartao("01 // SERVIDOR", "Iniciar servidor",
                "Abre o painel do servidor: escolha a porta, acompanhe o log, os usuários e as salas.",
                () -> new TelaServidor().setVisible(true)));
        opcoes.add(cartao("02 // CLIENTE", "Entrar no chat",
                "Conecta a um servidor, escolhe um apelido e conversa nas salas. Abra quantos quiser.",
                () -> new TelaConexao("127.0.0.1", Servidor.PORTA_PADRAO).setVisible(true)));

        JLabel rodape = new JLabel("SOCKETS TCP  ·  CLIENTES E SERVIDOR MULTITHREAD  ·  PROTOCOLO CAMPO=VALOR COM BYTE STUFFING");
        rodape.setForeground(Estilo.TEXTO_SUAVE);
        rodape.setFont(Estilo.mono(Font.PLAIN, 10f));
        rodape.setHorizontalAlignment(JLabel.CENTER);
        rodape.setBorder(BorderFactory.createEmptyBorder(6, 20, 16, 20));

        setLayout(new BorderLayout());
        getContentPane().setBackground(Estilo.FUNDO);
        add(Estilo.cabecalho(new JLabel("Chat Distribuído"),
                new JLabel("chat multiusuário com salas · sistemas distribuídos"), null), BorderLayout.NORTH);
        add(opcoes, BorderLayout.CENTER);
        add(rodape, BorderLayout.SOUTH);

        setSize(700, 340);
        setResizable(false);
        setLocationRelativeTo(null);
    }

    /** Cartao clicavel com etiqueta, titulo e descricao; acende em neon sob o mouse. */
    private static JPanel cartao(String etiqueta, String titulo, String descricao, Runnable acao) {
        final boolean[] sobre = { false };

        JLabel e = new JLabel(etiqueta);
        e.setFont(Estilo.mono(Font.BOLD, 10.5f));
        e.setForeground(Estilo.PRIMARIA);
        JLabel t = new JLabel(titulo + "  →");
        t.setFont(Estilo.fonte(Font.BOLD, 17f));
        t.setForeground(Estilo.TEXTO);
        JLabel d = new JLabel("<html>" + descricao + "</html>");
        d.setForeground(Estilo.TEXTO_SUAVE);
        d.setFont(Estilo.fonte(Font.PLAIN, 12.5f));
        d.setVerticalAlignment(JLabel.TOP);

        JPanel p = new JPanel(new BorderLayout(0, 10)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Estilo.suavizar(g);
                int w = getWidth(), h = getHeight();
                RoundRectangle2D forma = new RoundRectangle2D.Float(4, 4, w - 8, h - 8, 18, 18);
                g2.setPaint(new GradientPaint(0, 0, Estilo.SUPERFICIE, 0, h, Estilo.PAINEL));
                g2.fill(forma);
                if (sobre[0]) {
                    Estilo.brilho(g2, forma, Estilo.PRIMARIA, 3);
                    g2.setPaint(new GradientPaint(0, 0, Estilo.PRIMARIA, w, h, Estilo.SECUNDARIA));
                } else {
                    g2.setColor(Estilo.BORDA_FORTE);
                }
                g2.setStroke(new BasicStroke(1.3f));
                g2.draw(forma);
                g2.setPaint(new GradientPaint(22, 0, Estilo.PRIMARIA, 70, 0, Estilo.SECUNDARIA));
                g2.fillRect(22, 4, 48, 2);   // detalhe no topo do cartao
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(22, 22, 20, 22));
        p.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel topo = new JPanel(new BorderLayout(0, 6));
        topo.setOpaque(false);
        topo.add(e, BorderLayout.NORTH);
        topo.add(t, BorderLayout.SOUTH);
        p.add(topo, BorderLayout.NORTH);
        p.add(d, BorderLayout.CENTER);
        p.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent ev) { sobre[0] = true;  t.setForeground(Estilo.PRIMARIA); p.repaint(); }
            @Override public void mouseExited(MouseEvent ev)  { sobre[0] = false; t.setForeground(Estilo.TEXTO);    p.repaint(); }
            @Override public void mouseClicked(MouseEvent ev) { acao.run(); }
        });
        return p;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Estilo.aplicarLookAndFeel();
            String modo = (args.length > 0) ? args[0].toLowerCase() : "";
            if (modo.equals("servidor"))     new TelaServidor().setVisible(true);
            else if (modo.equals("cliente")) new TelaConexao("127.0.0.1", Servidor.PORTA_PADRAO).setVisible(true);
            else                             new ChatApp().setVisible(true);
        });
    }
}
