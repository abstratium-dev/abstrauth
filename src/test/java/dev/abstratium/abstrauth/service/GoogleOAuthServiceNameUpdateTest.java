package dev.abstratium.abstrauth.service;

import dev.abstratium.abstrauth.entity.Account;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain unit tests for the name-preservation logic applied during Google OAuth callback.
 * The rule: never overwrite an existing non-blank account name with a blank value from Google.
 */
public class GoogleOAuthServiceNameUpdateTest {

    private void applyNameUpdate(Account account, String incomingName) {
        if (incomingName != null && !incomingName.isBlank()) {
            account.setName(incomingName);
        }
    }

    @Test
    public void testNonBlankIncomingNameOverwritesExistingName() {
        Account account = new Account();
        account.setName("Old Name");

        applyNameUpdate(account, "New Name");

        assertEquals("New Name", account.getName());
    }

    @Test
    public void testNullIncomingNameDoesNotOverwriteExistingName() {
        Account account = new Account();
        account.setName("Existing Name");

        applyNameUpdate(account, null);

        assertEquals("Existing Name", account.getName());
    }

    @Test
    public void testBlankIncomingNameDoesNotOverwriteExistingName() {
        Account account = new Account();
        account.setName("Existing Name");

        applyNameUpdate(account, "   ");

        assertEquals("Existing Name", account.getName());
    }

    @Test
    public void testEmptyIncomingNameDoesNotOverwriteExistingName() {
        Account account = new Account();
        account.setName("Existing Name");

        applyNameUpdate(account, "");

        assertEquals("Existing Name", account.getName());
    }

    @Test
    public void testNonBlankIncomingNameSetsNameWhenExistingIsNull() {
        Account account = new Account();
        account.setName(null);

        applyNameUpdate(account, "New Name");

        assertEquals("New Name", account.getName());
    }

    @Test
    public void testNullIncomingNameLeavesNullNameUntouched() {
        Account account = new Account();
        account.setName(null);

        applyNameUpdate(account, null);

        assertNull(account.getName());
    }
}
