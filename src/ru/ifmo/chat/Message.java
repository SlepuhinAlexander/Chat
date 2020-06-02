package ru.ifmo.chat;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * Interaction object to send and to receive via {@link Server} and {@link Client}.
 */
public class Message implements Serializable {
    /**
     * Sets up the formatting for displaying timestamps
     */
    public static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("(MMM, d HH:mm:ss) ", Locale.ENGLISH);
    /**
     * Contains the Message's author and sender name.
     */
    private final String sender;
    /**
     * Message content
     */
    private final String message;
    /**
     * A timestamp displaying when this Message was sent
     */
    private ZonedDateTime sent;

    public Message(String sender, String message) {
        this.sender = Objects.requireNonNull(sender, "sender cannot be null");
        this.message = Objects.requireNonNull(message, "message cannot be null");
    }

    /**
     * Retrieves the timestamp when this Message was sent.
     *
     * @return the timestamp then this Message was sent.
     */
    public ZonedDateTime getSent() {
        return sent;
    }

    /**
     * Retrieves the Message's author name.
     *
     * @return the Message's author name.
     */
    public String getSender() {
        return sender;
    }

    /**
     * Retrieves the content of the Message.
     *
     * @return the content of the Message.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the {@link Message#sent} timestamp to current time moment.
     */
    public void setSent() {
        sent = ZonedDateTime.now();
    }

    @Override
    public String toString() {
        return sent == null ? sender + " : " + message :
                sent.format(FORMAT) + sender + " : " + message;
    }
}
