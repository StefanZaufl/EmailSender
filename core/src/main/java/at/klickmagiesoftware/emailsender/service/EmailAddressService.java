package at.klickmagiesoftware.emailsender.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Service for email address validation and parsing.
 * Provides centralized email address handling for the application.
 */
@Service
public class EmailAddressService {

    private static final Logger logger = LoggerFactory.getLogger(EmailAddressService.class);

    /**
     * Delimiter used to separate multiple email addresses in a single field.
     */
    public static final String RECIPIENT_DELIMITER = ";";

    /**
     * Pattern for validating email addresses.
     * Uses a practical regex that covers most valid email formats.
     */
    public static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@([a-zA-Z0-9]+([.\\-][a-zA-Z0-9]+)?)+\\.[a-zA-Z]{2,}$"
    );

    /**
     * Validates if the given string is a valid email address format.
     *
     * @param email the email address to validate
     * @return true if the email format is valid, false otherwise
     */
    public boolean isValidEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    /**
     * Parses a potentially semicolon-separated list of email addresses and validates each one.
     * Invalid email addresses are logged as warnings but processing continues with valid ones.
     *
     * @param recipientValue the raw recipient value from the data source (may contain multiple emails separated by semicolons)
     * @param rowNumber the row number for logging purposes
     * @return a list of valid email addresses (may be empty if all are invalid)
     */
    public List<String> parseAndValidateRecipients(String recipientValue, int rowNumber) {
        List<String> validEmails = new ArrayList<>();

        if (recipientValue == null || recipientValue.isBlank()) {
            return validEmails;
        }

        String[] recipients = recipientValue.split(RECIPIENT_DELIMITER);

        for (String recipient : recipients) {
            String trimmedEmail = recipient.trim();
            if (trimmedEmail.isEmpty()) {
                continue; // Skip empty entries between semicolons
            }

            if (isValidEmail(trimmedEmail)) {
                validEmails.add(trimmedEmail);
            } else {
                logger.warn("Row {}: Invalid email format '{}', skipping this recipient", rowNumber, trimmedEmail);
            }
        }

        return validEmails;
    }
}
