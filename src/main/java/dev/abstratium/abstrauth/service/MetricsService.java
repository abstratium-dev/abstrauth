package dev.abstratium.abstrauth.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for tracking application metrics using Micrometer.
 * Provides counters, gauges, and timers for key OAuth operations.
 */
@ApplicationScoped
public class MetricsService {

    @Inject
    MeterRegistry registry;

    @Inject
    AccountService accountService;

    @Inject
    OAuthClientService clientService;

    // Counters for authentication events
    private Counter successfulLogins;
    private Counter failedLogins;
    private Counter signups;
    private Counter passwordChanges;
    private Counter explicitLogouts;

    // Counters for OAuth operations
    private Counter authorizationRequests;
    private Counter authorizationApprovals;
    private Counter authorizationDenials;
    private Counter tokenRequests;
    private Counter tokenRequestsSuccess;
    private Counter tokenRequestsFailure;
    private Counter tokenRevocations;
    private Counter tokenIntrospections;
    private Counter tokenExchangeRequests;
    private Counter tokenExchangeSuccess;
    private Counter tokenExchangeFailure;

    // Counters for client management
    private Counter clientCreations;
    private Counter clientDeletions;
    private Counter secretCreations;
    private Counter secretRevocations;
    private Counter secretDeletions;

    // Counters for role management
    private Counter roleAssignments;
    private Counter roleRemovals;

    // Counters for errors
    private Counter authenticationErrors;
    private Counter authorizationErrors;
    private Counter validationErrors;

    // Note: We do not track active sessions because we cannot reliably detect automatic session expirations

    // Cached counts for gauges
    private final AtomicLong totalAccounts = new AtomicLong(0);
    private final AtomicLong totalClients = new AtomicLong(0);

    public void initialize() {
        // Authentication metrics
        successfulLogins = Counter.builder("abstrauth.auth.login.success")
                .description("Total number of successful logins (does NOT decrement on logout - independent counter)")
                .register(registry);

        failedLogins = Counter.builder("abstrauth.auth.login.failure")
                .description("Number of failed login attempts")
                .register(registry);

        signups = Counter.builder("abstrauth.auth.signup")
                .description("Number of user signups")
                .register(registry);

        passwordChanges = Counter.builder("abstrauth.auth.password.change")
                .description("Number of password changes")
                .register(registry);

        explicitLogouts = Counter.builder("abstrauth.auth.logout.explicit")
                .description("Total number of explicit user-initiated logouts (does NOT include automatic session expirations - independent counter)")
                .register(registry);

        // OAuth operation metrics
        authorizationRequests = Counter.builder("abstrauth.oauth.authorization.request")
                .description("Number of authorization requests")
                .register(registry);

        authorizationApprovals = Counter.builder("abstrauth.oauth.authorization.approval")
                .description("Number of authorization approvals")
                .register(registry);

        authorizationDenials = Counter.builder("abstrauth.oauth.authorization.denial")
                .description("Number of authorization denials")
                .register(registry);

        tokenRequests = Counter.builder("abstrauth.oauth.token.request")
                .description("Total number of token requests")
                .register(registry);

        tokenRequestsSuccess = Counter.builder("abstrauth.oauth.token.success")
                .description("Number of successful token requests")
                .register(registry);

        tokenRequestsFailure = Counter.builder("abstrauth.oauth.token.failure")
                .description("Number of failed token requests")
                .register(registry);

        tokenRevocations = Counter.builder("abstrauth.oauth.token.revocation")
                .description("Number of token revocations")
                .register(registry);

        tokenIntrospections = Counter.builder("abstrauth.oauth.token.introspection")
                .description("Number of token introspection requests")
                .register(registry);

        tokenExchangeRequests = Counter.builder("abstrauth.oauth.token.exchange.request")
                .description("Total number of token exchange requests")
                .register(registry);

        tokenExchangeSuccess = Counter.builder("abstrauth.oauth.token.exchange.success")
                .description("Number of successful token exchanges")
                .register(registry);

        tokenExchangeFailure = Counter.builder("abstrauth.oauth.token.exchange.failure")
                .description("Number of failed token exchanges")
                .register(registry);

        // Client management metrics
        clientCreations = Counter.builder("abstrauth.client.creation")
                .description("Number of OAuth clients created")
                .register(registry);

        clientDeletions = Counter.builder("abstrauth.client.deletion")
                .description("Number of OAuth clients deleted")
                .register(registry);

        secretCreations = Counter.builder("abstrauth.client.secret.creation")
                .description("Number of client secrets created")
                .register(registry);

        secretRevocations = Counter.builder("abstrauth.client.secret.revocation")
                .description("Number of client secrets revoked")
                .register(registry);

        secretDeletions = Counter.builder("abstrauth.client.secret.deletion")
                .description("Number of client secrets deleted")
                .register(registry);

        // Role management metrics
        roleAssignments = Counter.builder("abstrauth.role.assignment")
                .description("Number of role assignments to users")
                .register(registry);

        roleRemovals = Counter.builder("abstrauth.role.removal")
                .description("Number of role removals from users")
                .register(registry);

        // Error metrics
        authenticationErrors = Counter.builder("abstrauth.error.authentication")
                .description("Number of authentication errors")
                .register(registry);

        authorizationErrors = Counter.builder("abstrauth.error.authorization")
                .description("Number of authorization errors")
                .register(registry);

        validationErrors = Counter.builder("abstrauth.error.validation")
                .description("Number of validation errors")
                .register(registry);

        // Gauges for current state
        Gauge.builder("abstrauth.accounts.total", totalAccounts, counter -> (double) counter.get())
                .description("Total number of user accounts")
                .register(registry);

        Gauge.builder("abstrauth.clients.total", totalClients, counter -> (double) counter.get())
                .description("Total number of OAuth clients")
                .register(registry);

        // Note: We do NOT provide an active sessions gauge because we cannot reliably track
        // automatic session expirations. Use successfulLogins - explicitLogouts as a rough
        // approximation, but be aware this will drift over time due to automatic expirations.

        // Initialize counts immediately
        updateEntityCounts();
    }

