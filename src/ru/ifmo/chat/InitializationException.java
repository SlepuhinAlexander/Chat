package ru.ifmo.chat;

/**
 * A marker exception class to indicate that something (e.g. {@link Server} or {@link Client}) has failed to initialize
 * due to some issues with its relative config file(s).
 */
public class InitializationException extends Exception {
    public InitializationException() {
        super();
    }

    public InitializationException(String message) {
        super(message);
    }
}
