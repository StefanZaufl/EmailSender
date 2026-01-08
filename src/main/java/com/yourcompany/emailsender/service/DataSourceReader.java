package com.yourcompany.emailsender.service;

import com.yourcompany.emailsender.model.EmailData;

import java.util.List;

/**
 * Interface for reading data from various data source formats.
 * Implementations should read rows from the data source and convert them to EmailData objects.
 */
public interface DataSourceReader {

    /**
     * Checks if this reader supports the given data source type.
     *
     * @param type the data source type (e.g., "csv", "excel")
     * @return true if this reader can handle the type
     */
    boolean supports(String type);

    /**
     * Reads all rows from the data source that should be processed.
     * Only rows where the process column matches the process value are returned.
     *
     * @param path            the path to the data file
     * @param sheetName       the sheet name (for Excel files, can be null for CSV)
     * @param processColumn   the column name that determines if a row should be processed
     * @param processValue    the value in the process column that triggers processing
     * @param recipientColumn the column name containing the recipient email address
     * @return list of EmailData objects for rows that should be processed
     */
    List<EmailData> readData(String path, String sheetName, String processColumn,
                             String processValue, String recipientColumn);
}
