package dev.abstratium.abstrauth.boundary;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps ConflictException to HTTP 409 Conflict responses.
 */
@Provider
public class ConflictExceptionMapper implements ExceptionMapper<ConflictException> {

    @Override
    public Response toResponse(ConflictException exception) {
        return Response.status(Response.Status.CONFLICT)
                .entity(new ErrorResponse(exception.getMessage()))
                .build();
    }

    public static class ErrorResponse {
        public String error;

        public ErrorResponse(String error) {
            this.error = error;
        }
    }
}
