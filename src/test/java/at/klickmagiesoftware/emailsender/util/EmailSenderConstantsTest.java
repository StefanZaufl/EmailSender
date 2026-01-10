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
    void placeholderPattern_matchesHyphenated() {
        Matcher matcher = EmailSenderConstants.PLACEHOLDER_PATTERN.matcher("{{first-name}}");
        assertTrue(matcher.find());
        assertEquals("first-name", matcher.group(1));
    }

    @Test
    void placeholderPattern_matchesGermanCharacters() {
        Matcher matcher = EmailSenderConstants.PLACEHOLDER_PATTERN.matcher("{{größe}}");
        assertTrue(matcher.find());
        assertEquals("größe", matcher.group(1));
    }

    @Test
    void placeholderPattern_matchesGermanUmlauts() {
        Matcher matcher = EmailSenderConstants.PLACEHOLDER_PATTERN.matcher("{{Größe_Änderung}}");
        assertTrue(matcher.find());
        assertEquals("Größe_Änderung", matcher.group(1));
    }

    @Test
    void placeholderPattern_matchesAllGermanSpecialChars() {
        // Test all German special characters: ä, ö, ü, Ä, Ö, Ü, ß
        Matcher matcher = EmailSenderConstants.PLACEHOLDER_PATTERN.matcher("{{äöüÄÖÜß}}");
        assertTrue(matcher.find());
        assertEquals("äöüÄÖÜß", matcher.group(1));
    }

    @Test
    void placeholderPattern_matchesHyphenWithGermanChars() {
        Matcher matcher = EmailSenderConstants.PLACEHOLDER_PATTERN.matcher("{{vor-name_größe}}");
        assertTrue(matcher.find());
        assertEquals("vor-name_größe", matcher.group(1));
    }

    @Test
    void placeholderPattern_doesNotMatchEmpty() {
        Matcher matcher = EmailSenderConstants.PLACEHOLDER_PATTERN.matcher("{{}}");
        assertFalse(matcher.find());
    }

    // XML tag stripping tests

    @Test
    void stripXmlTags_removesSimpleTag() {
        assertEquals("firstname", EmailSenderConstants.stripXmlTags("first<b>name"));
    }

    @Test
    void stripXmlTags_removesMultipleTags() {
        assertEquals("first_name", EmailSenderConstants.stripXmlTags("first<b>_</b>name"));
    }

    @Test
    void stripXmlTags_removesComplexTags() {
        assertEquals("field_name", EmailSenderConstants.stripXmlTags("field<w:r><w:t>_name</w:t></w:r>"));
    }

    @Test
    void stripXmlTags_preservesTextWithoutTags() {
        assertEquals("simple_field", EmailSenderConstants.stripXmlTags("simple_field"));
    }

    @Test
    void stripXmlTags_handlesNull() {
        assertEquals("", EmailSenderConstants.stripXmlTags(null));
    }

    @Test
    void stripXmlTags_handlesEmptyString() {
        assertEquals("", EmailSenderConstants.stripXmlTags(""));
    }

    @Test
    void stripXmlTags_removesTagsWithAttributes() {
        assertEquals("test_value", EmailSenderConstants.stripXmlTags("test<span class=\"bold\">_value</span>"));
    }

    @Test
    void stripXmlTags_removesWhitespaceAndNewlines() {
        // Simulates what docx4j outputs with newlines between XML elements
        String input = "first</w:t>\n    </w:r>\n    <w:r>\n      <w:t>_</w:t>\n    </w:r>\n    <w:r>\n      <w:t>name";
        assertEquals("first_name", EmailSenderConstants.stripXmlTags(input));
    }

    @Test
    void stripXmlTags_removesTabsAndSpaces() {
        assertEquals("first_name", EmailSenderConstants.stripXmlTags("first\t<b>\n_\t</b> name"));
    }

    // isValidPlaceholderFieldName tests

    @Test
    void isValidPlaceholderFieldName_simpleFieldName_valid() {
        assertTrue(EmailSenderConstants.isValidPlaceholderFieldName("name"));
    }

    @Test
    void isValidPlaceholderFieldName_withUnderscore_valid() {
        assertTrue(EmailSenderConstants.isValidPlaceholderFieldName("first_name"));
    }

    @Test
    void isValidPlaceholderFieldName_withHyphen_valid() {
        assertTrue(EmailSenderConstants.isValidPlaceholderFieldName("first-name"));
    }

    @Test
    void isValidPlaceholderFieldName_withNumbers_valid() {
        assertTrue(EmailSenderConstants.isValidPlaceholderFieldName("field123"));
    }

    @Test
    void isValidPlaceholderFieldName_withGermanChars_valid() {
        assertTrue(EmailSenderConstants.isValidPlaceholderFieldName("größe"));
        assertTrue(EmailSenderConstants.isValidPlaceholderFieldName("Änderung"));
        assertTrue(EmailSenderConstants.isValidPlaceholderFieldName("übung"));
        assertTrue(EmailSenderConstants.isValidPlaceholderFieldName("Öffnung"));
        assertTrue(EmailSenderConstants.isValidPlaceholderFieldName("Ärger"));
        assertTrue(EmailSenderConstants.isValidPlaceholderFieldName("fußball"));
    }

    @Test
    void isValidPlaceholderFieldName_withMixedChars_valid() {
        assertTrue(EmailSenderConstants.isValidPlaceholderFieldName("vor-name_größe"));
    }

    @Test
    void isValidPlaceholderFieldName_empty_invalid() {
        assertFalse(EmailSenderConstants.isValidPlaceholderFieldName(""));
    }

    @Test
    void isValidPlaceholderFieldName_null_invalid() {
        assertFalse(EmailSenderConstants.isValidPlaceholderFieldName(null));
    }

    @Test
    void isValidPlaceholderFieldName_withSpaces_invalid() {
        assertFalse(EmailSenderConstants.isValidPlaceholderFieldName("first name"));
    }

    @Test
    void isValidPlaceholderFieldName_withSpecialChars_invalid() {
        assertFalse(EmailSenderConstants.isValidPlaceholderFieldName("field@name"));
        assertFalse(EmailSenderConstants.isValidPlaceholderFieldName("field#name"));
        assertFalse(EmailSenderConstants.isValidPlaceholderFieldName("field.name"));
    }

    // Interrupted placeholder pattern tests

    @Test
    void interruptedPlaceholderPattern_matchesCleanPlaceholder() {
        Matcher matcher = EmailSenderConstants.INTERRUPTED_PLACEHOLDER_PATTERN.matcher("{{name}}");
        assertTrue(matcher.find());
        assertEquals("{{name}}", matcher.group(0));
        assertEquals("name", matcher.group(1));
    }

    @Test
    void interruptedPlaceholderPattern_matchesWithXmlTags() {
        Matcher matcher = EmailSenderConstants.INTERRUPTED_PLACEHOLDER_PATTERN.matcher("{{first<b>_name}}");
        assertTrue(matcher.find());
        assertEquals("{{first<b>_name}}", matcher.group(0));
        assertEquals("first<b>_name", matcher.group(1));
    }

    @Test
    void interruptedPlaceholderPattern_matchesComplexXmlTags() {
        String input = "{{first<w:r><w:t>_</w:t></w:r>name}}";
        Matcher matcher = EmailSenderConstants.INTERRUPTED_PLACEHOLDER_PATTERN.matcher(input);
        assertTrue(matcher.find());
        assertEquals(input, matcher.group(0));
        assertEquals("first<w:r><w:t>_</w:t></w:r>name", matcher.group(1));
    }
}
