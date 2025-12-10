package dev.abstratium.abstrauth.service;

/**
 * Represents the standard roles for the Abstratium Abstrauth service.
 */
public interface Roles {
    /** The oauth client_id for the Abstrauth service */
    String CLIENT_ID = "abstratium-abstrauth";

    /** not really intended for use except when adding this role to the database */
    String _ADMIN_PLAIN = "admin";

    /** The admin (super user) role */
    String ADMIN = CLIENT_ID + "_" + _ADMIN_PLAIN;

    /** The user is simply that. Used to ensure that they can only call some APIs if they are also signed in. */
    String USER = CLIENT_ID + "_user";

    /** Required by users who want to manage accounts */
    String MANAGE_ACCOUNTS = CLIENT_ID + "_manage-accounts";

    /** Required by users who want to manage oauth clients */
    String MANAGE_CLIENTS = CLIENT_ID + "_manage-clients";
}
