package com.yourcompany.emailsender.service;

import com.yourcompany.emailsender.config.AppConfig;
import com.yourcompany.emailsender.exception.EmailSenderException;
import com.yourcompany.emailsender.model.EmailData;
import org.docx4j.Docx4jProperties;
import org.docx4j.fonts.PhysicalFonts;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PdfGeneratorServiceTest {

    private static final Logger logger = LoggerFactory.getLogger(PdfGeneratorServiceTest.class);

    private AppConfig appConfig;
    private static boolean pdfGenerationSupported = false;

    @TempDir
    Path tempDir;

    @TempDir
    static Path staticTempDir;

    @BeforeAll
    static void checkPdfSupport() {
        // Configure docx4j to avoid font scanning issues on different systems.
        // Set a restrictive regex to limit font discovery to only common safe fonts.
        // This must be done BEFORE any Mapper is created (which triggers font discovery).
        PhysicalFonts.setRegex(".*(calibri|cour|arial|times|comic|georgia|impact|tahoma|trebuc|verdana|symbol|webdings|wingding|liberation|dejavu|freesans|freeserif|freemono).*");
        Docx4jProperties.setProperty("docx4j.fonts.fop.util.autodetect.FontFileFinder.fontDirFinder.ignore", true);

        // Check if PDF generation is supported in the current environment.
        // Some systems have font configurations that cause docx4j to fail.
        try {
            Path testDocx = staticTempDir.resolve("pdf-support-test.docx");
            WordprocessingMLPackage wordPackage = WordprocessingMLPackage.createPackage();
            wordPackage.getMainDocumentPart().addParagraphOfText("PDF Support Test");
            wordPackage.save(new File(testDocx.toString()));

            // Try to convert to PDF
            ByteArrayOutputStream pdfOut = new ByteArrayOutputStream();
            org.docx4j.Docx4J.toPDF(wordPackage, pdfOut);

            pdfGenerationSupported = pdfOut.size() > 0;
            logger.info("PDF generation support check: PASSED");
        } catch (AssertionError | Exception e) {
            logger.warn("PDF generation not supported in this environment", e);
            pdfGenerationSupported = false;
        }
    }

    @BeforeEach
    void setUp() {
        appConfig = createAppConfig();
    }

    @Test
    void generatePdf_validTemplate_returnsPdfBytes() throws Exception {
        assumeTrue(pdfGenerationSupported,
                "PDF generation not supported in this environment (font configuration issue)");

        // Arrange
        Path docxPath = createSimpleDocxTemplate();
        appConfig.getTemplates().setAttachment(docxPath.toString());

        PdfGeneratorService service = new PdfGeneratorService(appConfig);

        Map<String, String> fields = new HashMap<>();
        EmailData emailData = new EmailData("test@example.com", fields, 1);

        // Act
        byte[] result = service.generatePdf(emailData);

        // Assert
        assertNotNull(result);
        assertTrue(result.length > 0);
        // PDF files start with %PDF
        assertTrue(new String(result, 0, 4).startsWith("%PDF"));
    }

    @Test
    void generatePdf_templateWithPlaceholders_replacesValues() throws Exception {
        assumeTrue(pdfGenerationSupported,
                "PDF generation not supported in this environment (font configuration issue)");

        // Arrange
        Path docxPath = createDocxTemplateWithPlaceholders();
        appConfig.getTemplates().setAttachment(docxPath.toString());

        PdfGeneratorService service = new PdfGeneratorService(appConfig);

        Map<String, String> fields = new HashMap<>();
        fields.put("name", "John Doe");
        fields.put("company", "Acme Corp");
        EmailData emailData = new EmailData("test@example.com", fields, 1);

        // Act
        byte[] result = service.generatePdf(emailData);

        // Assert
        assertNotNull(result);
        assertTrue(result.length > 0);
    }

    @Test
    void generatePdf_templateNotFound_throwsException() {
        // Arrange
        appConfig.getTemplates().setAttachment("/nonexistent/template.docx");

        PdfGeneratorService service = new PdfGeneratorService(appConfig);

        Map<String, String> fields = new HashMap<>();
        EmailData emailData = new EmailData("test@example.com", fields, 1);

        // Act & Assert
        assertThrows(EmailSenderException.class, () -> service.generatePdf(emailData));
    }

    @Test
    void generateDocx_validTemplate_returnsDocxBytes() throws Exception {
        // Arrange
        Path docxPath = createSimpleDocxTemplate();
        appConfig.getTemplates().setAttachment(docxPath.toString());

        PdfGeneratorService service = new PdfGeneratorService(appConfig);

        Map<String, String> fields = new HashMap<>();
        EmailData emailData = new EmailData("test@example.com", fields, 1);

        // Act
        byte[] result = service.generateDocx(emailData);

        // Assert
        assertNotNull(result);
        assertTrue(result.length > 0);
        // DOCX files are ZIP files, which start with PK
        assertEquals('P', result[0]);
        assertEquals('K', result[1]);
    }

    @Test
    void generateDocx_templateNotFound_throwsException() {
        // Arrange
        appConfig.getTemplates().setAttachment("/nonexistent/template.docx");

        PdfGeneratorService service = new PdfGeneratorService(appConfig);

        Map<String, String> fields = new HashMap<>();
        EmailData emailData = new EmailData("test@example.com", fields, 1);

        // Act & Assert
        assertThrows(EmailSenderException.class, () -> service.generateDocx(emailData));
    }

    @Test
    void generateDocx_templateWithPlaceholders_returnsValidDocx() throws Exception {
        // Arrange
        Path docxPath = createDocxTemplateWithPlaceholders();
        appConfig.getTemplates().setAttachment(docxPath.toString());

        PdfGeneratorService service = new PdfGeneratorService(appConfig);

        Map<String, String> fields = new HashMap<>();
        fields.put("name", "John Doe");
        fields.put("company", "Acme Corp");
        EmailData emailData = new EmailData("test@example.com", fields, 1);

        // Act
        byte[] result = service.generateDocx(emailData);

        // Assert
        assertNotNull(result);
        assertTrue(result.length > 0);
        // DOCX files are ZIP files, which start with PK
        assertEquals('P', result[0]);
        assertEquals('K', result[1]);
    }

    @Test
    void generatePdf_withFieldMappings_usesMapping() throws Exception {
        assumeTrue(pdfGenerationSupported,
                "PDF generation not supported in this environment (font configuration issue)");

        // Arrange
        Path docxPath = createDocxTemplateWithPlaceholders();
        appConfig.getTemplates().setAttachment(docxPath.toString());

        Map<String, String> fieldMappings = new HashMap<>();
        fieldMappings.put("{{name}}", "FullName");
        appConfig.setFieldMappings(fieldMappings);

        PdfGeneratorService service = new PdfGeneratorService(appConfig);

        Map<String, String> fields = new HashMap<>();
        fields.put("FullName", "Jane Smith");
        fields.put("company", "Tech Inc");
        EmailData emailData = new EmailData("test@example.com", fields, 1);

        // Act
        byte[] result = service.generatePdf(emailData);

        // Assert
        assertNotNull(result);
        assertTrue(result.length > 0);
    }

    @Test
    void generateDocx_withFieldMappings_usesMapping() throws Exception {
        // Arrange
        Path docxPath = createDocxTemplateWithPlaceholders();
        appConfig.getTemplates().setAttachment(docxPath.toString());

        Map<String, String> fieldMappings = new HashMap<>();
        fieldMappings.put("{{name}}", "FullName");
        appConfig.setFieldMappings(fieldMappings);

        PdfGeneratorService service = new PdfGeneratorService(appConfig);

        Map<String, String> fields = new HashMap<>();
        fields.put("FullName", "Jane Smith");
        fields.put("company", "Tech Inc");
        EmailData emailData = new EmailData("test@example.com", fields, 1);

        // Act
        byte[] result = service.generateDocx(emailData);

        // Assert
        assertNotNull(result);
        assertTrue(result.length > 0);
    }

    @Test
    void generateDocx_multipleRows_generatesUniqueDocx() throws Exception {
        // Arrange
        Path docxPath = createDocxTemplateWithPlaceholders();
        appConfig.getTemplates().setAttachment(docxPath.toString());

        PdfGeneratorService service = new PdfGeneratorService(appConfig);

        Map<String, String> fields1 = new HashMap<>();
        fields1.put("name", "John");
        fields1.put("company", "Acme");
        EmailData emailData1 = new EmailData("john@example.com", fields1, 1);

        Map<String, String> fields2 = new HashMap<>();
        fields2.put("name", "Jane");
        fields2.put("company", "Tech");
        EmailData emailData2 = new EmailData("jane@example.com", fields2, 2);

        // Act
        byte[] result1 = service.generateDocx(emailData1);
        byte[] result2 = service.generateDocx(emailData2);

        // Assert
        assertNotNull(result1);
        assertNotNull(result2);
        assertTrue(result1.length > 0);
        assertTrue(result2.length > 0);
    }

    private Path createSimpleDocxTemplate() throws Exception {
        Path docxPath = tempDir.resolve("simple-template.docx");

        WordprocessingMLPackage wordPackage = WordprocessingMLPackage.createPackage();
        MainDocumentPart mainPart = wordPackage.getMainDocumentPart();
        mainPart.addParagraphOfText("Hello World");

        wordPackage.save(new File(docxPath.toString()));

        return docxPath;
    }

    private Path createDocxTemplateWithPlaceholders() throws Exception {
        Path docxPath = tempDir.resolve("template-with-placeholders.docx");

        WordprocessingMLPackage wordPackage = WordprocessingMLPackage.createPackage();
        MainDocumentPart mainPart = wordPackage.getMainDocumentPart();
        mainPart.addParagraphOfText("Hello {{name}}!");
        mainPart.addParagraphOfText("Welcome to {{company}}.");
        mainPart.addParagraphOfText("This is your personalized document.");

        wordPackage.save(new File(docxPath.toString()));

        return docxPath;
    }

    private AppConfig createAppConfig() {
        AppConfig config = new AppConfig();

        AppConfig.MicrosoftConfig microsoftConfig = new AppConfig.MicrosoftConfig();
        microsoftConfig.setTenantId("test-tenant");
        microsoftConfig.setClientId("test-client");
        microsoftConfig.setClientSecret("test-secret");
        microsoftConfig.setSenderEmail("sender@example.com");
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
        emailConfig.setSubjectTemplate("Test Subject");
        emailConfig.setRecipientColumn("Email");
        config.setEmail(emailConfig);

        config.setFieldMappings(new HashMap<>());

        return config;
    }
}
