package dev.abstratium.abstrauth.boundary;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.service.AccountService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/register")
@Tag(name = "Registration", description = "User registration endpoints")
public class RegistrationResource {

    @Inject
    AccountService accountService;

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Register new user", description = "Creates a new user account with credentials")
    public Response register(
            @FormParam("email") String email,
            @FormParam("name") String name,
            @FormParam("username") String username,
            @FormParam("password") String password) {

        if (email == null || email.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("invalid_request", "Email is required"))
                    .build();
        }

        if (username == null || username.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("invalid_request", "Username is required"))
                    .build();
        }

        if (password == null || password.length() < 8) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("invalid_request", "Password must be at least 8 characters"))
                    .build();
        }

        try {
            Account account = accountService.createAccount(email, name, username, password);
            return Response.status(Response.Status.CREATED)
                    .entity(new RegistrationResponse(account.getId(), account.getEmail(), account.getName()))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorResponse("conflict", e.getMessage()))
                    .build();
        }
    }

    public static class RegistrationResponse {
        public String id;
        public String email;
        public String name;

        public RegistrationResponse(String id, String email, String name) {
            this.id = id;
            this.email = email;
            this.name = name;
        }
    }

    public static class ErrorResponse {
        public String error;
        public String error_description;

        public ErrorResponse(String error, String errorDescription) {
            this.error = error;
            this.error_description = errorDescription;
        }
    }
}
