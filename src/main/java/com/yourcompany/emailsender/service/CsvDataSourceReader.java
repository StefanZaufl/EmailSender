package com.yourcompany.emailsender.service;

import com.yourcompany.emailsender.exception.EmailSenderException;
import com.yourcompany.emailsender.model.EmailData;
import com.yourcompany.emailsender.util.EmailSenderConstants;
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
 */
@Service
public class CsvDataSourceReader implements DataSourceReader {

    private static final Logger logger = LoggerFactory.getLogger(CsvDataSourceReader.class);

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
                    String recipientEmail = record.get(recipientColumn);
                    if (recipientEmail == null || recipientEmail.isBlank()) {
                        logger.warn("Row {}: Empty recipient email, skipping", rowNumber);
                        continue;
                    }

                    if (!EmailSenderConstants.isValidEmail(recipientEmail)) {
                        logger.warn("Row {}: Invalid email format '{}', skipping", rowNumber, recipientEmail);
                        continue;
                    }

                    Map<String, String> fields = new HashMap<>();
                    for (String header : headers) {
                        fields.put(header, record.get(header));
                    }

                    emailDataList.add(new EmailData(recipientEmail.trim(), fields, rowNumber));
                    logger.debug("Row {}: Added for processing - {}", rowNumber, recipientEmail);
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
