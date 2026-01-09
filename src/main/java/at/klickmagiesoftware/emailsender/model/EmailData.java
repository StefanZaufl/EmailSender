package at.klickmagiesoftware.emailsender.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Model class representing data for a single email to be sent.
 * Contains the recipient email address and all field values from the data source row.
 */
public class EmailData {

    private final String recipientEmail;
    private final Map<String, String> fields;
    private final int rowNumber;

    public EmailData(String recipientEmail, Map<String, String> fields, int rowNumber) {
        this.recipientEmail = recipientEmail;
        this.fields = new HashMap<>(fields);
        this.rowNumber = rowNumber;
    }

    public String getRecipientEmail() {
        return recipientEmail;
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
                "recipientEmail='" + recipientEmail + '\'' +
                ", rowNumber=" + rowNumber +
                ", fields=" + fields +
                '}';
    }
}
