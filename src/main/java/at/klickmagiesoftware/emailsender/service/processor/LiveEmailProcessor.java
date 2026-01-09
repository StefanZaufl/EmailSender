package at.klickmagiesoftware.emailsender.service.processor;

import at.klickmagiesoftware.emailsender.model.EmailData;
import at.klickmagiesoftware.emailsender.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Live email processing strategy that actually sends emails via Microsoft Graph.
 */
@Component
public class LiveEmailProcessor implements EmailProcessingStrategy {

    private static final Logger logger = LoggerFactory.getLogger(LiveEmailProcessor.class);

    private final EmailService emailService;

    public LiveEmailProcessor(EmailService emailService) {
        this.emailService = emailService;
    }

    @Override
    public void process(EmailData emailData) {
        logger.debug("Live processing email for: {}", emailData.getRecipientEmail());
        emailService.sendEmail(emailData);
    }

    @Override
    public String getModeName() {
        return "LIVE";
    }

    @Override
    public String getCompletionMessage() {
        return null; // No additional message needed for live mode
    }
}
