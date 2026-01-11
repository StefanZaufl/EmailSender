package at.klickmagiesoftware.emailsender.service;

import at.klickmagiesoftware.emailsender.config.AppConfig;
import at.klickmagiesoftware.emailsender.exception.EmailSenderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportServiceTest {

    @TempDir
    Path tempDir;

    private AppConfig appConfig;
    private ReportService reportService;

    @BeforeEach
    void setUp() {
        appConfig = createAppConfig();
        reportService = new ReportService(appConfig);
    }

    @Test
    void recordSuccess_addsSuccessfulResult() {
        // Act
        reportService.recordSuccess("test@example.com");

        // Assert
        assertEquals(1, reportService.getResultCount());
        assertEquals(1, reportService.getSuccessCount());
        assertEquals(0, reportService.getFailureCount());

        List<ReportService.EmailResult> results = reportService.getResults();
        assertEquals(1, results.size());
        assertEquals("test@example.com", results.getFirst().email());
        assertTrue(results.getFirst().success());
        assertNull(results.getFirst().errorMessage());
    }

    @Test
    void recordFailure_addsFailedResult() {
        // Act
        reportService.recordFailure("fail@example.com", "Connection timeout");

        // Assert
        assertEquals(1, reportService.getResultCount());
        assertEquals(0, reportService.getSuccessCount());
        assertEquals(1, reportService.getFailureCount());

        List<ReportService.EmailResult> results = reportService.getResults();
        assertEquals(1, results.size());
        assertEquals("fail@example.com", results.getFirst().email());
        assertFalse(results.getFirst().success());
        assertEquals("Connection timeout", results.getFirst().errorMessage());
    }

    @Test
    void recordMixedResults_tracksCorrectly() {
        // Act
        reportService.recordSuccess("success1@example.com");
        reportService.recordFailure("fail1@example.com", "Error 1");
        reportService.recordSuccess("success2@example.com");
        reportService.recordFailure("fail2@example.com", "Error 2");

        // Assert
        assertEquals(4, reportService.getResultCount());
        assertEquals(2, reportService.getSuccessCount());
        assertEquals(2, reportService.getFailureCount());
    }

    @Test
    void recordInterrupted_addsInterruptedResult() {
        // Act
        reportService.recordInterrupted("interrupted@example.com");

        // Assert
        assertEquals(1, reportService.getResultCount());
        assertEquals(0, reportService.getSuccessCount());
        assertEquals(0, reportService.getFailureCount());
        assertEquals(1, reportService.getInterruptedCount());

        List<ReportService.EmailResult> results = reportService.getResults();
        assertEquals(1, results.size());
        assertEquals("interrupted@example.com", results.getFirst().email());
        assertFalse(results.getFirst().success());
        assertEquals("Interrupted", results.getFirst().errorMessage());
    }

    @Test
    void recordInterrupted_multipleEmails_addsAllAsInterrupted() {
        // Act
        reportService.recordInterrupted(List.of("first@example.com", "second@example.com", "third@example.com"));

        // Assert
        assertEquals(3, reportService.getResultCount());
        assertEquals(0, reportService.getSuccessCount());
        assertEquals(0, reportService.getFailureCount());
        assertEquals(3, reportService.getInterruptedCount());
    }

    @Test
    void recordMixedResultsWithInterrupted_tracksCorrectly() {
        // Act
        reportService.recordSuccess("success@example.com");
        reportService.recordFailure("fail@example.com", "Error");
        reportService.recordInterrupted("interrupted@example.com");

        // Assert
        assertEquals(3, reportService.getResultCount());
        assertEquals(1, reportService.getSuccessCount());
        assertEquals(1, reportService.getFailureCount());
        assertEquals(1, reportService.getInterruptedCount());
    }

    @Test
    void clear_removesAllResults() {
        // Arrange
        reportService.recordSuccess("test@example.com");
        reportService.recordFailure("fail@example.com", "Error");
        assertEquals(2, reportService.getResultCount());

        // Act
        reportService.clear();

        // Assert
        assertEquals(0, reportService.getResultCount());
        assertEquals(0, reportService.getSuccessCount());
        assertEquals(0, reportService.getFailureCount());
    }

    @Test
    void writeReport_createsValidCsvFile() throws IOException {
        // Arrange
        Path reportPath = tempDir.resolve("report.csv");
        reportService.recordSuccess("success@example.com");
        reportService.recordFailure("fail@example.com", "API error");

        // Act
        reportService.writeReport(reportPath.toString());

        // Assert
        assertTrue(Files.exists(reportPath));
        String content = Files.readString(reportPath);

        // Verify CSV header
        assertTrue(content.contains("Email,Status"));

        // Verify data rows
        assertTrue(content.contains("success@example.com,Success"));
        assertTrue(content.contains("fail@example.com,Failed"));
    }

    @Test
    void writeReport_withConfiguredPath_usesConfigPath() throws IOException {
        // Arrange
        Path reportPath = tempDir.resolve("configured-report.csv");
        AppConfig.ReportConfig reportConfig = new AppConfig.ReportConfig();
        reportConfig.setOutputPath(reportPath.toString());
        appConfig.setReport(reportConfig);

        ReportService service = new ReportService(appConfig);
        service.recordSuccess("test@example.com");

        // Act
        service.writeReport();

        // Assert
        assertTrue(Files.exists(reportPath));
        String content = Files.readString(reportPath);
        assertTrue(content.contains("test@example.com,Success"));
    }

    @Test
    void writeReport_withNoConfiguredPath_skipsReportGeneration() {
        // Arrange - no report config set
        reportService.recordSuccess("test@example.com");

        // Act & Assert - should not throw
        assertDoesNotThrow(() -> reportService.writeReport());
    }

    @Test
    void writeReport_withEmptyConfiguredPath_skipsReportGeneration() {
        // Arrange
        AppConfig.ReportConfig reportConfig = new AppConfig.ReportConfig();
        reportConfig.setOutputPath("");
        appConfig.setReport(reportConfig);

        ReportService service = new ReportService(appConfig);
        service.recordSuccess("test@example.com");

        // Act & Assert - should not throw
        assertDoesNotThrow(() -> service.writeReport());
    }

    @Test
    void writeReport_withNoResults_skipsReportGeneration() {
        // Arrange
        Path reportPath = tempDir.resolve("empty-report.csv");

        // Act
        reportService.writeReport(reportPath.toString());

        // Assert - file should not be created when there are no results
        assertFalse(Files.exists(reportPath));
    }

    @Test
    void writeReport_createsParentDirectories() {
        // Arrange
        Path reportPath = tempDir.resolve("subdir/nested/report.csv");
        reportService.recordSuccess("test@example.com");

        // Act
        reportService.writeReport(reportPath.toString());

        // Assert
        assertTrue(Files.exists(reportPath));
        assertTrue(Files.exists(reportPath.getParent()));
    }

    @Test
    void writeReport_preservesOrderOfResults() throws IOException {
        // Arrange
        Path reportPath = tempDir.resolve("ordered-report.csv");
        reportService.recordSuccess("first@example.com");
        reportService.recordFailure("second@example.com", "Error");
        reportService.recordSuccess("third@example.com");

        // Act
        reportService.writeReport(reportPath.toString());

        // Assert
        List<String> lines = Files.readAllLines(reportPath);
        assertEquals(4, lines.size()); // header + 3 data rows

        assertEquals("Email,Status", lines.get(0));
        assertEquals("first@example.com,Success", lines.get(1));
        assertEquals("second@example.com,Failed", lines.get(2));
        assertEquals("third@example.com,Success", lines.get(3));
    }

    @Test
    void writeReport_withInterrupted_showsInterruptedStatus() throws IOException {
        // Arrange
        Path reportPath = tempDir.resolve("interrupted-report.csv");
        reportService.recordSuccess("success@example.com");
        reportService.recordFailure("fail@example.com", "Error");
        reportService.recordInterrupted("interrupted@example.com");

        // Act
        reportService.writeReport(reportPath.toString());

        // Assert
        List<String> lines = Files.readAllLines(reportPath);
        assertEquals(4, lines.size()); // header + 3 data rows

        assertEquals("Email,Status", lines.get(0));
        assertEquals("success@example.com,Success", lines.get(1));
        assertEquals("fail@example.com,Failed", lines.get(2));
        assertEquals("interrupted@example.com,Interrupted", lines.get(3));
    }

    @Test
    void writeReport_handlesSpecialCharactersInEmail() throws IOException {
        // Arrange
        Path reportPath = tempDir.resolve("special-chars-report.csv");
        reportService.recordSuccess("user+tag@example.com");
        reportService.recordSuccess("user.name@sub.domain.com");

        // Act
        reportService.writeReport(reportPath.toString());

        // Assert
        String content = Files.readString(reportPath);
        assertTrue(content.contains("user+tag@example.com"));
        assertTrue(content.contains("user.name@sub.domain.com"));
    }

    @Test
    void writeReport_invalidPath_throwsException() {
        // Arrange
        reportService.recordSuccess("test@example.com");

        // Act & Assert
        assertThrows(EmailSenderException.class, () ->
            reportService.writeReport("/nonexistent/invalid\0path/report.csv")
        );
    }

    @Test
    void getResults_returnsCopy() {
        // Arrange
        reportService.recordSuccess("test@example.com");

        // Act
        List<ReportService.EmailResult> results = reportService.getResults();
        int originalSize = reportService.getResultCount();

        // Modifying the returned list should not affect the service
        results.clear();

        // Assert
        assertEquals(originalSize, reportService.getResultCount());
    }

    @Test
    void multipleWrites_preservesPreviousContent() throws IOException {
        // Arrange
        Path reportPath = tempDir.resolve("multiple-writes.csv");
        reportService.recordSuccess("first@example.com");
        reportService.writeReport(reportPath.toString());

        // Add more results
        reportService.recordSuccess("second@example.com");

        // Act - write again
        reportService.writeReport(reportPath.toString());

        // Assert - should contain all results
        String content = Files.readString(reportPath);
        assertTrue(content.contains("first@example.com"));
        assertTrue(content.contains("second@example.com"));
    }

    private AppConfig createAppConfig() {
        AppConfig config = new AppConfig();

        AppConfig.MicrosoftConfig microsoftConfig = new AppConfig.MicrosoftConfig();
        microsoftConfig.setTenantId("test-tenant");
        microsoftConfig.setClientId("test-client");
        microsoftConfig.setClientSecret("test-secret");
        config.setMicrosoft(microsoftConfig);

        AppConfig.DatasourceConfig datasourceConfig = new AppConfig.DatasourceConfig();
        datasourceConfig.setType("csv");
        datasourceConfig.setPath("/path/to/data.csv");
        datasourceConfig.setProcessColumn("SendEmail");
        datasourceConfig.setProcessValue("Yes");
        config.setDatasource(datasourceConfig);

        AppConfig.TemplatesConfig templatesConfig = new AppConfig.TemplatesConfig();
        templatesConfig.setEmailBody("/path/to/email.html");
        templatesConfig.setAttachment("/path/to/attachment.docx");
        config.setTemplates(templatesConfig);

        AppConfig.EmailConfig emailConfig = new AppConfig.EmailConfig();
        emailConfig.setSenderEmail("sender@example.com");
        emailConfig.setSubjectTemplate("Test Subject");
        emailConfig.setRecipientColumn("Email");
        config.setEmail(emailConfig);

        return config;
    }
}
