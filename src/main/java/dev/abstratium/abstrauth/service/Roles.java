package dev.abstratium.abstrauth.service;

/**
 * Represents the standard roles for the Abstratium Abstrauth service.
 */
public interface Roles {
    /** The oauth client_id for the Abstrauth service */
    String CLIENT_ID = "abstratium-abstrauth";

    /** not really intended for use except when adding this role to the database */
    String _ADMIN_PLAIN = "admin";
    String _MANAGE_ACCOUNTS_PLAIN = "manage-accounts";
    String _MANAGE_CLIENTS_PLAIN = "manage-clients";
    String _USER_PLAIN = "user";

    /** The admin (super user) role. Can manage all clients and accounts of all organisations. */
    String ADMIN = CLIENT_ID + "_" + _ADMIN_PLAIN;

    /** The user is simply that. Used to ensure that they can only call 
     * some APIs if they are also signed in. */
    String USER = CLIENT_ID + "_" + _USER_PLAIN;

    /** Required by users who want to manage accounts. 
     * They manage only accounts of their own organisation(s) 
     * and only for the organisation that they are signed in as. */
    String MANAGE_ACCOUNTS = CLIENT_ID + "_" + _MANAGE_ACCOUNTS_PLAIN;
    
    /** Required by users who want to manage clients. 
     * They manage only clients of their own organisation(s) 
     * and only for the organisation that they are signed in as. */
    String MANAGE_CLIENTS = CLIENT_ID + "_" + _MANAGE_CLIENTS_PLAIN;

    /* ADDITIONAL VIRTUAL ROLES
       ============================

       - member of organisation - they can choose the orgId of such orgs when they sign in.
       - owner of organisation - they can administer the organisation by adding/removing accounts
                                 to/from the organisation. They cannot manage accounts/clients
                                 of the organisation, but will normally also have the MANAGE_ACCOUNTS
                                 and MANAGE_CLIENTS roles, allowing them to manage those things for
                                 the organisation that they chose when signing in.
                                 Owners also manage subscriptions so that they decide which
                                 applications (clients) the organisations users may use.
                                 IMPORTANT: the owner cannot assign ALL roles of a client to their
                                 users, otherwise they could add "admin" when subscribing to abstrauth
                                 and have control over all accounts and clients in all organisations!
     */
}
