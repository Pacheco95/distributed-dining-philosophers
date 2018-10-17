/*
 * Copyright (c) 2018. Michael Pacheco - All Rights Reserved
 * mdpgd95@gmail.com
 */

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

public class Fork implements Serializable {
    // Main server socket connection
    transient private Socket mainConn;
    transient private ObjectInputStream mainIn;
    transient private ObjectOutputStream mainOut;

    // Fork server info
    private String hostname = "localhost"; // TODO change IP to global IP
    private int forkServerPort;
    transient private ServerSocket forkServerConnection;

    transient private Socket leftConn;
    transient private ObjectInputStream leftIn;
    transient private ObjectOutputStream leftOut;

    transient private Socket rightConn;
    transient private ObjectInputStream rightIn;
    transient private ObjectOutputStream rightOut;

    transient FairnessPolicy policy;

    transient private boolean acquired;
    transient private Queue<ObjectOutputStream> queue;

    private Map<Socket, Integer> eatTimes;

    /**
     * Connect to main server and starts the fork server.
     * @param mainServerHostname address of the main server
     * @param mainServerPort main server port
     * @param forkServerPort the fork server port
     */
    private Fork(String mainServerHostname, int mainServerPort, int forkServerPort) {
        try {
            this.forkServerPort = forkServerPort;
            forkServerConnection = new ServerSocket(forkServerPort);
            queue = new LinkedList<>();
            eatTimes = new HashMap<>(2);
            policy = new AlternatedFairnessPolicy();

            // Start main server connection and pass self information details as this
            mainConn = new Socket(mainServerHostname, mainServerPort);
            mainOut = new ObjectOutputStream(mainConn.getOutputStream());
            mainOut.writeObject(this);

            listenMainServer();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Listen for main server commands
     */
    private void listenMainServer() {
        try {
            mainIn = new ObjectInputStream(mainConn.getInputStream());

            Message message;
            boolean done = false;
            while (!done && (message = (Message) mainIn.readObject()) != null) {
                switch (message.getKind()) {
                    case STOP:
                        done = true;
                        break;
                    case SETUP:
                        break;
                    case START:
                        leftConn = forkServerConnection.accept();
                        rightConn = forkServerConnection.accept();
                        new Thread(() -> listenPhilosopher(leftConn)).start();
                        new Thread(() -> listenPhilosopher(rightConn)).start();
                        break;
                }
            }
            cleanup();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Handle philosopher connection
     *
     * @param philosopher which philosopher to handle
     */
    private void listenPhilosopher(Socket philosopher) {
        ObjectInputStream in = null;
        ObjectOutputStream out = null;

        try {
            in = new ObjectInputStream(philosopher.getInputStream());
            out = new ObjectOutputStream(philosopher.getOutputStream());
            String philosopherName = (String) in.readObject();
            if (philosopher == leftConn) {
                leftIn = in;
                leftOut = out;
                System.out.println(String.format("Left philosopher connected: %s!", philosopherName));
            } else {
                rightIn = in;
                rightOut = out;
                System.out.println(String.format("Right philosopher connected: %s!", philosopherName));
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }

        assert in != null;
        assert out != null;

        eatTimes.put(philosopher, 0);

        try {
            Message message;
            while ((message = (Message) in.readObject()) != null) {
                synchronized (this) {
                    switch (message.getKind()) {
                        case REQUEST_FORK:
                            if (philosopher == policy.whoWillEat(eatTimes)) {
                                acquired = true;
                                out.writeObject(new Message(Message.Kind.FORK_ACQUIRED));
                            } else {
                                out.writeObject(new Message(Message.Kind.FORK_IN_USE));
                                queue.add(out);
                            }
                            break;
                        case RELEASE_FORK:
                            acquired = false;
                            boolean gotToEat = (boolean) message.getMessage();
                            if (gotToEat) {
                                int times = eatTimes.get(philosopher);
                                eatTimes.put(philosopher, times + 1);
                            } else queue.add(out);

                            if (!queue.isEmpty()) {
                                queue.poll().writeObject(new Message(Message.Kind.FORK_ACQUIRED));
                                acquired = true;
                            }
                            System.out.println(String.format("[%2d, %2d]", eatTimes.get(leftConn), eatTimes.get(rightConn)));
                            break;
                    }
                }
            }

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void cleanup() {
        System.out.println("Cleaning up ...");
        try {
            leftIn.close();
            rightIn.close();

            leftOut.close();
            rightOut.close();

            mainIn.close();
            mainOut.close();

            mainConn.close();
            rightConn.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    int getForkServerPort() {
        return forkServerPort;
    }

    String getHostname() {
        return hostname;
    }

    @SuppressWarnings("unused")
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public static void main(String[] args) {
        String mainServerAddress = args[0];
        int mainServerPort = Integer.parseInt(args[1]);
        int forkServerPort = Integer.parseInt(args[2]);
        new Fork(mainServerAddress, mainServerPort, forkServerPort);
    }
}
