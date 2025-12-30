package dev.abstratium.abstrauth.boundary;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps TimedOutException to HTTP 410 Gone responses.
 * Used when authorization codes or other time-sensitive tokens have expired.
 */
@Provider
public class TimedOutExceptionMapper implements ExceptionMapper<TimedOutException> {

    @Override
    public Response toResponse(TimedOutException exception) {
        return Response.status(Response.Status.GONE)
                .entity(new ErrorResponse(exception.getMessage()))
                .build();
    }
}
