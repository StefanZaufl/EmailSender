package at.klickmagiesoftware.emailsender.service;

import at.klickmagiesoftware.emailsender.config.AppConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SenderTypeResolver.
 */
class SenderTypeResolverTest {

    private AppConfig appConfig;

    @BeforeEach
    void setUp() {
        appConfig = createAppConfig();
    }

    @Test
    void init_noSenderGroup_sendFromGroupIsFalse() {
        // Arrange - no sender-group configured
        appConfig.getEmail().setSenderGroup(null);

        // Act
        SenderTypeResolver resolver = new SenderTypeResolver(appConfig);
        resolver.init();

        // Assert
        assertFalse(resolver.isSendFromGroup());
        assertNull(resolver.getSenderGroup());
        assertEquals("sender@example.com", resolver.getSenderEmail());
    }

    @Test
    void init_emptySenderGroup_sendFromGroupIsFalse() {
        // Arrange - empty sender-group
        appConfig.getEmail().setSenderGroup("");

        // Act
        SenderTypeResolver resolver = new SenderTypeResolver(appConfig);
        resolver.init();

        // Assert
        assertFalse(resolver.isSendFromGroup());
    }

    @Test
    void init_blankSenderGroup_sendFromGroupIsFalse() {
        // Arrange - blank sender-group
        appConfig.getEmail().setSenderGroup("   ");

        // Act
        SenderTypeResolver resolver = new SenderTypeResolver(appConfig);
        resolver.init();

        // Assert
        assertFalse(resolver.isSendFromGroup());
    }

    @Test
    void init_withSenderGroup_sendFromGroupIsTrue() {
        // Arrange - sender-group configured
        appConfig.getEmail().setSenderGroup("group@example.com");

        // Act
        SenderTypeResolver resolver = new SenderTypeResolver(appConfig);
        resolver.init();

        // Assert
        assertTrue(resolver.isSendFromGroup());
        assertEquals("group@example.com", resolver.getSenderGroup());
        assertEquals("sender@example.com", resolver.getSenderEmail());
    }

    @Test
    void getSenderEmail_returnsConfiguredEmail() {
        // Arrange
        appConfig.getEmail().setSenderEmail("custom-sender@example.com");

        // Act
        SenderTypeResolver resolver = new SenderTypeResolver(appConfig);
        resolver.init();

        // Assert
        assertEquals("custom-sender@example.com", resolver.getSenderEmail());
    }

    @Test
    void getSenderGroup_returnsConfiguredGroup() {
        // Arrange
        appConfig.getEmail().setSenderGroup("team@example.com");

        // Act
        SenderTypeResolver resolver = new SenderTypeResolver(appConfig);
        resolver.init();

        // Assert
        assertEquals("team@example.com", resolver.getSenderGroup());
    }

    // ==================== Helper Methods ====================

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
        emailConfig.setAttachmentFilename("test.pdf");
        config.setEmail(emailConfig);

        AppConfig.ThrottlingConfig throttlingConfig = new AppConfig.ThrottlingConfig();
        config.setThrottling(throttlingConfig);

        config.setFieldMappings(new HashMap<>());

        return config;
    }
}
