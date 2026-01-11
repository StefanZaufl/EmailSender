package at.klickmagiesoftware.emailsender.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EmailAddressService.
 */
class EmailAddressServiceTest {

    private EmailAddressService emailAddressService;

    @BeforeEach
    void setUp() {
        emailAddressService = new EmailAddressService();
    }

    // ==================== isValidEmail Tests ====================

    @Test
    void isValidEmail_validEmail_returnsTrue() {
        assertTrue(emailAddressService.isValidEmail("test@example.com"));
        assertTrue(emailAddressService.isValidEmail("user.name@example.com"));
        assertTrue(emailAddressService.isValidEmail("user+tag@example.com"));
        assertTrue(emailAddressService.isValidEmail("user@sub.example.com"));
        assertTrue(emailAddressService.isValidEmail("user@example.co.uk"));
    }

    @Test
    void isValidEmail_invalidEmail_returnsFalse() {
        assertFalse(emailAddressService.isValidEmail("notanemail"));
        assertFalse(emailAddressService.isValidEmail("missing@domain"));
        assertFalse(emailAddressService.isValidEmail("@nodomain.com"));
        assertFalse(emailAddressService.isValidEmail("spaces in@email.com"));
        assertFalse(emailAddressService.isValidEmail("user@.com"));
    }

    @Test
    void isValidEmail_nullOrBlank_returnsFalse() {
        assertFalse(emailAddressService.isValidEmail(null));
        assertFalse(emailAddressService.isValidEmail(""));
        assertFalse(emailAddressService.isValidEmail("   "));
    }

    @Test
    void isValidEmail_withWhitespace_trimsAndValidates() {
        assertTrue(emailAddressService.isValidEmail("  test@example.com  "));
    }

    // ==================== parseAndValidateRecipients Tests ====================

    @Test
    void parseAndValidateRecipients_singleValidEmail_returnsListWithOneEmail() {
        List<String> result = emailAddressService.parseAndValidateRecipients("test@example.com", 1);

        assertEquals(1, result.size());
        assertEquals("test@example.com", result.get(0));
    }

    @Test
    void parseAndValidateRecipients_multipleValidEmails_returnsAllEmails() {
        String input = "john@example.com;jane@example.com;bob@example.com";
        List<String> result = emailAddressService.parseAndValidateRecipients(input, 1);

        assertEquals(3, result.size());
        assertEquals("john@example.com", result.get(0));
        assertEquals("jane@example.com", result.get(1));
        assertEquals("bob@example.com", result.get(2));
    }

    @Test
    void parseAndValidateRecipients_mixedValidAndInvalid_returnsOnlyValidEmails() {
        String input = "valid@example.com;notanemail;another@example.com";
        List<String> result = emailAddressService.parseAndValidateRecipients(input, 1);

        assertEquals(2, result.size());
        assertEquals("valid@example.com", result.get(0));
        assertEquals("another@example.com", result.get(1));
    }

    @Test
    void parseAndValidateRecipients_allInvalidEmails_returnsEmptyList() {
        String input = "notanemail;alsoinvalid;stillnot";
        List<String> result = emailAddressService.parseAndValidateRecipients(input, 1);

        assertTrue(result.isEmpty());
    }

    @Test
    void parseAndValidateRecipients_withWhitespace_trimsEmails() {
        String input = " john@example.com ; jane@example.com ; bob@example.com ";
        List<String> result = emailAddressService.parseAndValidateRecipients(input, 1);

        assertEquals(3, result.size());
        assertEquals("john@example.com", result.get(0));
        assertEquals("jane@example.com", result.get(1));
        assertEquals("bob@example.com", result.get(2));
    }

    @Test
    void parseAndValidateRecipients_withEmptyEntries_skipsEmptyEntries() {
        String input = "john@example.com;;bob@example.com;";
        List<String> result = emailAddressService.parseAndValidateRecipients(input, 1);

        assertEquals(2, result.size());
        assertEquals("john@example.com", result.get(0));
        assertEquals("bob@example.com", result.get(1));
    }

    @Test
    void parseAndValidateRecipients_nullInput_returnsEmptyList() {
        List<String> result = emailAddressService.parseAndValidateRecipients(null, 1);

        assertTrue(result.isEmpty());
    }

    @Test
    void parseAndValidateRecipients_blankInput_returnsEmptyList() {
        List<String> result = emailAddressService.parseAndValidateRecipients("   ", 1);

        assertTrue(result.isEmpty());
    }

    @Test
    void parseAndValidateRecipients_emptyInput_returnsEmptyList() {
        List<String> result = emailAddressService.parseAndValidateRecipients("", 1);

        assertTrue(result.isEmpty());
    }

    // ==================== parseAndValidateRecipients (without row number) Tests ====================

    @Test
    void parseAndValidateRecipientsNoRow_singleValidEmail_returnsListWithOneEmail() {
        List<String> result = emailAddressService.parseAndValidateRecipients("test@example.com");

        assertEquals(1, result.size());
        assertEquals("test@example.com", result.get(0));
    }

    @Test
    void parseAndValidateRecipientsNoRow_multipleValidEmails_returnsAllEmails() {
        String input = "john@example.com;jane@example.com";
        List<String> result = emailAddressService.parseAndValidateRecipients(input);

        assertEquals(2, result.size());
        assertEquals("john@example.com", result.get(0));
        assertEquals("jane@example.com", result.get(1));
    }

    @Test
    void parseAndValidateRecipientsNoRow_nullInput_returnsEmptyList() {
        List<String> result = emailAddressService.parseAndValidateRecipients(null);

        assertTrue(result.isEmpty());
    }
}
