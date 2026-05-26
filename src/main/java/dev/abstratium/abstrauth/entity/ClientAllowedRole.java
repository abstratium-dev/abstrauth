package dev.abstratium.abstrauth.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "T_client_allowed_roles")
public class ClientAllowedRole {

    @Embeddable
    public static class Id implements Serializable {

        @Column(name = "client_id", nullable = false)
        private String clientId;

        @Column(nullable = false, length = 100)
        private String role;

        public Id() {}

        public Id(String clientId, String role) {
            this.clientId = clientId;
            this.role = role;
        }

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Id)) return false;
            Id id = (Id) o;
            return Objects.equals(clientId, id.clientId) && Objects.equals(role, id.role);
        }

        @Override
        public int hashCode() {
            return Objects.hash(clientId, role);
        }
    }

    @EmbeddedId
    private Id id = new Id();

    @Column(name = "is_default")
    private Boolean isDefault = false;

    public Id getId() { return id; }
    public void setId(Id id) { this.id = id; }

    public String getClientId() { return id.getClientId(); }
    public void setClientId(String clientId) { id.setClientId(clientId); }

    public String getRole() { return id.getRole(); }
    public void setRole(String role) { id.setRole(role); }

    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }
}
