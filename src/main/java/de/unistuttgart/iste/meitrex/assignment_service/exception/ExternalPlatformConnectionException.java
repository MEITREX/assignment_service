package de.unistuttgart.iste.meitrex.assignment_service.exception;

public class ExternalPlatformConnectionException extends Exception {
    public ExternalPlatformConnectionException(String message) {
        super(message);
    }

    public ExternalPlatformConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
