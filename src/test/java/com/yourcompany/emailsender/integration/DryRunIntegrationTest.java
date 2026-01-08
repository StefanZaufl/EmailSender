package com.yourcompany.emailsender.integration;

import com.yourcompany.emailsender.cli.SendEmailCommand;
import com.yourcompany.emailsender.config.AppConfig;
import com.yourcompany.emailsender.service.CsvDataSourceReader;
import com.yourcompany.emailsender.service.DataSourceReader;
import com.yourcompany.emailsender.service.EmailService;
import com.yourcompany.emailsender.service.ExcelDataSourceReader;
import com.yourcompany.emailsender.service.processor.DryRunEmailProcessor;
import com.yourcompany.emailsender.service.processor.LiveEmailProcessor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration tests for dry run mode with CSV and Excel data sources.
 * These tests verify the complete flow from data loading through output file generation.
 */
class DryRunIntegrationTest {

    @Mock
    private EmailService emailService;

    @Mock
    private LiveEmailProcessor liveEmailProcessor;

    @TempDir
    Path tempDir;

    private Path outputDir;
    private Path dataDir;
    private Path templateDir;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);

        outputDir = tempDir.resolve("output");
        dataDir = tempDir.resolve("data");
        templateDir = tempDir.resolve("templates");

        Files.createDirectories(outputDir);
        Files.createDirectories(dataDir);
        Files.createDirectories(templateDir);
    }

    @Test
    void dryRun_withCsvDataSource_createsOutputFiles() throws Exception {
        // Arrange
        Path csvFile = createTestCsvFile();
        Path emailTemplate = createEmailTemplate();
        Path attachmentTemplate = createAttachmentTemplate();

        AppConfig appConfig = createAppConfig("csv", csvFile, emailTemplate, attachmentTemplate);

        // Mock EmailService to return prepared content
        when(emailService.prepareEmail(any())).thenAnswer(invocation -> {
            var emailData = invocation.getArgument(0, com.yourcompany.emailsender.model.EmailData.class);
            return new EmailService.EmailContent(
                    emailData.getRecipientEmail(),
                    "Test Subject for " + emailData.getField("FullName"),
                    "<html><body>Hello " + emailData.getField("FullName") + "</body></html>",
                    "PDF content".getBytes(),
                    emailData.getRowNumber()
            );
        });

        List<DataSourceReader> readers = List.of(new CsvDataSourceReader(), new ExcelDataSourceReader());
        DryRunEmailProcessor dryRunProcessor = new DryRunEmailProcessor(emailService);

        SendEmailCommand command = new SendEmailCommand(appConfig, readers, liveEmailProcessor, dryRunProcessor);
        CommandLine commandLine = new CommandLine(command);

        // Act
        int exitCode = commandLine.execute("--dry-run", "--output-dir=" + outputDir.toString());

        // Assert
        assertEquals(0, exitCode, "Command should complete successfully");

        // Verify output files were created for the 3 rows with SendEmail=Yes
        assertTrue(Files.exists(outputDir.resolve("row2_john.doe@example.com_body.html")));
        assertTrue(Files.exists(outputDir.resolve("row2_john.doe@example.com_attachment.pdf")));
        assertTrue(Files.exists(outputDir.resolve("row2_john.doe@example.com_meta.txt")));

        assertTrue(Files.exists(outputDir.resolve("row3_jane.smith@example.com_body.html")));
        assertTrue(Files.exists(outputDir.resolve("row3_jane.smith@example.com_attachment.pdf")));
        assertTrue(Files.exists(outputDir.resolve("row3_jane.smith@example.com_meta.txt")));

        assertTrue(Files.exists(outputDir.resolve("row5_alice.johnson@example.com_body.html")));
        assertTrue(Files.exists(outputDir.resolve("row5_alice.johnson@example.com_attachment.pdf")));
        assertTrue(Files.exists(outputDir.resolve("row5_alice.johnson@example.com_meta.txt")));

        // Verify content of one of the meta files
        String metaContent = Files.readString(outputDir.resolve("row2_john.doe@example.com_meta.txt"));
        assertTrue(metaContent.contains("john.doe@example.com"));
        assertTrue(metaContent.contains("Test Subject for John Doe"));

        // Verify PDF file contents - all should contain "PDF content"
        String pdfContent1 = Files.readString(outputDir.resolve("row2_john.doe@example.com_attachment.pdf"));
        assertEquals("PDF content", pdfContent1, "PDF content for John Doe should match");

        String pdfContent2 = Files.readString(outputDir.resolve("row3_jane.smith@example.com_attachment.pdf"));
        assertEquals("PDF content", pdfContent2, "PDF content for Jane Smith should match");

        String pdfContent3 = Files.readString(outputDir.resolve("row5_alice.johnson@example.com_attachment.pdf"));
        assertEquals("PDF content", pdfContent3, "PDF content for Alice Johnson should match");

        // Verify Bob Wilson (SendEmail=No) was not processed
        assertFalse(Files.exists(outputDir.resolve("row4_bob.wilson@example.com_body.html")));
    }

    @Test
    void dryRun_withExcelDataSource_createsOutputFiles() throws Exception {
        // Arrange
        Path excelFile = createTestExcelFile();
        Path emailTemplate = createEmailTemplate();
        Path attachmentTemplate = createAttachmentTemplate();

        AppConfig appConfig = createAppConfig("excel", excelFile, emailTemplate, attachmentTemplate);

        // Mock EmailService to return prepared content
        when(emailService.prepareEmail(any())).thenAnswer(invocation -> {
            var emailData = invocation.getArgument(0, com.yourcompany.emailsender.model.EmailData.class);
            return new EmailService.EmailContent(
                    emailData.getRecipientEmail(),
                    "Excel Report for " + emailData.getField("FullName"),
                    "<html><body>Dear " + emailData.getField("FullName") + ", your report from "
                            + emailData.getField("CompanyName") + "</body></html>",
                    ("PDF for " + emailData.getRecipientEmail()).getBytes(),
                    emailData.getRowNumber()
            );
        });

        List<DataSourceReader> readers = List.of(new CsvDataSourceReader(), new ExcelDataSourceReader());
        DryRunEmailProcessor dryRunProcessor = new DryRunEmailProcessor(emailService);

        SendEmailCommand command = new SendEmailCommand(appConfig, readers, liveEmailProcessor, dryRunProcessor);
        CommandLine commandLine = new CommandLine(command);

        // Act
        int exitCode = commandLine.execute("--dry-run", "--output-dir=" + outputDir.toString());

        // Assert
        assertEquals(0, exitCode, "Command should complete successfully");

        // Verify output files were created for the 2 rows with SendEmail=Yes
        assertTrue(Files.exists(outputDir.resolve("row2_excel.user1@example.com_body.html")));
        assertTrue(Files.exists(outputDir.resolve("row2_excel.user1@example.com_attachment.pdf")));
        assertTrue(Files.exists(outputDir.resolve("row2_excel.user1@example.com_meta.txt")));

        assertTrue(Files.exists(outputDir.resolve("row4_excel.user3@example.com_body.html")));
        assertTrue(Files.exists(outputDir.resolve("row4_excel.user3@example.com_attachment.pdf")));
        assertTrue(Files.exists(outputDir.resolve("row4_excel.user3@example.com_meta.txt")));

        // Verify HTML body content
        String htmlContent = Files.readString(outputDir.resolve("row2_excel.user1@example.com_body.html"));
        assertTrue(htmlContent.contains("Excel User One"));
        assertTrue(htmlContent.contains("Excel Corp"));

        // Verify meta file content
        String metaContent = Files.readString(outputDir.resolve("row4_excel.user3@example.com_meta.txt"));
        assertTrue(metaContent.contains("excel.user3@example.com"));
        assertTrue(metaContent.contains("Excel Report for Excel User Three"));

        // Verify PDF file contents - each should contain personalized content with email address
        String pdfContent1 = Files.readString(outputDir.resolve("row2_excel.user1@example.com_attachment.pdf"));
        assertEquals("PDF for excel.user1@example.com", pdfContent1,
                "PDF content for Excel User One should contain correct email");

        String pdfContent2 = Files.readString(outputDir.resolve("row4_excel.user3@example.com_attachment.pdf"));
        assertEquals("PDF for excel.user3@example.com", pdfContent2,
                "PDF content for Excel User Three should contain correct email");

        // Verify user2 (SendEmail=No) was not processed
        assertFalse(Files.exists(outputDir.resolve("row3_excel.user2@example.com_body.html")));
    }

    private Path createTestCsvFile() throws IOException {
        Path csvFile = dataDir.resolve("test-data.csv");
        String csvContent = """
                FullName,Email,CompanyName,ReportDate,SendEmail
                John Doe,john.doe@example.com,Acme Corp,January 2024,Yes
                Jane Smith,jane.smith@example.com,Tech Solutions,January 2024,Yes
                Bob Wilson,bob.wilson@example.com,Global Inc,January 2024,No
                Alice Johnson,alice.johnson@example.com,StartupXYZ,January 2024,Yes
                """;
        Files.writeString(csvFile, csvContent);
        return csvFile;
    }

    private Path createTestExcelFile() throws IOException {
        Path excelFile = dataDir.resolve("test-data.xlsx");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Data");

            // Header row
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("FullName");
            headerRow.createCell(1).setCellValue("Email");
            headerRow.createCell(2).setCellValue("CompanyName");
            headerRow.createCell(3).setCellValue("ReportDate");
            headerRow.createCell(4).setCellValue("SendEmail");

            // Data rows
            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("Excel User One");
            row1.createCell(1).setCellValue("excel.user1@example.com");
            row1.createCell(2).setCellValue("Excel Corp");
            row1.createCell(3).setCellValue("February 2024");
            row1.createCell(4).setCellValue("Yes");

            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("Excel User Two");
            row2.createCell(1).setCellValue("excel.user2@example.com");
            row2.createCell(2).setCellValue("Data Inc");
            row2.createCell(3).setCellValue("February 2024");
            row2.createCell(4).setCellValue("No");

            Row row3 = sheet.createRow(3);
            row3.createCell(0).setCellValue("Excel User Three");
            row3.createCell(1).setCellValue("excel.user3@example.com");
            row3.createCell(2).setCellValue("Sheet LLC");
            row3.createCell(3).setCellValue("February 2024");
            row3.createCell(4).setCellValue("Yes");

            try (FileOutputStream fos = new FileOutputStream(excelFile.toFile())) {
                workbook.write(fos);
            }
        }

        return excelFile;
    }

    private Path createEmailTemplate() throws IOException {
        Path templateFile = templateDir.resolve("email-template.html");
        String templateContent = """
                <!DOCTYPE html>
                <html xmlns:th="http://www.thymeleaf.org">
                <body>
                    <p>Dear <span th:text="${FullName}">Name</span>,</p>
                    <p>Your report for <span th:text="${CompanyName}">Company</span> is attached.</p>
                </body>
                </html>
                """;
        Files.writeString(templateFile, templateContent);
        return templateFile;
    }

    private Path createAttachmentTemplate() throws IOException {
        // Create a minimal valid DOCX file for testing
        // In this test we mock EmailService, so we just need a placeholder file
        Path templateFile = templateDir.resolve("attachment-template.docx");
        Files.write(templateFile, "placeholder".getBytes());
        return templateFile;
    }

    private AppConfig createAppConfig(String dataSourceType, Path dataSourcePath,
                                       Path emailTemplatePath, Path attachmentTemplatePath) {
        AppConfig appConfig = new AppConfig();

        // Microsoft config (not used in dry run but required)
        AppConfig.MicrosoftConfig microsoftConfig = new AppConfig.MicrosoftConfig();
        microsoftConfig.setTenantId("test-tenant");
        microsoftConfig.setClientId("test-client");
        microsoftConfig.setClientSecret("test-secret");
        microsoftConfig.setSenderEmail("sender@example.com");
        appConfig.setMicrosoft(microsoftConfig);

        // Datasource config
        AppConfig.DatasourceConfig datasourceConfig = new AppConfig.DatasourceConfig();
        datasourceConfig.setType(dataSourceType);
        datasourceConfig.setPath(dataSourcePath.toString());
        datasourceConfig.setProcessColumn("SendEmail");
        datasourceConfig.setProcessValue("Yes");
        appConfig.setDatasource(datasourceConfig);

        // Templates config
        AppConfig.TemplatesConfig templatesConfig = new AppConfig.TemplatesConfig();
        templatesConfig.setEmailBody(emailTemplatePath.toString());
        templatesConfig.setAttachment(attachmentTemplatePath.toString());
        appConfig.setTemplates(templatesConfig);

        // Email config
        AppConfig.EmailConfig emailConfig = new AppConfig.EmailConfig();
        emailConfig.setSubjectTemplate("Report for {{FullName}}");
        emailConfig.setRecipientColumn("Email");
        emailConfig.setAttachmentFilename("report.pdf");
        appConfig.setEmail(emailConfig);

        // Throttling config (disabled for tests)
        AppConfig.ThrottlingConfig throttlingConfig = new AppConfig.ThrottlingConfig();
        throttlingConfig.setEnabled(false);
        appConfig.setThrottling(throttlingConfig);

        return appConfig;
    }
}
