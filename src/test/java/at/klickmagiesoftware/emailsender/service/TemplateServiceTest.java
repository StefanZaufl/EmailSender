package at.klickmagiesoftware.emailsender.service;

import at.klickmagiesoftware.emailsender.config.AppConfig;
import at.klickmagiesoftware.emailsender.exception.EmailSenderException;
import at.klickmagiesoftware.emailsender.model.EmailData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TemplateServiceTest {

    private AppConfig appConfig;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        appConfig = createAppConfig();
    }

    @Test
    void processSubject_simplePlaceholders_replacesCorrectly() throws IOException {
        // Arrange
        Path templatePath = createHtmlTemplate("<html><body>Hello</body></html>");
        appConfig.getTemplates().setEmailBody(templatePath.toString());
        appConfig.getEmail().setSubjectTemplate("Hello {{name}}, welcome to {{company}}");

        TemplateService service = new TemplateService(appConfig);

        Map<String, String> fields = new HashMap<>();
        fields.put("name", "John");
        fields.put("company", "Acme Corp");
        EmailData emailData = new EmailData("john@example.com", fields, 1);

        // Act
        String result = service.processSubject(emailData);

        // Assert
        assertEquals("Hello John, welcome to Acme Corp", result);
    }

    @Test
    void processSubject_withFieldMappings_usesMapping() throws IOException {
        // Arrange
        Path templatePath = createHtmlTemplate("<html><body>Hello</body></html>");
        appConfig.getTemplates().setEmailBody(templatePath.toString());
        appConfig.getEmail().setSubjectTemplate("Hello {{name}}");

        Map<String, String> fieldMappings = new HashMap<>();
        fieldMappings.put("{{name}}", "FullName");
        appConfig.setFieldMappings(fieldMappings);

        TemplateService service = new TemplateService(appConfig);

        Map<String, String> fields = new HashMap<>();
        fields.put("FullName", "Jane Smith");
        fields.put("name", "WrongName"); // Direct match should be overridden by mapping
        EmailData emailData = new EmailData("jane@example.com", fields, 1);

        // Act
        String result = service.processSubject(emailData);

        // Assert
        assertEquals("Hello Jane Smith", result);
    }

    @Test
    void processSubject_missingPlaceholder_keepsPlaceholder() throws IOException {
        // Arrange
        Path templatePath = createHtmlTemplate("<html><body>Hello</body></html>");
        appConfig.getTemplates().setEmailBody(templatePath.toString());
        appConfig.getEmail().setSubjectTemplate("Hello {{name}}, your code is {{code}}");

        TemplateService service = new TemplateService(appConfig);

        Map<String, String> fields = new HashMap<>();
        fields.put("name", "John");
        // Note: 'code' field is missing
        EmailData emailData = new EmailData("john@example.com", fields, 1);

        // Act
        String result = service.processSubject(emailData);

        // Assert
        assertEquals("Hello John, your code is {{code}}", result);
    }

    @Test
    void processSubject_noPlaceholders_returnsOriginal() throws IOException {
        // Arrange
        Path templatePath = createHtmlTemplate("<html><body>Hello</body></html>");
        appConfig.getTemplates().setEmailBody(templatePath.toString());
        appConfig.getEmail().setSubjectTemplate("Welcome to our service!");

        TemplateService service = new TemplateService(appConfig);

        Map<String, String> fields = new HashMap<>();
        EmailData emailData = new EmailData("john@example.com", fields, 1);

        // Act
        String result = service.processSubject(emailData);

        // Assert
        assertEquals("Welcome to our service!", result);
    }

    @Test
    void processEmailBody_thymeleafTemplate_processesCorrectly() throws IOException {
        // Arrange
        String htmlContent = """
                <!DOCTYPE html>
                <html xmlns:th="http://www.thymeleaf.org">
                <body>
                <p>Hello <span th:text="${name}">Name</span>!</p>
                <p>Company: <span th:text="${company}">Company</span></p>
                </body>
                </html>
                """;
        Path templatePath = createHtmlTemplate(htmlContent);
        appConfig.getTemplates().setEmailBody(templatePath.toString());

        TemplateService service = new TemplateService(appConfig);

        Map<String, String> fields = new HashMap<>();
        fields.put("name", "John Doe");
        fields.put("company", "Acme Corp");
        EmailData emailData = new EmailData("john@example.com", fields, 1);

        // Act
        String result = service.processEmailBody(emailData);

        // Assert
        assertTrue(result.contains("John Doe"));
        assertTrue(result.contains("Acme Corp"));
    }

    @Test
    void processEmailBody_withFieldMappings_appliesMapping() throws IOException {
        // Arrange
        String htmlContent = """
                <!DOCTYPE html>
                <html xmlns:th="http://www.thymeleaf.org">
                <body>
                <p>Hello <span th:text="${displayName}">Name</span>!</p>
                </body>
                </html>
                """;
        Path templatePath = createHtmlTemplate(htmlContent);
        appConfig.getTemplates().setEmailBody(templatePath.toString());

        Map<String, String> fieldMappings = new HashMap<>();
        fieldMappings.put("{{displayName}}", "FullName");
        appConfig.setFieldMappings(fieldMappings);

        TemplateService service = new TemplateService(appConfig);

        Map<String, String> fields = new HashMap<>();
        fields.put("FullName", "Jane Smith");
        EmailData emailData = new EmailData("jane@example.com", fields, 1);

        // Act
        String result = service.processEmailBody(emailData);

        // Assert
        assertTrue(result.contains("Jane Smith"));
    }

    @Test
    void processEmailBody_templateNotFound_throwsException() {
        // Arrange
        appConfig.getTemplates().setEmailBody("/nonexistent/template.html");

        TemplateService service = new TemplateService(appConfig);

        Map<String, String> fields = new HashMap<>();
        EmailData emailData = new EmailData("john@example.com", fields, 1);

        // Act & Assert
        assertThrows(EmailSenderException.class, () -> service.processEmailBody(emailData));
    }

    @Test
    void readTemplateContent_validFile_returnsContent() throws IOException {
        // Arrange
        String expectedContent = "<html><body>Test Content</body></html>";
        Path templatePath = createHtmlTemplate(expectedContent);
        appConfig.getTemplates().setEmailBody(templatePath.toString());

        TemplateService service = new TemplateService(appConfig);

        // Act
        String result = service.readTemplateContent(templatePath.toString());

        // Assert
        assertEquals(expectedContent, result);
    }

    @Test
    void readTemplateContent_fileNotFound_throwsException() {
        // Arrange
        TemplateService service = new TemplateService(appConfig);

        // Act & Assert
        assertThrows(
                EmailSenderException.class,
                () -> service.readTemplateContent("/nonexistent/file.html")
        );
    }

    private Path createHtmlTemplate(String content) throws IOException {
        Path templatePath = tempDir.resolve("template.html");
        Files.writeString(templatePath, content);
        return templatePath;
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

        config.setFieldMappings(new HashMap<>());

        return config;
    }
}
