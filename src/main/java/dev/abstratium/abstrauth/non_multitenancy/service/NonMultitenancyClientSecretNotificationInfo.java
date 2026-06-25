package dev.abstratium.abstrauth.non_multitenancy.service;

import java.time.Instant;

/**
 * Information about a client secret needed for sending expiration notifications.
 * This record is used by cross-tenant notification queries that bypass the
 * Hibernate tenant discriminator.
 */
public record NonMultitenancyClientSecretNotificationInfo(
    Long id,
    String clientId,
    String clientName,
    String description,
    Instant expiresAt,
    String accountId,
    String orgId
) {
}
