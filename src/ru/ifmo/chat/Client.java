package ru.ifmo.chat;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public class Client {
    /**
     * Relative path to settings file
     */
    private static final Path SETTINGS = Paths.get("resources/Client.properties");
    /**
     * Listing of mandatory settings from the settings file that are required to startup the Client
     */
    private static final String[] MANDATORY_SETTINGS = {"server.ip", "server.port", "client.senderName"};

    static {
        if (!Files.exists(SETTINGS)) {
            try {
                Files.createFile(SETTINGS);
            } catch (IOException e) {
                System.out.println("Failed to create file " + SETTINGS.toAbsolutePath());
                e.printStackTrace();
            }
        }
    }

    /**
     * Overall holder for Client settings.
     */
    private final Properties properties = new Properties();

    public static void main(String[] args) {
        new Client().start();
    }

    /**
     * Initializes the Client's properties by reading the SETTINGS file.
     * In case of missing a mandatory setting requests the user for its value and updates the SETTINGS file.
     *
     * @throws InitializationException in case of any troubles with reading or setting the Client's properties.
     */
    private void init() throws InitializationException {
        Scanner scanner = new Scanner(System.in);
        try (BufferedReader reader = Files.newBufferedReader(SETTINGS)) {
            properties.load(reader);
        } catch (IOException e) {
            System.out.println("Failed to read from file " + SETTINGS.toAbsolutePath());
            e.printStackTrace();
            throw new InitializationException("Client settings were not properly set");
        }
        AtomicBoolean changed = new AtomicBoolean(false);
        Stream.of(MANDATORY_SETTINGS)
                .forEach(prop -> {
                    if (properties.getProperty(prop) == null) {
                        System.out.print("missing property '" + prop + "': ");
                        properties.setProperty(prop, scanner.nextLine());
                        changed.set(true);
                    }
                });
        if (changed.get()) {
            try (BufferedWriter writer = Files.newBufferedWriter(SETTINGS)) {
                properties.store(writer, "");
            } catch (IOException e) {
                System.out.println("Failed to write to file " + SETTINGS.toAbsolutePath());
                e.printStackTrace();
                throw new InitializationException("Client settings were not properly set");
            }
        }
    }

    /**
     * Initializes the Client's settings. Establishes connection to Server.
     * Starts up one Thread each for sending and receiving Messages.
     *
     * @see Message
     */
    private void start() {
        try {
            init();
        } catch (InitializationException e) {
            System.out.println("Failed to init the Client's settings. Check the settings in " +
                               SETTINGS.toAbsolutePath());
            e.printStackTrace();
            return;
        }
        Socket socket = new Socket();
        InetSocketAddress endpoint = new InetSocketAddress(properties.getProperty("server.ip"),
                Integer.parseInt(properties.getProperty("server.port")));
        try {
            socket.connect(endpoint);
        } catch (IOException e) {
            System.out.println("Failed to connect to Server at " + endpoint + "\nCheck the connection settings in " +
                               SETTINGS.toAbsolutePath());
            e.printStackTrace();
            return;
        }
        new Thread(new Sender(socket)).start();
        new Thread(new Receiver(socket)).start();
    }

    /**
     * Thread task to infinitely receive {@link Message}-s from {@link Server} and print them out in the system
     * console.
     */
    private class Receiver extends Worker {
        private final Socket socket;
        private ObjectInputStream in;

        public Receiver(Socket socket) {
            this.socket = Objects.requireNonNull(socket);
        }

        @Override
        protected void init() throws IOException {
            in = new ObjectInputStream(socket.getInputStream());
        }

        @Override
        protected void loop() throws IOException {
            Message received;
            try {
                received = ((Message) in.readObject());
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                received = null;
            }
            System.out.println(received);
        }

        @Override
        protected void stop() throws Exception {
            socket.close();
        }
    }

    /**
     * Thread task to infinitely wait for user input. The inputted string is wrapped into {@link Message} object and
     * sent to {@link Server}.
     */
    private class Sender extends Worker {
        private final Socket socket;
        private final String name = properties.getProperty("client.senderName");
        Scanner scanner = new Scanner(System.in);
        private ObjectOutputStream out;

        public Sender(Socket socket) {
            this.socket = Objects.requireNonNull(socket);
        }

        @Override
        protected void init() throws IOException {
            out = new ObjectOutputStream(socket.getOutputStream());
        }

        @Override
        protected void loop() throws Exception {
            System.out.print(name + ": ");
            String contents = scanner.nextLine();
            Message message = new Message(name, contents);
            message.setSent();
            out.writeObject(message);
        }

        @Override
        protected void stop() throws IOException {
            socket.close();
        }
    }
}
