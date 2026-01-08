package com.yourcompany.emailsender.service.processor;

import com.yourcompany.emailsender.exception.EmailSenderException;
import com.yourcompany.emailsender.model.EmailData;
import com.yourcompany.emailsender.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class DryRunEmailProcessorTest {

    @Mock
    private EmailService emailService;

    private DryRunEmailProcessor processor;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        processor = new DryRunEmailProcessor(emailService);
    }

    @Test
    void getModeName_returnsDryRun() {
        assertEquals("DRY RUN", processor.getModeName());
    }

    @Test
    void getCompletionMessage_containsOutputDirectory() {
        processor.setOutputDirectory("/test/output");
        String message = processor.getCompletionMessage();

        assertNotNull(message);
        assertTrue(message.contains("/test/output"));
    }

    @Test
    void setOutputDirectory_updatesDirectory() {
        processor.setOutputDirectory("/new/path");

        assertEquals(Path.of("/new/path"), processor.getOutputDirectory());
    }

    @Test
    void initialize_createsOutputDirectory() {
        Path outputDir = tempDir.resolve("new-output");
        processor.setOutputDirectory(outputDir.toString());

        assertFalse(Files.exists(outputDir));

        processor.initialize();

        assertTrue(Files.exists(outputDir));
        assertTrue(Files.isDirectory(outputDir));
    }

    @Test
    void initialize_existingDirectory_doesNotFail() {
        processor.setOutputDirectory(tempDir.toString());

        // Should not throw
        assertDoesNotThrow(() -> processor.initialize());
    }

    @Test
    void process_writesAllOutputFiles() throws IOException {
        // Arrange
        processor.setOutputDirectory(tempDir.toString());

        Map<String, String> fields = new HashMap<>();
        fields.put("name", "John Doe");
        EmailData emailData = new EmailData("john@example.com", fields, 1);

        EmailService.EmailContent content = new EmailService.EmailContent(
                "john@example.com",
                "Test Subject",
                "<html><body>Test Body</body></html>",
                new byte[]{0x25, 0x50, 0x44, 0x46}, // %PDF
                1
        );
        when(emailService.prepareEmail(any(EmailData.class))).thenReturn(content);

        // Act
        processor.process(emailData);

        // Assert - check that files were created
        Path htmlFile = tempDir.resolve("row1_john@example.com_body.html");
        Path pdfFile = tempDir.resolve("row1_john@example.com_attachment.pdf");
        Path metaFile = tempDir.resolve("row1_john@example.com_meta.txt");

        assertTrue(Files.exists(htmlFile), "HTML file should exist");
        assertTrue(Files.exists(pdfFile), "PDF file should exist");
        assertTrue(Files.exists(metaFile), "Meta file should exist");
    }

    @Test
    void process_htmlFileContainsBody() throws IOException {
        // Arrange
        processor.setOutputDirectory(tempDir.toString());

        Map<String, String> fields = new HashMap<>();
        EmailData emailData = new EmailData("test@example.com", fields, 1);

        String expectedBody = "<html><body>Hello World</body></html>";
        EmailService.EmailContent content = new EmailService.EmailContent(
                "test@example.com",
                "Subject",
                expectedBody,
                new byte[]{},
                1
        );
        when(emailService.prepareEmail(any(EmailData.class))).thenReturn(content);

        // Act
        processor.process(emailData);

        // Assert
        Path htmlFile = tempDir.resolve("row1_test@example.com_body.html");
        String actualContent = Files.readString(htmlFile);
        assertEquals(expectedBody, actualContent);
    }

    @Test
    void process_metaFileContainsEmailInfo() throws IOException {
        // Arrange
        processor.setOutputDirectory(tempDir.toString());

        Map<String, String> fields = new HashMap<>();
        EmailData emailData = new EmailData("meta@example.com", fields, 5);

        EmailService.EmailContent content = new EmailService.EmailContent(
                "meta@example.com",
                "Important Subject",
                "<html></html>",
                new byte[]{},
                5
        );
        when(emailService.prepareEmail(any(EmailData.class))).thenReturn(content);

        // Act
        processor.process(emailData);

        // Assert
        Path metaFile = tempDir.resolve("row5_meta@example.com_meta.txt");
        String metaContent = Files.readString(metaFile);

        assertTrue(metaContent.contains("meta@example.com"));
        assertTrue(metaContent.contains("Important Subject"));
        assertTrue(metaContent.contains("5"));
    }

    @Test
    void process_sanitizesEmailInFilename() throws IOException {
        // Arrange
        processor.setOutputDirectory(tempDir.toString());

        Map<String, String> fields = new HashMap<>();
        EmailData emailData = new EmailData("user+tag@example.com", fields, 1);

        EmailService.EmailContent content = new EmailService.EmailContent(
                "user+tag@example.com",
                "Subject",
                "<html></html>",
                new byte[]{},
                1
        );
        when(emailService.prepareEmail(any(EmailData.class))).thenReturn(content);

        // Act
        processor.process(emailData);

        // Assert - special characters should be replaced with underscores
        Path htmlFile = tempDir.resolve("row1_user_tag@example.com_body.html");
        assertTrue(Files.exists(htmlFile));
    }

    @Test
    void process_pdfFileContainsAttachmentBytes() throws IOException {
        // Arrange
        processor.setOutputDirectory(tempDir.toString());

        Map<String, String> fields = new HashMap<>();
        EmailData emailData = new EmailData("pdf@example.com", fields, 1);

        byte[] expectedPdf = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D}; // %PDF-
        EmailService.EmailContent content = new EmailService.EmailContent(
                "pdf@example.com",
                "Subject",
                "<html></html>",
                expectedPdf,
                1
        );
        when(emailService.prepareEmail(any(EmailData.class))).thenReturn(content);

        // Act
        processor.process(emailData);

        // Assert
        Path pdfFile = tempDir.resolve("row1_pdf@example.com_attachment.pdf");
        byte[] actualPdf = Files.readAllBytes(pdfFile);
        assertArrayEquals(expectedPdf, actualPdf);
    }
}
