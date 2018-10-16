import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

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
    transient private CountDownLatch forksLatch;

    private Philosopher(String name, String mainServerAddress, int mainServerPort) {
        this.name = name;
        this.mainServerAddress = mainServerAddress;
        this.mainServerPort = mainServerPort;
        acquiredForks = new boolean[2];
        forksLatch = new CountDownLatch(2);
        connectToMainServer();
    }

    private void connectToMainServer() {
        try {
            mainServerConnection = new Socket(mainServerAddress, mainServerPort);
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
                System.err.println(message);
                switch (message.getKind()) {
                    case START:
                        new Thread(() -> listenForkServer(leftForkServerConnection), "Thread-LeftForkServer").start();
                        new Thread(() -> listenForkServer(rightForkServerConnection), "Thread-RightForkServer").start();
                        new Thread(this).start();
                        break;
                    case STOP:
                        break;
                    case TEXT:
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

            System.err.println(String.format("Connecting to right fork: %s at %d", rightForkServerAddress, rightForkServerPort));
            rightForkServerConnection = new Socket(rightForkServerAddress, rightForkServerPort);
            System.err.println("Connection done!");
            rightForkServerOutputStream = new ObjectOutputStream(rightForkServerConnection.getOutputStream());
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
                    case TEXT:
                        System.err.println(message);
                        break;
                    case FORK_ACQUIRED:
                        acquiredForks[forkIndex] = true;
                        System.err.println(forkSide + " fork acquired!");
                        synchronized (this.forksLatch) { forksLatch.countDown(); }
                        break;
                    case RELEASE_FORK:
                        acquiredForks[forkIndex] = false;
                        break;
                    case FORK_IN_USE:
                        System.err.println(forkSide + " fork in use!");
                        synchronized (this.forksLatch) { forksLatch.countDown(); }
                        break;
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void sendMessageToForkServer(ObjectOutputStream errputStream, Message message) {
        sendMessage(errputStream, message);
    }

    private void sendMessage(ObjectOutputStream errputStream, Message message) {
        try {
            errputStream.writeObject(message);
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
        Message releaseForkMessage = new Message(this, Message.Kind.RELEASE_FORK);
        sendMessageToForkServer(leftForkServerOutputStream, releaseForkMessage);
        sendMessageToForkServer(rightForkServerOutputStream, releaseForkMessage);
        forksLatch = new CountDownLatch(2);
    }

    private void requestForks() {
        Message requestForkMessage = new Message(this, Message.Kind.REQUEST_FORK);
        Message releaseForkMessage = new Message(Message.Kind.RELEASE_FORK);

        while (true) {
            System.err.println(name + " is requesting forks ...");
            sendMessageToForkServer(leftForkServerOutputStream, requestForkMessage);
            sendMessageToForkServer(rightForkServerOutputStream, requestForkMessage);
            try {
                forksLatch.await();
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (acquiredForks[0] && !acquiredForks[1]) {
                sendMessageToForkServer(leftForkServerOutputStream, releaseForkMessage);
                System.err.println("Giving back left fork ...");
            }
            else if (!acquiredForks[0] && acquiredForks[1]){
                sendMessageToForkServer(rightForkServerOutputStream, releaseForkMessage);
                System.err.println("Giving back right fork ...");
            }
            else if (acquiredForks[0] && acquiredForks[1]) return;
            else try {
                System.err.println("Waiting a random time ...");
                Thread.sleep((long) (Math.random() * 1000 + 100));
            } catch (InterruptedException e) { e.printStackTrace(); }
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
