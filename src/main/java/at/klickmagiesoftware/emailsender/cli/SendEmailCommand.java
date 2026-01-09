package at.klickmagiesoftware.emailsender.cli;

import at.klickmagiesoftware.emailsender.config.AppConfig;
import at.klickmagiesoftware.emailsender.exception.EmailSenderException;
import at.klickmagiesoftware.emailsender.model.EmailData;
import at.klickmagiesoftware.emailsender.service.DataSourceReader;
import at.klickmagiesoftware.emailsender.service.ReportService;
import at.klickmagiesoftware.emailsender.service.processor.DryRunEmailProcessor;
import at.klickmagiesoftware.emailsender.service.processor.EmailProcessingStrategy;
import at.klickmagiesoftware.emailsender.service.processor.LiveEmailProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

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
    private final LiveEmailProcessor liveEmailProcessor;
    private final DryRunEmailProcessor dryRunEmailProcessor;
    private final ReportService reportService;

    @Option(names = {"--dry-run"}, description = "Process everything but don't send emails - write output files to disk instead")
    private boolean dryRun;

    @Option(names = {"--verbose", "-v"}, description = "Enable verbose logging")
    private boolean verbose;

    @Option(names = {"--output-dir", "-o"}, description = "Output directory for dry-run mode (default: ./output)")
    private String outputDir = "./output";

    public SendEmailCommand(AppConfig appConfig,
                            List<DataSourceReader> dataSourceReaders,
                            LiveEmailProcessor liveEmailProcessor,
                            DryRunEmailProcessor dryRunEmailProcessor,
                            ReportService reportService) {
        this.appConfig = appConfig;
        this.dataSourceReaders = dataSourceReaders;
        this.liveEmailProcessor = liveEmailProcessor;
        this.dryRunEmailProcessor = dryRunEmailProcessor;
        this.reportService = reportService;
    }

    @Override
    public Integer call() {
        try {
            logger.info("Starting email sender...");

            // Select the appropriate processing strategy
            EmailProcessingStrategy processor = selectProcessor();

            logger.info("*** {} MODE ***", processor.getModeName());

            if (verbose) {
                logger.info("Verbose mode enabled");
            }

            // Validate configuration
            validateConfiguration();

            // Initialize the processor
            processor.initialize();

            // Read data from source
            List<EmailData> emailDataList = readDataSource();

            if (emailDataList.isEmpty()) {
                logger.warn("No rows found to process. Check your process column and value configuration.");
                return 0;
            }

            logger.info("Found {} rows to process", emailDataList.size());

            // Log throttling configuration for live mode
            AppConfig.ThrottlingConfig throttling = appConfig.getThrottling();
            boolean shouldThrottle = !dryRun && throttling.isEnabled();
            if (shouldThrottle) {
                logger.info("Throttling enabled: {} emails/minute ({}ms delay between emails)",
                        throttling.getEmailsPerMinute(), throttling.getDelayBetweenEmailsMs());
            }

            // Process each row using the selected strategy
            List<FailedEmail> failures = new ArrayList<>();
            int successCount = 0;

            for (int i = 0; i < emailDataList.size(); i++) {
                EmailData emailData = emailDataList.get(i);
                logger.info("Processing {} of {}: {}", i + 1, emailDataList.size(), emailData.getRecipientEmail());

                try {
                    processor.process(emailData);
                    successCount++;
                    reportService.recordSuccess(emailData.getRecipientEmail());

                    // Apply throttling delay between emails (not after the last one)
                    if (shouldThrottle && i < emailDataList.size() - 1) {
                        long delayMs = throttling.getDelayBetweenEmailsMs();
                        if (delayMs > 0) {
                            logger.debug("Throttling: waiting {}ms before next email", delayMs);
                            //noinspection BusyWait
                            Thread.sleep(delayMs);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Email sending interrupted");
                    failures.add(new FailedEmail(emailData, "Interrupted"));
                    reportService.recordFailure(emailData.getRecipientEmail(), "Interrupted");
                    break;
                } catch (Exception e) {
                    logger.error("Failed to process row {}: {}", emailData.getRowNumber(), e.getMessage());
                    if (verbose) {
                        logger.error("Stack trace:", e);
                    }
                    failures.add(new FailedEmail(emailData, e.getMessage()));
                    reportService.recordFailure(emailData.getRecipientEmail(), e.getMessage());
                }
            }

            // Write the CSV report
            reportService.writeReport();

            // Report results
            printSummary(successCount, failures, emailDataList.size(), processor);

            return failures.isEmpty() ? 0 : 1;

        } catch (Exception e) {
            logger.error("Fatal error: {}", e.getMessage());
            if (verbose) {
                logger.error("Stack trace:", e);
            }
            return 2;
        }
    }

    private EmailProcessingStrategy selectProcessor() {
        if (dryRun) {
            dryRunEmailProcessor.setOutputDirectory(outputDir);
            return dryRunEmailProcessor;
        }
        return liveEmailProcessor;
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

    private void printSummary(int successCount, List<FailedEmail> failures, int totalCount,
                              EmailProcessingStrategy processor) {
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

        String completionMessage = processor.getCompletionMessage();
        if (completionMessage != null) {
            logger.info("----------------------------------------");
            logger.info(completionMessage);
        }
    }

    private record FailedEmail(EmailData emailData, String errorMessage) {}
}
