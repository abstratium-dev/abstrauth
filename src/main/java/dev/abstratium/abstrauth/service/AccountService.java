package dev.abstratium.abstrauth.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.Credential;
import dev.abstratium.abstrauth.entity.Organisation;
import dev.abstratium.abstrauth.non_multitenancy.service.NonMultitenancyAccountRoleService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AccountService {

    private static final Logger log = Logger.getLogger(AccountService.class); 

    public static final String NATIVE = "native";
    public static final String GOOGLE = "google";
    public static final String MICROSOFT = "microsoft";
    private static final AtomicBoolean ONE_OR_MORE_ACCOUNTS_FOUND = new AtomicBoolean(false);

    @Inject
    EntityManager em;

    @Inject
    AccountRoleService accountRoleService;

    @Inject
    NonMultitenancyAccountRoleService nonMultitenancyAccountRoleService;

    @Inject
    OrganisationService organisationService;

    @Inject
    MetricsService metricsService;

    @ConfigProperty(name = "password.pepper")
    String pepper;

    @ConfigProperty(name = "default.org.uuid")
    String defaultOrgId;

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
    public Account createAccount(String email, String name, String username, String password, String authProvider, String organisationName) {
        // Check if email already exists
        if (findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email already exists");
        }

        // Determine if this is the first account before creating it
        boolean isFirstAccount = noAccountsExist();

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

        // Link account to organisation
        var orgId = defaultOrgId;
        if (isFirstAccount) {
            //  First account uses the existing default organisation from migration
        } else {
            if (organisationName == null || organisationName.isBlank()) {
                organisationName = name + "'s Organisation";
            }
            Organisation org = organisationService.createOrganisation(organisationName, account.getId());
            orgId = org.getId();
        }
        organisationService.addOwner(orgId, account.getId());
        organisationService.addMember(orgId, account.getId());

        addAbstrauthRoles(account, orgId);

        return account;
    }

    @Transactional
    public Account createAccountFromFederatedProvider(String email, String name, String picture,
                                         Boolean emailVerified, String authProvider) {
        // Check if email already exists
        if (findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email already exists");
        }

        // Determine if this is the first account before creating it
        boolean isFirstAccount = noAccountsExist();

        // Create account
        Account account = new Account();
        account.setEmail(email);
        account.setName(name);
        account.setPicture(picture);
        account.setEmailVerified(emailVerified != null ? emailVerified : false);
        account.setAuthProvider(authProvider);
        em.persist(account);

        // Link account to organisation
        var orgId = defaultOrgId;
        if (!isFirstAccount) {
            // For federated signups without invite, auto-generate org name from email
            String organisationName = email + "'s Organisation";
            Organisation org = organisationService.createOrganisation(organisationName, account.getId());
            orgId = org.getId();
        } // else: First account uses the existing default organisation from migration
        organisationService.addOwner(orgId, account.getId());
        organisationService.addMember(orgId, account.getId());

        addAbstrauthRoles(account, orgId);

        return account;
    }

    /**
     * Creates an account within an existing organisation (for admin/org owner use).
     * The account is linked as a member of the specified organisation.
     * No new organisation is created - the account joins the existing one.
     */
    @Transactional
    public Account createAccountForOrg(String email, String name, String username, String password, 
                                        String authProvider, String orgId) {
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

        // Link account to existing organisation as member (not owner - the admin is the owner)
        organisationService.addMember(orgId, account.getId());
        
        addAbstrauthRoles(account, orgId);

        return account;
    }

    private void addAbstrauthRoles(Account account, String orgId) {
        // Check if this is the first account
        // don't used cached value, since here we really need to know if EXACTLY ONE exists,
        // since adding roles happens long after creating the account
        boolean isFirstAccount = countAccounts() == 1;

        // First account also gets admin roles, and is recorded as the creator of the default org
        if (isFirstAccount) {
            nonMultitenancyAccountRoleService.addRole(orgId, account.getId(), Roles.CLIENT_ID, Roles._ADMIN_PLAIN);
            organisationService.updateCreatedBy(orgId, account.getId());
        }

        // All accounts get the "user" role for abstrauth, so that they can actually use it.
        nonMultitenancyAccountRoleService.addRole(orgId, account.getId(), Roles.CLIENT_ID, Roles._USER_PLAIN);

        // accounts also get the management roles, if the user is an owner in their org (which means that the first abstrauth user also gets them).
        boolean accountIsOwnerOfGivenOrg = organisationService.findOwnerRow(orgId, account.getId()).isPresent();
        if (accountIsOwnerOfGivenOrg) {
            nonMultitenancyAccountRoleService.addRole(orgId, account.getId(), Roles.CLIENT_ID, Roles._MANAGE_ACCOUNTS_PLAIN);
            nonMultitenancyAccountRoleService.addRole(orgId, account.getId(), Roles.CLIENT_ID, Roles._MANAGE_CLIENTS_PLAIN);
        }
        log.debug("finished adding abstrauth roles");
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

            metricsService.recordSuccessfulLogin();

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
            metricsService.recordFailedLogin();
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
     * Find all accounts that are members of the given organisation.
     * @param orgId The organisation ID
     * @return List of accounts that are members of the organisation
     */
    public List<Account> findAccountsInOrg(String orgId) {
        var query = em.createQuery(
            "SELECT DISTINCT a FROM Account a " +
            "JOIN OrganisationAccount oa ON a.id = oa.id.accountId " +
            "LEFT JOIN FETCH a.roles " +
            "WHERE oa.id.orgId = :orgId AND oa.id.role = 'member' " +
            "ORDER BY a.createdAt DESC",
            Account.class
        );
        query.setParameter("orgId", orgId);
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

    public boolean oneOrMoreAccountsExist() {
        if(ONE_OR_MORE_ACCOUNTS_FOUND.get()) {
            return true;
        } else {
            // count, as it may have changed
            if(countAccounts() >= 1) {
                // once this node has confirmed that at least one exists, it can cache the value.
                // TODO support special case where all accounts are deleted? or just restart server?
                ONE_OR_MORE_ACCOUNTS_FOUND.set(true);
                return true;
            } else {
                // leave ONE_OR_MORE_ACCOUNTS_FOUND as false
                return false;
            }
        }
    }

    public boolean noAccountsExist() {
        return !oneOrMoreAccountsExist();
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
}
