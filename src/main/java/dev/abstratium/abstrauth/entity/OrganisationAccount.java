package dev.abstratium.abstrauth.entity;

import jakarta.persistence.*;
import org.hibernate.envers.Audited;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "T_organisation_accounts")
@Audited
public class OrganisationAccount {

    @Embeddable
    public static class Id implements Serializable {

        @Column(name = "org_id", nullable = false, length = 36)
        private String orgId;

        @Column(name = "account_id", nullable = false, length = 36)
        private String accountId;

        @Column(nullable = false, length = 50)
        private String role;

        public Id() {}

        public Id(String orgId, String accountId, String role) {
            this.orgId = orgId;
            this.accountId = accountId;
            this.role = role;
        }

        public String getOrgId() { return orgId; }
        public void setOrgId(String orgId) { this.orgId = orgId; }
        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Id)) return false;
            Id id = (Id) o;
            return Objects.equals(orgId, id.orgId) &&
                   Objects.equals(accountId, id.accountId) &&
                   Objects.equals(role, id.role);
        }

        @Override
        public int hashCode() {
            return Objects.hash(orgId, accountId, role);
        }
    }

    @EmbeddedId
    private Id id = new Id();

    @Column(name = "added_at")
    private LocalDateTime addedAt;

    @PrePersist
    public void prePersist() {
        if (addedAt == null) {
            addedAt = LocalDateTime.now();
        }
    }

    public Id getId() { return id; }
    public void setId(Id id) { this.id = id; }

    public String getOrgId() { return id.getOrgId(); }
    public void setOrgId(String orgId) { id.setOrgId(orgId); }

    public String getAccountId() { return id.getAccountId(); }
    public void setAccountId(String accountId) { id.setAccountId(accountId); }

    public String getRole() { return id.getRole(); }
    public void setRole(String role) { id.setRole(role); }

    public LocalDateTime getAddedAt() { return addedAt; }
    public void setAddedAt(LocalDateTime addedAt) { this.addedAt = addedAt; }
}
