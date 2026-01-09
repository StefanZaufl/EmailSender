package at.klickmagiesoftware.emailsender.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EmailSenderConstants utility class.
 */
class EmailSenderConstantsTest {

    // Email validation tests

    @ParameterizedTest
    @ValueSource(strings = {
            "test@example.com",
            "user.name@domain.org",
            "user+tag@example.co.uk",
            "firstname.lastname@company.com",
            "email@subdomain.domain.com",
            "123@numbers.com",
            "email@domain.name"
    })
    void isValidEmail_validEmails_returnsTrue(String email) {
        assertTrue(EmailSenderConstants.isValidEmail(email),
                "Expected '" + email + "' to be valid");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "notanemail",
            "user@",
            "@example.com",
            "user@.com",
            "user@domain",
            "",
            "   ",
            "user name@domain.com",
            "user@domain..com"
    })
    void isValidEmail_invalidEmails_returnsFalse(String email) {
        assertFalse(EmailSenderConstants.isValidEmail(email),
                "Expected '" + email + "' to be invalid");
    }

    @Test
    void isValidEmail_null_returnsFalse() {
        assertFalse(EmailSenderConstants.isValidEmail(null));
    }

    @Test
    void isValidEmail_emailWithWhitespace_handlesCorrectly() {
        // With leading/trailing whitespace - should still validate after trim
        assertTrue(EmailSenderConstants.isValidEmail("  test@example.com  "));
    }

    // XML escaping tests

    @Test
    void escapeXml_ampersand_escaped() {
        assertEquals("John &amp; Jane", EmailSenderConstants.escapeXml("John & Jane"));
    }

    @Test
    void escapeXml_lessThan_escaped() {
        assertEquals("value &lt; 10", EmailSenderConstants.escapeXml("value < 10"));
    }

    @Test
    void escapeXml_greaterThan_escaped() {
        assertEquals("value &gt; 5", EmailSenderConstants.escapeXml("value > 5"));
    }

    @Test
    void escapeXml_doubleQuote_escaped() {
        assertEquals("say &quot;hello&quot;", EmailSenderConstants.escapeXml("say \"hello\""));
    }

    @Test
    void escapeXml_singleQuote_escaped() {
        assertEquals("it&apos;s working", EmailSenderConstants.escapeXml("it's working"));
    }

    @Test
    void escapeXml_multipleSpecialChars_allEscaped() {
        String input = "Tom & Jerry <Co> said \"it's great\"";
        String expected = "Tom &amp; Jerry &lt;Co&gt; said &quot;it&apos;s great&quot;";
        assertEquals(expected, EmailSenderConstants.escapeXml(input));
    }

    @Test
    void escapeXml_noSpecialChars_unchanged() {
        String input = "Simple text with no special characters";
        assertEquals(input, EmailSenderConstants.escapeXml(input));
    }

    @Test
    void escapeXml_null_returnsEmpty() {
        assertEquals("", EmailSenderConstants.escapeXml(null));
    }

    @Test
    void escapeXml_emptyString_returnsEmpty() {
        assertEquals("", EmailSenderConstants.escapeXml(""));
    }

    // Placeholder pattern tests

    @Test
    void placeholderPattern_matchesSimplePlaceholder() {
        Matcher matcher = EmailSenderConstants.PLACEHOLDER_PATTERN.matcher("Hello {{name}}!");
        assertTrue(matcher.find());
        assertEquals("{{name}}", matcher.group(0));
        assertEquals("name", matcher.group(1));
    }

    @Test
    void placeholderPattern_matchesMultiplePlaceholders() {
        Matcher matcher = EmailSenderConstants.PLACEHOLDER_PATTERN.matcher("{{firstName}} {{lastName}}");
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        assertEquals(2, count);
    }

    @Test
    void placeholderPattern_matchesWithUnderscore() {
        Matcher matcher = EmailSenderConstants.PLACEHOLDER_PATTERN.matcher("{{first_name}}");
        assertTrue(matcher.find());
        assertEquals("first_name", matcher.group(1));
    }

    @Test
    void placeholderPattern_matchesWithNumbers() {
        Matcher matcher = EmailSenderConstants.PLACEHOLDER_PATTERN.matcher("{{field123}}");
        assertTrue(matcher.find());
        assertEquals("field123", matcher.group(1));
    }

    @Test
    void placeholderPattern_doesNotMatchHyphenated() {
        // Note: Hyphens are not supported in the current pattern
        Matcher matcher = EmailSenderConstants.PLACEHOLDER_PATTERN.matcher("{{first-name}}");
        assertFalse(matcher.find());
    }

    @Test
    void placeholderPattern_doesNotMatchEmpty() {
        Matcher matcher = EmailSenderConstants.PLACEHOLDER_PATTERN.matcher("{{}}");
        assertFalse(matcher.find());
    }
}
