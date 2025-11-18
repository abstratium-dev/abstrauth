package dev.abstratium.abstrauth.boundary;

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
                    <title>Register - AbsrAuth</title>
                    <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; }
                        body {
                            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
                            min-height: 100vh;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            padding: 20px;
                        }
                        .container {
                            background: white;
                            border-radius: 12px;
                            box-shadow: 0 10px 40px rgba(0, 0, 0, 0.1);
                            padding: 40px;
                            max-width: 400px;
                            width: 100%;
                        }
                        h1 {
                            color: #333;
                            margin-bottom: 10px;
                            font-size: 28px;
                        }
                        .subtitle {
                            color: #666;
                            margin-bottom: 30px;
                            font-size: 14px;
                        }
                        .form-group {
                            margin-bottom: 20px;
                        }
                        label {
                            display: block;
                            margin-bottom: 8px;
                            color: #333;
                            font-weight: 500;
                            font-size: 14px;
                        }
                        input {
                            width: 100%;
                            padding: 12px;
                            border: 2px solid #e0e0e0;
                            border-radius: 8px;
                            font-size: 14px;
                            transition: border-color 0.3s;
                        }
                        input:focus {
                            outline: none;
                            border-color: #667eea;
                        }
                        button {
                            width: 100%;
                            padding: 14px;
                            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                            color: white;
                            border: none;
                            border-radius: 8px;
                            font-size: 16px;
                            font-weight: 600;
                            cursor: pointer;
                            transition: transform 0.2s, box-shadow 0.2s;
                        }
                        button:hover {
                            transform: translateY(-2px);
                            box-shadow: 0 5px 20px rgba(102, 126, 234, 0.4);
                        }
                        button:active {
                            transform: translateY(0);
                        }
                        .error {
                            background: #fee;
                            border: 1px solid #fcc;
                            color: #c33;
                            padding: 12px;
                            border-radius: 8px;
                            margin-bottom: 20px;
                            font-size: 14px;
                        }
                        .success {
                            background: #efe;
                            border: 1px solid #cfc;
                            color: #3c3;
                            padding: 12px;
                            border-radius: 8px;
                            margin-bottom: 20px;
                            font-size: 14px;
                        }
                        .help-text {
                            font-size: 12px;
                            color: #999;
                            margin-top: 4px;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <h1>Create Account</h1>
                        <p class="subtitle">Register for AbsrAuth OAuth Server</p>
                        
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
