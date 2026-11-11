import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Representa uma sala de bate-papo. E' parte do repositorio de dados
 * comuns mantido pelo servidor: guarda os participantes conectados e o
 * historico das ultimas mensagens.
 *
 * Todos os metodos sao "synchronized" porque varias threads de
 * atendimento (uma por cliente) acessam a mesma sala simultaneamente.
 */
public class Sala {

    private static final int MAX_HISTORICO = 50;

    private final String nome;
    private final List<ThreadCliente> participantes = new ArrayList<ThreadCliente>();
    private final LinkedList<String> historico = new LinkedList<String>();

    public Sala(String nome) {
        this.nome = nome;
    }

    public String getNome() {
        return nome;
    }

    public synchronized void entrar(ThreadCliente c) {
        if (!participantes.contains(c)) participantes.add(c);
    }

    public synchronized void sair(ThreadCliente c) {
        participantes.remove(c);
    }

    public synchronized boolean vazia() {
        return participantes.isEmpty();
    }

    public synchronized int quantidade() {
        return participantes.size();
    }

    public synchronized int tamanhoHistorico() {
        return historico.size();
    }

    /** Envia a linha a todos os participantes, exceto o remetente. */
    public synchronized void difundir(String linha, ThreadCliente remetente) {
        for (ThreadCliente c : participantes) {
            if (c != remetente) c.enviar(linha);
        }
    }

    /** Envia a linha a todos os participantes, inclusive o remetente. */
    public synchronized void difundirTodos(String linha) {
        for (ThreadCliente c : participantes) {
            c.enviar(linha);
        }
    }

    public synchronized void registrarHistorico(String linha) {
        historico.addLast(linha);
        while (historico.size() > MAX_HISTORICO) historico.removeFirst();
    }

    /** Devolve as ultimas n mensagens registradas. */
    public synchronized List<String> ultimas(int n) {
        int ini = Math.max(0, historico.size() - n);
        return new ArrayList<String>(historico.subList(ini, historico.size()));
    }

    /** Devolve os apelidos dos participantes separados por virgula. */
    public synchronized String listaUsuarios() {
        StringBuilder sb = new StringBuilder();
        for (ThreadCliente c : participantes) {
            if (sb.length() > 0) sb.append(",");
            sb.append(c.getApelido());
        }
        return sb.toString();
    }
}
