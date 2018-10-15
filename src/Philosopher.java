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
    transient private CountDownLatch forksLatch;

    private Philosopher(String name, String mainServerAddress, int mainServerPort) {
        this.name = name;
        this.mainServerAddress = mainServerAddress;
        this.mainServerPort = mainServerPort;
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
                System.out.println(message);
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
            leftForkServerConnection = new Socket(leftForkServerAddress, leftForkServerPort);
            leftForkServerOutputStream = new ObjectOutputStream(leftForkServerConnection.getOutputStream());

            String rightForkServerAddress = (String) connectionInfo[2];
            int rightForkServerPort = (int) connectionInfo[3];
            rightForkServerConnection = new Socket(rightForkServerAddress, rightForkServerPort);
            rightForkServerOutputStream = new ObjectOutputStream(rightForkServerConnection.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void listenForkServer(Socket forkServerConnection) {
        ObjectInputStream in = null;
        try {
            if (forkServerConnection == leftForkServerConnection)
                in = new ObjectInputStream(leftForkServerConnection.getInputStream());
            else
                in = new ObjectInputStream(rightForkServerConnection.getInputStream());
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
                        System.out.println(message);
                        break;
                    case FORK_ACQUIRED:
                        synchronized (this) { forksLatch.countDown(); }
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
            System.out.println("----------------------------------------------------------");
            think();
            requestForks();
            // Block until all forks are acquired
            try { forksLatch.await(); } catch (InterruptedException e) { e.printStackTrace(); }
            System.out.println("Forks acquired!");
            eat();
            giveBackForks();
        }
    }

    private void eat() {
        System.out.println(name + " is eating ...");
        try {
            int MAX_EAT_TIME = 5000;
            Thread.sleep((long) (Math.random() * MAX_EAT_TIME)); } catch (InterruptedException ignored) {}
        System.out.println(name + " ends eating ...");
    }

    private void giveBackForks() {
        Message releaseForkMessage = new Message(this, Message.Kind.FORK_RELEASED);
        sendMessageToForkServer(leftForkServerOutputStream, releaseForkMessage);
        sendMessageToForkServer(rightForkServerOutputStream, releaseForkMessage);
        forksLatch = new CountDownLatch(2);
    }

    private void requestForks() {
        System.out.println(name + " is requesting forks ...");
        Message requestForkMessage = new Message(this, Message.Kind.REQUEST_FORK);
        sendMessageToForkServer(leftForkServerOutputStream, requestForkMessage);
        sendMessageToForkServer(rightForkServerOutputStream, requestForkMessage);
    }

    private void think() {
        System.out.println(name + " is thinking ...");
        try {
            int MAX_THINK_TIME = 5000;
            Thread.sleep((long) (Math.random() * MAX_THINK_TIME)); } catch (InterruptedException ignored) {}
        System.out.println(name + " woke up ...");
    }

    String getName() {
        return name;
    }

    public static void main(String[] args) {
        new Philosopher(args[0], args[1], Integer.parseInt(args[2]));
    }
}
