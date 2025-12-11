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

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class AccountService {

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
    public Account createAccount(String email, String name, String username, String password) {
        // Check if email already exists
        if (findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email already exists");
        }

        // Check if username already exists
        if (findCredentialByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }

        // Check if this is the first account
        boolean isFirstAccount = countAccounts() == 0;

        // Create account
        Account account = new Account();
        account.setEmail(email);
        account.setName(name);
        account.setEmailVerified(false);
        account.setAuthProvider("native");
        em.persist(account);

        // Create credentials
        Credential credential = new Credential();
        credential.setAccountId(account.getId());
        credential.setUsername(username);
        credential.setPasswordHash(hashPassword(password));
        em.persist(credential);

        // Assign admin role to first account
        if (isFirstAccount) {
            accountRoleService.addRole(account.getId(), Roles.CLIENT_ID, Roles._ADMIN_PLAIN);
        }

        return account;
    }

    @Transactional
    public Account createFederatedAccount(String email, String name, String picture, 
                                         Boolean emailVerified, String authProvider) {
        // Check if email already exists
        if (findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email already exists");
        }

        // Check if this is the first account
        boolean isFirstAccount = countAccounts() == 0;

        // Create account
        Account account = new Account();
        account.setEmail(email);
        account.setName(name);
        account.setPicture(picture);
        account.setEmailVerified(emailVerified != null ? emailVerified : false);
        account.setAuthProvider(authProvider);
        em.persist(account);

        // Assign admin role to first account
        if (isFirstAccount) {
            accountRoleService.addRole(account.getId(), Roles.CLIENT_ID, Roles._ADMIN_PLAIN);
        }

        return account;
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

            return findById(credential.getAccountId());
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
     * @param accountId The ID of the users account whose client roles to use for filtering
     * @return List of accounts that share client roles with the user
     */
    public List<Account> findAccountsByUserClientRoles(String accountId) {
        // First, get all unique client IDs from the user's roles
        var clientIdsQuery = em.createQuery(
            "SELECT DISTINCT ar.clientId FROM AccountRole ar WHERE ar.accountId = :accountId", 
            String.class
        );
        clientIdsQuery.setParameter("accountId", accountId);
        List<String> userClientIds = clientIdsQuery.getResultList();
        
        // If user has no roles, return only the account belonging to them
        if (userClientIds.isEmpty()) {
            return findById(accountId)
                .map(List::of)
                .orElse(List.of());
        }
        
        // Then, find all account IDs that have roles for any of those client IDs
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
}
