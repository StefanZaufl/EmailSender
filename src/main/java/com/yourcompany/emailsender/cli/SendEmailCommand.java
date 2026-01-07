package com.yourcompany.emailsender.cli;

import com.yourcompany.emailsender.config.AppConfig;
import com.yourcompany.emailsender.exception.EmailSenderException;
import com.yourcompany.emailsender.model.EmailData;
import com.yourcompany.emailsender.service.DataSourceReader;
import com.yourcompany.emailsender.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Main CLI command for sending personalized emails with PDF attachments.
 */
@Component
@Command(
        name = "send-emails",
        mixinStandardHelpOptions = true,
        version = "1.0.0",
        description = "Sends personalized emails with PDF attachments based on data from CSV/Excel files."
)
public class SendEmailCommand implements Callable<Integer> {

    private static final Logger logger = LoggerFactory.getLogger(SendEmailCommand.class);

    private final AppConfig appConfig;
    private final List<DataSourceReader> dataSourceReaders;
    private final EmailService emailService;

    @Option(names = {"--dry-run"}, description = "Process everything but don't send emails - write output files to disk instead")
    private boolean dryRun;

    @Option(names = {"--verbose", "-v"}, description = "Enable verbose logging")
    private boolean verbose;

    @Option(names = {"--output-dir", "-o"}, description = "Output directory for dry-run mode (default: ./output)")
    private String outputDir = "./output";

    public SendEmailCommand(AppConfig appConfig, List<DataSourceReader> dataSourceReaders, EmailService emailService) {
        this.appConfig = appConfig;
        this.dataSourceReaders = dataSourceReaders;
        this.emailService = emailService;
    }

    @Override
    public Integer call() {
        try {
            logger.info("Starting email sender...");

            if (dryRun) {
                logger.info("*** DRY RUN MODE - No emails will be sent ***");
            }

            if (verbose) {
                logger.info("Verbose mode enabled");
            }

            // Validate configuration
            validateConfiguration();

            // Read data from source
            List<EmailData> emailDataList = readDataSource();

            if (emailDataList.isEmpty()) {
                logger.warn("No rows found to process. Check your process column and value configuration.");
                return 0;
            }

            logger.info("Found {} rows to process", emailDataList.size());

            // Process each row
            List<FailedEmail> failures = new ArrayList<>();
            int successCount = 0;

            for (int i = 0; i < emailDataList.size(); i++) {
                EmailData emailData = emailDataList.get(i);
                logger.info("Processing {} of {}: {}", i + 1, emailDataList.size(), emailData.getRecipientEmail());

                try {
                    if (dryRun) {
                        processDryRun(emailData);
                    } else {
                        emailService.sendEmail(emailData);
                    }
                    successCount++;
                } catch (Exception e) {
                    logger.error("Failed to process row {}: {}", emailData.getRowNumber(), e.getMessage());
                    if (verbose) {
                        logger.error("Stack trace:", e);
                    }
                    failures.add(new FailedEmail(emailData, e.getMessage()));
                }
            }

            // Report results
            printSummary(successCount, failures, emailDataList.size());

            return failures.isEmpty() ? 0 : 1;

        } catch (Exception e) {
            logger.error("Fatal error: {}", e.getMessage());
            if (verbose) {
                logger.error("Stack trace:", e);
            }
            return 2;
        }
    }

    private void validateConfiguration() {
        logger.info("Validating configuration...");

        AppConfig.DatasourceConfig dsConfig = appConfig.getDatasource();
        AppConfig.TemplatesConfig templatesConfig = appConfig.getTemplates();

        // Validate data source file exists
        Path dataSourcePath = Path.of(dsConfig.getPath());
        if (!Files.exists(dataSourcePath)) {
            throw new EmailSenderException("Data source file not found: " + dsConfig.getPath());
        }

        // Validate template files exist
        if (!Files.exists(Path.of(templatesConfig.getEmailBody()))) {
            throw new EmailSenderException("Email body template not found: " + templatesConfig.getEmailBody());
        }

        if (!Files.exists(Path.of(templatesConfig.getAttachment()))) {
            throw new EmailSenderException("Attachment template not found: " + templatesConfig.getAttachment());
        }

        logger.info("Configuration validated successfully");
    }

    private List<EmailData> readDataSource() {
        AppConfig.DatasourceConfig dsConfig = appConfig.getDatasource();
        String dataSourceType = dsConfig.getType();

        DataSourceReader reader = dataSourceReaders.stream()
                .filter(r -> r.supports(dataSourceType))
                .findFirst()
                .orElseThrow(() -> new EmailSenderException(
                        "No data source reader found for type: " + dataSourceType +
                                ". Supported types: csv, excel"));

        return reader.readData(
                dsConfig.getPath(),
                dsConfig.getSheetName(),
                dsConfig.getProcessColumn(),
                dsConfig.getProcessValue(),
                appConfig.getEmail().getRecipientColumn()
        );
    }

    private void processDryRun(EmailData emailData) throws IOException {
        EmailService.EmailContent content = emailService.prepareEmail(emailData);

        // Create output directory if needed
        Path outputPath = Path.of(outputDir);
        if (!Files.exists(outputPath)) {
            Files.createDirectories(outputPath);
        }

        // Generate safe filename from email
        String safeEmail = content.recipientEmail().replaceAll("[^a-zA-Z0-9@.]", "_");
        String baseFilename = String.format("row%d_%s", content.rowNumber(), safeEmail);

        // Write HTML body
        Path htmlPath = outputPath.resolve(baseFilename + "_body.html");
        Files.writeString(htmlPath, content.htmlBody());
        logger.info("  -> Written: {}", htmlPath);

        // Write PDF attachment
        Path pdfPath = outputPath.resolve(baseFilename + "_attachment.pdf");
        Files.write(pdfPath, content.pdfAttachment());
        logger.info("  -> Written: {}", pdfPath);

        // Write email metadata
        Path metaPath = outputPath.resolve(baseFilename + "_meta.txt");
        String metadata = String.format("""
                To: %s
                Subject: %s
                Row Number: %d
                """, content.recipientEmail(), content.subject(), content.rowNumber());
        Files.writeString(metaPath, metadata);
        logger.info("  -> Written: {}", metaPath);
    }

    private void printSummary(int successCount, List<FailedEmail> failures, int totalCount) {
        logger.info("========================================");
        logger.info("PROCESSING COMPLETE");
        logger.info("========================================");
        logger.info("Total rows processed: {}", totalCount);
        logger.info("Successful: {}", successCount);
        logger.info("Failed: {}", failures.size());

        if (!failures.isEmpty()) {
            logger.error("----------------------------------------");
            logger.error("FAILED EMAILS:");
            for (FailedEmail failure : failures) {
                logger.error("  Row {}: {} - {}",
                        failure.emailData.getRowNumber(),
                        failure.emailData.getRecipientEmail(),
                        failure.errorMessage);
            }
        }

        if (dryRun) {
            logger.info("----------------------------------------");
            logger.info("DRY RUN: Output files written to: {}", outputDir);
        }
    }

    private record FailedEmail(EmailData emailData, String errorMessage) {}
}
