package at.klickmagiesoftware.emailsender.service.processor;

import at.klickmagiesoftware.emailsender.model.EmailData;

/**
 * Strategy interface for processing emails.
 * Implementations define how emails are handled (e.g., actually sent or written to disk).
 */
public interface EmailProcessingStrategy {

    /**
     * Process a single email based on the strategy implementation.
     *
     * @param emailData the data for the email to process
     */
    void process(EmailData emailData);

    /**
     * Returns the name of the processing mode for logging purposes.
     *
     * @return the mode name (e.g., "LIVE", "DRY RUN")
     */
    String getModeName();

    /**
     * Returns a message to display when processing is complete.
     * Can return null if no additional message is needed.
     *
     * @return completion message or null
     */
    String getCompletionMessage();

    /**
     * Called before processing begins. Allows the strategy to perform any setup.
     */
    default void initialize() {
        // Default: no initialization needed
    }
}
