package dev.abstratium.abstrauth.util;

import jakarta.ws.rs.container.ContainerRequestContext;

/**
 * Utility class for extracting client IP addresses from HTTP requests.
 * Handles requests behind reverse proxies (nginx, load balancers, etc.).
 */
public class ClientIpUtil {

    private ClientIpUtil() {
        // Utility class - prevent instantiation
    }

    /**
     * Get the client IP address from the request.
     * Checks X-Forwarded-For header first (for requests behind a proxy/load balancer).
     * 
     * @param requestContext the JAX-RS request context
     * @return the client IP address
     */
    public static String getClientIp(ContainerRequestContext requestContext) {
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
}
