package at.klickmagiesoftware.emailsender.service;

import at.klickmagiesoftware.emailsender.config.AppConfig;
import at.klickmagiesoftware.emailsender.exception.EmailSenderException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for generating CSV reports of email sending results.
 * Each report contains the email address and whether the send was successful.
 */
@Service
public class ReportService {

    private static final Logger logger = LoggerFactory.getLogger(ReportService.class);

    private static final String HEADER_EMAIL = "Email";
    private static final String HEADER_STATUS = "Status";
    private static final String STATUS_SUCCESS = "Success";
    private static final String STATUS_FAILED = "Failed";
    private static final String STATUS_INTERRUPTED = "Interrupted";

    private final AppConfig appConfig;
    private final List<EmailResult> results = new ArrayList<>();

    public ReportService(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    /**
     * Records a successful email send.
     *
     * @param email the recipient email address
     */
    public void recordSuccess(String email) {
        results.add(new EmailResult(email, true, null));
        logger.debug("Recorded success for: {}", email);
    }

    /**
     * Records a successful email send for multiple recipients.
     * Each recipient is recorded as a separate success entry.
     *
     * @param emails the list of recipient email addresses
     */
    public void recordSuccess(List<String> emails) {
        for (String email : emails) {
            recordSuccess(email);
        }
    }

    /**
     * Records a failed email send.
     *
     * @param email        the recipient email address
     * @param errorMessage the error message describing the failure
     */
    public void recordFailure(String email, String errorMessage) {
        results.add(new EmailResult(email, false, errorMessage));
        logger.debug("Recorded failure for: {} - {}", email, errorMessage);
    }

    /**
     * Records a failed email send for multiple recipients.
     * Each recipient is recorded as a separate failure entry with the same error message.
     *
     * @param emails       the list of recipient email addresses
     * @param errorMessage the error message describing the failure
     */
    public void recordFailure(List<String> emails, String errorMessage) {
        for (String email : emails) {
            recordFailure(email, errorMessage);
        }
    }

    /**
     * Records an interrupted email (not processed due to interruption).
     *
     * @param email the recipient email address
     */
    public void recordInterrupted(String email) {
        results.add(new EmailResult(email, false, STATUS_INTERRUPTED));
        logger.debug("Recorded interrupted for: {}", email);
    }

    /**
     * Records interrupted emails for multiple recipients.
     * Each recipient is recorded as a separate interrupted entry.
     *
     * @param emails the list of recipient email addresses
     */
    public void recordInterrupted(List<String> emails) {
        for (String email : emails) {
            recordInterrupted(email);
        }
    }

    /**
     * Writes the collected results to a CSV report file.
     * The output path is determined by the configuration.
     *
     * @throws EmailSenderException if the report cannot be written
     */
    public void writeReport() {
        AppConfig.ReportConfig reportConfig = appConfig.getReport();
        if (reportConfig == null || reportConfig.getOutputPath() == null || reportConfig.getOutputPath().isBlank()) {
            logger.info("Report output path not configured, skipping report generation");
            return;
        }

        writeReport(reportConfig.getOutputPath());
    }

    /**
     * Writes the collected results to a CSV report file at the specified path.
     *
     * @param outputPath the path where the report should be written
     * @throws EmailSenderException if the report cannot be written
     */
    public void writeReport(String outputPath) {
        if (results.isEmpty()) {
            logger.info("No results to write to report");
            return;
        }

        Path reportPath;
        try {
            reportPath = Path.of(outputPath);
        } catch (java.nio.file.InvalidPathException e) {
            throw new EmailSenderException("Invalid report path: " + outputPath + " - " + e.getMessage(), e);
        }

        // Create parent directories if they don't exist
        try {
            Path parentDir = reportPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                logger.debug("Created report directory: {}", parentDir);
            }
        } catch (IOException e) {
            throw new EmailSenderException("Failed to create report directory: " + e.getMessage(), e);
        }

        try (FileWriter writer = new FileWriter(reportPath.toFile());
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT
                     .builder()
                     .setHeader(HEADER_EMAIL, HEADER_STATUS)
                     .build())) {

            for (EmailResult result : results) {
                String status;
                if (result.success()) {
                    status = STATUS_SUCCESS;
                } else if (STATUS_INTERRUPTED.equals(result.errorMessage())) {
                    status = STATUS_INTERRUPTED;
                } else {
                    status = STATUS_FAILED;
                }
                csvPrinter.printRecord(result.email(), status);
            }

            csvPrinter.flush();
            logger.info("Report written to: {} ({} records)", outputPath, results.size());

        } catch (IOException e) {
            throw new EmailSenderException("Failed to write report to " + outputPath + ": " + e.getMessage(), e);
        }
    }

    /**
     * Clears all recorded results.
     * Useful for testing or when processing multiple batches.
     */
    public void clear() {
        results.clear();
    }

    /**
     * Returns the number of recorded results.
     *
     * @return the count of recorded results
     */
    public int getResultCount() {
        return results.size();
    }

    /**
     * Returns the number of successful results.
     *
     * @return the count of successful results
     */
    public int getSuccessCount() {
        return (int) results.stream().filter(EmailResult::success).count();
    }

    /**
     * Returns the number of failed results (not including interrupted).
     *
     * @return the count of failed results
     */
    public int getFailureCount() {
        return (int) results.stream()
                .filter(r -> !r.success() && !STATUS_INTERRUPTED.equals(r.errorMessage()))
                .count();
    }

    /**
     * Returns the number of interrupted results.
     *
     * @return the count of interrupted results
     */
    public int getInterruptedCount() {
        return (int) results.stream()
                .filter(r -> STATUS_INTERRUPTED.equals(r.errorMessage()))
                .count();
    }

    /**
     * Returns a copy of all recorded results.
     *
     * @return a list of email results
     */
    public List<EmailResult> getResults() {
        return new ArrayList<>(results);
    }

    /**
     * Record representing the result of an email send attempt.
     *
     * @param email        the recipient email address
     * @param success      whether the send was successful
     * @param errorMessage the error message if failed, null if successful
     */
    public record EmailResult(String email, boolean success, String errorMessage) {
    }
}
