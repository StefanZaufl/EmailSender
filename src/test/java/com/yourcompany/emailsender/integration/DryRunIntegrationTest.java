package com.yourcompany.emailsender.integration;

import com.yourcompany.emailsender.cli.SendEmailCommand;
import com.yourcompany.emailsender.config.AppConfig;
import com.yourcompany.emailsender.service.CsvDataSourceReader;
import com.yourcompany.emailsender.service.DataSourceReader;
import com.yourcompany.emailsender.service.EmailService;
import com.yourcompany.emailsender.service.ExcelDataSourceReader;
import com.yourcompany.emailsender.service.PdfGeneratorService;
import com.yourcompany.emailsender.service.ReportService;
import com.yourcompany.emailsender.service.SenderTypeResolver;
import com.yourcompany.emailsender.service.TemplateService;
import com.yourcompany.emailsender.service.processor.DryRunEmailProcessor;
import com.yourcompany.emailsender.service.processor.LiveEmailProcessor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.docx4j.Docx4jProperties;
import org.docx4j.fonts.PhysicalFonts;
import org.junit.jupiter.api.BeforeAll;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for dry run mode with CSV and Excel data sources.
 * These tests verify the complete flow from data loading through output file generation
 * using real service implementations (no mocks).
 */
class DryRunIntegrationTest {

    @Mock
    private LiveEmailProcessor liveEmailProcessor;

    @Mock
    private SenderTypeResolver senderTypeResolver;

    @TempDir
    Path tempDir;

    private Path outputDir;
    private Path dataDir;
    private Path templateDir;
    private Path reportDir;

