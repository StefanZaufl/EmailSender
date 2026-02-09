package at.klickmagiesoftware.emailsender.service;

import at.klickmagiesoftware.emailsender.exception.EmailSenderException;
import at.klickmagiesoftware.emailsender.model.EmailData;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data source reader implementation for Excel files (.xlsx and .xls).
 * Supports multiple recipients per row, separated by semicolons.
 */
@Service
public class ExcelDataSourceReader implements DataSourceReader {

    private static final Logger logger = LoggerFactory.getLogger(ExcelDataSourceReader.class);
    private final DataFormatter dataFormatter = new DataFormatter();
    private final EmailAddressService emailAddressService;

    public ExcelDataSourceReader(EmailAddressService emailAddressService) {
        this.emailAddressService = emailAddressService;
    }

    @Override
    public boolean supports(String type) {
        return "excel".equalsIgnoreCase(type) || "xlsx".equalsIgnoreCase(type) || "xls".equalsIgnoreCase(type);
    }

    @Override
    public List<EmailData> readData(String path, String sheetName, String processColumn,
                                    String processValue, String recipientColumn) {
        logger.info("Reading Excel file: {} (sheet: {})", path, sheetName);
        List<EmailData> emailDataList = new ArrayList<>();

        try (InputStream inputStream = new FileInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            FormulaEvaluator formulaEvaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Sheet sheet = getSheet(workbook, sheetName, path);
            List<String> headers = readHeaders(sheet, formulaEvaluator);
            validateHeaders(headers, processColumn, recipientColumn, path);

            int processColumnIndex = headers.indexOf(processColumn);
            int recipientColumnIndex = headers.indexOf(recipientColumn);

            // Start from row 1 (skip header row 0)
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                String processColumnValue = getCellValueAsString(row.getCell(processColumnIndex), formulaEvaluator);

                if (processValue.equalsIgnoreCase(processColumnValue)) {
                    String recipientEmailValue = getCellValueAsString(row.getCell(recipientColumnIndex), formulaEvaluator);
                    if (recipientEmailValue == null || recipientEmailValue.isBlank()) {
                        logger.warn("Row {}: Empty recipient email, skipping", rowIndex + 1);
                        continue;
                    }

                    // Parse semicolon-separated recipients and validate each one
                    List<String> validEmails = emailAddressService.parseAndValidateRecipients(recipientEmailValue, rowIndex + 1);

                    if (validEmails.isEmpty()) {
                        logger.warn("Row {}: No valid email addresses found, skipping", rowIndex + 1);
                        continue;
                    }

                    Map<String, String> fields = new HashMap<>();
                    for (int colIndex = 0; colIndex < headers.size(); colIndex++) {
                        Cell cell = row.getCell(colIndex);
                        fields.put(headers.get(colIndex), getCellValueAsString(cell, formulaEvaluator));
                    }

                    emailDataList.add(new EmailData(validEmails, fields, rowIndex + 1));
                    if (validEmails.size() == 1) {
                        logger.debug("Row {}: Added for processing - {}", rowIndex + 1, validEmails.getFirst());
                    } else {
                        logger.debug("Row {}: Added for processing - {} recipients: {}",
                                rowIndex + 1, validEmails.size(), String.join(", ", validEmails));
                    }
                } else {
                    logger.debug("Row {}: Skipped ({}='{}')", rowIndex + 1, processColumn, processColumnValue);
                }
            }

            logger.info("Found {} rows to process in Excel file", emailDataList.size());
            return emailDataList;

        } catch (IOException e) {
            throw new EmailSenderException("Failed to read Excel file: " + path, e);
        }
    }

    private Sheet getSheet(Workbook workbook, String sheetName, String path) {
        Sheet sheet;
        if (sheetName != null && !sheetName.isBlank()) {
            sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                throw new EmailSenderException(
                        "Sheet '" + sheetName + "' not found in Excel file: " + path +
                                ". Available sheets: " + getSheetNames(workbook));
            }
        } else {
            sheet = workbook.getSheetAt(0);
            logger.info("No sheet name specified, using first sheet: {}", sheet.getSheetName());
        }
        return sheet;
    }

    private List<String> readHeaders(Sheet sheet, FormulaEvaluator formulaEvaluator) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            throw new EmailSenderException("Excel file has no header row");
        }

        List<String> headers = new ArrayList<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            String headerValue = getCellValueAsString(cell, formulaEvaluator);
            if (headerValue != null && !headerValue.isBlank()) {
                headers.add(headerValue.trim());
            }
        }
        return headers;
    }

    private String getCellValueAsString(Cell cell, FormulaEvaluator formulaEvaluator) {
        return dataFormatter.formatCellValue(cell, formulaEvaluator);
    }

    private void validateHeaders(List<String> headers, String processColumn,
                                 String recipientColumn, String path) {
        if (!headers.contains(processColumn)) {
            throw new EmailSenderException(
                    "Process column '" + processColumn + "' not found in Excel file: " + path +
                            ". Available columns: " + headers);
        }
        if (!headers.contains(recipientColumn)) {
            throw new EmailSenderException(
                    "Recipient column '" + recipientColumn + "' not found in Excel file: " + path +
                            ". Available columns: " + headers);
        }
    }

    private List<String> getSheetNames(Workbook workbook) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            names.add(workbook.getSheetName(i));
        }
        return names;
    }
}
