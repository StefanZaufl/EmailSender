package com.yourcompany.emailsender.service;

import com.microsoft.graph.models.Group;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.kiota.ApiException;
import com.yourcompany.emailsender.config.AppConfig;
import com.yourcompany.emailsender.exception.EmailSenderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;

/**
 * Service to resolve whether the configured sender email belongs to a user or a group.
 * Supports auto-detection via Graph API or manual configuration override.
 */
@Service
public class SenderTypeResolver {

    private static final Logger logger = LoggerFactory.getLogger(SenderTypeResolver.class);

    private final GraphServiceClient graphClient;
    private final AppConfig appConfig;

    private volatile Boolean isGroup;
    private volatile String groupId;

    public SenderTypeResolver(GraphServiceClient graphClient, AppConfig appConfig) {
        this.graphClient = graphClient;
        this.appConfig = appConfig;
    }

    @PostConstruct
    public void init() {
        resolveSenderType();
    }

    /**
     * Returns whether the sender is a group mailbox.
     */
    public boolean isSenderGroup() {
        return Boolean.TRUE.equals(isGroup);
    }

    /**
     * Returns the group ID if the sender is a group, null otherwise.
     */
    public String getGroupId() {
        return groupId;
    }

    /**
     * Resolves the sender type (user or group) either from configuration or by auto-detection.
     */
    private void resolveSenderType() {
        String senderEmail = appConfig.getMicrosoft().getSenderEmail();
        Boolean configuredIsGroup = appConfig.getMicrosoft().getSenderIsGroup();

        if (configuredIsGroup != null) {
            // Use the configured value
            this.isGroup = configuredIsGroup;
            logger.info("Sender type configured as: {}", isGroup ? "GROUP" : "USER");

            if (isGroup) {
                // Need to resolve the group ID
                this.groupId = resolveGroupId(senderEmail);
                if (this.groupId == null) {
                    throw new EmailSenderException(
                            "Sender email '" + senderEmail + "' is configured as a group but no group was found. " +
                            "Please verify the email address or check Group.ReadWrite.All permission.");
                }
                logger.info("Resolved group ID: {}", groupId);
            }
        } else {
            // Auto-detect
            logger.info("Auto-detecting sender type for: {}", senderEmail);
            autoDetectSenderType(senderEmail);
        }
    }

    /**
     * Auto-detects whether the sender email belongs to a user or group.
     */
    private void autoDetectSenderType(String senderEmail) {
        // First, try to find as a user
        if (tryResolveAsUser(senderEmail)) {
            this.isGroup = false;
            logger.info("Sender '{}' detected as USER", senderEmail);
            return;
        }

        // Try to find as a group
        String resolvedGroupId = resolveGroupId(senderEmail);
        if (resolvedGroupId != null) {
            this.isGroup = true;
            this.groupId = resolvedGroupId;
            logger.info("Sender '{}' detected as GROUP (id: {})", senderEmail, groupId);
            return;
        }

        // Neither user nor group found
        throw new EmailSenderException(
                "Sender email '" + senderEmail + "' is neither a valid user nor a group. " +
                "Please verify the email address or set 'sender-is-group' in configuration.");
    }

    /**
     * Tries to resolve the sender as a user.
     * Returns true if the sender is a valid user, false otherwise.
     */
    private boolean tryResolveAsUser(String senderEmail) {
        try {
            var user = graphClient.users().byUserId(senderEmail).get();
            return user != null;
        } catch (ApiException e) {
            if (e.getResponseStatusCode() == 404) {
                logger.debug("Sender '{}' is not a user (404)", senderEmail);
                return false;
            }
            // Other errors - log and return false to try group resolution
            logger.warn("Error checking if sender is a user: {} - {}", e.getResponseStatusCode(), e.getMessage());
            return false;
        } catch (Exception e) {
            logger.warn("Unexpected error checking if sender is a user: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Resolves the group ID for the given email address.
     * Returns the group ID if found, null otherwise.
     */
    private String resolveGroupId(String senderEmail) {
        try {
            // Query groups by mail or proxyAddresses
            var groups = graphClient.groups().get(config -> {
                config.queryParameters.filter = "mail eq '" + senderEmail + "'";
                config.queryParameters.select = new String[]{"id", "displayName", "mail"};
            });

            List<Group> groupList = groups != null ? groups.getValue() : null;
            if (groupList != null && !groupList.isEmpty()) {
                Group group = groupList.get(0);
                logger.debug("Found group '{}' with ID: {}", group.getDisplayName(), group.getId());
                return group.getId();
            }

            logger.debug("No group found with mail: {}", senderEmail);
            return null;
        } catch (ApiException e) {
            logger.warn("Error querying groups: {} - {}", e.getResponseStatusCode(), e.getMessage());
            return null;
        } catch (Exception e) {
            logger.warn("Unexpected error querying groups: {}", e.getMessage());
            return null;
        }
    }
}
