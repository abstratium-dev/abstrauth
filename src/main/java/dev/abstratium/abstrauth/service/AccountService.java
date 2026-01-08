package dev.abstratium.abstrauth.service;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.Credential;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.jdt.annotation.NonNull;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class AccountService {

    public static final String NATIVE = "native";
    public static final String GOOGLE = "google";

    @Inject
    EntityManager em;

    @Inject
    AccountRoleService accountRoleService;

    @ConfigProperty(name = "password.pepper")
    String pepper;

    // BCrypt with strength 12 (2^12 rounds, OWASP recommendation)
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);

    public Optional<@NonNull Account> findByEmail(String email) {
        var query = em.createQuery("SELECT a FROM Account a WHERE a.email = :email", Account.class);
        query.setParameter("email", email);
        return query.getResultStream().findFirst();
    }

    public Optional<@NonNull Account> findById(String id) {
        return Optional.ofNullable(em.find(Account.class, id));
    }

    @Transactional
    public Account createAccount(String email, String name, String username, String password, String authProvider) {
        // Check if email already exists
        if (findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email already exists");
        }

        // Create account
        Account account = new Account();
        account.setEmail(email);
        account.setName(name);
        account.setEmailVerified(false);
        account.setAuthProvider(authProvider);
        account.setPicture(null);
        account.setCreatedAt(LocalDateTime.now());
        em.persist(account);

        // Create credentials
        if(AccountService.NATIVE.equals(authProvider) && password != null) {
            createCredentialForAccount(account.getId(), username, password);
        }

        addRoles(account);

        return account;
    }

    @Transactional
    public Account createFederatedAccount(String email, String name, String picture, 
                                         Boolean emailVerified, String authProvider) {
        // Check if email already exists
        if (findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email already exists");
        }

        // Create account
        Account account = new Account();
        account.setEmail(email);
        account.setName(name);
        account.setPicture(picture);
        account.setEmailVerified(emailVerified != null ? emailVerified : false);
        account.setAuthProvider(authProvider);
        em.persist(account);

        addRoles(account);

        return account;
    }

    private void addRoles(Account account) {
        // All accounts get the "user" role
        accountRoleService.addRole(account.getId(), Roles.CLIENT_ID, Roles._USER_PLAIN);
        
        // Check if this is the first account
        boolean isFirstAccount = countAccounts() == 1;

        // First account also gets admin and management roles
        if (isFirstAccount) {
            accountRoleService.addRole(account.getId(), Roles.CLIENT_ID, Roles._ADMIN_PLAIN);
            accountRoleService.addRole(account.getId(), Roles.CLIENT_ID, Roles._MANAGE_ACCOUNTS_PLAIN);
            accountRoleService.addRole(account.getId(), Roles.CLIENT_ID, Roles._MANAGE_CLIENTS_PLAIN);
        }
    }

    @Transactional
    public Account updateAccount(Account account) {
        return em.merge(account);
    }

    public Optional<@NonNull Credential> findCredentialByUsername(String username) {
        var query = em.createQuery("SELECT c FROM Credential c WHERE c.username = :username", Credential.class);
        query.setParameter("username", username);
        return query.getResultStream().findFirst();
    }

    public Optional<@NonNull Credential> findCredentialByAccountId(String accountId) {
        var query = em.createQuery("SELECT c FROM Credential c WHERE c.accountId = :accountId", Credential.class);
        query.setParameter("accountId", accountId);
        return query.getResultStream().findFirst();
    }

    @Transactional
    public Optional<@NonNull Account> authenticate(String username, String password) {
        Optional<Credential> credentialOpt = findCredentialByUsername(username);
        if (credentialOpt.isEmpty()) {
            return Optional.empty();
        }

        Credential credential = credentialOpt.get();

        // Check if account is locked
        if (credential.getLockedUntil() != null && 
            credential.getLockedUntil().isAfter(java.time.LocalDateTime.now())) {
            return Optional.empty();
        }

        // Verify password
        if (verifyPassword(password, credential.getPasswordHash())) {
            // Reset failed attempts on successful login
            credential.setFailedLoginAttempts(0);
            credential.setLockedUntil(null);
            em.merge(credential);

            Optional<Account> accountOpt = findById(credential.getAccountId());
            // Eagerly fetch roles to avoid LazyInitializationException
            accountOpt.ifPresent(account -> account.getRoles().size());
            return accountOpt;
        } else {
            // Increment failed attempts
            int attempts = credential.getFailedLoginAttempts() + 1;
            credential.setFailedLoginAttempts(attempts);

            // Lock account after 5 failed attempts for 15 minutes
            if (attempts >= 5) {
                credential.setLockedUntil(java.time.LocalDateTime.now().plusMinutes(15));
            }

            em.merge(credential);
            return Optional.empty();
        }
    }

    private String hashPassword(String password) {
        // Apply pepper (application-wide secret) before hashing for defense-in-depth
        return passwordEncoder.encode(pepper + password);
    }

    private boolean verifyPassword(String plainPassword, String hashedPassword) {
        try {
            // Apply pepper before verification
            return passwordEncoder.matches(pepper + plainPassword, hashedPassword);
        } catch (IllegalArgumentException e) {
            // Invalid hash format
            return false;
        }
    }

    /**
     * Find all accounts with roles eagerly loaded
     * @return List of all accounts
     */
    public List<Account> findAll() {
        var query = em.createQuery("SELECT a FROM Account a LEFT JOIN FETCH a.roles ORDER BY a.createdAt DESC", Account.class);
        return query.getResultList();
    }

    /**
     * Find accounts filtered by the user's client roles.
     * Returns accounts that have roles for any of the same clients as the given user.
     * Excludes the universal "user" role from filtering since all users have it.
     * @param accountId The ID of the users account whose client roles to use for filtering
     * @return List of accounts that share client roles with the user
     */
    public List<Account> findAccountsByUserClientRoles(String accountId) {
        // Get all client IDs from the user's roles
        var clientIdsQuery = em.createQuery(
            "SELECT DISTINCT ar.clientId FROM AccountRole ar WHERE ar.accountId = :accountId", 
            String.class
        );
        clientIdsQuery.setParameter("accountId", accountId);
        List<String> allClientIds = clientIdsQuery.getResultList();
        
        // Filter out the default client's "user" role in Java (simpler SQL, better performance)
        List<String> userClientIds = allClientIds.stream()
            .filter(clientId -> !clientId.equals(Roles.CLIENT_ID))
            .collect(java.util.stream.Collectors.toList());
        
        // If user has no roles (other than the default "user" role), return only their own account
        if (userClientIds.isEmpty()) {
            return findById(accountId)
                .map(List::of)
                .orElse(List.of());
        }
        
        // Find all account IDs that have roles for any of those client IDs
        var accountIdsQuery = em.createQuery(
            "SELECT DISTINCT ar.accountId FROM AccountRole ar WHERE ar.clientId IN :clientIds",
            String.class
        );
        accountIdsQuery.setParameter("clientIds", userClientIds);
        List<String> accountIds = accountIdsQuery.getResultList();
        
        // If no accounts found, return only the account belonging to them
        if (accountIds.isEmpty()) {
            return findById(accountId)
                .map(List::of)
                .orElse(List.of());
        }
        
        // Finally, fetch the full accounts with roles eagerly loaded
        var accountsQuery = em.createQuery(
            "SELECT DISTINCT a FROM Account a LEFT JOIN FETCH a.roles WHERE a.id IN :accountIds ORDER BY a.createdAt DESC",
            Account.class
        );
        accountsQuery.setParameter("accountIds", accountIds);
        return accountsQuery.getResultList();
    }

    /**
     * Count the total number of accounts in the database
     * @return The number of accounts
     */
    public long countAccounts() {
        var query = em.createQuery("SELECT COUNT(a) FROM Account a", Long.class);
        return query.getSingleResult();
    }

    /**
     * Delete an account and all associated data
     * 
     * Note: Most child records are deleted automatically via CASCADE DELETE constraints:
     * - T_account_roles (FK_account_roles_account)
     * - T_credentials (FK_credentials_account_id)
     * - T_federated_identities (FK_federated_account)
     * - T_authorization_codes (FK_authorization_codes_account_id)
     * 
     * However, T_authorization_requests has no foreign key constraint, so we delete it manually.
     * 
     * @param accountId The ID of the account to delete
     * @throws IllegalArgumentException if attempting to delete the account with the only admin role for abstratium-abstrauth
     */
    @Transactional
    public void deleteAccount(String accountId) {
        Account account = em.find(Account.class, accountId);
        if (account == null) {
            throw new IllegalArgumentException("Account not found");
        }

        // Prevent deletion if this account has the only admin role for abstratium-abstrauth
        if (hasOnlyAdminRoleForAbstrauthClient(accountId)) {
            throw new IllegalArgumentException("Cannot delete the account with the only admin role for " + Roles.CLIENT_ID);
        }

        // Delete authorization requests (no CASCADE DELETE constraint in database)
        var authReqQuery = em.createQuery("DELETE FROM AuthorizationRequest ar WHERE ar.accountId = :accountId");
        authReqQuery.setParameter("accountId", accountId);
        authReqQuery.executeUpdate();

        // Delete the account (CASCADE DELETE will handle roles, credentials, federated identities, and auth codes)
        em.remove(account);
    }
    
    /**
     * Check if this account has the only admin role for abstratium-abstrauth
     * 
     * @param accountId The account ID to check
     * @return true if this account has the only admin role for abstratium-abstrauth
     */
    private boolean hasOnlyAdminRoleForAbstrauthClient(String accountId) {
        // Check if this account has the admin role for abstratium-abstrauth
        var accountRoleQuery = em.createQuery(
            "SELECT COUNT(ar) FROM AccountRole ar WHERE ar.accountId = :accountId AND ar.clientId = :clientId AND ar.role = :role",
            Long.class
        );
        accountRoleQuery.setParameter("accountId", accountId);
        accountRoleQuery.setParameter("clientId", Roles.CLIENT_ID);
        accountRoleQuery.setParameter("role", Roles._ADMIN_PLAIN);
        long accountHasAdminRole = accountRoleQuery.getSingleResult();
        
        if (accountHasAdminRole == 0) {
            return false; // This account doesn't have the admin role
        }
        
        // Count total admin roles for abstratium-abstrauth
        var totalAdminQuery = em.createQuery(
            "SELECT COUNT(ar) FROM AccountRole ar WHERE ar.clientId = :clientId AND ar.role = :role",
            Long.class
        );
        totalAdminQuery.setParameter("clientId", Roles.CLIENT_ID);
        totalAdminQuery.setParameter("role", Roles._ADMIN_PLAIN);
        long totalAdminRoles = totalAdminQuery.getSingleResult();
        
        return totalAdminRoles <= 1; // This is the only admin
    }

    /**
     * Create a credential for an existing account
     * @param accountId The account ID
     * @param username The username (typically email)
     * @param password The plain text password
     */
    private void createCredentialForAccount(String accountId, String username, String password) {
        // Check if username already exists
        if (findCredentialByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }

        Credential credential = new Credential();
        credential.setAccountId(accountId);
        credential.setUsername(username);
        credential.setPasswordHash(hashPassword(password));
        em.persist(credential);
    }

    /**
     * Update password for an existing credential
     * @param accountId The account ID
     * @param oldPassword The old password to verify
     * @param newPassword The new password to set
     * @return true if password was updated, false if old password didn't match
     */
    @Transactional
    public boolean updatePassword(String accountId, String oldPassword, String newPassword) {
        Optional<Credential> credentialOpt = findCredentialByAccountId(accountId);
        if (credentialOpt.isEmpty()) {
            throw new IllegalArgumentException("No credentials found for this account");
        }

        Credential credential = credentialOpt.get();
        
        // Verify old password
        if (!verifyPassword(oldPassword, credential.getPasswordHash())) {
            return false;
        }

        // Update to new password
        credential.setPasswordHash(hashPassword(newPassword));
        em.merge(credential);
        return true;
    }

    @Transactional
    public void deleteAll() {
        em.createQuery("DELETE FROM Account").executeUpdate();
    }
}
