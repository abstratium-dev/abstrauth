package dev.abstratium.abstrauth.boundary.api;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.AccountRole;
import dev.abstratium.abstrauth.service.AccountRoleService;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.Roles;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.microprofile.jwt.JsonWebToken;

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
    JsonWebToken accessToken;    
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List accounts", description = "Returns accounts based on user permissions")
    @RolesAllowed({Roles.ADMIN, Roles.MANAGE_ACCOUNTS})
    public List<AccountResponse> listAccounts() {
        // Get the current user's ID from the JWT token (sub claim)
        // String currentUserId = securityIdentity.getPrincipal().getName();
        String accountId = accessToken.getSubject();

        // If user is admin, return all accounts
        if (securityIdentity.hasRole(Roles.ADMIN)) {
            return accountService.findAll().stream()
                    .map(this::toAccountResponse)
                    .collect(Collectors.toList());
        }
        
        // Otherwise, return accounts filtered by user's client roles
        return accountService.findAccountsByUserClientRoles(accountId).stream()
                .map(this::toAccountResponse)
                .collect(Collectors.toList());
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

        // Add the role
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
    public static class ErrorResponse {
        public String error;

        public ErrorResponse(String error) {
            this.error = error;
        }
    }
}
