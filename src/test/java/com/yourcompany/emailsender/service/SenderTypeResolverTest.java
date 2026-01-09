package com.yourcompany.emailsender.service;

import com.microsoft.graph.groups.GroupsRequestBuilder;
import com.microsoft.graph.models.Group;
import com.microsoft.graph.models.GroupCollectionResponse;
import com.microsoft.graph.models.User;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.UsersRequestBuilder;
import com.microsoft.graph.users.item.UserItemRequestBuilder;
import com.microsoft.kiota.ApiException;
import com.yourcompany.emailsender.config.AppConfig;
import com.yourcompany.emailsender.exception.EmailSenderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SenderTypeResolver, testing auto-detection and configuration override.
 */
class SenderTypeResolverTest {

    @Mock
    private GraphServiceClient graphClient;

    @Mock
    private UsersRequestBuilder usersRequestBuilder;

    @Mock
    private UserItemRequestBuilder userItemRequestBuilder;

    @Mock
    private GroupsRequestBuilder groupsRequestBuilder;

    private AppConfig appConfig;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        appConfig = createAppConfig();

        // Set up mock chain for graph client - users
        when(graphClient.users()).thenReturn(usersRequestBuilder);
        when(usersRequestBuilder.byUserId(anyString())).thenReturn(userItemRequestBuilder);