    // Authentication metrics
    public void recordSuccessfulLogin() {
        successfulLogins.increment();
    }

    public void recordFailedLogin() {
        failedLogins.increment();
    }

    public void recordSignup() {
        signups.increment();
    }

    // TODO: Integrate when password change functionality is implemented
    // public void recordPasswordChange() {
    //     passwordChanges.increment();
    // }

    public void recordExplicitLogout() {
        explicitLogouts.increment();
    }

    // OAuth operation metrics
    public void recordAuthorizationRequest() {
        authorizationRequests.increment();
    }

    public void recordAuthorizationApproval() {
        authorizationApprovals.increment();
    }

    // TODO: Integrate when explicit authorization denial is implemented
    // Currently users can only approve or let requests expire
    // public void recordAuthorizationDenial() {
    //     authorizationDenials.increment();
    // }

    public void recordTokenRequest() {
        tokenRequests.increment();
    }

    public void recordTokenRequestSuccess() {
        tokenRequestsSuccess.increment();
    }

    public void recordTokenRequestFailure() {
        tokenRequestsFailure.increment();
    }

    public void recordTokenExchangeRequest() {
        tokenExchangeRequests.increment();
    }

    public void recordTokenExchangeSuccess() {
        tokenExchangeSuccess.increment();
    }

    public void recordTokenExchangeFailure() {
        tokenExchangeFailure.increment();
    }

    // TODO: Integrate in TokenRevocationService when revocation endpoint is called
    // public void recordTokenRevocation() {
    //     tokenRevocations.increment();
    // }

    // TODO: Integrate when token introspection endpoint is implemented
    // public void recordTokenIntrospection() {
    //     tokenIntrospections.increment();
    // }

    // Client management metrics
    public void recordClientCreation() {
        clientCreations.increment();
    }

    public void recordClientDeletion() {
        clientDeletions.increment();
    }

    // TODO: Integrate in ClientSecretsResource when secret creation endpoint is called
    // public void recordSecretCreation() {
    //     secretCreations.increment();
    // }

    // TODO: Integrate in ClientSecretsResource when secret revocation endpoint is called
    // public void recordSecretRevocation() {
    //     secretRevocations.increment();
    // }

    // TODO: Integrate in ClientSecretsResource when secret deletion endpoint is called
    // public void recordSecretDeletion() {
    //     secretDeletions.increment();
    // }

    // Role management metrics
    // TODO: Integrate in AccountRolesResource when role assignment endpoint is called
    // public void recordRoleAssignment() {
    //     roleAssignments.increment();
    // }

    // TODO: Integrate in AccountRolesResource when role removal endpoint is called
    // public void recordRoleRemoval() {
    //     roleRemovals.increment();
    // }

    // Error metrics
    // TODO: Integrate when authentication error handling is centralized
    // public void recordAuthenticationError() {
    //     authenticationErrors.increment();
    // }

    // TODO: Integrate when authorization error handling is centralized
    // public void recordAuthorizationError() {
    //     authorizationErrors.increment();
    // }

    public void recordValidationError() {
        validationErrors.increment();
    }

    /**
     * Scheduled task to update entity counts every 15 minutes.
     * Uses SQL COUNT queries for efficiency.
     */
    @Scheduled(every = "15m")
    void updateEntityCounts() {
        totalAccounts.set(accountService.countAccounts());
        totalClients.set(clientService.countClients());
    }
}
