package at.klickmagiesoftware.emailsender.service;

import com.microsoft.graph.models.Attachment;
import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.EmailAddress;
import com.microsoft.graph.models.FileAttachment;
import com.microsoft.graph.models.ItemBody;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.Recipient;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.sendmail.SendMailPostRequestBody;
import com.microsoft.kiota.ApiException;
import at.klickmagiesoftware.emailsender.config.AppConfig;
import at.klickmagiesoftware.emailsender.exception.EmailSenderException;
import at.klickmagiesoftware.emailsender.model.EmailData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for sending emails via Microsoft Graph API.
 */
@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final GraphServiceClient graphClient;
    private final AppConfig appConfig;
    private final TemplateService templateService;
    private final PdfGeneratorService pdfGeneratorService;
    private final SenderTypeResolver senderTypeResolver;

    public EmailService(GraphServiceClient graphClient, AppConfig appConfig,
                        TemplateService templateService, PdfGeneratorService pdfGeneratorService,
                        SenderTypeResolver senderTypeResolver) {
        this.graphClient = graphClient;
        this.appConfig = appConfig;
        this.templateService = templateService;
        this.pdfGeneratorService = pdfGeneratorService;
        this.senderTypeResolver = senderTypeResolver;
    }

    /**
     * Sends a personalized email with PDF attachment to the recipients specified in the EmailData.
     * Supports multiple recipients (semicolon-separated in the data source).
     * Includes retry logic with exponential backoff for handling throttling (HTTP 429) errors.
     *
     * @param emailData the data for the email to send
     */
    public void sendEmail(EmailData emailData) {
        String recipientsDisplay = emailData.getRecipientsAsString();
        logger.info("Sending email to: {} (row {}) via {}",
                recipientsDisplay, emailData.getRowNumber(),
                senderTypeResolver.isSendFromGroup() ? "group" : "user");

        try {
            // Process templates
            String subject = templateService.processSubject(emailData);
            String htmlBody = templateService.processEmailBody(emailData);
            byte[] pdfAttachment = pdfGeneratorService.generatePdf(emailData);

            // Build the email message with all recipients
            Message message = createMessage(emailData.getRecipientEmails(), subject, htmlBody, pdfAttachment);

            // Send via Microsoft Graph with retry logic
            sendWithRetry(emailData, message, subject, htmlBody);

            logger.info("Email sent successfully to: {} (row {})", recipientsDisplay, emailData.getRowNumber());

        } catch (EmailSenderException e) {
            throw e;
        } catch (Exception e) {
            throw new EmailSenderException("Failed to send email to " + recipientsDisplay +
                    " (row " + emailData.getRowNumber() + ")", e);
        }
    }

    /**
     * Sends the email with retry logic for handling throttling (HTTP 429) errors.
     * Uses exponential backoff between retries.
     * Routes to either user sendMail or group sendMail API based on sender type.
     */
    private void sendWithRetry(EmailData emailData, Message message, String subject, String htmlBody) {
        AppConfig.ThrottlingConfig throttling = appConfig.getThrottling();
        int maxRetries = throttling.isEnabled() ? throttling.getMaxRetries() : 0;
        long delayMs = throttling.getInitialRetryDelayMs();
        String recipientsDisplay = emailData.getRecipientsAsString();

        Exception lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                if (senderTypeResolver.isSendFromGroup()) {
                    sendViaGroup(message);
                } else {
                    sendViaUser(message);
                }
                return; // Success
            } catch (ApiException e) {
                lastException = e;
                int statusCode = e.getResponseStatusCode();

                if (statusCode == 429 && attempt < maxRetries) {
                    // Throttled - retry with exponential backoff
                    long retryAfterMs = parseRetryAfter(e, delayMs);
                    logger.warn("Throttled (HTTP 429) sending to {} - retrying in {}ms (attempt {}/{})",
                            recipientsDisplay, retryAfterMs, attempt + 1, maxRetries);

                    try {
                        Thread.sleep(retryAfterMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new EmailSenderException("Email sending interrupted during retry", ie);
                    }

                    // Exponential backoff for next retry
                    delayMs = delayMs * 2;
                } else if (statusCode == 429) {
                    // Max retries exceeded
                    throw new EmailSenderException("Max retries exceeded for email to " +
                            recipientsDisplay + " (row " + emailData.getRowNumber() +
                            ") - still being throttled after " + maxRetries + " attempts", e);
                } else {
                    // Non-throttling error - don't retry
                    throw new EmailSenderException("Failed to send email to " + recipientsDisplay +
                            " (row " + emailData.getRowNumber() + ") - HTTP " + statusCode, e);
                }
            } catch (EmailSenderException e) {
                // Rethrow our own exceptions without wrapping
                throw e;
            } catch (Exception e) {
                // Non-API exception - don't retry
                throw new EmailSenderException("Failed to send email to " + recipientsDisplay +
                        " (row " + emailData.getRowNumber() + ")", e);
            }
        }

        // Should not reach here, but just in case
        throw new EmailSenderException("Failed to send email to " + recipientsDisplay +
                " (row " + emailData.getRowNumber() + ")", lastException);
    }

    /**
     * Sends email via the users sendMail API endpoint.
     */
    private void sendViaUser(Message message) {
        SendMailPostRequestBody sendMailRequest = new SendMailPostRequestBody();
        sendMailRequest.setMessage(message);
        sendMailRequest.setSaveToSentItems(true);

        String senderEmail = senderTypeResolver.getSenderEmail();
        graphClient.users().byUserId(senderEmail).sendMail().post(sendMailRequest);
    }

    /**
     * Sends email from a group mailbox using a user's sendMail endpoint.
     * Microsoft Graph doesn't have a direct /groups/{id}/sendMail endpoint.
     * Instead, we use /users/{senderEmail}/sendMail and set the 'from' property
     * to the group's email address. The sending user must have "Send As"
     * permission on the group in Exchange Online.
     */
    private void sendViaGroup(Message message) {
        String senderEmail = senderTypeResolver.getSenderEmail();
        String senderGroup = senderTypeResolver.getSenderGroup();

        // Set the 'from' property to the group's email address
        Recipient fromRecipient = new Recipient();
        EmailAddress fromAddress = new EmailAddress();
        fromAddress.setAddress(senderGroup);
        fromRecipient.setEmailAddress(fromAddress);
        message.setFrom(fromRecipient);

        SendMailPostRequestBody sendMailRequest = new SendMailPostRequestBody();
        sendMailRequest.setMessage(message);
        sendMailRequest.setSaveToSentItems(true);

        logger.debug("Sending email via user '{}' on behalf of group '{}'", senderEmail, senderGroup);
        graphClient.users().byUserId(senderEmail).sendMail().post(sendMailRequest);
    }

    /**
     * Parses the Retry-After header from the API exception.
     * Returns the suggested wait time in milliseconds, or the default delay if not available.
     */
    private long parseRetryAfter(ApiException e, long defaultDelayMs) {
        // Try to extract Retry-After from response headers
        var headers = e.getResponseHeaders();
        if (headers == null) {
            return defaultDelayMs;
        }
        var retryAfterValues = headers.get("Retry-After");
        if (retryAfterValues != null && !retryAfterValues.isEmpty()) {
            try {
                // Retry-After can be in seconds
                int seconds = Integer.parseInt(retryAfterValues.iterator().next());
                return seconds * 1000L;
            } catch (NumberFormatException nfe) {
                logger.debug("Could not parse Retry-After header: {}", retryAfterValues);
            }
        }
        return defaultDelayMs;
    }

    /**
     * Prepares email content for dry-run mode without actually sending.
     * Returns processed subject, HTML body, and PDF bytes.
     *
     * @param emailData the data for the email
     * @return an EmailContent record with all processed content
     */
    public EmailContent prepareEmail(EmailData emailData) {
        logger.info("Preparing email for: {} (row {})", emailData.getRecipientsAsString(), emailData.getRowNumber());

        String subject = templateService.processSubject(emailData);
        String htmlBody = templateService.processEmailBody(emailData);
        byte[] pdfAttachment = pdfGeneratorService.generatePdf(emailData);

        return new EmailContent(
                emailData.getRecipientEmails(),
                subject,
                htmlBody,
                pdfAttachment,
                emailData.getRowNumber()
        );
    }

    private Message createMessage(List<String> recipientEmails, String subject, String htmlBody, byte[] pdfAttachment) {
        Message message = new Message();

        // Set recipients - support multiple recipients
        List<Recipient> recipients = new ArrayList<>();
        for (String recipientEmail : recipientEmails) {
            Recipient recipient = new Recipient();
            EmailAddress emailAddress = new EmailAddress();
            emailAddress.setAddress(recipientEmail);
            recipient.setEmailAddress(emailAddress);
            recipients.add(recipient);
        }
        message.setToRecipients(recipients);

        // Set subject
        message.setSubject(subject);

        // Set HTML body
        ItemBody body = new ItemBody();
        body.setContentType(BodyType.Html);
        body.setContent(htmlBody);
        message.setBody(body);

        // Add PDF attachment
        FileAttachment attachment = new FileAttachment();
        attachment.setOdataType("#microsoft.graph.fileAttachment");
        attachment.setName(appConfig.getEmail().getAttachmentFilename());
        attachment.setContentType("application/pdf");
        attachment.setContentBytes(pdfAttachment);

        List<Attachment> attachments = new ArrayList<>();
        attachments.add(attachment);
        message.setAttachments(attachments);

        return message;
    }

    /**
     * Record to hold prepared email content for dry-run mode.
     * Supports multiple recipients.
     */
    public record EmailContent(
            List<String> recipientEmails,
            String subject,
            String htmlBody,
            byte[] pdfAttachment,
            int rowNumber
    ) {
        /**
         * Returns the first recipient email for backwards compatibility.
         */
        public String recipientEmail() {
            return recipientEmails.isEmpty() ? null : recipientEmails.getFirst();
        }

        /**
         * Returns a comma-separated string of all recipient emails.
         */
        public String recipientsAsString() {
            return String.join(", ", recipientEmails);
        }
    }
}
