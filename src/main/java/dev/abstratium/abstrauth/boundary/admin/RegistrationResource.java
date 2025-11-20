package dev.abstratium.abstrauth.boundary.admin;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.service.AccountService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/register")
@Tag(name = "Registration", description = "User registration endpoints")
public class RegistrationResource {

    @Inject
    AccountService accountService;

    @ConfigProperty(name = "allow.registration", defaultValue = "false")
    boolean allowRegistration;

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Operation(summary = "Registration form", description = "Returns an HTML form for user registration")
    public String getRegistrationForm() {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Register - abstrauth</title>
                </head>
                <body>
                    <div class="container">
                        <h1>Create Account</h1>
                        <p class="subtitle">Register for abstrauth OAuth Server</p>
                        
                        <div id="message"></div>
                        
                        <form id="registrationForm" method="POST" action="/api/register">
                            <div class="form-group">
                                <label for="email">Email Address *</label>
                                <input type="email" id="email" name="email" required 
                                       placeholder="your.email@example.com">
                            </div>
                            
                            <div class="form-group">
                                <label for="name">Full Name</label>
                                <input type="text" id="name" name="name" 
                                       placeholder="John Doe">
                            </div>
                            
                            <div class="form-group">
                                <label for="username">Username *</label>
                                <input type="text" id="username" name="username" required 
                                       placeholder="johndoe">
                            </div>
                            
                            <div class="form-group">
                                <label for="password">Password *</label>
                                <input type="password" id="password" name="password" required 
                                       placeholder="••••••••">
                                <p class="help-text">Must be at least 8 characters</p>
                            </div>
                            
                            <button type="submit">Create Account</button>
                        </form>
                    </div>
                    
                    <script>
                        document.getElementById('registrationForm').addEventListener('submit', async (e) => {
                            e.preventDefault();
                            
                            const formData = new FormData(e.target);
                            const messageDiv = document.getElementById('message');
                            
                            try {
                                const response = await fetch('/api/register', {
                                    method: 'POST',
                                    body: new URLSearchParams(formData)
                                });
                                
                                const data = await response.json();
                                
                                if (response.ok) {
                                    messageDiv.innerHTML = '<div class="success">Account created successfully! ID: ' + data.id + '</div>';
                                    e.target.reset();
                                } else {
                                    messageDiv.innerHTML = '<div class="error">' + (data.error_description || data.error || 'Registration failed') + '</div>';
                                }
                            } catch (error) {
                                messageDiv.innerHTML = '<div class="error">Network error. Please try again.</div>';
                            }
                        });
                    </script>
                </body>
                </html>
                """;
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Register new user", description = "Creates a new user account with credentials")
    public Response register(
            @FormParam("email") String email,
            @FormParam("name") String name,
            @FormParam("username") String username,
            @FormParam("password") String password) {

        if (!allowRegistration) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse("forbidden", "Registration is disabled"))
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
