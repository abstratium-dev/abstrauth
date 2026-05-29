package dev.abstratium.abstrauth.service;

/**
 * Thrown when an org attempts to sign in to a client it is not subscribed to
 * and the client has auto_subscribe = false.
 */
public class NoSubscriptionException extends RuntimeException {
    public NoSubscriptionException(String orgId, String clientId) {
        super("Organisation " + orgId + " is not subscribed to client " + clientId);
    }
}
