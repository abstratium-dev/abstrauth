package dev.abstratium.abstrauth.filter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;

/**
 * Response filter to add rate limit headers to responses.
 * 
 * These headers inform clients about their current rate limit status.
 */
@Provider
public class RateLimitResponseFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext,
                      ContainerResponseContext responseContext) throws IOException {
        
        // Add rate limit headers if they were set by the request filter
        Object limit = requestContext.getProperty("X-RateLimit-Limit");
        Object remaining = requestContext.getProperty("X-RateLimit-Remaining");
        Object reset = requestContext.getProperty("X-RateLimit-Reset");
        
        if (limit != null) {
            responseContext.getHeaders().add("X-RateLimit-Limit", limit);
        }
        
        if (remaining != null) {
            responseContext.getHeaders().add("X-RateLimit-Remaining", remaining);
        }
        
        if (reset != null) {
            responseContext.getHeaders().add("X-RateLimit-Reset", reset);
        }
    }
}
