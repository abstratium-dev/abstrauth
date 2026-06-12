package dev.abstratium.abstrauth.util;

/**
 * Utility class for client ID manipulation.
 * Client IDs may be prefixed with the owning organisation's UUID and a double underscore
 * (e.g. {@code 550e8400-e29b-41d4-a716-446655440000__myapp}).
 * This utility strips that prefix when constructing human-friendly identifiers.
 */
public class ClientIdUtil {

    private ClientIdUtil() {
        // Utility class - prevent instantiation
    }

    /**
     * Strips the organisation UUID prefix from a client ID if present.
     * The prefix is expected to be a 36-character UUID followed by two underscores.
     * The remaining part after the prefix must be non-empty for stripping to occur.
     *
     * @param clientId the full client ID, potentially prefixed with an org UUID
     * @return the client ID with the UUID + "__" prefix removed, or the original if absent/invalid
     */
    public static String stripOrgPrefix(String clientId) {
        if (clientId == null || clientId.length() <= 38) {
            return clientId;
        }
        String prefix = clientId.substring(0, 38);
        int firstDash = prefix.indexOf('-');
        if (firstDash != 8) {
            return clientId;
        }
        if (!prefix.endsWith("__")) {
            return clientId;
        }
        String uuidPart = prefix.substring(0, 36);
        if (!isValidUuid(uuidPart)) {
            return clientId;
        }
        return clientId.substring(38);
    }

    private static boolean isValidUuid(String s) {
        if (s.length() != 36) {
            return false;
        }
        return s.charAt(8) == '-'
                && s.charAt(13) == '-'
                && s.charAt(18) == '-'
                && s.charAt(23) == '-'
                && isHex(s.charAt(0)) && isHex(s.charAt(1))
                && isHex(s.charAt(2)) && isHex(s.charAt(3))
                && isHex(s.charAt(4)) && isHex(s.charAt(5))
                && isHex(s.charAt(6)) && isHex(s.charAt(7))
                && isHex(s.charAt(9)) && isHex(s.charAt(10))
                && isHex(s.charAt(11)) && isHex(s.charAt(12))
                && isHex(s.charAt(14)) && isHex(s.charAt(15))
                && isHex(s.charAt(16)) && isHex(s.charAt(17))
                && isHex(s.charAt(19)) && isHex(s.charAt(20))
                && isHex(s.charAt(21)) && isHex(s.charAt(22))
                && isHex(s.charAt(24)) && isHex(s.charAt(25))
                && isHex(s.charAt(26)) && isHex(s.charAt(27))
                && isHex(s.charAt(28)) && isHex(s.charAt(29))
                && isHex(s.charAt(30)) && isHex(s.charAt(31))
                && isHex(s.charAt(32)) && isHex(s.charAt(33))
                && isHex(s.charAt(34)) && isHex(s.charAt(35));
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f')
                || (c >= 'A' && c <= 'F');
    }
}
