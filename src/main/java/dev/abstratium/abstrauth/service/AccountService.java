package dev.abstratium.abstrauth.service;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.Credential;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.inject.Inject;
import org.wildfly.security.password.Password;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.WildFlyElytronPasswordProvider;
import org.wildfly.security.password.interfaces.BCryptPassword;
import org.wildfly.security.password.spec.EncryptablePasswordSpec;
import org.wildfly.security.password.spec.IteratedSaltedPasswordAlgorithmSpec;
import org.wildfly.security.password.util.ModularCrypt;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Optional;

@ApplicationScoped
public class AccountService {

    @Inject
    EntityManager em;

    public Optional<Account> findByEmail(String email) {
        var query = em.createQuery("SELECT a FROM Account a WHERE a.email = :email", Account.class);
        query.setParameter("email", email);
        return query.getResultStream().findFirst();
    }

    public Optional<Account> findById(String id) {
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

        // Create account
        Account account = new Account();
        account.setEmail(email);
        account.setName(name);
        account.setEmailVerified(false);
        em.persist(account);

        // Create credentials
        Credential credential = new Credential();
        credential.setAccountId(account.getId());
        credential.setUsername(username);
        credential.setPasswordHash(hashPassword(password));
        em.persist(credential);

        return account;
    }

    public Optional<Credential> findCredentialByUsername(String username) {
        var query = em.createQuery("SELECT c FROM Credential c WHERE c.username = :username", Credential.class);
        query.setParameter("username", username);
        return query.getResultStream().findFirst();
    }

    public Optional<Credential> findCredentialByAccountId(String accountId) {
        var query = em.createQuery("SELECT c FROM Credential c WHERE c.accountId = :accountId", Credential.class);
        query.setParameter("accountId", accountId);
        return query.getResultStream().findFirst();
    }

    @Transactional
    public Optional<Account> authenticate(String username, String password) {
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
        try {
            WildFlyElytronPasswordProvider provider = new WildFlyElytronPasswordProvider();
            PasswordFactory passwordFactory = PasswordFactory.getInstance(BCryptPassword.ALGORITHM_BCRYPT, provider);
            
            int iterationCount = 10;
            IteratedSaltedPasswordAlgorithmSpec iteratedAlgorithmSpec = new IteratedSaltedPasswordAlgorithmSpec(iterationCount, generateSalt());
            EncryptablePasswordSpec encryptableSpec = new EncryptablePasswordSpec(password.toCharArray(), iteratedAlgorithmSpec);
            
            BCryptPassword original = (BCryptPassword) passwordFactory.generatePassword(encryptableSpec);
            return ModularCrypt.encodeAsString(original);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Failed to hash password", e);
        }
    }

    private boolean verifyPassword(String plainPassword, String hashedPassword) {
        try {
            WildFlyElytronPasswordProvider provider = new WildFlyElytronPasswordProvider();
            PasswordFactory passwordFactory = PasswordFactory.getInstance(BCryptPassword.ALGORITHM_BCRYPT, provider);
            
            Password restored = passwordFactory.translate(ModularCrypt.decode(hashedPassword));
            return passwordFactory.verify(restored, plainPassword.toCharArray());
        } catch (InvalidKeyException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            return false;
        }
    }

    private byte[] generateSalt() {
        byte[] salt = new byte[BCryptPassword.BCRYPT_SALT_SIZE];
        new java.security.SecureRandom().nextBytes(salt);
        return salt;
    }
}
