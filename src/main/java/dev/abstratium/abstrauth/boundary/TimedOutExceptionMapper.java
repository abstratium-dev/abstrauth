package dev.abstratium.abstrauth.boundary;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps TimedOutException to HTTP 410 Gone responses.
 * This allows service layer validation to throw TimedOutException
 * and have it automatically converted to appropriate HTTP error responses.
 */
@Provider
public class TimedOutExceptionMapper implements ExceptionMapper<TimedOutException> {

    @Override
    public Response toResponse(TimedOutException exception) {
        return Response.status(Response.Status.GONE)
                .entity(new ErrorResponse(exception.getMessage()))
                .build();
    }

    /**
     * Simple error response structure
     */
    public static class ErrorResponse {
        public String error;

        public ErrorResponse(String error) {
            this.error = error;
        }
    }
}
