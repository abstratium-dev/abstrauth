package dev.abstratium.abstrauth.service.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * User information from Google's userinfo endpoint
 * https://developers.google.com/identity/protocols/oauth2/openid-connect#obtainuserinfo
 */
public class GoogleUserInfo {
    
    private String sub; // Google user ID (OpenID Connect)
    private String id;  // Google user ID (older OAuth2 endpoint)
    private String email;
    
    @JsonProperty("email_verified")
    private Boolean emailVerified;
    
    private String name;
    private String picture;
    
    @JsonProperty("given_name")
    private String givenName;
    
    @JsonProperty("family_name")
    private String familyName;

    // Getters and setters
    public String getSub() {
        // Google's v1 userinfo returns 'id', v2+ returns 'sub'
        return sub != null ? sub : id;
    }

    public void setSub(String sub) {
        this.sub = sub;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Boolean getEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(Boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPicture() {
        return picture;
    }

    public void setPicture(String picture) {
        this.picture = picture;
    }

    public String getGivenName() {
        return givenName;
    }

    public void setGivenName(String givenName) {
        this.givenName = givenName;
    }

    public String getFamilyName() {
        return familyName;
    }

    public void setFamilyName(String familyName) {
        this.familyName = familyName;
    }
}
