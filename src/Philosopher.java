import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.util.Objects;

@SuppressWarnings("PointlessBooleanExpression")
public class Philosopher implements Runnable, Serializable {
    private String name;

    transient private String mainServerAddress;

    transient private int mainServerPort;
    transient private Socket mainServerConnection;
    transient private Socket leftForkServerConnection;

    transient private ObjectOutputStream leftForkServerOutputStream;
    transient private Socket rightForkServerConnection;

    transient private ObjectOutputStream rightForkServerOutputStream;

    transient private boolean acquiredForks[];

    private boolean gotToEat;

    private Philosopher(String name, String mainServerAddress, int mainServerPort) {
        this.name = name;
        this.mainServerAddress = mainServerAddress;
        this.mainServerPort = mainServerPort;
        acquiredForks = new boolean[2];
        gotToEat = false;
        connectToMainServer();
    }

    private void connectToMainServer() {
        try {
            System.err.println("Connecting to main server ...");
            mainServerConnection = new Socket(mainServerAddress, mainServerPort);
            System.err.println("Connection established!");
            ObjectOutputStream mainServerOutputStream = new ObjectOutputStream(mainServerConnection.getOutputStream());
            mainServerOutputStream.writeObject(this);
        } catch (IOException e) {
            e.printStackTrace();
        }

        listenMainServer();
    }

    private void listenMainServer() {
        try {
            ObjectInputStream mainServerInputStream = new ObjectInputStream(mainServerConnection.getInputStream());
            Message message;
            while ((message = (Message) mainServerInputStream.readObject()) != null) {
                switch (message.getKind()) {
                    case START:
                        new Thread(() -> listenForkServer(leftForkServerConnection), "Left Fork Server Listener Thread").start();
                        new Thread(() -> listenForkServer(rightForkServerConnection), "Right Fork Server Listener Thread").start();
                        new Thread(this).start();
                        break;
                    case STOP:
                        // TODO end program
                        break;
                    case SETUP:
                        setupForkConnection((Object[]) message.getMessage());
                        break;
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void setupForkConnection(Object[] connectionInfo) {
        try {
            String leftForkServerAddress = (String) connectionInfo[0];
            int leftForkServerPort = (int) connectionInfo[1];
            String rightForkServerAddress = (String) connectionInfo[2];
            int rightForkServerPort = (int) connectionInfo[3];

            if (leftForkServerPort == rightForkServerPort) throw new AssertionError();

            System.err.println(String.format("Connecting to left fork: %s at %d", leftForkServerAddress, leftForkServerPort));
            leftForkServerConnection = new Socket(leftForkServerAddress, leftForkServerPort);
            System.err.println("Connection done!");
            leftForkServerOutputStream = new ObjectOutputStream(leftForkServerConnection.getOutputStream());
            leftForkServerOutputStream.writeObject(name);

            System.err.println(String.format("Connecting to right fork: %s at %d", rightForkServerAddress, rightForkServerPort));
            rightForkServerConnection = new Socket(rightForkServerAddress, rightForkServerPort);
            System.err.println("Connection done!");
            rightForkServerOutputStream = new ObjectOutputStream(rightForkServerConnection.getOutputStream());
            rightForkServerOutputStream.writeObject(name);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void listenForkServer(Socket forkServerConnection) {
        ObjectInputStream in = null;
        int forkIndex = -1;
        String forkSide = "";
        try {
            if (forkServerConnection == leftForkServerConnection) {
                in = new ObjectInputStream(leftForkServerConnection.getInputStream());
                forkIndex = 0;
                forkSide = "Left";
            }
            else {
                in = new ObjectInputStream(rightForkServerConnection.getInputStream());
                forkIndex = 1;
                forkSide = "Right";
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            Message message;
            while ((message = (Message) Objects.requireNonNull(in).readObject()) != null) {
                switch (message.getKind()) {
                    case START:
                        new Thread(this).start();
                        break;
                    case FORK_ACQUIRED:
                        acquiredForks[forkIndex] = true;
                        System.err.println(forkSide + " fork acquired!");
                        synchronized (this) { notify(); }
                        break;
                    case FORK_IN_USE:
                        System.err.println(forkSide + " fork in use!");
                        break;
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void sendMessageToForkServer(ObjectOutputStream outputStream, Message message) {
        sendMessage(outputStream, message);
    }

    private void sendMessage(ObjectOutputStream outputStream, Message message) {
        try {
            outputStream.writeObject(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        //noinspection InfiniteLoopStatement
        while (true) {
            System.err.println("----------------------------------------------------------");
            think();
            // Block until all forks are acquired
            requestForks();
            eat();
            gotToEat = true;
            giveBackForks();
        }
    }

    private void eat() {
        System.err.println(name + " is eating ...");
        try {
            int MAX_EAT_TIME = 5000;
            Thread.sleep((long) (Math.random() * MAX_EAT_TIME)); } catch (InterruptedException ignored) {}
        System.err.println(name + " ends eating ...");
    }

    private void giveBackForks() {
        Message releaseForkMessage = new Message(gotToEat, Message.Kind.RELEASE_FORK);
        if (acquiredForks[0]) {
            System.err.println("Giving back left fork ...");
            sendMessageToForkServer(leftForkServerOutputStream, releaseForkMessage);
            acquiredForks[0] = false;
        }
        if (acquiredForks[1]) {
            System.err.println("Giving back right fork ...");
            sendMessageToForkServer(rightForkServerOutputStream, releaseForkMessage);
            acquiredForks[1] = false;
        }
    }

    private void requestForks() {
        Message requestForkMessage = new Message(this, Message.Kind.REQUEST_FORK);
        System.err.println(name + " is requesting forks ...");

        sendMessageToForkServer(leftForkServerOutputStream, requestForkMessage);
        sendMessageToForkServer(rightForkServerOutputStream, requestForkMessage);

        synchronized (this) {
            while (true) {
                try {
                    // Wait for a fork
                    wait();
                    // Wait for another fork
                    wait(3000);
                    if (acquiredForks[0] && acquiredForks[1])
                        return;
                    // Forks not acquired
                    gotToEat = false;
                    giveBackForks();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void think() {
        System.err.println(name + " is thinking ...");
        try {
            int MAX_THINK_TIME = 5000;
            Thread.sleep((long) (Math.random() * MAX_THINK_TIME)); } catch (InterruptedException ignored) {}
        System.err.println(name + " woke up ...");
    }

    String getName() {
        return name;
    }

    public static void main(String[] args) {
        new Philosopher(args[0], args[1], Integer.parseInt(args[2]));
    }
}
