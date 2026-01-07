package com.yourcompany.emailsender.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmailSenderExceptionTest {

    @Test
    void constructor_withMessage_setsMessage() {
        // Arrange & Act
        EmailSenderException exception = new EmailSenderException("Test error message");

        // Assert
        assertEquals("Test error message", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void constructor_withMessageAndCause_setsBoth() {
        // Arrange
        RuntimeException cause = new RuntimeException("Original error");

        // Act
        EmailSenderException exception = new EmailSenderException("Wrapped error", cause);

        // Assert
        assertEquals("Wrapped error", exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertEquals("Original error", exception.getCause().getMessage());
    }

    @Test
    void exception_isRuntimeException() {
        // Arrange & Act
        EmailSenderException exception = new EmailSenderException("Test");

        // Assert
        assertTrue(exception instanceof RuntimeException);
    }

    @Test
    void exception_canBeThrown() {
        // Act & Assert
        assertThrows(EmailSenderException.class, () -> {
            throw new EmailSenderException("Test exception");
        });
    }

    @Test
    void exception_withNullMessage_allowsNull() {
        // Arrange & Act
        EmailSenderException exception = new EmailSenderException(null);

        // Assert
        assertNull(exception.getMessage());
    }

    @Test
    void exception_chainedCause_preservesStackTrace() {
        // Arrange
        IllegalArgumentException rootCause = new IllegalArgumentException("Root cause");
        RuntimeException intermediateCause = new RuntimeException("Intermediate", rootCause);

        // Act
        EmailSenderException exception = new EmailSenderException("Top level", intermediateCause);

        // Assert
        assertEquals(intermediateCause, exception.getCause());
        assertEquals(rootCause, exception.getCause().getCause());
    }
}
