package dev.abstratium.abstrauth.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service to query Envers audit history using native SQL.
 * Native SQL is used instead of the Envers AuditReader API to ensure
 * compatibility with GraalVM native image builds.
 *
 * Every query is filtered by the caller's orgId to enforce tenant isolation.
 */
@ApplicationScoped
public class AuditHistoryService {

    @Inject
    EntityManager em;

    @Inject
    CurrentOrgContext currentOrgContext;

    /**
     * Supported auditable entity types, each defining how to query and
     * org-filter its audit table.
     */
    public enum AuditableEntity {
        account(
            "T_accounts_AUD",
            "id",
            new String[]{"id", "email", "email_verified", "name", "picture", "auth_provider", "created_at"},
            OrgFilterStrategy.VIA_ORGANISATION_ACCOUNTS,
            Roles.MANAGE_ACCOUNTS
        ),
        credential(
            "T_credentials_AUD",
            "id",
            new String[]{"id", "account_id", "username", "password_hash", "failed_login_attempts", "locked_until", "created_at"},
            OrgFilterStrategy.VIA_ORGANISATION_ACCOUNTS_BY_ACCOUNT_ID,
            Roles.MANAGE_ACCOUNTS
        ),
        oauth_client(
            "T_oauth_clients_AUD",
            "id",
            new String[]{"id", "client_id", "client_name", "client_type", "redirect_uris", "allowed_scopes",
                         "require_pkce", "auto_subscribe", "publik", "created_at", "org_id"},
            OrgFilterStrategy.DIRECT_ORG_ID,
            Roles.MANAGE_CLIENTS
        ),
        account_role(
            "T_account_roles_AUD",
            "id",
            new String[]{"id", "account_id", "client_id", "role", "created_at", "org_id"},
            OrgFilterStrategy.DIRECT_ORG_ID,
            Roles.MANAGE_ACCOUNTS
        ),
        federated_identity(
            "T_federated_identities_AUD",
            "id",
            new String[]{"id", "account_id", "provider", "provider_user_id", "email", "connected_at"},
            OrgFilterStrategy.VIA_ORGANISATION_ACCOUNTS_BY_ACCOUNT_ID,
            Roles.MANAGE_ACCOUNTS
        ),
        client_secret(
            "T_oauth_client_secrets_AUD",
            "id",
            new String[]{"id", "client_id", "created_at", "expires_at", "is_active", "description", "account_id", "org_id"},
            OrgFilterStrategy.DIRECT_ORG_ID,
            Roles.MANAGE_CLIENTS
        ),
        organisation(
            "T_organisations_AUD",
            "id",
            new String[]{"id", "name", "created_by_account_id", "created_at"},
            OrgFilterStrategy.SELF_IS_ORG,
            Roles.MANAGE_ACCOUNTS
        ),
        organisation_account(
            "T_organisation_accounts_AUD",
            null, // composite PK — handled specially
            new String[]{"org_id", "account_id", "role", "added_at"},
            OrgFilterStrategy.DIRECT_ORG_ID,
            Roles.MANAGE_ACCOUNTS
        ),
        subscription(
            "T_subscriptions_AUD",
            "id",
            new String[]{"id", "org_id", "client_id", "created_at"},
            OrgFilterStrategy.DIRECT_ORG_ID,
            Roles.MANAGE_CLIENTS
        ),
        client_allowed_role(
            "T_client_allowed_roles_AUD",
            null, // composite PK — handled specially
            new String[]{"client_id", "role", "is_default", "available_to_foreign_orgs"},
            OrgFilterStrategy.VIA_OAUTH_CLIENTS_BY_CLIENT_ID,
            Roles.MANAGE_CLIENTS
        ),
        client_role(
            "T_client_roles_AUD",
            "id",
            new String[]{"id", "role", "org_id", "src_client_id", "target_client_id", "created_at"},
            OrgFilterStrategy.DIRECT_ORG_ID,
            Roles.MANAGE_CLIENTS
        );

        final String auditTable;
        final String pkColumn; // null for composite PK entities
        final String[] columns;
        final OrgFilterStrategy orgFilter;
        final String requiredRole;

