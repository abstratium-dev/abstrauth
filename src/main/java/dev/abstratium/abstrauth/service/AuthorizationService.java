package dev.abstratium.abstrauth.service;

import dev.abstratium.abstrauth.entity.AuthorizationCode;
import dev.abstratium.abstrauth.entity.AuthorizationRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

@ApplicationScoped
public class AuthorizationService {

    @Inject
    EntityManager em;

    private static final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public AuthorizationRequest createAuthorizationRequest(
            String clientId,
            String redirectUri,
            String scope,
            String state,
            String codeChallenge,
            String codeChallengeMethod) {

        AuthorizationRequest request = new AuthorizationRequest();
        request.setClientId(clientId);
        request.setRedirectUri(redirectUri);
        request.setScope(scope);
        request.setState(state);
        request.setCodeChallenge(codeChallenge);
        request.setCodeChallengeMethod(codeChallengeMethod);
        request.setStatus("pending");

        em.persist(request);
        return request;
    }

    public Optional<AuthorizationRequest> findAuthorizationRequest(String requestId) {
        return Optional.ofNullable(em.find(AuthorizationRequest.class, requestId));
    }

    @Transactional
    public void approveAuthorizationRequest(String requestId, String accountId) {
        AuthorizationRequest request = em.find(AuthorizationRequest.class, requestId);
        if (request == null) {
            throw new IllegalArgumentException("Authorization request not found");
        }

        if (request.getExpiresAt().isBefore(LocalDateTime.now())) {
            request.setStatus("expired");
            em.merge(request);
            throw new IllegalStateException("Authorization request has expired");
        }

        request.setAccountId(accountId);
        request.setStatus("approved");
        em.merge(request);
    }

    @Transactional
    public AuthorizationCode generateAuthorizationCode(String requestId) {
        AuthorizationRequest request = em.find(AuthorizationRequest.class, requestId);
        if (request == null || !"approved".equals(request.getStatus())) {
            throw new IllegalStateException("Authorization request not approved");
        }

        AuthorizationCode authCode = new AuthorizationCode();
        authCode.setCode(generateSecureCode());
        authCode.setAuthorizationRequestId(requestId);
        authCode.setAccountId(request.getAccountId());
        authCode.setClientId(request.getClientId());
        authCode.setRedirectUri(request.getRedirectUri());
        authCode.setScope(request.getScope());
        authCode.setCodeChallenge(request.getCodeChallenge());
        authCode.setCodeChallengeMethod(request.getCodeChallengeMethod());

        em.persist(authCode);
        return authCode;
    }

    public Optional<AuthorizationCode> findAuthorizationCode(String code) {
        var query = em.createQuery("SELECT ac FROM AuthorizationCode ac WHERE ac.code = :code", AuthorizationCode.class);
        query.setParameter("code", code);
        return query.getResultStream().findFirst();
    }

    @Transactional
    public void markCodeAsUsed(String code) {
        Optional<AuthorizationCode> authCodeOpt = findAuthorizationCode(code);
        if (authCodeOpt.isPresent()) {
            AuthorizationCode authCode = authCodeOpt.get();
            authCode.setUsed(true);
            em.merge(authCode);
        }
    }

    @Transactional
    public void markAuthorizationCodeAsUsed(String authCodeId) {
        AuthorizationCode authCode = em.find(AuthorizationCode.class, authCodeId);
        if (authCode != null) {
            authCode.setUsed(true);
            em.merge(authCode);
        }
    }

    private String generateSecureCode() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
