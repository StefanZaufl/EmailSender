package com.yourcompany.emailsender.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
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
}
