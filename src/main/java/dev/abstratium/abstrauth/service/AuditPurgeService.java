package dev.abstratium.abstrauth.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;

/**
 * Scheduled service that purges Envers audit rows older than the configured retention period.
 * The job runs daily at 03:00 UTC by default and removes rows from all {@code *_AUD} tables,
 * then deletes orphaned {@code REVINFO} rows that are no longer referenced by any audit table.
 */
@ApplicationScoped
public class AuditPurgeService {

    private static final Logger log = Logger.getLogger(AuditPurgeService.class);

    /**
     * All Envers audit tables in dependency order. Each table has a {@code REV}
     * column that references {@code REVINFO(REV)}.
     */
    private static final String[] AUDIT_TABLES = {
            "T_accounts_AUD",
            "T_credentials_AUD",
            "T_oauth_clients_AUD",
            "T_account_roles_AUD",
            "T_federated_identities_AUD",
            "T_oauth_client_secrets_AUD",
            "T_organisations_AUD",
            "T_organisation_accounts_AUD",
            "T_subscriptions_AUD",
            "T_client_allowed_roles_AUD",
            "T_client_roles_AUD"
    };

    private static final String DELETE_AUDIT_TABLE_TEMPLATE =
            "DELETE FROM %s WHERE REV IN (SELECT REV FROM REVINFO WHERE REVTSTMP < :cutoff)";

    @Inject
    EntityManager em;

    @ConfigProperty(name = "abstrauth.audit.retention.days", defaultValue = "90")
    int retentionDays;

    /**
     * Scheduled entry point for the daily audit purge job.
     * The scheduler requires a {@code void} return type, so the actual purge work is
     * delegated to {@link #purgeAuditData(int)}.
     */
    @Scheduled(cron = "${abstrauth.audit.purge.cron}")
    public void purgeAuditData() {
        purgeAuditData(retentionDays);
    }

    /**
     * Purges audit data older than the given retention period.
     *
     * @param retentionDays number of days to retain audit data
     * @return summary of the purge run
     */
    @Transactional
    public AuditPurgeResult purgeAuditData(int retentionDays) {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        long cutoffMillis = cutoff.toEpochMilli();

        Map<String, Long> deletedByTable = new LinkedHashMap<>();
        long totalDeleted = 0;

        for (String table : AUDIT_TABLES) {
            String sql = String.format(DELETE_AUDIT_TABLE_TEMPLATE, table);
            Query query = em.createNativeQuery(sql);
            query.setParameter("cutoff", cutoffMillis);
            long deleted = query.executeUpdate();
            deletedByTable.put(table, deleted);
            totalDeleted += deleted;
        }

        long revInfoDeleted = deleteOrphanedRevInfo(cutoffMillis);
        totalDeleted += revInfoDeleted;

        log.infof("Purged %d audit rows older than %d days (cutoff=%d). Deleted by table: %s, REVINFO rows deleted: %d",
                totalDeleted, retentionDays, cutoffMillis, deletedByTable, revInfoDeleted);

        return new AuditPurgeResult(retentionDays, cutoffMillis, deletedByTable, revInfoDeleted, totalDeleted);
    }

    private long deleteOrphanedRevInfo(long cutoffMillis) {
        StringBuilder sql = new StringBuilder("DELETE FROM REVINFO r WHERE r.REVTSTMP < :cutoff");
        for (String table : AUDIT_TABLES) {
            sql.append(" AND NOT EXISTS (SELECT 1 FROM ").append(table).append(" a WHERE a.REV = r.REV)");
        }

        Query query = em.createNativeQuery(sql.toString());
        query.setParameter("cutoff", cutoffMillis);
        return query.executeUpdate();
    }

    /**
     * Result of an audit purge run.
     */
    public static class AuditPurgeResult {
        private final int retentionDays;
        private final long cutoffTimestamp;
        private final Map<String, Long> deletedByTable;
        private final long deletedRevInfoCount;
        private final long totalDeleted;

        public AuditPurgeResult(int retentionDays, long cutoffTimestamp,
                Map<String, Long> deletedByTable, long deletedRevInfoCount, long totalDeleted) {
            this.retentionDays = retentionDays;
            this.cutoffTimestamp = cutoffTimestamp;
            this.deletedByTable = Map.copyOf(deletedByTable);
            this.deletedRevInfoCount = deletedRevInfoCount;
            this.totalDeleted = totalDeleted;
        }

        public int getRetentionDays() {
            return retentionDays;
        }

        public long getCutoffTimestamp() {
            return cutoffTimestamp;
        }

        public Map<String, Long> getDeletedByTable() {
            return deletedByTable;
        }

        public long getDeletedRevInfoCount() {
            return deletedRevInfoCount;
        }

        public long getTotalDeleted() {
            return totalDeleted;
        }
    }
}