        AuditableEntity(String auditTable, String pkColumn, String[] columns, OrgFilterStrategy orgFilter, String requiredRole) {
            this.auditTable = auditTable;
            this.pkColumn = pkColumn;
            this.columns = columns;
            this.orgFilter = orgFilter;
            this.requiredRole = requiredRole;
        }

        public String getRequiredRole() {
            return requiredRole;
        }
    }

    enum OrgFilterStrategy {
        /** Entity has an org_id column directly in the audit table */
        DIRECT_ORG_ID,
        /** The entity IS the organisation — filter where id = orgId */
        SELF_IS_ORG,
        /** Entity has account_id; filter via T_organisation_accounts */
        VIA_ORGANISATION_ACCOUNTS_BY_ACCOUNT_ID,
        /** Entity PK is account id; filter via T_organisation_accounts */
        VIA_ORGANISATION_ACCOUNTS,
        /** Entity has client_id; filter via T_oauth_clients.org_id */
        VIA_OAUTH_CLIENTS_BY_CLIENT_ID
    }

    /**
     * Returns the audit history for a given entity, filtered by the current org.
     *
     * @param entityType the entity type name (must match an AuditableEntity enum value)
     * @param primaryKey the primary key value (for composite PKs this is a slash-separated string)
     * @return list of revision maps, each containing column values + revision metadata
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getHistory(String entityType, String primaryKey) {
        AuditableEntity entity;
        try {
            entity = AuditableEntity.valueOf(entityType);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown entity type: " + entityType);
        }

        String orgId = currentOrgContext.getOrgId();
        if (orgId == null || orgId.isBlank()) {
            throw new IllegalStateException("No organisation context available");
        }

        StringBuilder sql = new StringBuilder("SELECT ");

        // Select entity columns
        for (int i = 0; i < entity.columns.length; i++) {
            sql.append("a.").append(entity.columns[i]);
            sql.append(", ");
        }
        // Add revision metadata
        sql.append("a.REV, a.REVTYPE, r.REVTSTMP, r.username, r.correlation_id, r.change_note");

        sql.append(" FROM ").append(entity.auditTable).append(" a");
        sql.append(" JOIN REVINFO r ON a.REV = r.REV");

        // Build WHERE clause with PK filter and org filter
        sql.append(" WHERE ");
        appendPkFilter(sql, entity, primaryKey);
        sql.append(" AND ");
        appendOrgFilter(sql, entity);

        sql.append(" ORDER BY a.REV ASC");

        Query query = em.createNativeQuery(sql.toString());
        setPkParameters(query, entity, primaryKey);
        query.setParameter("orgId", orgId);

        List<Object[]> rows = query.getResultList();
        return mapResults(rows, entity);
    }

    /**
     * Returns the audit history for a given entity type, filtered by a specific column value
     * rather than by primary key. This is useful for fetching related entity history
     * (e.g. all account_role entries for a given account_id).
     *
     * @param entityType the entity type name (must match an AuditableEntity enum value)
     * @param column     the column name to filter by (must exist in the entity's columns)
     * @param value      the value to filter by
     * @return list of revision maps, each containing column values + revision metadata
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getHistoryByColumn(String entityType, String column, String value) {
        AuditableEntity entity;
        try {
            entity = AuditableEntity.valueOf(entityType);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown entity type: " + entityType);
        }

        // Validate the column exists in this entity
        boolean columnExists = false;
        for (String col : entity.columns) {
            if (col.equals(column)) {
                columnExists = true;
                break;
            }
        }
        if (!columnExists) {
            throw new IllegalArgumentException("Column '" + column + "' does not exist in entity type: " + entityType);
        }

        String orgId = currentOrgContext.getOrgId();
        if (orgId == null || orgId.isBlank()) {
            throw new IllegalStateException("No organisation context available");
        }

        StringBuilder sql = new StringBuilder("SELECT ");

        for (int i = 0; i < entity.columns.length; i++) {
            sql.append("a.").append(entity.columns[i]);
            sql.append(", ");
        }
        sql.append("a.REV, a.REVTYPE, r.REVTSTMP, r.username, r.correlation_id, r.change_note");

        sql.append(" FROM ").append(entity.auditTable).append(" a");
        sql.append(" JOIN REVINFO r ON a.REV = r.REV");

        sql.append(" WHERE a.").append(column).append(" = :filterValue");
        sql.append(" AND ");
        appendOrgFilter(sql, entity);

        sql.append(" ORDER BY a.REV ASC");

        Query query = em.createNativeQuery(sql.toString());
        query.setParameter("filterValue", value);
        query.setParameter("orgId", orgId);

        List<Object[]> rows = query.getResultList();
        return mapResults(rows, entity);
    }

    private void appendPkFilter(StringBuilder sql, AuditableEntity entity, String primaryKey) {
        if (entity.pkColumn != null) {
            sql.append("a.").append(entity.pkColumn).append(" = :pk");
        } else if (entity == AuditableEntity.organisation_account) {
            // composite PK: org_id/account_id/role
            sql.append("a.org_id = :pkOrg AND a.account_id = :pkAccount AND a.role = :pkRole");
        } else if (entity == AuditableEntity.client_allowed_role) {
            // composite PK: client_id/role
            sql.append("a.client_id = :pkClientId AND a.role = :pkRole");
        }
    }

    private void setPkParameters(Query query, AuditableEntity entity, String primaryKey) {
        if (entity.pkColumn != null) {
            query.setParameter("pk", primaryKey);
        } else if (entity == AuditableEntity.organisation_account) {
            String[] parts = primaryKey.split("/");
            if (parts.length != 3) {
                throw new IllegalArgumentException(
                    "organisation_account requires composite key in format: orgId/accountId/role");
            }
            query.setParameter("pkOrg", parts[0]);
            query.setParameter("pkAccount", parts[1]);
            query.setParameter("pkRole", parts[2]);
        } else if (entity == AuditableEntity.client_allowed_role) {
            String[] parts = primaryKey.split("/");
            if (parts.length != 2) {
                throw new IllegalArgumentException(
                    "client_allowed_role requires composite key in format: clientId/role");
            }
            query.setParameter("pkClientId", parts[0]);
            query.setParameter("pkRole", parts[1]);
        }
    }

    private void appendOrgFilter(StringBuilder sql, AuditableEntity entity) {
        switch (entity.orgFilter) {
            case DIRECT_ORG_ID:
                sql.append("(a.org_id = :orgId OR (a.REVTYPE = 2 AND a.org_id IS NULL))");
                break;
            case SELF_IS_ORG:
                sql.append("a.id = :orgId");
                break;
            case VIA_ORGANISATION_ACCOUNTS:
                // account PK is the account_id in organisation_accounts
                sql.append("EXISTS (SELECT 1 FROM T_organisation_accounts oa WHERE oa.account_id = a.id AND oa.org_id = :orgId)");
                break;
            case VIA_ORGANISATION_ACCOUNTS_BY_ACCOUNT_ID:
                // entity has account_id column
                sql.append("EXISTS (SELECT 1 FROM T_organisation_accounts oa WHERE oa.account_id = a.account_id AND oa.org_id = :orgId)");
                break;
            case VIA_OAUTH_CLIENTS_BY_CLIENT_ID:
                // entity has client_id; look up owning org via T_oauth_clients
                sql.append("EXISTS (SELECT 1 FROM T_oauth_clients c WHERE c.client_id = a.client_id AND c.org_id = :orgId)");
                break;
        }
    }

    private List<Map<String, Object>> mapResults(List<Object[]> rows, AuditableEntity entity) {
        List<Map<String, Object>> results = new ArrayList<>(rows.size());
        int colCount = entity.columns.length;

        for (Object[] row : rows) {
            Map<String, Object> entry = new LinkedHashMap<>();

            // Entity columns
            for (int i = 0; i < colCount; i++) {
                entry.put(entity.columns[i], row[i]);
            }

            // Revision metadata (appended after entity columns)
            entry.put("rev", row[colCount]);
            entry.put("revType", row[colCount + 1]);
            entry.put("revTimestamp", row[colCount + 2]);
            entry.put("username", row[colCount + 3]);
            entry.put("correlationId", row[colCount + 4]);
            entry.put("changeNote", row[colCount + 5]);

            results.add(entry);
        }

        return results;
    }
}
