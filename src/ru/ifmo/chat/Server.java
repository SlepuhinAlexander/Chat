package ru.ifmo.chat;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public class Server {
    /**
     * Relative path to settings file
     */
    private static final Path SETTINGS = Paths.get("resources/Server.properties");

    /**
     * Listing of mandatory settings from the settings file that are required to startup the Server.
     */
    private static final String[] MANDATORY_SETTINGS = {"port"};

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
        new Server().start();
    }

    /**
     * Initializes the Server's properties by reading the SETTINGS file.
     * In case of missing a mandatory setting requests the user for its value and updated the SETTINGS file.
     */
    private void init() throws InitializationException{
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
        InetSocketAddress endpoint = new InetSocketAddress("localhost",
                Integer.parseInt(properties.getProperty("port")));
        try (ServerSocket serverSocket = new ServerSocket(endpoint.getPort())){
            Socket clientSocket = serverSocket.accept();
            ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
            ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
            while (true) {
                out.writeObject(in.readObject());
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }


}
