import javafx.util.Pair;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MainServer {
    private Map<Socket, Pair<Philosopher, Pair<ObjectInputStream, ObjectOutputStream>>> philosophers;
    private Map<Socket, Pair<Fork, Pair<ObjectInputStream, ObjectOutputStream>>> forks;


    private MainServer(int port, int numberOfPhilosophers) {
        philosophers = new HashMap<>();
        forks = new HashMap<>();

        try {
            ServerSocket connection = new ServerSocket(port);

            for (int i = 0; i < 2*numberOfPhilosophers; i++) {
                Socket conn = connection.accept();
                ObjectInputStream in = new ObjectInputStream(conn.getInputStream());
                ObjectOutputStream out = new ObjectOutputStream(conn.getOutputStream());

                Object client = in.readObject();

                if (client instanceof Philosopher) {
                    philosophers.put(conn, new Pair<>((Philosopher) client, new Pair<>(in, out)));
                    System.out.println(String.format("Philosopher %s connected!", ((Philosopher) client).getName()));
                }
                else if (client instanceof Fork) {
                    forks.put(conn, new Pair<>((Fork) client, new Pair<>(in, out)));
                    System.out.println("Fork connected!");
                }
            }

            setupTopology();
            startDining();
            // TODO stop dining
            Thread.currentThread().join();
        } catch (IOException | ClassNotFoundException | InterruptedException e) {
            e.printStackTrace();
        }

    }

    private void startDining() {
        Message startMessage = new Message(Message.Kind.START);

        forks.forEach((socket, data) -> {
            try {
                data.getValue().getValue().writeObject(startMessage);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        philosophers.forEach((socket, data) -> {
            try {
                data.getValue().getValue().writeObject(startMessage);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void setupTopology() {
        // Transform philosophers map to list
        ArrayList<Pair<Philosopher, Pair<ObjectInputStream, ObjectOutputStream>>> philosophersList = new ArrayList<>(philosophers.values());
        // Transform forks map to list
        ArrayList<Pair<Fork, Pair<ObjectInputStream, ObjectOutputStream>>> forksList = new ArrayList<>(forks.values());

        for (int i = 0; i < philosophersList.size(); i++) {
            try {
                int right = ((i + 1) == philosophersList.size()) ? 0 : (i + 1);

                Fork leftServer = forksList.get(i).getKey();
                String leftAddr = leftServer.getHostname();
                int leftPort = leftServer.getForkServerPort();
                
                Fork rightServer = forksList.get(right).getKey();
                String rightAddr = rightServer.getHostname();
                int rightPort = rightServer.getForkServerPort();

                // Send forks connection info to philosopher i
                ObjectOutputStream out = philosophersList.get(i).getValue().getValue();
                out.writeObject(new Message(new Object[] {leftAddr, leftPort, rightAddr, rightPort}, Message.Kind.SETUP));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        new MainServer(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
    }
}
