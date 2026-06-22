package dev.abstratium.abstrauth.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import org.hibernate.envers.Audited;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a role assignment that allows a source client
 * to call a target client with a specific role.
 * The role must exist in ClientAllowedRole for the target client.
 */
@Entity
@Table(name = "T_client_roles")
@Audited
public class ClientRole {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 100)
    private String role;

    @TenantId
    @Column(name = "org_id", nullable = false, length = 36)
    private String orgId;

    @Column(name = "src_client_id", nullable = false)
    private String srcClientId;

    @Column(name = "target_client_id", nullable = false)
    private String targetClientId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getSrcClientId() {
        return srcClientId;
    }

    public void setSrcClientId(String srcClientId) {
        this.srcClientId = srcClientId;
    }

    public String getTargetClientId() {
        return targetClientId;
    }

    public void setTargetClientId(String targetClientId) {
        this.targetClientId = targetClientId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