    @BeforeAll
    static void configureDocx4j() {
        // Configure docx4j to avoid font scanning issues on different systems
        // These settings must be applied before any docx4j Mapper is instantiated

        // Set a restrictive regex to limit font discovery to only common safe fonts
        // This excludes emoji fonts and other fonts with complex glyph tables that can cause issues
        // The regex must be set BEFORE any Mapper is created (which triggers font discovery)
        PhysicalFonts.setRegex(".*(calibri|cour|arial|times|comic|georgia|impact|tahoma|trebuc|verdana|symbol|webdings|wingding|liberation|dejavu|freesans|freeserif|freemono).*");

        // Additional properties to help avoid font issues
        Docx4jProperties.setProperty("docx4j.fonts.fop.util.autodetect.FontFileFinder.fontDirFinder.ignore", true);
    }

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);

        outputDir = tempDir.resolve("output");
        dataDir = tempDir.resolve("data");
        templateDir = tempDir.resolve("templates");
        reportDir = tempDir.resolve("reports");

        Files.createDirectories(outputDir);
        Files.createDirectories(dataDir);
        Files.createDirectories(templateDir);
        Files.createDirectories(reportDir);
    }

    @Test
    void dryRun_withCsvDataSource_createsOutputFiles() throws Exception {
        // Arrange
        Path csvFile = createTestCsvFile();
        Path emailTemplate = createEmailTemplate();
        Path attachmentTemplate = createAttachmentTemplate();
        Path reportPath = reportDir.resolve("csv-report.csv");

        AppConfig appConfig = createAppConfig("csv", csvFile, emailTemplate, attachmentTemplate, reportPath);

        // Create real services (no mocks except for SenderTypeResolver which is not used in dry-run)
        TemplateService templateService = new TemplateService(appConfig);
        PdfGeneratorService pdfGeneratorService = new PdfGeneratorService(appConfig);
        when(senderTypeResolver.isSenderGroup()).thenReturn(false);
        EmailService emailService = new EmailService(null, appConfig, templateService, pdfGeneratorService, senderTypeResolver);

        List<DataSourceReader> readers = List.of(new CsvDataSourceReader(), new ExcelDataSourceReader());
        DryRunEmailProcessor dryRunProcessor = new DryRunEmailProcessor(emailService);
        ReportService reportService = new ReportService(appConfig);

        SendEmailCommand command = new SendEmailCommand(appConfig, readers, liveEmailProcessor, dryRunProcessor, reportService);
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
        assertTrue(metaContent.contains("Report for John Doe"));

        // Verify HTML body content contains personalized data
        String htmlContent = Files.readString(outputDir.resolve("row2_john.doe@example.com_body.html"));
        assertTrue(htmlContent.contains("John Doe"), "HTML should contain recipient name");
        assertTrue(htmlContent.contains("Acme Corp"), "HTML should contain company name");

        // Verify PDF file contents using PDFBox
        String pdfText1 = extractPdfText(outputDir.resolve("row2_john.doe@example.com_attachment.pdf"));
        assertTrue(pdfText1.contains("John Doe"), "PDF should contain recipient name");
        assertTrue(pdfText1.contains("Acme Corp"), "PDF should contain company name");

        String pdfText2 = extractPdfText(outputDir.resolve("row3_jane.smith@example.com_attachment.pdf"));
        assertTrue(pdfText2.contains("Jane Smith"), "PDF should contain recipient name");
        assertTrue(pdfText2.contains("Tech Solutions"), "PDF should contain company name");

        String pdfText3 = extractPdfText(outputDir.resolve("row5_alice.johnson@example.com_attachment.pdf"));
        assertTrue(pdfText3.contains("Alice Johnson"), "PDF should contain recipient name");
        assertTrue(pdfText3.contains("StartupXYZ"), "PDF should contain company name");

        // Verify Bob Wilson (SendEmail=No) was not processed
        assertFalse(Files.exists(outputDir.resolve("row4_bob.wilson@example.com_body.html")));

        // Verify the CSV report was generated
        assertTrue(Files.exists(reportPath), "Report file should be created");
        List<String> reportLines = Files.readAllLines(reportPath);
        assertEquals(4, reportLines.size(), "Report should have header + 3 data rows");
        assertEquals("Email,Status", reportLines.get(0));
        assertTrue(reportLines.get(1).contains("john.doe@example.com,Success"));
        assertTrue(reportLines.get(2).contains("jane.smith@example.com,Success"));
        assertTrue(reportLines.get(3).contains("alice.johnson@example.com,Success"));
    }

    @Test
    void dryRun_withExcelDataSource_createsOutputFiles() throws Exception {
        // Arrange
        Path excelFile = createTestExcelFile();
        Path emailTemplate = createEmailTemplate();
        Path attachmentTemplate = createAttachmentTemplate();
        Path reportPath = reportDir.resolve("excel-report.csv");

        AppConfig appConfig = createAppConfig("excel", excelFile, emailTemplate, attachmentTemplate, reportPath);

        // Create real services (no mocks except for SenderTypeResolver which is not used in dry-run)
        TemplateService templateService = new TemplateService(appConfig);
        PdfGeneratorService pdfGeneratorService = new PdfGeneratorService(appConfig);
        when(senderTypeResolver.isSenderGroup()).thenReturn(false);
        EmailService emailService = new EmailService(null, appConfig, templateService, pdfGeneratorService, senderTypeResolver);

        List<DataSourceReader> readers = List.of(new CsvDataSourceReader(), new ExcelDataSourceReader());
        DryRunEmailProcessor dryRunProcessor = new DryRunEmailProcessor(emailService);
        ReportService reportService = new ReportService(appConfig);

        SendEmailCommand command = new SendEmailCommand(appConfig, readers, liveEmailProcessor, dryRunProcessor, reportService);
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
        assertTrue(htmlContent.contains("Excel User One"), "HTML should contain recipient name");
        assertTrue(htmlContent.contains("Excel Corp"), "HTML should contain company name");

        // Verify meta file content
        String metaContent = Files.readString(outputDir.resolve("row4_excel.user3@example.com_meta.txt"));
        assertTrue(metaContent.contains("excel.user3@example.com"));
        assertTrue(metaContent.contains("Report for Excel User Three"));

        // Verify PDF file contents using PDFBox
        String pdfText1 = extractPdfText(outputDir.resolve("row2_excel.user1@example.com_attachment.pdf"));
        assertTrue(pdfText1.contains("Excel User One"), "PDF should contain recipient name");
        assertTrue(pdfText1.contains("Excel Corp"), "PDF should contain company name");

        String pdfText2 = extractPdfText(outputDir.resolve("row4_excel.user3@example.com_attachment.pdf"));
        assertTrue(pdfText2.contains("Excel User Three"), "PDF should contain recipient name");
        assertTrue(pdfText2.contains("Sheet LLC"), "PDF should contain company name");

        // Verify user2 (SendEmail=No) was not processed
        assertFalse(Files.exists(outputDir.resolve("row3_excel.user2@example.com_body.html")));

        // Verify the CSV report was generated
        assertTrue(Files.exists(reportPath), "Report file should be created");
        List<String> reportLines = Files.readAllLines(reportPath);
        assertEquals(3, reportLines.size(), "Report should have header + 2 data rows");
        assertEquals("Email,Status", reportLines.get(0));
        assertTrue(reportLines.get(1).contains("excel.user1@example.com,Success"));
        assertTrue(reportLines.get(2).contains("excel.user3@example.com,Success"));
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
                    <p>Report Date: <span th:text="${ReportDate}">Date</span></p>
                </body>
                </html>
                """;
        Files.writeString(templateFile, templateContent);
        return templateFile;
    }

    /**
     * Creates a valid DOCX template file with placeholders for personalization.
     * Uses Apache POI to create a proper Word document.
     *
     * The PdfGeneratorService uses {{variable}} format for placeholder matching
     * and does direct string replacement in the document XML.
     */
    private Path createAttachmentTemplate() throws IOException {
        Path templateFile = templateDir.resolve("attachment-template.docx");

        try (XWPFDocument document = new XWPFDocument()) {
            // Title paragraph
            XWPFParagraph titleParagraph = document.createParagraph();
            XWPFRun titleRun = titleParagraph.createRun();
            titleRun.setText("Monthly Report");
            titleRun.setBold(true);
            titleRun.setFontSize(16);

            // Add an empty paragraph for spacing
            document.createParagraph();

            // Recipient paragraph with placeholder - use separate runs to avoid XML splitting issues
            XWPFParagraph recipientParagraph = document.createParagraph();
            XWPFRun recipientLabelRun = recipientParagraph.createRun();
            recipientLabelRun.setText("Prepared for: ");
            XWPFRun recipientValueRun = recipientParagraph.createRun();
            recipientValueRun.setText("{{FullName}}");

            // Company paragraph with placeholder
            XWPFParagraph companyParagraph = document.createParagraph();
            XWPFRun companyLabelRun = companyParagraph.createRun();
            companyLabelRun.setText("Company: ");
            XWPFRun companyValueRun = companyParagraph.createRun();
            companyValueRun.setText("{{CompanyName}}");

            // Date paragraph with placeholder
            XWPFParagraph dateParagraph = document.createParagraph();
            XWPFRun dateLabelRun = dateParagraph.createRun();
            dateLabelRun.setText("Report Date: ");
            XWPFRun dateValueRun = dateParagraph.createRun();
            dateValueRun.setText("{{ReportDate}}");

            // Add an empty paragraph for spacing
            document.createParagraph();

            // Content paragraph
            XWPFParagraph contentParagraph = document.createParagraph();
            XWPFRun contentRun = contentParagraph.createRun();
            contentRun.setText("This report contains your monthly summary.");

            try (FileOutputStream fos = new FileOutputStream(templateFile.toFile())) {
                document.write(fos);
            }
        }

        return templateFile;
    }

    private AppConfig createAppConfig(String dataSourceType, Path dataSourcePath,
                                       Path emailTemplatePath, Path attachmentTemplatePath,
                                       Path reportPath) {
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

        // Report config
        if (reportPath != null) {
            AppConfig.ReportConfig reportConfig = new AppConfig.ReportConfig();
            reportConfig.setOutputPath(reportPath.toString());
            appConfig.setReport(reportConfig);
        }

        return appConfig;
    }

    /**
     * Extracts text content from a PDF file using PDFBox.
     *
     * @param pdfPath the path to the PDF file
     * @return the extracted text content
     */
    private String extractPdfText(Path pdfPath) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }
}
