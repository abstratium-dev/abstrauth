package dev.abstratium.abstrauth.filter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Rate Limiting Filter for OAuth endpoints.
 * 
 * Implements a sliding window rate limiter to prevent abuse and brute-force attacks
 * on authentication endpoints. Uses in-memory storage (suitable for single-instance
 * deployments; use Redis/distributed cache for multi-instance).
 * 
 * Rate limits are applied per IP address.
 */
@Provider
@PreMatching
@Priority(1000) // Run early in the filter chain
public class RateLimitFilter implements ContainerRequestFilter {

    private static final Logger log = Logger.getLogger(RateLimitFilter.class); 

    @ConfigProperty(name = "rate-limit.enabled", defaultValue = "true")
    boolean rateLimitEnabled;

    @ConfigProperty(name = "rate-limit.oauth.max-requests", defaultValue = "10")
    int maxRequests;

    @ConfigProperty(name = "rate-limit.oauth.window-seconds", defaultValue = "60")
    int windowSeconds;

    @ConfigProperty(name = "rate-limit.oauth.ban-duration-seconds", defaultValue = "300")
    int banDurationSeconds;

    // In-memory storage for rate limiting
    // Key: IP address, Value: Request tracker
    private final Map<String, RequestTracker> requestTrackers = new ConcurrentHashMap<>();

    // Banned IPs (temporary bans for repeated violations)
    private final Map<String, Instant> bannedIps = new ConcurrentHashMap<>();

    /**
     * Clear all rate limit tracking data.
     * Useful for testing or administrative purposes.
     */
    public void clearAll() {
        requestTrackers.clear();
        bannedIps.clear();
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (!rateLimitEnabled) {
            return;
        }

        String path = requestContext.getUriInfo().getPath();
        
        // Only apply rate limiting to OAuth endpoints
        if (!isOAuthEndpoint(path)) {
            return;
        }

        String clientIp = getClientIp(requestContext);
        
        // Check if IP is banned
        if (isBanned(clientIp)) {

            log.info("IP " + clientIp + " is banned, blocking request");

            requestContext.abortWith(
                Response.status(429) // Too Many Requests
                    .entity("Rate limit exceeded. Please try again later.")
                    .header("Retry-After", banDurationSeconds)
                    .build()
            );
            return;
        }

        // Check rate limit
        if (isRateLimited(clientIp)) {
            // Ban the IP temporarily for repeated violations
            bannedIps.put(clientIp, Instant.now().plusSeconds(banDurationSeconds));

            log.info("Rate limit exceeded for IP: " + clientIp + ", temporarily banning");
            
            requestContext.abortWith(
                Response.status(429) // Too Many Requests
                    .entity("Rate limit exceeded. Too many requests from your IP address.")
                    .header("Retry-After", banDurationSeconds)
                    .header("X-RateLimit-Limit", String.valueOf(maxRequests))
                    .header("X-RateLimit-Window", String.valueOf(windowSeconds))
                    .build()
            );
            return;
        }

        // Record the request
        recordRequest(clientIp);
        
        // Add rate limit headers to response
        RequestTracker tracker = requestTrackers.get(clientIp);
        if (tracker != null) {
            int remaining = Math.max(0, maxRequests - tracker.getCount());
            requestContext.setProperty("X-RateLimit-Limit", maxRequests);
            requestContext.setProperty("X-RateLimit-Remaining", remaining);
            requestContext.setProperty("X-RateLimit-Reset", tracker.getWindowEnd().getEpochSecond());
        }
    }

    /**
     * Check if the request path is an OAuth endpoint that should be rate-limited.
     */
    private boolean isOAuthEndpoint(String path) {
        return path.startsWith("/oauth2/authorize") ||
               path.startsWith("/oauth2/token") ||
               path.startsWith("/oauth2/callback") ||
               path.startsWith("/oauth2/federated") ||
               path.startsWith("/api/signup") ||
               path.startsWith("/api/signin") ||
               // Also check without leading slash (in case of different path handling)
               path.startsWith("oauth2/authorize") ||
               path.startsWith("oauth2/token") ||
               path.startsWith("oauth2/callback") ||
               path.startsWith("oauth2/federated") ||
               path.startsWith("api/signup") ||
               path.startsWith("api/signin");
    }

    /**
     * Get the client IP address from the request.
     * Checks X-Forwarded-For header first (for requests behind a proxy/load balancer).
     */
    private String getClientIp(ContainerRequestContext requestContext) {
        // Check X-Forwarded-For header (set by reverse proxies)
        String forwardedFor = requestContext.getHeaderString("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return forwardedFor.split(",")[0].trim();
        }
        
        // Check X-Real-IP header (set by some reverse proxies)
        String realIp = requestContext.getHeaderString("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp;
        }
        
        // Fall back to remote address
        // Note: In production behind a reverse proxy, this will be the proxy's IP
        return requestContext.getUriInfo().getRequestUri().getHost();
    }

    /**
     * Check if an IP is currently banned.
     */
    private boolean isBanned(String ip) {
        Instant banExpiry = bannedIps.get(ip);
        if (banExpiry == null) {
            return false;
        }
        
        if (Instant.now().isAfter(banExpiry)) {
            // Ban has expired, remove it
            bannedIps.remove(ip);
            return false;
        }
        
        return true;
    }

    /**
     * Check if an IP has exceeded the rate limit.
     */
    private boolean isRateLimited(String ip) {
        RequestTracker tracker = requestTrackers.get(ip);
        
        if (tracker == null) {
            return false;
        }
        
        // Clean up expired window
        if (Instant.now().isAfter(tracker.getWindowEnd())) {
            requestTrackers.remove(ip);
            return false;
        }
        
        return tracker.getCount() >= maxRequests;
    }

    /**
     * Record a request for rate limiting.
     */
    private void recordRequest(String ip) {
        requestTrackers.compute(ip, (key, tracker) -> {
            Instant now = Instant.now();
            
            if (tracker == null || now.isAfter(tracker.getWindowEnd())) {
                // Start a new window
                return new RequestTracker(now, windowSeconds);
            }
            
            // Increment counter in current window
            tracker.increment();
            return tracker;
        });
        
        // Cleanup old entries periodically (simple approach)
        if (requestTrackers.size() > 10000) {
            cleanupExpiredEntries();
        }
    }

    /**
     * Remove expired entries to prevent memory leaks.
     */
    private void cleanupExpiredEntries() {
        Instant now = Instant.now();
        requestTrackers.entrySet().removeIf(entry -> 
            now.isAfter(entry.getValue().getWindowEnd())
        );
        bannedIps.entrySet().removeIf(entry ->
            now.isAfter(entry.getValue())
        );
    }

    /**
     * Tracks requests within a time window for a single IP.
     */
    private static class RequestTracker {
        private final Instant windowEnd;
        private final AtomicInteger count;

        public RequestTracker(Instant windowStart, int windowSeconds) {
            this.windowEnd = windowStart.plusSeconds(windowSeconds);
            this.count = new AtomicInteger(1);
        }

        public void increment() {
            count.incrementAndGet();
        }

        public int getCount() {
            return count.get();
        }

        public Instant getWindowEnd() {
            return windowEnd;
        }
    }
}
