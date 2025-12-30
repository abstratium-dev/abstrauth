package dev.abstratium.abstrauth.boundary;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Standard error response structure used across all API endpoints.
 * Supports both simple error messages and OAuth 2.0 error responses.
 */
@RegisterForReflection
@Schema(description = "Error Response")
public class ErrorResponse {
    
    @Schema(description = "Error code or message", examples = {"invalid_request", "Account not found"})
    public String error;
    
    @Schema(description = "Human-readable error description", examples = "Authorization code is invalid or expired")
    public String error_description;
    
    @Schema(description = "URI identifying a human-readable web page with error information", examples = "https://auth.example.com/error/invalid_grant")
    public String error_uri;
    
    /**
     * Default constructor for cases where fields are set after construction.
     */
    public ErrorResponse() {
    }
    
    /**
     * Create error response with just an error message.
     */
    public ErrorResponse(String error) {
        this.error = error;
    }
    
    /**
     * Create error response with error code and description.
     */
    public ErrorResponse(String error, String errorDescription) {
        this.error = error;
        this.error_description = errorDescription;
    }
    
    /**
     * Create error response with error code, description, and URI.
     */
    public ErrorResponse(String error, String errorDescription, String errorUri) {
        this.error = error;
        this.error_description = errorDescription;
        this.error_uri = errorUri;
    }
}
