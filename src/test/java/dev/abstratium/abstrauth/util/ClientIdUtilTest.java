package dev.abstratium.abstrauth.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for ClientIdUtil.stripOrgPrefix.
 */
public class ClientIdUtilTest {

    @Test
    void stripOrgPrefix_withValidUuidPrefix_returnsStripped() {
        String clientId = "550e8400-e29b-41d4-a716-446655440000__myapp";
        assertEquals("myapp", ClientIdUtil.stripOrgPrefix(clientId));
    }

    @Test
    void stripOrgPrefix_withValidUuidPrefixAndLongSuffix_returnsStripped() {
        String clientId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890__my-service-app";
        assertEquals("my-service-app", ClientIdUtil.stripOrgPrefix(clientId));
    }

    @Test
    void stripOrgPrefix_withoutPrefix_returnsOriginal() {
        String clientId = "myapp";
        assertEquals("myapp", ClientIdUtil.stripOrgPrefix(clientId));
    }

    @Test
    void stripOrgPrefix_withUnderscoreButNoUuid_returnsOriginal() {
        String clientId = "org__myapp";
        assertEquals("org__myapp", ClientIdUtil.stripOrgPrefix(clientId));
    }

    @Test
    void stripOrgPrefix_withSingleUnderscoreAfterUuid_returnsOriginal() {
        String clientId = "550e8400-e29b-41d4-a716-446655440000_myapp";
        assertEquals("550e8400-e29b-41d4-a716-446655440000_myapp", ClientIdUtil.stripOrgPrefix(clientId));
    }

    @Test
    void stripOrgPrefix_withInvalidUuidFormat_returnsOriginal() {
        String clientId = "not-a-uuid__myapp";
        assertEquals("not-a-uuid__myapp", ClientIdUtil.stripOrgPrefix(clientId));
    }

    @Test
    void stripOrgPrefix_withUuidButNoSuffix_returnsOriginal() {
        String clientId = "550e8400-e29b-41d4-a716-446655440000__";
        assertEquals("550e8400-e29b-41d4-a716-446655440000__", ClientIdUtil.stripOrgPrefix(clientId));
    }

    @Test
    void stripOrgPrefix_exactly38Chars_noSuffix_returnsOriginal() {
        String clientId = "550e8400-e29b-41d4-a716-446655440000__";
        assertEquals(clientId, ClientIdUtil.stripOrgPrefix(clientId));
    }

    @Test
    void stripOrgPrefix_null_returnsNull() {
        assertNull(ClientIdUtil.stripOrgPrefix(null));
    }

    @Test
    void stripOrgPrefix_emptyString_returnsOriginal() {
        assertEquals("", ClientIdUtil.stripOrgPrefix(""));
    }

    @Test
    void stripOrgPrefix_uppercaseUuidPrefix_returnsStripped() {
        String clientId = "550E8400-E29B-41D4-A716-446655440000__MYAPP";
        assertEquals("MYAPP", ClientIdUtil.stripOrgPrefix(clientId));
    }
}
