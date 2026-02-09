package at.klickmagiesoftware.emailsender.service;

import at.klickmagiesoftware.emailsender.exception.EmailSenderException;
import at.klickmagiesoftware.emailsender.model.EmailData;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data source reader implementation for CSV files.
 * Supports multiple recipients per row, separated by semicolons.
 */
@Service
public class CsvDataSourceReader implements DataSourceReader {

    private static final Logger logger = LoggerFactory.getLogger(CsvDataSourceReader.class);

    private final EmailAddressService emailAddressService;

    public CsvDataSourceReader(EmailAddressService emailAddressService) {
        this.emailAddressService = emailAddressService;
    }

    @Override
    public boolean supports(String type) {
        return "csv".equalsIgnoreCase(type);
    }

    @Override
    public List<EmailData> readData(String path, String sheetName, String processColumn,
                                    String processValue, String recipientColumn) {
        logger.info("Reading CSV file: {}", path);
        List<EmailData> emailDataList = new ArrayList<>();

        try (Reader reader = new FileReader(path);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            List<String> headers = parser.getHeaderNames();
            validateHeaders(headers, processColumn, recipientColumn, path);

            int rowNumber = 1; // Start at 1 since header is row 0
            for (CSVRecord record : parser) {
                rowNumber++;
                String processColumnValue = record.get(processColumn);

                if (processValue.equalsIgnoreCase(processColumnValue)) {
                    String recipientEmailValue = record.get(recipientColumn);
                    if (recipientEmailValue == null || recipientEmailValue.isBlank()) {
                        logger.warn("Row {}: Empty recipient email, skipping", rowNumber);
                        continue;
                    }

                    // Parse semicolon-separated recipients and validate each one
                    List<String> validEmails = emailAddressService.parseAndValidateRecipients(recipientEmailValue, rowNumber);

                    if (validEmails.isEmpty()) {
                        logger.warn("Row {}: No valid email addresses found, skipping", rowNumber);
                        continue;
                    }

                    Map<String, String> fields = new HashMap<>();
                    for (String header : headers) {
                        fields.put(header, record.get(header));
                    }

                    emailDataList.add(new EmailData(validEmails, fields, rowNumber));
                    if (validEmails.size() == 1) {
                        logger.debug("Row {}: Added for processing - {}", rowNumber, validEmails.getFirst());
                    } else {
                        logger.debug("Row {}: Added for processing - {} recipients: {}",
                                rowNumber, validEmails.size(), String.join(", ", validEmails));
                    }
                } else {
                    logger.debug("Row {}: Skipped ({}='{}')", rowNumber, processColumn, processColumnValue);
                }
            }

            logger.info("Found {} rows to process in CSV file", emailDataList.size());
            return emailDataList;

        } catch (IOException e) {
            throw new EmailSenderException("Failed to read CSV file: " + path, e);
        }
    }

    private void validateHeaders(List<String> headers, String processColumn,
                                 String recipientColumn, String path) {
        if (!headers.contains(processColumn)) {
            throw new EmailSenderException(
                    "Process column '" + processColumn + "' not found in CSV file: " + path +
                            ". Available columns: " + headers);
        }
        if (!headers.contains(recipientColumn)) {
            throw new EmailSenderException(
                    "Recipient column '" + recipientColumn + "' not found in CSV file: " + path +
                            ". Available columns: " + headers);
        }
    }
}
