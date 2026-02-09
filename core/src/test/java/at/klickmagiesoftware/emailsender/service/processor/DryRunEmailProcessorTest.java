package at.klickmagiesoftware.emailsender.service.processor;

import at.klickmagiesoftware.emailsender.model.EmailData;
import at.klickmagiesoftware.emailsender.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
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
    void process_writesAllOutputFiles() {
        // Arrange
        processor.setOutputDirectory(tempDir.toString());

        Map<String, String> fields = new HashMap<>();
        fields.put("name", "John Doe");
        EmailData emailData = new EmailData("john@example.com", fields, 1);

        EmailService.EmailContent content = new EmailService.EmailContent(
                List.of("john@example.com"),
                "Test Subject",
                "<html><body>Test Body</body></html>",
                List.of(new EmailService.AttachmentData(
                        new byte[]{0x25, 0x50, 0x44, 0x46}, // %PDF
                        "report.pdf",
                        "application/pdf"
                )),
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
                List.of("test@example.com"),
                "Subject",
                expectedBody,
                List.of(new EmailService.AttachmentData(
                        new byte[]{},
                        "attachment.pdf",
                        "application/pdf"
                )),
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
                List.of("meta@example.com"),
                "Important Subject",
                "<html></html>",
                List.of(new EmailService.AttachmentData(
                        new byte[]{},
                        "document.pdf",
                        "application/pdf"
                )),
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
        assertTrue(metaContent.contains("document.pdf"));
        assertTrue(metaContent.contains("Row Number: 5"));
        assertTrue(metaContent.contains("Recipient Count: 1"));
        assertTrue(metaContent.contains("Attachment Count: 1"));
    }

    @Test
    void process_noAttachments_writesHtmlAndMetaOnly() throws IOException {
        // Arrange
        processor.setOutputDirectory(tempDir.toString());

        Map<String, String> fields = new HashMap<>();
        fields.put("name", "No Attach User");
        EmailData emailData = new EmailData("noattach@example.com", fields, 3);

        EmailService.EmailContent content = new EmailService.EmailContent(
                List.of("noattach@example.com"),
                "No Attachment Subject",
                "<html><body>No attachments here</body></html>",
                List.of(), // empty attachments
                3
        );
        when(emailService.prepareEmail(any(EmailData.class))).thenReturn(content);

        // Act
        processor.process(emailData);

        // Assert - HTML and meta files should exist
        Path htmlFile = tempDir.resolve("row3_noattach@example.com_body.html");
        Path metaFile = tempDir.resolve("row3_noattach@example.com_meta.txt");
        assertTrue(Files.exists(htmlFile), "HTML file should exist");
        assertTrue(Files.exists(metaFile), "Meta file should exist");

        // Assert - no PDF files should exist
        try (var files = Files.list(tempDir)) {
            long pdfCount = files.filter(p -> p.toString().endsWith(".pdf")).count();
            assertEquals(0, pdfCount, "No PDF files should be created");
        }

        // Assert - meta file should show 0 attachments
        String metaContent = Files.readString(metaFile);
        assertTrue(metaContent.contains("Attachment Count: 0"));
        assertTrue(metaContent.contains("No Attachment Subject"));
    }

    @Test
    void process_sanitizesEmailInFilename() {
        // Arrange
        processor.setOutputDirectory(tempDir.toString());

        Map<String, String> fields = new HashMap<>();
        EmailData emailData = new EmailData("user+tag@example.com", fields, 1);

        EmailService.EmailContent content = new EmailService.EmailContent(
                List.of("user+tag@example.com"),
                "Subject",
                "<html></html>",
                List.of(new EmailService.AttachmentData(
                        new byte[]{},
                        "report.pdf",
                        "application/pdf"
                )),
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
                List.of("pdf@example.com"),
                "Subject",
                "<html></html>",
                List.of(new EmailService.AttachmentData(
                        expectedPdf,
                        "document.pdf",
                        "application/pdf"
                )),
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
