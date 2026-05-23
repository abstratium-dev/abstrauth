package dev.abstratium.abstrauth.service.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * User information from Microsoft Graph API
 * https://learn.microsoft.com/en-us/graph/api/user-get
 */
public class MicrosoftUserInfo {

    private String id; // Microsoft Entra object ID

    @JsonProperty("displayName")
    private String displayName;

    @JsonProperty("mail")
    private String mail;

    @JsonProperty("userPrincipalName")
    private String userPrincipalName;

    @JsonProperty("givenName")
    private String givenName;

    @JsonProperty("surname")
    private String surname;

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getMail() {
        return mail;
    }

    public void setMail(String mail) {
        this.mail = mail;
    }

    public String getUserPrincipalName() {
        return userPrincipalName;
    }

    public void setUserPrincipalName(String userPrincipalName) {
        this.userPrincipalName = userPrincipalName;
    }

    public String getGivenName() {
        return givenName;
    }

    public void setGivenName(String givenName) {
        this.givenName = givenName;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    /**
     * Returns the best available email address.
     * Uses mail if available, otherwise falls back to userPrincipalName if it looks like an email.
     */
    public String getEmail() {
        if (mail != null && !mail.isBlank()) {
            return mail;
        }
        if (userPrincipalName != null && userPrincipalName.contains("@")) {
            return userPrincipalName;
        }
        return null;
    }

    /**
     * Returns the user's full name.
     * Uses displayName if available, otherwise combines givenName and surname.
     */
    public String getName() {
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        if (givenName != null && surname != null) {
            return givenName + " " + surname;
        }
        if (givenName != null) {
            return givenName;
        }
        return null;
    }
}
