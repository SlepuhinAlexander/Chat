package ru.ifmo.chat;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Properties;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Server class for the Chat messaging program. Corresponds to the {@link Client} class.
 * {@link Server} is designed to hold connections with any {@link Client}-s attached to it; to receive
 * {@link Message}-s from any connected {@link Client} independently; and to send out each of these {@link Message}-s
 * to all {@link Client}-s connected to this Chat (to this {@link Server}), except the original author.
 */
public class Server {
    /**
     * Relative path to settings file
     */
    private static final Path SETTINGS = Paths.get("resources/Server.properties");

    /**
     * Listing of mandatory settings from the settings file that are required to startup the {@link Server}.
     */
    private static final String[] MANDATORY_SETTINGS = {"port"};

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
     * Thread-safe blocking collection to store connected {@link Server.ClientEntity}-es.
     * Connections are all collected by {@link Server.Greeter} and processed by a {@link Server.Receiver} each.
     * Used for further sending out {@link Message}-s from {@link Server#messageQueue} by {@link Server.Distributor}.
     */
    private final ConcurrentHashMap<UUID, ClientEntity> clients = new ConcurrentHashMap<>();

    /**
     * Thread-safe blocking queue to collect {@link Message}-s coming from connected {@link Client}-s (via
     * {@link Server.Receiver}-s) and to send them out to all connected participants of the Chat
     * (via {@link Server.Distributor}).
     */
    private final LinkedBlockingQueue<MessageEntity> messageQueue = new LinkedBlockingQueue<>();

    /**
     * Overall holder for {@link Server} settings.
     */
    private final Properties properties = new Properties();

    /**
     * Entry point for starting up a {@link Server}
     */
    public static void main(String[] args) {
        new Server().start();
    }

    /**
     * Initializes the {@link Server} properties by reading the {@link Server#SETTINGS} file.
     * In case of missing a mandatory setting requests the user for its value and updates the {@link Server#SETTINGS}
     * file.
     */
    private void init() throws InitializationException {
        Scanner scanner = new Scanner(System.in);
        try (BufferedReader reader = Files.newBufferedReader(SETTINGS)) {
            properties.load(reader);
        } catch (IOException e) {
            System.out.println("Failed to read from file " + SETTINGS.toAbsolutePath());
            e.printStackTrace();
            throw new InitializationException("Server settings were not properly set");
        }
        AtomicBoolean changed = new AtomicBoolean(false);
        Stream.of(MANDATORY_SETTINGS)
                .forEach(prop -> {
                    if (properties.getProperty(prop) == null) {
                        System.out.print("Missing property '" + prop + "': ");
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
                throw new InitializationException("Server settings were not properly set");
            }
        }
    }

    private void start() {
        try {
            init();
        } catch (InitializationException e) {
            System.out.println("Failed to initialize server settings. Check the settings in " +
                               SETTINGS.toAbsolutePath());
            e.printStackTrace();
            return;
        }
        new Thread(new Greeter()).start();
        new Thread(new Distributor()).start();
    }

    /**
     * Thread task to infinitely await for {@link Client}'s connection.
     * Received connection is accepted, collected and held opened (until the {@link Client} disconnects itself).
     * Each connected {@link Client} gets its own {@link Receiver} to receive and collect incoming {@link Message}-s.
     * Each connected {@link Client} becomes a target for full distribution of incoming {@link Message}-s.
     */
    private class Greeter extends Worker {
        private ServerSocket serverSocket;

        @Override
        protected void init() {
            InetSocketAddress endpoint = new InetSocketAddress("localhost",
                    Integer.parseInt(properties.getProperty("port")));
            try {
                serverSocket = new ServerSocket(endpoint.getPort());
                System.out.println("server started");
            } catch (IOException e) {
                System.out.println("Failed to open a server socket at " + endpoint);
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
        }

        @Override
        protected void loop() {
            try {
                Socket clientSocket = serverSocket.accept();
                byte[] uuid = new byte[36];
                int read = clientSocket.getInputStream().read(uuid);
                if (read != 36) {
                    System.out.println("invalid uuid provided: " + new String(uuid));
                    return;
                }
                ClientEntity client = new ClientEntity(UUID.nameUUIDFromBytes(uuid),
                        clientSocket,
                        new ObjectOutputStream(clientSocket.getOutputStream()));
                clients.put(client.uuid, client);
                new Thread(new Receiver(client)).start();
                System.out.println("connection set: " + clientSocket.getInetAddress());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        protected void stop() {

        }
    }

    /**
     * Thread task corresponding to a single {@link Client} currently connected to this {@link Server}.
     * Infinitely awaits for incoming {@link Message}-s from the connected {@link Client}.
     * Receives {@link Message}-s and stores them into {@link Server#messageQueue} wrapped in
     * {@link Server.MessageEntity}.
     */
    private class Receiver extends Worker {
        private final ClientEntity client;
        private ObjectInputStream in;
        private UUID uuid;

        public Receiver(ClientEntity client) {
            this.client = Objects.requireNonNull(client);
        }

        @Override
        protected void init() throws IOException {
            uuid = client.uuid;
            if (!clients.containsKey(uuid)) throw new IllegalStateException("unknown UUID");
            in = new ObjectInputStream(client.socket.getInputStream());
            System.out.println("receiver initialized");
        }

        @Override
        protected void loop() throws IOException {
            Message received;
            try {
                received = (Message) in.readObject();
                System.out.println("message received: " + received);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                received = null;
            }
            if (received != null) {
                try {
                    messageQueue.put(new MessageEntity(uuid, received));
                    System.out.println("message put in queue: " + received);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    e.printStackTrace();
                }
            }
        }

        @Override
        protected void stop() throws IOException {
            in.close();
            client.socket.close();
            clients.remove(uuid);
        }
    }

    /**
     * Thread task to send out each {@link Message} stored in the {@link Server#messageQueue} to all currently
     * connected participants of the Chat except for original {@link Message}'s author.
     * Supposed to be in one instance per {@link Server}.
     */
    private class Distributor extends Worker {
        @Override
        protected void init() {
            System.out.println("distributor initialized");
        }

        @Override
        protected void loop() {
            try {
                MessageEntity message = messageQueue.take();
                System.out.println("message taken from queue: " + message.message);
                clients.keySet().forEach(uuid -> {
                    if (uuid.equals(message.author)) return;
                    try {
                        clients.get(uuid).out.writeObject(message.message);
                        System.out.println("message sent " + message.message + " to " + uuid);
                    } catch (IOException e) {
                        e.printStackTrace();
                        try {
                            clients.get(uuid).socket.close();
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                        clients.remove(uuid);
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                e.printStackTrace();
            }
        }

        @Override
        protected void stop() {

        }
    }

    /**
     * Wrapper class to hold together the {@link Client}'s UUID, Socket and ObjectOutputStream.
     * Is collected in {@link Server#clients} by {@link Server.Greeter}.
     * Used to identify {@link Client}-s and to send out {@link Message}s.
     */
    @SuppressWarnings("InnerClassMayBeStatic")
    private class ClientEntity {
        private final UUID uuid;
        private final Socket socket;
        private final ObjectOutputStream out;

        public ClientEntity(UUID uuid, Socket socket, ObjectOutputStream out) {
            this.uuid = Objects.requireNonNull(uuid);
            this.socket = Objects.requireNonNull(socket);
            this.out = Objects.requireNonNull(out);
        }
    }

    /**
     * Wrapper class to hold together a {@link Message} with its author (identified by the UUID of the {@link Client}
     * connection).
     */
    @SuppressWarnings("InnerClassMayBeStatic")
    private class MessageEntity {
        private final UUID author;
        private final Message message;

        public MessageEntity(UUID author, Message message) {
            this.author = Objects.requireNonNull(author);
            this.message = Objects.requireNonNull(message);
        }
    }


}
