package dev.abstratium.abstrauth.boundary.api;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import dev.abstratium.abstrauth.boundary.ErrorResponse;
import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.AccountRole;
import dev.abstratium.abstrauth.service.AccountRoleService;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.Roles;
import io.quarkus.oidc.IdToken;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/accounts")
@Tag(name = "Accounts", description = "Account management endpoints")
public class AccountsResource {
    
    @Inject
    AccountService accountService;
    
    @Inject
    AccountRoleService accountRoleService;
    
    @Inject
    SecurityIdentity securityIdentity;
    
    @Inject
    @IdToken
    JsonWebToken token;    
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List accounts", description = "Returns accounts based on user permissions. Users with only USER role see their own account, MANAGE_ACCOUNTS role sees accounts they manage, ADMIN sees all.")
    @RolesAllowed({Roles.USER})
    public List<AccountResponse> listAccounts() {
        // Get the current user's ID from the JWT token (sub claim)
        String accountId = token.getSubject();

        // If user is admin, return all accounts
        if (securityIdentity.hasRole(Roles.ADMIN)) {
            return accountService.findAll().stream()
                    .map(this::toAccountResponse)
                    .collect(Collectors.toList());
        }
        
        // If user has manage-accounts role, return accounts filtered by user's client roles
        if (securityIdentity.hasRole(Roles.MANAGE_ACCOUNTS)) {
            return accountService.findAccountsByUserClientRoles(accountId).stream()
                    .map(this::toAccountResponse)
                    .collect(Collectors.toList());
        }
        
        // Otherwise, return only the current user's account
        return accountService.findById(accountId)
                .map(this::toAccountResponse)
                .map(List::of)
                .orElse(List.of());
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create account", description = "Creates a new account manually. Designed to be used when registration is disabled, so that an admin can add new accounts.")
    @RolesAllowed(Roles.MANAGE_ACCOUNTS)
    public Response createAccount(@Valid CreateAccountRequest request) {
        // Validate authProvider
        if (!request.authProvider.equals(AccountService.GOOGLE) && !request.authProvider.equals(AccountService.NATIVE)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("authProvider must be either 'google', or 'native'"))
                    .build();
        }

        // Check if email already exists
        if (accountService.findByEmail(request.email).isPresent()) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorResponse("Email already exists"))
                    .build();
        }

        // For native provider, check if username already exists (in case it ever differs from email)
        if (accountService.findCredentialByUsername(request.email).isPresent()) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorResponse("Username already exists"))
                    .build();
        }

        // Generate a random password for native accounts.
        // For native accounts, use provided name; for federated, it will be updated on first sign-in
        String name = request.name;
        String generatedPassword = null;
        if(AccountService.NATIVE.equals(request.authProvider)) {
            generatedPassword = generateRandomPassword();
        } else {
            name = "NOT YET SIGNED IN";
        }

        Account account = accountService.createAccount(request.email, name, request.email, generatedPassword, request.authProvider);
        
        // Create invite token
        String inviteToken = createInviteToken(request.authProvider, request.email, generatedPassword);
        
        return Response.status(Response.Status.CREATED)
                .entity(new CreateAccountResponse(toAccountResponse(account), inviteToken))
                .build();
    }
    
    private String generateRandomPassword() {
        // Generate a secure random password (16 characters)
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            password.append(chars.charAt(random.nextInt(chars.length())));
        }
        return password.toString();
    }
    
    private String createInviteToken(String authProvider, String email, String password) {
        // Create JSON-like string with invite data
        StringBuilder tokenData = new StringBuilder();
        tokenData.append("{\"authProvider\":\"").append(authProvider).append("\"");
        tokenData.append(",\"email\":\"").append(email).append("\"");
        if (password != null) {
            tokenData.append(",\"password\":\"").append(password).append("\"");
        }
        tokenData.append("}");
        
        // Base64 encode the token
        return Base64.getEncoder().encodeToString(tokenData.toString().getBytes());
    }

    @POST
    @Path("/role")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Add role to account", description = "Adds a role to an account for a specific client")
    @RolesAllowed(Roles.MANAGE_ACCOUNTS)
    public Response addAccountRole(@Valid AddAccountRoleRequest request) {
        String accountId = request.accountId;
        // Verify account exists
        if (accountService.findById(accountId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Account not found"))
                    .build();
        }

        // Add the role (validation happens inside)
        // IllegalArgumentException will be caught by IllegalArgumentExceptionMapper and returned as 400
        AccountRole accountRole = accountRoleService.addRole(accountId, request.clientId, request.role);
        
        // Return the created role
        RoleInfo roleInfo = new RoleInfo(accountRole.getClientId(), accountRole.getRole());
        return Response.status(Response.Status.CREATED).entity(roleInfo).build();
    }

    @DELETE
    @Path("/role")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Remove role from account", description = "Removes a role from an account for a specific client")
    @RolesAllowed(Roles.MANAGE_ACCOUNTS)
    public Response removeAccountRole(@Valid RemoveRoleRequest request) {
        String accountId = request.accountId;
        // Verify account exists
        if (accountService.findById(accountId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Account not found"))
                    .build();
        }

        // Remove the role
        accountRoleService.removeRole(accountId, request.clientId, request.role);
        
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{accountId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Delete account", description = "Deletes an account and all associated data")
    @RolesAllowed(Roles.MANAGE_ACCOUNTS)
    public Response deleteAccount(@PathParam("accountId") String accountId) {
        // Verify account exists
        if (accountService.findById(accountId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Account not found"))
                    .build();
        }

        // Delete the account
        accountService.deleteAccount(accountId);
        
        return Response.noContent().build();
    }

    @POST
    @Path("/reset-password")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Reset password", description = "Resets password for a native account after verifying old password")
    public Response resetPassword(@Valid ResetPasswordRequest request) {
        // Get account ID from JWT token
        String accountId = token.getSubject();
        
        // Verify account exists
        if (accountService.findById(accountId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Account not found"))
                    .build();
        }

        // Update password
        boolean success = accountService.updatePassword(accountId, request.oldPassword, request.newPassword);
        
        if (!success) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Old password is incorrect"))
                    .build();
        }
        
        return Response.ok().entity(new SuccessResponse("Password updated successfully")).build();
    }

    private AccountResponse toAccountResponse(Account account) {
        List<RoleInfo> roles = account.getRoles().stream()
                .map(role -> new RoleInfo(role.getClientId(), role.getRole()))
                .collect(Collectors.toList());
        
        return new AccountResponse(
                account.getId(),
                account.getEmail(),
                account.getName(),
                account.getEmailVerified(),
                account.getAuthProvider(),
                account.getPicture(),
                account.getCreatedAt() != null ? account.getCreatedAt().toString() : null,
                roles
        );
    }

    @RegisterForReflection
    public static class AccountResponse {
        public String id;
        public String email;
        public String name;
        public Boolean emailVerified;
        public String authProvider;
        public String picture;
        public String createdAt;
        public List<RoleInfo> roles;

        public AccountResponse(String id, String email, String name, Boolean emailVerified,
                             String authProvider, String picture, String createdAt, List<RoleInfo> roles) {
            this.id = id;
            this.email = email;
            this.name = name;
            this.emailVerified = emailVerified;
            this.authProvider = authProvider;
            this.picture = picture;
            this.createdAt = createdAt;
            this.roles = roles;
        }
    }

    @RegisterForReflection
    public static class RoleInfo {
        public String clientId;
        public String role;

        public RoleInfo(String clientId, String role) {
            this.clientId = clientId;
            this.role = role;
        }
    }

    @RegisterForReflection
    public static class AddAccountRoleRequest {
        @NotBlank(message = "Account ID is required")
        public String accountId;
        
        @NotBlank(message = "Client ID is required")
        public String clientId;
        
        @NotBlank(message = "Role is required")
        @Pattern(regexp = "[a-zA-Z0-9\\-]+", message = "Role must contain only alphanumeric characters and hyphens")
        public String role;
    }

    @RegisterForReflection
    public static class RemoveRoleRequest {
        @NotBlank(message = "Account ID is required")
        public String accountId;
        
        @NotBlank(message = "Client ID is required")
        public String clientId;
        
        @NotBlank(message = "Role is required")
        @Pattern(regexp = "[a-zA-Z0-9\\-]+", message = "Role must contain only alphanumeric characters and hyphens")
        public String role;
    }

    @RegisterForReflection
    public static class CreateAccountRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        public String email;
        
        // Name is optional for federated providers, but should be provided for native
        public String name;
        
        @NotBlank(message = "Auth provider is required")
        @Pattern(regexp = "google|native", message = "Auth provider must be either 'google', or 'native'")
        public String authProvider;
    }
    
    @RegisterForReflection
    public static class CreateAccountResponse {
        public AccountResponse account;
        public String inviteToken;
        
        public CreateAccountResponse(AccountResponse account, String inviteToken) {
            this.account = account;
            this.inviteToken = inviteToken;
        }
    }
    
    @RegisterForReflection
    public static class ResetPasswordRequest {
        @NotBlank(message = "Old password is required")
        public String oldPassword;
        
        @NotBlank(message = "New password is required")
        public String newPassword;
    }
    
    @RegisterForReflection
    public static class SuccessResponse {
        public String message;
        
        public SuccessResponse(String message) {
            this.message = message;
        }
    }
}
