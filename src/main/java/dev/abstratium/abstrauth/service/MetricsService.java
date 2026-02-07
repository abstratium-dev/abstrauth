package dev.abstratium.abstrauth.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.atomic.AtomicInteger;

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

    // Counters for OAuth operations
    private Counter authorizationRequests;
    private Counter authorizationApprovals;
    private Counter authorizationDenials;
    private Counter tokenRequests;
    private Counter tokenRequestsSuccess;
    private Counter tokenRequestsFailure;
    private Counter tokenRevocations;
    private Counter tokenIntrospections;

    // Counters for client management
    private Counter clientCreations;
    private Counter clientDeletions;
    private Counter secretCreations;
    private Counter secretRevocations;
    private Counter secretDeletions;

    // Counters for role management
    private Counter roleAssignments;
    private Counter roleRemovals;
    private Counter serviceAccountRoleAssignments;
    private Counter serviceAccountRoleRemovals;

    // Counters for errors
    private Counter authenticationErrors;
    private Counter authorizationErrors;
    private Counter validationErrors;

    // Active sessions tracking
    private final AtomicInteger activeSessions = new AtomicInteger(0);

    public void initialize() {
        // Authentication metrics
        successfulLogins = Counter.builder("abstrauth.auth.login.success")
                .description("Number of successful login attempts")
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

        serviceAccountRoleAssignments = Counter.builder("abstrauth.service.role.assignment")
                .description("Number of role assignments to service accounts")
                .register(registry);

        serviceAccountRoleRemovals = Counter.builder("abstrauth.service.role.removal")
                .description("Number of role removals from service accounts")
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
        Gauge.builder("abstrauth.accounts.total", () -> (double) accountService.findAll().size())
                .description("Total number of user accounts")
                .register(registry);

        Gauge.builder("abstrauth.clients.total", () -> (double) clientService.findAll().size())
                .description("Total number of OAuth clients")
                .register(registry);

        Gauge.builder("abstrauth.sessions.active", activeSessions, counter -> (double) counter.get())
                .description("Number of currently active sessions")
                .register(registry);
    }

    // Authentication metrics
    public void recordSuccessfulLogin() {
        successfulLogins.increment();
        activeSessions.incrementAndGet();
    }

    public void recordFailedLogin() {
        failedLogins.increment();
    }

    public void recordSignup() {
        signups.increment();
    }

    public void recordPasswordChange() {
        passwordChanges.increment();
    }

    public void recordLogout() {
        activeSessions.decrementAndGet();
    }

    // OAuth operation metrics
    public void recordAuthorizationRequest() {
        authorizationRequests.increment();
    }

    public void recordAuthorizationApproval() {
        authorizationApprovals.increment();
    }

    public void recordAuthorizationDenial() {
        authorizationDenials.increment();
    }

    public void recordTokenRequest() {
        tokenRequests.increment();
    }

    public void recordTokenRequestSuccess() {
        tokenRequestsSuccess.increment();
    }

    public void recordTokenRequestFailure() {
        tokenRequestsFailure.increment();
    }

    public void recordTokenRevocation() {
        tokenRevocations.increment();
    }

    public void recordTokenIntrospection() {
        tokenIntrospections.increment();
    }

    // Client management metrics
    public void recordClientCreation() {
        clientCreations.increment();
    }

    public void recordClientDeletion() {
        clientDeletions.increment();
    }

    public void recordSecretCreation() {
        secretCreations.increment();
    }

    public void recordSecretRevocation() {
        secretRevocations.increment();
    }

    public void recordSecretDeletion() {
        secretDeletions.increment();
    }

    // Role management metrics
    public void recordRoleAssignment() {
        roleAssignments.increment();
    }

    public void recordRoleRemoval() {
        roleRemovals.increment();
    }

    public void recordServiceAccountRoleAssignment() {
        serviceAccountRoleAssignments.increment();
    }

    public void recordServiceAccountRoleRemoval() {
        serviceAccountRoleRemovals.increment();
    }

    // Error metrics
    public void recordAuthenticationError() {
        authenticationErrors.increment();
    }

    public void recordAuthorizationError() {
        authorizationErrors.increment();
    }

    public void recordValidationError() {
        validationErrors.increment();
    }
}
