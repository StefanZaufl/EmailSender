package com.yourcompany.emailsender.service;

import com.microsoft.graph.models.Attachment;
import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.EmailAddress;
import com.microsoft.graph.models.FileAttachment;
import com.microsoft.graph.models.ItemBody;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.Recipient;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.sendmail.SendMailPostRequestBody;
import com.yourcompany.emailsender.config.AppConfig;
import com.yourcompany.emailsender.exception.EmailSenderException;
import com.yourcompany.emailsender.model.EmailData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
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

    public EmailService(GraphServiceClient graphClient, AppConfig appConfig,
                        TemplateService templateService, PdfGeneratorService pdfGeneratorService) {
        this.graphClient = graphClient;
        this.appConfig = appConfig;
        this.templateService = templateService;
        this.pdfGeneratorService = pdfGeneratorService;
    }

    /**
     * Sends a personalized email with PDF attachment to the recipient specified in the EmailData.
     *
     * @param emailData the data for the email to send
     */
    public void sendEmail(EmailData emailData) {
        logger.info("Sending email to: {} (row {})", emailData.getRecipientEmail(), emailData.getRowNumber());

        try {
            // Process templates
            String subject = templateService.processSubject(emailData);
            String htmlBody = templateService.processEmailBody(emailData);
            byte[] pdfAttachment = pdfGeneratorService.generatePdf(emailData);

            // Build the email message
            Message message = createMessage(emailData.getRecipientEmail(), subject, htmlBody, pdfAttachment);

            // Send via Microsoft Graph
            SendMailPostRequestBody sendMailRequest = new SendMailPostRequestBody();
            sendMailRequest.setMessage(message);
            sendMailRequest.setSaveToSentItems(true);

            String senderEmail = appConfig.getMicrosoft().getSenderEmail();
            graphClient.users().byUserId(senderEmail).sendMail().post(sendMailRequest);

            logger.info("Email sent successfully to: {} (row {})", emailData.getRecipientEmail(), emailData.getRowNumber());

        } catch (Exception e) {
            throw new EmailSenderException("Failed to send email to " + emailData.getRecipientEmail() +
                    " (row " + emailData.getRowNumber() + ")", e);
        }
    }

    /**
     * Prepares email content for dry-run mode without actually sending.
     * Returns processed subject, HTML body, and PDF bytes.
     *
     * @param emailData the data for the email
     * @return an EmailContent record with all processed content
     */
    public EmailContent prepareEmail(EmailData emailData) {
        logger.info("Preparing email for: {} (row {})", emailData.getRecipientEmail(), emailData.getRowNumber());

        String subject = templateService.processSubject(emailData);
        String htmlBody = templateService.processEmailBody(emailData);
        byte[] pdfAttachment = pdfGeneratorService.generatePdf(emailData);

        return new EmailContent(
                emailData.getRecipientEmail(),
                subject,
                htmlBody,
                pdfAttachment,
                emailData.getRowNumber()
        );
    }

    private Message createMessage(String recipientEmail, String subject, String htmlBody, byte[] pdfAttachment) {
        Message message = new Message();

        // Set recipient
        Recipient recipient = new Recipient();
        EmailAddress emailAddress = new EmailAddress();
        emailAddress.setAddress(recipientEmail);
        recipient.setEmailAddress(emailAddress);
        message.setToRecipients(List.of(recipient));

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
     */
    public record EmailContent(
            String recipientEmail,
            String subject,
            String htmlBody,
            byte[] pdfAttachment,
            int rowNumber
    ) {}
}
