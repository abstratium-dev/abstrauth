package dev.abstratium.abstrauth.boundary.api;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.service.AccountService;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/signup")
@Tag(name = "Signup", description = "User signup endpoints")
@PermitAll
public class SignupResource {

    @Inject
    AccountService accountService;

    @ConfigProperty(name = "allow.signup", defaultValue = "false")
    boolean allowSignup;

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Sign up new user", description = "Creates a new user account with credentials")
    public Response signup(
            @FormParam("email") String email,
            @FormParam("name") String name,
            @FormParam("username") String username,
            @FormParam("password") String password) {

        if (!allowSignup) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse("forbidden", "Signup is disabled"))
                    .build();
        }

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
                    .entity(new SignupResponse(account.getId(), account.getEmail(), account.getName()))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorResponse("conflict", e.getMessage()))
                    .build();
        }
    }

    @RegisterForReflection
    public static class SignupResponse {
        public String id;
        public String email;
        public String name;

        public SignupResponse(String id, String email, String name) {
            this.id = id;
            this.email = email;
            this.name = name;
        }
    }

    @RegisterForReflection
    public static class ErrorResponse {
        public String error;
        public String error_description;

        public ErrorResponse(String error, String errorDescription) {
            this.error = error;
            this.error_description = errorDescription;
        }
    }
}
