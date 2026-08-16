package dev.abstratium.abstrauth.entity;

/**
 * Controls when a {@link ClientAllowedRole} is automatically assigned to users
 * on first sign-in to the client.
 *
 * <p>Stored in the database as a VARCHAR(30) column with a CHECK constraint
 * enforcing the three valid values. Mapped by JPA via
 * {@link jakarta.persistence.EnumType#STRING} so the enum name's
 * {@link #getDbValue() db value} is persisted directly.</p>
 */
public enum DefaultAssignment {

    /** Not auto-seeded. Was {@code is_default = false}. */
    NOT_DEFAULT("not_default"),

    /** Seeded for every member on first sign-in. Was {@code is_default = true}. */
    ALL_USERS("all_users"),

    /**
     * Seeded only when the signing-in user is an owner of their organisation.
     * This allows a client vendor to restrict automatic access to org owners,
     * who then grant access to individual members via the accounts UI.
     */
    ORG_OWNERS_ONLY("org_owners_only");

    private final String dbValue;

    DefaultAssignment(String dbValue) {
        this.dbValue = dbValue;
    }

    /**
     * The string stored in the database column.
     *
     * @return the database representation of this enum value
     */
    public String getDbValue() {
        return dbValue;
    }

    /**
     * Parse a database string into the enum. Falls back to {@link #NOT_DEFAULT}
     * for null or unrecognised values, so a corrupt or legacy row never causes
     * a runtime exception.
     *
     * @param value the raw string from the database column
     * @return the matching enum value, or {@link #NOT_DEFAULT} if none match
     */
    public static DefaultAssignment fromDbValue(String value) {
        if (value == null) {
            return NOT_DEFAULT;
        }
        for (DefaultAssignment da : values()) {
            if (da.dbValue.equals(value)) {
                return da;
            }
        }
        return NOT_DEFAULT;
    }

    /**
     * Whether this assignment causes the role to be auto-seeded at all.
     *
     * @return {@code true} for {@link #ALL_USERS} and {@link #ORG_OWNERS_ONLY};
     *         {@code false} for {@link #NOT_DEFAULT}
     */
    public boolean isSeeded() {
        return this != NOT_DEFAULT;
    }
}
