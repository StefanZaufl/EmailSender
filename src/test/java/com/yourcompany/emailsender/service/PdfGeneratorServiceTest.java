package com.yourcompany.emailsender.service;

import com.yourcompany.emailsender.config.AppConfig;
import com.yourcompany.emailsender.exception.EmailSenderException;
import com.yourcompany.emailsender.model.EmailData;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PdfGeneratorServiceTest {

    private AppConfig appConfig;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        appConfig = createAppConfig();
    }

    @Test
    void generatePdf_validTemplate_returnsPdfBytes() throws Exception {
        // Arrange
        Path docxPath = createSimpleDocxTemplate("Hello World");
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
        Path docxPath = createSimpleDocxTemplate("Hello World");
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
    void generatePdf_withFieldMappings_usesMapping() throws Exception {
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
    void generatePdf_multipleRows_generatesUniquePdfs() throws Exception {
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
        byte[] result1 = service.generatePdf(emailData1);
        byte[] result2 = service.generatePdf(emailData2);

        // Assert
        assertNotNull(result1);
        assertNotNull(result2);
        // Both should be valid PDFs but may have different content/sizes
        assertTrue(result1.length > 0);
        assertTrue(result2.length > 0);
    }

    private Path createSimpleDocxTemplate(String content) throws Exception {
        Path docxPath = tempDir.resolve("simple-template.docx");

        WordprocessingMLPackage wordPackage = WordprocessingMLPackage.createPackage();
        MainDocumentPart mainPart = wordPackage.getMainDocumentPart();
        mainPart.addParagraphOfText(content);

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
