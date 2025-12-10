package dev.abstratium.abstrauth.boundary.api;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.Roles;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
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
}
