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

public class Fork implements Serializable {
    // Main server socket connection
    transient private Socket mainConn;
    transient private ObjectInputStream mainIn;
    transient private ObjectOutputStream mainOut;

    // Fork server info
    private String hostname = "localhost";
    private int forkServerPort;
    transient private ServerSocket forkServerConnection;

    transient private Socket leftConn;
    transient private ObjectInputStream leftIn;
    transient private ObjectOutputStream leftOut;

    transient private Socket rightConn;
    transient private ObjectInputStream rightIn;
    transient private ObjectOutputStream rightOut;

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

            // TODO change IP to global IP
            
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
                System.out.println(message);
                switch (message.getKind()) {
                    case STOP:
                        done = true;
                        break;
                    case TEXT:
                        break;
                    case SETUP:
                        leftConn = forkServerConnection.accept();
                        rightConn = forkServerConnection.accept();
                        break;
                    case START:
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
     * @param philosopherConnection which philosopher to handle
     */
    private void listenPhilosopher(Socket philosopherConnection) {
        try {
            ObjectInputStream in = new ObjectInputStream(philosopherConnection.getInputStream());
            ObjectOutputStream out = new ObjectOutputStream(philosopherConnection.getOutputStream());
            if (philosopherConnection == leftConn) {
                leftIn = in;
                leftOut = out;
                System.out.println("Left philosopher connected!");
            } else {
                rightIn = in;
                rightOut = out;
                System.out.println("Right philosopher connected!");
            }

            Message message;
            while ((message = (Message) in.readObject()) != null) {
                System.out.println(message);
                switch (message.getKind()) {
                    case REQUEST_FORK:
                        Thread.sleep(3000);
                        out.writeObject(new Message(Message.Kind.FORK_ACQUIRED));
                        break;
                }
            }

        } catch (IOException | ClassNotFoundException | InterruptedException e) {
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

    @SuppressWarnings("unused")
    int getForkServerPort() {
        return forkServerPort;
    }

    @SuppressWarnings("unused")
    public void setForkServerPort(int forkServerPort) {
        this.forkServerPort = forkServerPort;
    }

    @SuppressWarnings("unused")
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
