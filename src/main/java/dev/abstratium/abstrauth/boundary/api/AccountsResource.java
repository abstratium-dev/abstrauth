package dev.abstratium.abstrauth.boundary.api;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.service.AccountService;
import io.quarkus.runtime.annotations.RegisterForReflection;
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

@Path("/api/accounts")
@Tag(name = "Accounts", description = "Account management endpoints")
@RolesAllowed("abstratium-abstrauth_admin")
public class AccountsResource {

    @Inject
    AccountService accountService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List all accounts", description = "Returns a list of all registered accounts (admin only)")
    public List<AccountResponse> listAccounts() {
        return accountService.findAll().stream()
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
