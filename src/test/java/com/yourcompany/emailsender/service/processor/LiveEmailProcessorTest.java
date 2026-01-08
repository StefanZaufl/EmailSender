package com.yourcompany.emailsender.service.processor;

import com.yourcompany.emailsender.model.EmailData;
import com.yourcompany.emailsender.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LiveEmailProcessorTest {

    private StubEmailService stubEmailService;
    private LiveEmailProcessor processor;

    @BeforeEach
    void setUp() {
        stubEmailService = new StubEmailService();
        processor = new LiveEmailProcessor(stubEmailService);
    }

    @Test
    void getModeName_returnsLive() {
        assertEquals("LIVE", processor.getModeName());
    }

    @Test
    void getCompletionMessage_returnsNull() {
        assertNull(processor.getCompletionMessage());
    }

    @Test
    void process_callsEmailServiceSendEmail() {
        // Arrange
        Map<String, String> fields = new HashMap<>();
        fields.put("name", "John Doe");
        EmailData emailData = new EmailData("john@example.com", fields, 1);

        // Act
        processor.process(emailData);

        // Assert
        assertTrue(stubEmailService.wasSendEmailCalled());
        assertEquals(1, stubEmailService.getSendEmailCallCount());
    }

    @Test
    void process_passesCorrectEmailData() {
        // Arrange
        Map<String, String> fields = new HashMap<>();
        fields.put("name", "Jane Smith");
        fields.put("company", "Tech Corp");
        EmailData emailData = new EmailData("jane@example.com", fields, 5);

        // Act
        processor.process(emailData);

        // Assert
        EmailData sentData = stubEmailService.getLastSentEmailData();
        assertNotNull(sentData);
        assertEquals("jane@example.com", sentData.getRecipientEmail());
        assertEquals(5, sentData.getRowNumber());
        assertEquals("Jane Smith", sentData.getField("name"));
        assertEquals("Tech Corp", sentData.getField("company"));
    }

    @Test
    void process_propagatesExceptions() {
        // Arrange
        stubEmailService.setThrowOnSend(new RuntimeException("Email send failed"));

        Map<String, String> fields = new HashMap<>();
        EmailData emailData = new EmailData("error@example.com", fields, 1);

        // Act & Assert
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> processor.process(emailData));
        assertEquals("Email send failed", thrown.getMessage());
    }

    @Test
    void initialize_doesNothing() {
        // Default implementation should not throw
        assertDoesNotThrow(() -> processor.initialize());
    }

    @Test
    void implementsEmailProcessingStrategy() {
        assertTrue(processor instanceof EmailProcessingStrategy);
    }

    @Test
    void process_multipleEmails_callsSendEmailMultipleTimes() {
        // Arrange
        EmailData emailData1 = new EmailData("first@example.com", new HashMap<>(), 1);
        EmailData emailData2 = new EmailData("second@example.com", new HashMap<>(), 2);
        EmailData emailData3 = new EmailData("third@example.com", new HashMap<>(), 3);

        // Act
        processor.process(emailData1);
        processor.process(emailData2);
        processor.process(emailData3);

        // Assert
        assertEquals(3, stubEmailService.getSendEmailCallCount());
        assertEquals("third@example.com", stubEmailService.getLastSentEmailData().getRecipientEmail());
    }

    /**
     * Stub implementation of EmailService for testing.
     */
    private static class StubEmailService extends EmailService {
        private EmailData lastSentEmailData;
        private int sendEmailCallCount = 0;
        private RuntimeException throwOnSend;

        StubEmailService() {
            super(null, null, null, null);
        }

        void setThrowOnSend(RuntimeException exception) {
            this.throwOnSend = exception;
        }

        @Override
        public void sendEmail(EmailData emailData) {
            if (throwOnSend != null) {
                throw throwOnSend;
            }
            this.lastSentEmailData = emailData;
            this.sendEmailCallCount++;
        }

        @Override
        public EmailContent prepareEmail(EmailData emailData) {
            return new EmailContent(
                    emailData.getRecipientEmail(),
                    "Subject",
                    "<html></html>",
                    new byte[]{},
                    emailData.getRowNumber()
            );
        }

        EmailData getLastSentEmailData() {
            return lastSentEmailData;
        }

        boolean wasSendEmailCalled() {
            return sendEmailCallCount > 0;
        }

        int getSendEmailCallCount() {
            return sendEmailCallCount;
        }
    }
}
