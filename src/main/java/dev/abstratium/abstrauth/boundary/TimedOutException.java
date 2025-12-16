package dev.abstratium.abstrauth.boundary;

public class TimedOutException extends RuntimeException {
    public TimedOutException(String message) {
        super(message);
    }
}
