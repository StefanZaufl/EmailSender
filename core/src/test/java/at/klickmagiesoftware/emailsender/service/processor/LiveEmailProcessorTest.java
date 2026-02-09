package at.klickmagiesoftware.emailsender.service.processor;

import at.klickmagiesoftware.emailsender.model.EmailData;
import at.klickmagiesoftware.emailsender.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LiveEmailProcessorTest {

    @Mock
    private EmailService emailService;

    private LiveEmailProcessor processor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        processor = new LiveEmailProcessor(emailService);
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
        verify(emailService, times(1)).sendEmail(emailData);
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
        verify(emailService).sendEmail(argThat(data ->
                data.getRecipientEmail().equals("jane@example.com") &&
                data.getRowNumber() == 5 &&
                data.getField("name").equals("Jane Smith") &&
                data.getField("company").equals("Tech Corp")
        ));
    }

    @Test
    void process_propagatesExceptions() {
        // Arrange
        Map<String, String> fields = new HashMap<>();
        EmailData emailData = new EmailData("error@example.com", fields, 1);

        doThrow(new RuntimeException("Email send failed"))
                .when(emailService).sendEmail(any(EmailData.class));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> processor.process(emailData));
    }

    @Test
    void initialize_doesNothing() {
        // Default implementation should not throw
        assertDoesNotThrow(() -> processor.initialize());
    }

    @Test
    void implementsEmailProcessingStrategy() {
        assertInstanceOf(EmailProcessingStrategy.class, processor);
    }
}