        // Set up mock chain for graph client - groups
        when(graphClient.groups()).thenReturn(groupsRequestBuilder);
    }

    // ==================== Auto-Detection Tests ====================

    @Test
    void init_senderIsUser_detectsAsUser() {
        // Arrange
        User mockUser = new User();
        mockUser.setId("user-123");
        mockUser.setMail("sender@example.com");
        when(userItemRequestBuilder.get()).thenReturn(mockUser);

        // Act
        SenderTypeResolver resolver = new SenderTypeResolver(graphClient, appConfig);
        resolver.init();

        // Assert
        assertFalse(resolver.isSenderGroup());
        assertNull(resolver.getGroupId());
    }

    @Test
    void init_senderIsGroup_detectsAsGroup() {
        // Arrange - User lookup returns 404
        ApiException notFoundException = createApiException(404);
        when(userItemRequestBuilder.get()).thenThrow(notFoundException);

        // Arrange - Group lookup returns a group
        Group mockGroup = new Group();
        mockGroup.setId("group-456");
        mockGroup.setDisplayName("Test Group");
        mockGroup.setMail("sender@example.com");

        GroupCollectionResponse groupResponse = new GroupCollectionResponse();
        groupResponse.setValue(List.of(mockGroup));

        when(groupsRequestBuilder.get(any())).thenReturn(groupResponse);

        // Act
        SenderTypeResolver resolver = new SenderTypeResolver(graphClient, appConfig);
        resolver.init();

        // Assert
        assertTrue(resolver.isSenderGroup());
        assertEquals("group-456", resolver.getGroupId());
    }

    @Test
    void init_senderNotFound_throwsException() {
        // Arrange - User lookup returns 404
        ApiException notFoundException = createApiException(404);
        when(userItemRequestBuilder.get()).thenThrow(notFoundException);

        // Arrange - Group lookup returns empty list
        GroupCollectionResponse emptyResponse = new GroupCollectionResponse();
        emptyResponse.setValue(List.of());
        when(groupsRequestBuilder.get(any())).thenReturn(emptyResponse);

        // Act & Assert
        SenderTypeResolver resolver = new SenderTypeResolver(graphClient, appConfig);
        EmailSenderException exception = assertThrows(
                EmailSenderException.class,
                resolver::init
        );

        assertTrue(exception.getMessage().contains("is neither a valid user nor a group"));
    }

    @Test
    void init_userLookupThrowsNon404Error_fallsBackToGroupCheck() {
        // Arrange - User lookup throws 500 error (not 404)
        ApiException serverError = createApiException(500);
        when(userItemRequestBuilder.get()).thenThrow(serverError);

        // Arrange - Group lookup returns a group
        Group mockGroup = new Group();
        mockGroup.setId("group-789");
        mockGroup.setMail("sender@example.com");

        GroupCollectionResponse groupResponse = new GroupCollectionResponse();
        groupResponse.setValue(List.of(mockGroup));

        when(groupsRequestBuilder.get(any())).thenReturn(groupResponse);

        // Act
        SenderTypeResolver resolver = new SenderTypeResolver(graphClient, appConfig);
        resolver.init();

        // Assert - should fall back to group check
        assertTrue(resolver.isSenderGroup());
        assertEquals("group-789", resolver.getGroupId());
    }

    // ==================== Configuration Override Tests ====================

    @Test
    void init_explicitSenderIsGroupTrue_skipsUserCheck() {
        // Arrange
        appConfig.getMicrosoft().setSenderIsGroup(true);

        // Arrange - Group lookup returns a group
        Group mockGroup = new Group();
        mockGroup.setId("group-explicit");
        mockGroup.setMail("sender@example.com");

        GroupCollectionResponse groupResponse = new GroupCollectionResponse();
        groupResponse.setValue(List.of(mockGroup));

        when(groupsRequestBuilder.get(any())).thenReturn(groupResponse);

        // Act
        SenderTypeResolver resolver = new SenderTypeResolver(graphClient, appConfig);
        resolver.init();

        // Assert
        assertTrue(resolver.isSenderGroup());
        assertEquals("group-explicit", resolver.getGroupId());
        // Should NOT have called users API
        verify(userItemRequestBuilder, never()).get();
    }

    @Test
    void init_explicitSenderIsGroupFalse_skipsGroupCheck() {
        // Arrange
        appConfig.getMicrosoft().setSenderIsGroup(false);

        // Act
        SenderTypeResolver resolver = new SenderTypeResolver(graphClient, appConfig);
        resolver.init();

        // Assert
        assertFalse(resolver.isSenderGroup());
        assertNull(resolver.getGroupId());
        // Should NOT have called users or groups API for detection
        verify(userItemRequestBuilder, never()).get();
        verify(groupsRequestBuilder, never()).get(any());
    }

    @Test
    void init_explicitSenderIsGroupTrueButGroupNotFound_throwsException() {
        // Arrange
        appConfig.getMicrosoft().setSenderIsGroup(true);

        // Arrange - Group lookup returns empty
        GroupCollectionResponse emptyResponse = new GroupCollectionResponse();
        emptyResponse.setValue(List.of());
        when(groupsRequestBuilder.get(any())).thenReturn(emptyResponse);

        // Act & Assert
        SenderTypeResolver resolver = new SenderTypeResolver(graphClient, appConfig);
        EmailSenderException exception = assertThrows(
                EmailSenderException.class,
                resolver::init
        );

        assertTrue(exception.getMessage().contains("is configured as a group but no group was found"));
    }

    // ==================== Group Resolution Tests ====================

    @Test
    void init_multipleGroupsFound_usesFirstGroup() {
        // Arrange - User lookup returns 404
        ApiException notFoundException = createApiException(404);
        when(userItemRequestBuilder.get()).thenThrow(notFoundException);

        // Arrange - Group lookup returns multiple groups
        Group group1 = new Group();
        group1.setId("group-first");
        group1.setDisplayName("First Group");

        Group group2 = new Group();
        group2.setId("group-second");
        group2.setDisplayName("Second Group");

        GroupCollectionResponse groupResponse = new GroupCollectionResponse();
        groupResponse.setValue(List.of(group1, group2));

        when(groupsRequestBuilder.get(any())).thenReturn(groupResponse);

        // Act
        SenderTypeResolver resolver = new SenderTypeResolver(graphClient, appConfig);
        resolver.init();

        // Assert - should use the first group
        assertTrue(resolver.isSenderGroup());
        assertEquals("group-first", resolver.getGroupId());
    }

    @Test
    void init_groupLookupThrowsException_treatsAsNotFound() {
        // Arrange - User lookup returns 404
        ApiException notFoundException = createApiException(404);
        when(userItemRequestBuilder.get()).thenThrow(notFoundException);

        // Arrange - Group lookup throws exception
        ApiException groupError = createApiException(500);
        when(groupsRequestBuilder.get(any())).thenThrow(groupError);

        // Act & Assert
        SenderTypeResolver resolver = new SenderTypeResolver(graphClient, appConfig);
        EmailSenderException exception = assertThrows(
                EmailSenderException.class,
                resolver::init
        );

        assertTrue(exception.getMessage().contains("is neither a valid user nor a group"));
    }

    @Test
    void init_groupLookupReturnsNull_treatsAsNotFound() {
        // Arrange - User lookup returns 404
        ApiException notFoundException = createApiException(404);
        when(userItemRequestBuilder.get()).thenThrow(notFoundException);

        // Arrange - Group lookup returns null
        when(groupsRequestBuilder.get(any())).thenReturn(null);

        // Act & Assert
        SenderTypeResolver resolver = new SenderTypeResolver(graphClient, appConfig);
        EmailSenderException exception = assertThrows(
                EmailSenderException.class,
                resolver::init
        );

        assertTrue(exception.getMessage().contains("is neither a valid user nor a group"));
    }

    @Test
    void init_groupResponseWithNullValue_treatsAsNotFound() {
        // Arrange - User lookup returns 404
        ApiException notFoundException = createApiException(404);
        when(userItemRequestBuilder.get()).thenThrow(notFoundException);

        // Arrange - Group lookup returns response with null value
        GroupCollectionResponse nullValueResponse = new GroupCollectionResponse();
        nullValueResponse.setValue(null);
        when(groupsRequestBuilder.get(any())).thenReturn(nullValueResponse);

        // Act & Assert
        SenderTypeResolver resolver = new SenderTypeResolver(graphClient, appConfig);
        EmailSenderException exception = assertThrows(
                EmailSenderException.class,
                resolver::init
        );

        assertTrue(exception.getMessage().contains("is neither a valid user nor a group"));
    }

    // ==================== Helper Methods ====================

    private ApiException createApiException(int statusCode) {
        ApiException exception = mock(ApiException.class);
        when(exception.getResponseStatusCode()).thenReturn(statusCode);
        when(exception.getMessage()).thenReturn("HTTP " + statusCode + " error");
        return exception;
    }

    private AppConfig createAppConfig() {
        AppConfig config = new AppConfig();

        AppConfig.MicrosoftConfig microsoftConfig = new AppConfig.MicrosoftConfig();
        microsoftConfig.setTenantId("test-tenant");
        microsoftConfig.setClientId("test-client");
        microsoftConfig.setClientSecret("test-secret");
        microsoftConfig.setSenderEmail("sender@example.com");
        // senderIsGroup is null by default (auto-detect)
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

        AppConfig.ThrottlingConfig throttlingConfig = new AppConfig.ThrottlingConfig();
        config.setThrottling(throttlingConfig);

        config.setFieldMappings(new HashMap<>());

        return config;
    }
}
