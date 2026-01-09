package at.klickmagiesoftware.emailsender.service;

import at.klickmagiesoftware.emailsender.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * Service to determine whether emails should be sent from a group or directly from the user.
 * When sender-group is configured, emails are sent via the user but with the group's address
 * in the "from" field. Otherwise, emails are sent directly from the user.
 */
@Service
public class SenderTypeResolver {

    private static final Logger logger = LoggerFactory.getLogger(SenderTypeResolver.class);

    private final AppConfig appConfig;

    private volatile boolean sendFromGroup;

    public SenderTypeResolver(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @PostConstruct
    public void init() {
        resolveSenderType();
    }

    /**
     * Returns whether emails should be sent from a group mailbox.
     * When true, the email's "from" field will be set to the group address.
     */
    public boolean isSendFromGroup() {
        return sendFromGroup;
    }

    /**
     * Returns the group email address if configured, null otherwise.
     */
    public String getSenderGroup() {
        return appConfig.getEmail().getSenderGroup();
    }

    /**
     * Returns the sender user email address.
     */
    public String getSenderEmail() {
        return appConfig.getEmail().getSenderEmail();
    }

    /**
     * Determines the sender type based on configuration.
     * If sender-group is configured, emails will be sent from that group.
     */
    private void resolveSenderType() {
        String senderEmail = appConfig.getEmail().getSenderEmail();
        String senderGroup = appConfig.getEmail().getSenderGroup();

        if (senderGroup != null && !senderGroup.isBlank()) {
            this.sendFromGroup = true;
            logger.info("Configured to send emails from group '{}' via user '{}'", senderGroup, senderEmail);
        } else {
            this.sendFromGroup = false;
            logger.info("Configured to send emails directly from user '{}'", senderEmail);
        }
    }
}
