package dev.abstratium.abstrauth.boundary;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps IllegalArgumentException to HTTP 400 Bad Request responses.
 * This allows service layer validation to throw IllegalArgumentException
 * and have it automatically converted to appropriate HTTP error responses.
 */
@Provider
public class IllegalArgumentExceptionMapper implements ExceptionMapper<IllegalArgumentException> {

    @Override
    public Response toResponse(IllegalArgumentException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(exception.getMessage()))
                .build();
    }
}
