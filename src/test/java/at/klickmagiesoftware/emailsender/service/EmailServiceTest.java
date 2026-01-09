package at.klickmagiesoftware.emailsender.service;

import com.microsoft.graph.groups.GroupsRequestBuilder;
import com.microsoft.graph.groups.item.GroupItemRequestBuilder;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.UsersRequestBuilder;
import com.microsoft.graph.users.item.UserItemRequestBuilder;
import com.microsoft.graph.users.item.sendmail.SendMailRequestBuilder;
import com.microsoft.kiota.ApiException;
import com.microsoft.kiota.ResponseHeaders;
import at.klickmagiesoftware.emailsender.config.AppConfig;
import at.klickmagiesoftware.emailsender.exception.EmailSenderException;
import at.klickmagiesoftware.emailsender.model.EmailData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EmailService, focusing on throttling and retry logic.
 */
class EmailServiceTest {

    @Mock
    private GraphServiceClient graphClient;

    @Mock
    private TemplateService templateService;

    @Mock
    private PdfGeneratorService pdfGeneratorService;

    @Mock
    private UsersRequestBuilder usersRequestBuilder;

    @Mock
    private UserItemRequestBuilder userItemRequestBuilder;

    @Mock
    private SendMailRequestBuilder sendMailRequestBuilder;

    @Mock
    private SenderTypeResolver senderTypeResolver;

    @Mock
    private GroupsRequestBuilder groupsRequestBuilder;

    @Mock
    private GroupItemRequestBuilder groupItemRequestBuilder;

    @Mock
    private com.microsoft.graph.groups.item.sendmail.SendMailRequestBuilder groupSendMailRequestBuilder;

    private AppConfig appConfig;
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        appConfig = createAppConfig();

        // Set up mock chain for graph client - user sending
        when(graphClient.users()).thenReturn(usersRequestBuilder);
        when(usersRequestBuilder.byUserId(anyString())).thenReturn(userItemRequestBuilder);
        when(userItemRequestBuilder.sendMail()).thenReturn(sendMailRequestBuilder);

        // Set up mock chain for graph client - group sending
        when(graphClient.groups()).thenReturn(groupsRequestBuilder);
        when(groupsRequestBuilder.byGroupId(anyString())).thenReturn(groupItemRequestBuilder);
        when(groupItemRequestBuilder.sendMail()).thenReturn(groupSendMailRequestBuilder);

        // Set up template and PDF mocks
        when(templateService.processSubject(any())).thenReturn("Test Subject");
        when(templateService.processEmailBody(any())).thenReturn("<html><body>Test</body></html>");
        when(pdfGeneratorService.generatePdf(any())).thenReturn(new byte[]{1, 2, 3});

        // Default to user sender type
        when(senderTypeResolver.isSenderGroup()).thenReturn(false);
        when(senderTypeResolver.getGroupId()).thenReturn(null);

