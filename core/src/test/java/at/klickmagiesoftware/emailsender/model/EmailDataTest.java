package at.klickmagiesoftware.emailsender.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EmailDataTest {

    @Test
    void constructor_setsAllFields() {
        // Arrange
        String email = "test@example.com";
        Map<String, String> fields = new HashMap<>();
        fields.put("name", "John");
        fields.put("company", "Acme");
        int rowNumber = 5;

        // Act
        EmailData emailData = new EmailData(email, fields, rowNumber);

        // Assert
        assertEquals(email, emailData.getRecipientEmail());
        assertEquals(rowNumber, emailData.getRowNumber());
        assertEquals("John", emailData.getField("name"));
        assertEquals("Acme", emailData.getField("company"));
    }

    @Test
    void getFields_returnsCopyOfMap() {
        // Arrange
        Map<String, String> fields = new HashMap<>();
        fields.put("name", "John");
        EmailData emailData = new EmailData("test@example.com", fields, 1);

        // Act
        Map<String, String> returnedFields = emailData.getFields();
        returnedFields.put("name", "Modified");

        // Assert - original should be unchanged
        assertEquals("John", emailData.getField("name"));
    }

    @Test
    void constructor_createsCopyOfFieldsMap() {
        // Arrange
        Map<String, String> fields = new HashMap<>();
        fields.put("name", "John");
        EmailData emailData = new EmailData("test@example.com", fields, 1);

        // Act - modify original map
        fields.put("name", "Modified");

        // Assert - EmailData should be unchanged
        assertEquals("John", emailData.getField("name"));
    }

    @Test
    void getField_existingField_returnsValue() {
        // Arrange
        Map<String, String> fields = new HashMap<>();
        fields.put("name", "John");
        EmailData emailData = new EmailData("test@example.com", fields, 1);

        // Act & Assert
        assertEquals("John", emailData.getField("name"));
    }

    @Test
    void getField_nonExistingField_returnsNull() {
        // Arrange
        Map<String, String> fields = new HashMap<>();
        fields.put("name", "John");
        EmailData emailData = new EmailData("test@example.com", fields, 1);

        // Act & Assert
        assertNull(emailData.getField("nonexistent"));
    }

    @Test
    void getFields_emptyMap_returnsEmptyMap() {
        // Arrange
        Map<String, String> fields = new HashMap<>();
        EmailData emailData = new EmailData("test@example.com", fields, 1);

        // Act & Assert
        assertTrue(emailData.getFields().isEmpty());
    }

    @Test
    void toString_containsAllInformation() {
        // Arrange
        Map<String, String> fields = new HashMap<>();
        fields.put("name", "John");
        EmailData emailData = new EmailData("test@example.com", fields, 5);

        // Act
        String result = emailData.toString();

        // Assert
        assertTrue(result.contains("test@example.com"));
        assertTrue(result.contains("5"));
        assertTrue(result.contains("name"));
        assertTrue(result.contains("John"));
    }

    @Test
    void getRecipientEmail_returnsCorrectValue() {
        // Arrange
        EmailData emailData = new EmailData("recipient@test.com", new HashMap<>(), 1);

        // Act & Assert
        assertEquals("recipient@test.com", emailData.getRecipientEmail());
    }

    @Test
    void getRowNumber_returnsCorrectValue() {
        // Arrange
        EmailData emailData = new EmailData("test@example.com", new HashMap<>(), 42);

        // Act & Assert
        assertEquals(42, emailData.getRowNumber());
    }

    @Test
    void fields_withSpecialCharacters_handledCorrectly() {
        // Arrange
        Map<String, String> fields = new HashMap<>();
        fields.put("message", "Hello, \"World\"!");
        fields.put("amount", "$1,234.56");
        fields.put("notes", "Line1\nLine2");
        EmailData emailData = new EmailData("test@example.com", fields, 1);

        // Act & Assert
        assertEquals("Hello, \"World\"!", emailData.getField("message"));
        assertEquals("$1,234.56", emailData.getField("amount"));
        assertEquals("Line1\nLine2", emailData.getField("notes"));
    }

    // ==================== Multi-Recipient Tests ====================

    @Test
    void constructorWithList_setsMultipleRecipients() {
        // Arrange
        List<String> emails = List.of("john@example.com", "jane@example.com", "bob@example.com");
        Map<String, String> fields = new HashMap<>();
        fields.put("name", "Team");

        // Act
        EmailData emailData = new EmailData(emails, fields, 1);

        // Assert
        assertEquals(3, emailData.getRecipientEmails().size());
        assertEquals("john@example.com", emailData.getRecipientEmails().get(0));
        assertEquals("jane@example.com", emailData.getRecipientEmails().get(1));
        assertEquals("bob@example.com", emailData.getRecipientEmails().get(2));
    }

    @Test
    void getRecipientEmail_withMultipleRecipients_returnsFirst() {
        // Arrange
        List<String> emails = List.of("first@example.com", "second@example.com");
        EmailData emailData = new EmailData(emails, new HashMap<>(), 1);

        // Act & Assert
        assertEquals("first@example.com", emailData.getRecipientEmail());
    }

    @Test
    void hasMultipleRecipients_withSingleRecipient_returnsFalse() {
        // Arrange
        EmailData emailData = new EmailData("single@example.com", new HashMap<>(), 1);

        // Act & Assert
        assertFalse(emailData.hasMultipleRecipients());
    }

    @Test
    void hasMultipleRecipients_withMultipleRecipients_returnsTrue() {
        // Arrange
        List<String> emails = List.of("john@example.com", "jane@example.com");
        EmailData emailData = new EmailData(emails, new HashMap<>(), 1);

        // Act & Assert
        assertTrue(emailData.hasMultipleRecipients());
    }

    @Test
    void getRecipientsAsString_singleRecipient_returnsEmail() {
        // Arrange
        EmailData emailData = new EmailData("single@example.com", new HashMap<>(), 1);

        // Act & Assert
        assertEquals("single@example.com", emailData.getRecipientsAsString());
    }

    @Test
    void getRecipientsAsString_multipleRecipients_returnsCommaSeparated() {
        // Arrange
        List<String> emails = List.of("john@example.com", "jane@example.com", "bob@example.com");
        EmailData emailData = new EmailData(emails, new HashMap<>(), 1);

        // Act & Assert
        assertEquals("john@example.com, jane@example.com, bob@example.com", emailData.getRecipientsAsString());
    }

    @Test
    void getRecipientEmails_returnsCopyOfList() {
        // Arrange
        List<String> emails = List.of("john@example.com", "jane@example.com");
        EmailData emailData = new EmailData(emails, new HashMap<>(), 1);

        // Act
        List<String> returnedEmails = emailData.getRecipientEmails();
        returnedEmails.add("new@example.com"); // Modify the returned list

        // Assert - original should be unchanged
        assertEquals(2, emailData.getRecipientEmails().size());
        assertEquals("john@example.com", emailData.getRecipientEmails().get(0));
        assertEquals("jane@example.com", emailData.getRecipientEmails().get(1));
    }

    @Test
    void toString_multipleRecipients_containsAllEmails() {
        // Arrange
        List<String> emails = List.of("john@example.com", "jane@example.com");
        Map<String, String> fields = new HashMap<>();
        fields.put("name", "Team");
        EmailData emailData = new EmailData(emails, fields, 5);

        // Act
        String result = emailData.toString();

        // Assert
        assertTrue(result.contains("john@example.com"));
        assertTrue(result.contains("jane@example.com"));
        assertTrue(result.contains("5"));
    }
}
