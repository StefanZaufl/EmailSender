package at.klickmagiesoftware.emailsender.service.processor;

import at.klickmagiesoftware.emailsender.exception.EmailSenderException;
import at.klickmagiesoftware.emailsender.model.EmailData;
import at.klickmagiesoftware.emailsender.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dry-run email processing strategy that writes output files to disk instead of sending emails.
 * Useful for testing and previewing email content before actual sending.
 */
@Component
public class DryRunEmailProcessor implements EmailProcessingStrategy {

    private static final Logger logger = LoggerFactory.getLogger(DryRunEmailProcessor.class);

    private final EmailService emailService;
    private Path outputDirectory;

    public DryRunEmailProcessor(EmailService emailService) {
        this.emailService = emailService;
        this.outputDirectory = Path.of("./output");
    }

    /**
     * Sets the output directory for dry-run files.
     *
     * @param outputDir the directory path
     */
    public void setOutputDirectory(String outputDir) {
        this.outputDirectory = Path.of(outputDir);
    }

    /**
     * Gets the current output directory.
     *
     * @return the output directory path
     */
    public Path getOutputDirectory() {
        return outputDirectory;
    }

    @Override
    public void initialize() {
        try {
            if (!Files.exists(outputDirectory)) {
                Files.createDirectories(outputDirectory);
                logger.info("Created output directory: {}", outputDirectory);
            }
        } catch (IOException e) {
            throw new EmailSenderException("Failed to create output directory: " + outputDirectory, e);
        }
    }

    @Override
    public void process(EmailData emailData) {
        logger.debug("Dry-run processing email for: {}", emailData.getRecipientEmail());

        EmailService.EmailContent content = emailService.prepareEmail(emailData);

        try {
            writeOutputFiles(content);
        } catch (IOException e) {
            throw new EmailSenderException("Failed to write dry-run output for row " + emailData.getRowNumber(), e);
        }
    }

    private void writeOutputFiles(EmailService.EmailContent content) throws IOException {
        // Generate safe filename from email
        String safeEmail = content.recipientEmail().replaceAll("[^a-zA-Z0-9@.]", "_");
        String baseFilename = String.format("row%d_%s", content.rowNumber(), safeEmail);

        // Write HTML body
        Path htmlPath = outputDirectory.resolve(baseFilename + "_body.html");
        Files.writeString(htmlPath, content.htmlBody());
        logger.info("  -> Written: {}", htmlPath);

        // Write PDF attachment
        Path pdfPath = outputDirectory.resolve(baseFilename + "_attachment.pdf");
        Files.write(pdfPath, content.pdfAttachment());
        logger.info("  -> Written: {}", pdfPath);

        // Write email metadata
        Path metaPath = outputDirectory.resolve(baseFilename + "_meta.txt");
        String metadata = String.format("""
                To: %s
                Subject: %s
                Row Number: %d
                """, content.recipientEmail(), content.subject(), content.rowNumber());
        Files.writeString(metaPath, metadata);
        logger.info("  -> Written: {}", metaPath);
    }

    @Override
    public String getModeName() {
        return "DRY RUN";
    }

    @Override
    public String getCompletionMessage() {
        return "DRY RUN: Output files written to: " + outputDirectory;
    }
}