        emailService = new EmailService(graphClient, appConfig, templateService, pdfGeneratorService, senderTypeResolver);
    }

    @Test
    void sendEmail_success_sendsOnce() {
        // Arrange
        EmailData emailData = createEmailData();

        // Act
        emailService.sendEmail(emailData);

        // Assert
        verify(sendMailRequestBuilder, times(1)).post(any());
    }

    @Test
    void sendEmail_throttled429_retriesWithBackoff() {
        // Arrange
        EmailData emailData = createEmailData();
        ApiException throttledException = createApiException(429);

        // First call throws 429, second succeeds
        doThrow(throttledException)
                .doNothing()
                .when(sendMailRequestBuilder).post(any());

        // Act
        long startTime = System.currentTimeMillis();
        emailService.sendEmail(emailData);
        long duration = System.currentTimeMillis() - startTime;

        // Assert - should have retried once
        verify(sendMailRequestBuilder, times(2)).post(any());
        // Should have waited at least the initial retry delay (50ms configured in test)
        assertTrue(duration >= 40, "Should have waited for retry delay");
    }

    @Test
    void sendEmail_throttled429_respectsMaxRetries() {
        // Arrange
        EmailData emailData = createEmailData();
        ApiException throttledException = createApiException(429);

        // Always throw 429
        doThrow(throttledException).when(sendMailRequestBuilder).post(any());

        // Act & Assert
        EmailSenderException exception = assertThrows(
                EmailSenderException.class,
                () -> emailService.sendEmail(emailData)
        );

        // Should have tried initial + 3 retries = 4 times
        verify(sendMailRequestBuilder, times(4)).post(any());
        assertTrue(exception.getMessage().contains("Max retries exceeded"));
    }

    @Test
    void sendEmail_throttlingDisabled_noRetry() {
        // Arrange
        appConfig.getThrottling().setEnabled(false);
        EmailData emailData = createEmailData();
        ApiException throttledException = createApiException(429);

        doThrow(throttledException).when(sendMailRequestBuilder).post(any());

        // Act & Assert
        assertThrows(EmailSenderException.class, () -> emailService.sendEmail(emailData));

        // Should only try once when throttling is disabled
        verify(sendMailRequestBuilder, times(1)).post(any());
    }

    @Test
    void sendEmail_nonThrottlingError_doesNotRetry() {
        // Arrange
        EmailData emailData = createEmailData();
        ApiException serverError = createApiException(500);

        doThrow(serverError).when(sendMailRequestBuilder).post(any());

        // Act & Assert
        EmailSenderException exception = assertThrows(
                EmailSenderException.class,
                () -> emailService.sendEmail(emailData)
        );

        // Should only try once for non-429 errors
        verify(sendMailRequestBuilder, times(1)).post(any());
        assertTrue(exception.getMessage().contains("HTTP 500"));
    }

    @Test
    void sendEmail_400BadRequest_doesNotRetry() {
        // Arrange
        EmailData emailData = createEmailData();
        ApiException badRequest = createApiException(400);

        doThrow(badRequest).when(sendMailRequestBuilder).post(any());

        // Act & Assert
        EmailSenderException exception = assertThrows(
                EmailSenderException.class,
                () -> emailService.sendEmail(emailData)
        );

        verify(sendMailRequestBuilder, times(1)).post(any());
        assertTrue(exception.getMessage().contains("HTTP 400"));
    }

    @Test
    void sendEmail_403Forbidden_doesNotRetry() {
        // Arrange
        EmailData emailData = createEmailData();
        ApiException forbidden = createApiException(403);

        doThrow(forbidden).when(sendMailRequestBuilder).post(any());

        // Act & Assert
        assertThrows(EmailSenderException.class, () -> emailService.sendEmail(emailData));

        verify(sendMailRequestBuilder, times(1)).post(any());
    }

    @Test
    void sendEmail_throttled429WithRetryAfterHeader_usesRetryAfter() {
        // Arrange
        EmailData emailData = createEmailData();
        // Use Retry-After of 0 seconds for fast test - still validates header parsing
        ApiException throttledException = createApiExceptionWithRetryAfter();

        // First call throws 429, second succeeds
        doThrow(throttledException)
                .doNothing()
                .when(sendMailRequestBuilder).post(any());

        // Act
        emailService.sendEmail(emailData);

        // Assert - should have retried using Retry-After header
        verify(sendMailRequestBuilder, times(2)).post(any());
    }

    @Test
    void sendEmail_maxRetriesZero_noRetries() {
        // Arrange
        appConfig.getThrottling().setMaxRetries(0);
        EmailData emailData = createEmailData();
        ApiException throttledException = createApiException(429);

        doThrow(throttledException).when(sendMailRequestBuilder).post(any());

        // Act & Assert
        assertThrows(EmailSenderException.class, () -> emailService.sendEmail(emailData));

        // Should only try once when maxRetries is 0
        verify(sendMailRequestBuilder, times(1)).post(any());
    }

    @Test
    void sendEmail_customMaxRetries_retriesCorrectNumberOfTimes() {
        // Arrange
        appConfig.getThrottling().setMaxRetries(2);
        EmailData emailData = createEmailData();
        ApiException throttledException = createApiException(429);

        doThrow(throttledException).when(sendMailRequestBuilder).post(any());

        // Act & Assert
        assertThrows(EmailSenderException.class, () -> emailService.sendEmail(emailData));

        // Should try initial + 2 retries = 3 times
        verify(sendMailRequestBuilder, times(3)).post(any());
    }

    @Test
    void sendEmail_eventualSuccess_returnsAfterRetry() {
        // Arrange
        EmailData emailData = createEmailData();
        ApiException throttledException = createApiException(429);

        // First two calls throw 429, third succeeds
        doThrow(throttledException)
                .doThrow(throttledException)
                .doNothing()
                .when(sendMailRequestBuilder).post(any());

        // Act
        assertDoesNotThrow(() -> emailService.sendEmail(emailData));

        // Assert - should have succeeded on third attempt
        verify(sendMailRequestBuilder, times(3)).post(any());
    }

    @Test
    void prepareEmail_returnsCorrectContent() {
        // Arrange
        EmailData emailData = createEmailData();
        when(templateService.processSubject(emailData)).thenReturn("Custom Subject");
        when(templateService.processEmailBody(emailData)).thenReturn("<html>Custom Body</html>");
        when(pdfGeneratorService.generatePdf(emailData)).thenReturn(new byte[]{4, 5, 6});

        // Act
        EmailService.EmailContent content = emailService.prepareEmail(emailData);

        // Assert
        assertEquals("test@example.com", content.recipientEmail());
        assertEquals("Custom Subject", content.subject());
        assertEquals("<html>Custom Body</html>", content.htmlBody());
        assertArrayEquals(new byte[]{4, 5, 6}, content.pdfAttachment());
        assertEquals(1, content.rowNumber());
    }

    // ==================== Group Sender Tests ====================

    @Test
    void sendEmail_groupSender_usesGroupSendMailApi() {
        // Arrange
        when(senderTypeResolver.isSenderGroup()).thenReturn(true);
        when(senderTypeResolver.getGroupId()).thenReturn("group-123");
        EmailData emailData = createEmailData();

        // Act
        emailService.sendEmail(emailData);

        // Assert - should use group sendMail API, not user sendMail
        verify(groupSendMailRequestBuilder, times(1)).post(any());
        verify(sendMailRequestBuilder, never()).post(any());
    }

    @Test
    void sendEmail_groupSender_throttled429_retriesWithBackoff() {
        // Arrange
        when(senderTypeResolver.isSenderGroup()).thenReturn(true);
        when(senderTypeResolver.getGroupId()).thenReturn("group-123");
        EmailData emailData = createEmailData();
        ApiException throttledException = createApiException(429);

        // First call throws 429, second succeeds
        doThrow(throttledException)
                .doNothing()
                .when(groupSendMailRequestBuilder).post(any());

        // Act
        long startTime = System.currentTimeMillis();
        emailService.sendEmail(emailData);
        long duration = System.currentTimeMillis() - startTime;

        // Assert - should have retried once
        verify(groupSendMailRequestBuilder, times(2)).post(any());
        assertTrue(duration >= 40, "Should have waited for retry delay");
    }

    @Test
    void sendEmail_groupSender_nonThrottlingError_doesNotRetry() {
        // Arrange
        when(senderTypeResolver.isSenderGroup()).thenReturn(true);
        when(senderTypeResolver.getGroupId()).thenReturn("group-123");
        EmailData emailData = createEmailData();
        ApiException serverError = createApiException(500);

        doThrow(serverError).when(groupSendMailRequestBuilder).post(any());

        // Act & Assert
        EmailSenderException exception = assertThrows(
                EmailSenderException.class,
                () -> emailService.sendEmail(emailData)
        );

        verify(groupSendMailRequestBuilder, times(1)).post(any());
        assertTrue(exception.getMessage().contains("HTTP 500"));
    }

    @Test
    void sendEmail_groupSenderWithNullGroupId_throwsException() {
        // Arrange
        when(senderTypeResolver.isSenderGroup()).thenReturn(true);
        when(senderTypeResolver.getGroupId()).thenReturn(null);
        EmailData emailData = createEmailData();

        // Act & Assert
        EmailSenderException exception = assertThrows(
                EmailSenderException.class,
                () -> emailService.sendEmail(emailData)
        );

        assertTrue(exception.getMessage().contains("Group ID not resolved"));
    }

    @Test
    void sendEmail_userSender_usesUserSendMailApi() {
        // Arrange
        when(senderTypeResolver.isSenderGroup()).thenReturn(false);
        EmailData emailData = createEmailData();

        // Act
        emailService.sendEmail(emailData);

        // Assert - should use user sendMail API, not group sendMail
        verify(sendMailRequestBuilder, times(1)).post(any());
        verify(groupSendMailRequestBuilder, never()).post(any());
    }

    // ==================== Helper Methods ====================

    private EmailData createEmailData() {
        Map<String, String> fields = new HashMap<>();
        fields.put("name", "Test User");
        return new EmailData("test@example.com", fields, 1);
    }

    private ApiException createApiException(int statusCode) {
        ApiException exception = mock(ApiException.class);
        when(exception.getResponseStatusCode()).thenReturn(statusCode);
        when(exception.getResponseHeaders()).thenReturn(null);
        return exception;
    }

    private ApiException createApiExceptionWithRetryAfter() {
        ApiException exception = mock(ApiException.class);
        when(exception.getResponseStatusCode()).thenReturn(429);

        // Mock ResponseHeaders - it extends HashMap<String, Set<String>>
        ResponseHeaders headers = mock(ResponseHeaders.class);
        Set<String> retryAfterValue = new HashSet<>();
        retryAfterValue.add(String.valueOf(0));
        when(headers.get("Retry-After")).thenReturn(retryAfterValue);
        when(exception.getResponseHeaders()).thenReturn(headers);

        return exception;
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
        emailConfig.setAttachmentFilename("test.pdf");
        config.setEmail(emailConfig);

        // Throttling config with short delays for fast tests
        AppConfig.ThrottlingConfig throttlingConfig = new AppConfig.ThrottlingConfig();
        throttlingConfig.setInitialRetryDelayMs(50); // Short delay for fast tests
        config.setThrottling(throttlingConfig);

        config.setFieldMappings(new HashMap<>());

        return config;
    }
}
