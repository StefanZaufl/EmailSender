package at.klickmagiesoftware.emailsender.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Model class representing data for a single email to be sent.
 * Contains the recipient email addresses and all field values from the data source row.
 * Supports multiple recipients (semicolon-separated in the data source).
 */
public class EmailData {

    private final List<String> recipientEmails;
    private final Map<String, String> fields;
    private final int rowNumber;

    /**
     * Creates EmailData with a single recipient email.
     *
     * @param recipientEmail the recipient email address
     * @param fields all field values from the data source row
     * @param rowNumber the row number in the data source
     */
    public EmailData(String recipientEmail, Map<String, String> fields, int rowNumber) {
        this.recipientEmails = new ArrayList<>();
        this.recipientEmails.add(recipientEmail);
        this.fields = new HashMap<>(fields);
        this.rowNumber = rowNumber;
    }

    /**
     * Creates EmailData with multiple recipient emails.
     *
     * @param recipientEmails the list of recipient email addresses
     * @param fields all field values from the data source row
     * @param rowNumber the row number in the data source
     */
    public EmailData(List<String> recipientEmails, Map<String, String> fields, int rowNumber) {
        this.recipientEmails = new ArrayList<>(recipientEmails);
        this.fields = new HashMap<>(fields);
        this.rowNumber = rowNumber;
    }

    /**
     * Returns the first recipient email address.
     * For backwards compatibility with single-recipient use cases.
     *
     * @return the first recipient email address
     */
    public String getRecipientEmail() {
        return recipientEmails.isEmpty() ? null : recipientEmails.getFirst();
    }

    /**
     * Returns all recipient email addresses.
     *
     * @return a copy of the list of recipient email addresses
     */
    public List<String> getRecipientEmails() {
        return new ArrayList<>(recipientEmails);
    }

    /**
     * Returns whether this EmailData has multiple recipients.
     *
     * @return true if there are multiple recipients
     */
    public boolean hasMultipleRecipients() {
        return recipientEmails.size() > 1;
    }

    /**
     * Returns a comma-separated string of all recipient emails.
     * Useful for display and logging purposes.
     *
     * @return comma-separated list of all recipient emails
     */
    public String getRecipientsAsString() {
        return String.join(", ", recipientEmails);
    }

    public Map<String, String> getFields() {
        return new HashMap<>(fields);
    }

    public String getField(String fieldName) {
        return fields.get(fieldName);
    }

    public int getRowNumber() {
        return rowNumber;
    }

    @Override
    public String toString() {
        return "EmailData{" +
                "recipientEmails=" + recipientEmails +
                ", rowNumber=" + rowNumber +
                ", fields=" + fields +
                '}';
    }
}
