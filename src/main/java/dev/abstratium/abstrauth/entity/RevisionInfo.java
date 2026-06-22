package dev.abstratium.abstrauth.entity;

import jakarta.enterprise.inject.spi.CDI;
import jakarta.persistence.*;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionListener;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

import io.quarkus.arc.Arc;
import io.quarkus.security.identity.SecurityIdentity;

import java.time.Instant;

@Entity
@RevisionEntity(RevisionInfo.RevisionInfoListener.class)
@Table(name = "REVINFO")
public class RevisionInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @RevisionNumber
    @Column(name = "REV")
    private Long rev;

    @RevisionTimestamp
    @Column(name = "REVTSTMP")
    private Long revtstmp;

    @Column(name = "username")
    private String username;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "change_note")
    private String changeNote;

    public Long getRev() {
        return rev;
    }

    public void setRev(Long rev) {
        this.rev = rev;
    }

    public Long getRevtstmp() {
        return revtstmp;
    }

    public void setRevtstmp(Long revtstmp) {
        this.revtstmp = revtstmp;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getChangeNote() {
        return changeNote;
    }

    public void setChangeNote(String changeNote) {
        this.changeNote = changeNote;
    }

    public static class RevisionInfoListener implements RevisionListener {
        @Override
        public void newRevision(Object revisionEntity) {
            RevisionInfo revisionInfo = (RevisionInfo) revisionEntity;
            revisionInfo.setRevtstmp(Instant.now().toEpochMilli());
            revisionInfo.setUsername(getCurrentUsername());
            revisionInfo.setChangeNote(getCurrentChangeNote());
        }

        private String getCurrentUsername() {
            try {
                if (Arc.container() != null && Arc.container().requestContext().isActive()) {
                    SecurityIdentity identity = CDI.current().select(SecurityIdentity.class).get();
                    if (identity != null && !identity.isAnonymous() && identity.getPrincipal() != null) {
                        return identity.getPrincipal().getName();
                    }
                }
            } catch (Exception e) {
                // No security context available (e.g. bootstrap, scheduled tasks)
            }
            return "system";
        }

        private String getCurrentChangeNote() {
            try {
                if (Arc.container() != null && Arc.container().requestContext().isActive()) {
                    ChangeNoteContext ctx = CDI.current().select(ChangeNoteContext.class).get();
                    if (ctx != null && ctx.hasChangeNote()) {
                        return ctx.getChangeNote();
                    }
                }
            } catch (Exception e) {
                // No request context available
            }
            return null;
        }
    }
}
