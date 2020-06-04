package ru.ifmo.chat;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;


/**
 * Client class for the Chat messaging program. Corresponds to the {@link Server} class.
 * {@link Client} is designed to send {@link Message}-s to the {@link Server} and to receive {@link Message}-s from the
 * {@link Server} independently.
 * Provides a minimal user interface to compose a {@link Message} to send and to view received {@link Message}-s.
 * Received {@link Message}-s may originate from any other instance of this {@link Client}, connected to the same
 * {@link Server}, allowing multi-user conversation in a Chat.
 */
public class Client {
    /**
     * Relative path to settings file
     */
    private static final Path SETTINGS = Paths.get("resources/Client.properties");
    /**
     * Listing of mandatory settings from the settings file that are required to startup the {@link Client}.
     */
    private static final String[] MANDATORY_SETTINGS = {"server.ip", "server.port", "client.senderName"};

    // Setting up the mandatory config file in case it does not exist for some reason.
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
     * Overall holder for {@link Client} settings.
     */
    private final Properties properties = new Properties();

    /**
     * Unique {@link UUID} to identify this {@link Client} in {@link Server} connections.
     */
    private final UUID uuid;

    public Client() {
        this.uuid = UUID.randomUUID();
    }

    public static void main(String[] args) {
        new Client().start();
    }

    /**
     * Initializes the {@link Client} properties by reading the {@link Client#SETTINGS} file.
     * In case of missing a mandatory setting requests the user for its value and updates the {@link Client#SETTINGS}
     * file.
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
     * Initializes the {@link Client}'s settings. Establishes connection to {@link Server}.
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
        InetSocketAddress endpoint = new InetSocketAddress(properties.getProperty("server.ip"),
                Integer.parseInt(properties.getProperty("server.port")));
        try {
            connect(endpoint);
        } catch (IOException e) {
            System.out.println("Failed to connect to Server at " + endpoint + "\nCheck the connection settings in " +
                               SETTINGS.toAbsolutePath());
            e.printStackTrace();
        }
    }

    /**
     * Tries to connect to a specified {@code endpoint} taken from config and describing a {@link Server} to connect.
     * In success, introduces itself to the {@link Server} and Starts up one Thread each for sending and
     * receiving {@link Message}-s.
     */
    private void connect(InetSocketAddress endpoint) throws IOException {
        Socket socket = new Socket();
        socket.connect(endpoint);
        socket.getOutputStream().write(uuid.toString().getBytes(StandardCharsets.UTF_8));
        new Thread(new Sender(socket)).start();
        new Thread(new Receiver(socket)).start();
    }

    /**
     * Thread task to infinitely receive {@link Message}-s from {@link Server} and print them out in the system
     * console.
     */
    @SuppressWarnings("InnerClassMayBeStatic")
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
            Message received = null;
            try {
                received = (Message) in.readObject();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            System.out.println(received);
        }

        @Override
        protected void stop() throws IOException {
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
        protected void loop() throws IOException {
            System.out.print(name + ": ");
            String contents = scanner.nextLine();
            Message message = new Message(name, contents);
            message.setSent();
            if (socket.isConnected() && socket.isBound() && !socket.isClosed() && !socket.isOutputShutdown()) {
                out.writeObject(message);
            } else {
                System.out.println("lost connection to server");
            }
        }

        @Override
        protected void stop() throws IOException {
            socket.close();
        }
    }
}
