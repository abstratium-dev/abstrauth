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

    private static final String ADMIN = "admin";

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
            accountRoleService.addRole(account.getId(), "abstratium-abstrauth", ADMIN);
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
            accountRoleService.addRole(account.getId(), "abstratium-abstrauth", ADMIN);
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
     * Find all accounts
     * @return List of all accounts
     */
    public List<Account> findAll() {
        var query = em.createQuery("SELECT a FROM Account a ORDER BY a.createdAt DESC", Account.class);
        return query.getResultList();
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
