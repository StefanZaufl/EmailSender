package at.klickmagiesoftware.emailsender.util;

import java.util.regex.Pattern;

/**
 * Shared constants and utility methods for template processing.
 * For email address validation and parsing, use {@link at.klickmagiesoftware.emailsender.service.EmailAddressService}.
 */
public final class EmailSenderConstants {

    private EmailSenderConstants() {
        // Utility class - prevent instantiation
    }

    /**
     * Pattern for matching {{placeholder}} syntax in templates.
     * Supports word characters (a-z, A-Z, 0-9, _), German special characters (äöüÄÖÜß), and hyphens (-).
     */
    public static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([\\w\\-äöüÄÖÜß]+)}}");

    /**
     * Pattern for matching XML tags (used for stripping tags from placeholders).
     */
    public static final Pattern XML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    /**
     * Pattern for matching placeholders that may be interrupted by XML tags.
     * This matches {{ followed by any content (including XML tags) until }}.
     */
    public static final Pattern INTERRUPTED_PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([^}]*?)}}");

    /**
     * Strips XML tags and whitespace from a string.
     * Used to clean up placeholders that may be interrupted by formatting tags.
     * Also removes any whitespace/newlines that may appear between XML elements.
     *
     * @param input the string potentially containing XML tags
     * @return the string with all XML tags and whitespace removed
     */
    public static String stripXmlTags(String input) {
        if (input == null) {
            return "";
        }
        // First remove XML tags, then remove all whitespace (spaces, tabs, newlines)
        String withoutTags = XML_TAG_PATTERN.matcher(input).replaceAll("");
        return withoutTags.replaceAll("\\s+", "");
    }

    /**
     * Checks if a field name contains only valid placeholder characters.
     * Valid characters are: word characters (a-z, A-Z, 0-9, _), German special characters (äöüÄÖÜß), and hyphens (-).
     *
     * @param fieldName the field name to validate
     * @return true if the field name contains only valid characters
     */
    public static boolean isValidPlaceholderFieldName(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return false;
        }
        return fieldName.matches("[\\w\\-äöüÄÖÜß]+");
    }

    /**
     * Escapes special XML characters to prevent XML injection.
     * Escapes: &amp; &lt; &gt; &quot; &apos;
     *
     * @param input the string to escape
     * @return the escaped string, or empty string if input is null
     */
    public static String escapeXml(String input) {
        if (input == null) {
            return "";
        }
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
