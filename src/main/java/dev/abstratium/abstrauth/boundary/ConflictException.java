package dev.abstratium.abstrauth.boundary;

/**
 * Exception thrown when there's a conflict, such as trying to create a duplicate resource.
 * This will be mapped to HTTP 409 Conflict by ConflictExceptionMapper.
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
