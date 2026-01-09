package at.klickmagiesoftware.emailsender.service;

import at.klickmagiesoftware.emailsender.exception.EmailSenderException;
import at.klickmagiesoftware.emailsender.model.EmailData;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExcelDataSourceReaderTest {

    private ExcelDataSourceReader reader;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        reader = new ExcelDataSourceReader();
    }

    @Test
    void supports_excel_returnsTrue() {
        assertTrue(reader.supports("excel"));
        assertTrue(reader.supports("EXCEL"));
        assertTrue(reader.supports("xlsx"));
        assertTrue(reader.supports("XLSX"));
        assertTrue(reader.supports("xls"));
    }

    @Test
    void supports_otherTypes_returnsFalse() {
        assertFalse(reader.supports("csv"));
        assertFalse(reader.supports("json"));
        assertFalse(reader.supports("txt"));
    }

    @Test
    void readData_validExcel_returnsFilteredRows() throws IOException {
        // Arrange
        Path excelFile = createTestExcelFile(
                new String[]{"FullName", "Email", "CompanyName", "SendEmail"},
                new Object[][]{
                        {"John Doe", "john@example.com", "Acme Corp", "Yes"},
                        {"Jane Smith", "jane@example.com", "Tech Inc", "No"},
                        {"Bob Wilson", "bob@example.com", "Global Ltd", "Yes"}
                }
        );

        // Act
        List<EmailData> result = reader.readData(
                excelFile.toString(),
                null,
                "SendEmail",
                "Yes",
                "Email"
        );

        // Assert
        assertEquals(2, result.size());
        assertEquals("john@example.com", result.get(0).getRecipientEmail());
        assertEquals("bob@example.com", result.get(1).getRecipientEmail());
    }

    @Test
    void readData_validExcel_includesAllFields() throws IOException {
        // Arrange
        Path excelFile = createTestExcelFile(
                new String[]{"FullName", "Email", "CompanyName", "SendEmail"},
                new Object[][]{
                        {"John Doe", "john@example.com", "Acme Corp", "Yes"}
                }
        );

        // Act
        List<EmailData> result = reader.readData(
                excelFile.toString(),
                null,
                "SendEmail",
                "Yes",
                "Email"
        );

        // Assert
        assertEquals(1, result.size());
        EmailData emailData = result.getFirst();
        assertEquals("John Doe", emailData.getField("FullName"));
        assertEquals("john@example.com", emailData.getField("Email"));
        assertEquals("Acme Corp", emailData.getField("CompanyName"));
        assertEquals("Yes", emailData.getField("SendEmail"));
    }

    @Test
    void readData_specificSheet_readsFromCorrectSheet() throws IOException {
        // Arrange
        Path excelFile = createMultiSheetExcelFile();

        // Act
        List<EmailData> result = reader.readData(
                excelFile.toString(),
                "SecondSheet",
                "SendEmail",
                "Yes",
                "Email"
        );

        // Assert
        assertEquals(1, result.size());
        assertEquals("second@example.com", result.getFirst().getRecipientEmail());
    }

    @Test
    void readData_noSheetSpecified_usesFirstSheet() throws IOException {
        // Arrange
        Path excelFile = createMultiSheetExcelFile();

        // Act
        List<EmailData> result = reader.readData(
                excelFile.toString(),
                null,
                "SendEmail",
                "Yes",
                "Email"
        );

        // Assert
        assertEquals(1, result.size());
        assertEquals("first@example.com", result.getFirst().getRecipientEmail());
    }

    @Test
    void readData_emptyEmail_skipsRow() throws IOException {
        // Arrange
        Path excelFile = createTestExcelFile(
                new String[]{"FullName", "Email", "CompanyName", "SendEmail"},
                new Object[][]{
                        {"John Doe", "", "Acme Corp", "Yes"},
                        {"Jane Smith", "jane@example.com", "Tech Inc", "Yes"}
                }
        );

        // Act
        List<EmailData> result = reader.readData(
                excelFile.toString(),
                null,
                "SendEmail",
                "Yes",
                "Email"
        );

        // Assert
        assertEquals(1, result.size());
        assertEquals("jane@example.com", result.getFirst().getRecipientEmail());
    }

    @Test
    void readData_noMatchingRows_returnsEmptyList() throws IOException {
        // Arrange
        Path excelFile = createTestExcelFile(
                new String[]{"FullName", "Email", "CompanyName", "SendEmail"},
                new Object[][]{
                        {"John Doe", "john@example.com", "Acme Corp", "No"},
                        {"Jane Smith", "jane@example.com", "Tech Inc", "No"}
                }
        );

        // Act
        List<EmailData> result = reader.readData(
                excelFile.toString(),
                null,
                "SendEmail",
                "Yes",
                "Email"
        );

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void readData_missingProcessColumn_throwsException() throws IOException {
        // Arrange
        Path excelFile = createTestExcelFile(
                new String[]{"FullName", "Email", "CompanyName"},
                new Object[][]{
                        {"John Doe", "john@example.com", "Acme Corp"}
                }
        );

        // Act & Assert
        EmailSenderException exception = assertThrows(
                EmailSenderException.class,
                () -> reader.readData(
                        excelFile.toString(),
                        null,
                        "SendEmail",
                        "Yes",
                        "Email"
                )
        );
        assertTrue(exception.getMessage().contains("SendEmail"));
        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    void readData_invalidSheetName_throwsException() throws IOException {
        // Arrange
        Path excelFile = createTestExcelFile(
                new String[]{"FullName", "Email", "SendEmail"},
                new Object[][]{
                        {"John Doe", "john@example.com", "Yes"}
                }
        );

        // Act & Assert
        EmailSenderException exception = assertThrows(
                EmailSenderException.class,
                () -> reader.readData(
                        excelFile.toString(),
                        "NonExistentSheet",
                        "SendEmail",
                        "Yes",
                        "Email"
                )
        );
        assertTrue(exception.getMessage().contains("NonExistentSheet"));
        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    void readData_nonExistentFile_throwsException() {
        // Act & Assert
        assertThrows(
                EmailSenderException.class,
                () -> reader.readData(
                        "/nonexistent/path/file.xlsx",
                        null,
                        "SendEmail",
                        "Yes",
                        "Email"
                )
        );
    }

    @Test
    void readData_numericValues_convertsToString() throws IOException {
        // Arrange
        Path excelFile = createTestExcelFile(
                new String[]{"FullName", "Email", "Amount", "SendEmail"},
                new Object[][]{
                        {"John Doe", "john@example.com", 1234.56, "Yes"}
                }
        );

        // Act
        List<EmailData> result = reader.readData(
                excelFile.toString(),
                null,
                "SendEmail",
                "Yes",
                "Email"
        );

        // Assert
        assertEquals(1, result.size());
        // POI's DataFormatter formats numbers, so we just check it's not null
        assertNotNull(result.getFirst().getField("Amount"));
    }

    @Test
    void readData_tracksRowNumbers() throws IOException {
        // Arrange
        Path excelFile = createTestExcelFile(
                new String[]{"FullName", "Email", "SendEmail"},
                new Object[][]{
                        {"John Doe", "john@example.com", "No"},
                        {"Jane Smith", "jane@example.com", "Yes"},
                        {"Bob Wilson", "bob@example.com", "No"},
                        {"Alice Brown", "alice@example.com", "Yes"}
                }
        );

        // Act
        List<EmailData> result = reader.readData(
                excelFile.toString(),
                null,
                "SendEmail",
                "Yes",
                "Email"
        );

        // Assert
        assertEquals(2, result.size());
        assertEquals(3, result.get(0).getRowNumber()); // Jane is on Excel row 3 (header=row 1, first data=row 2)
        assertEquals(5, result.get(1).getRowNumber()); // Alice is on Excel row 5
    }

    private Path createTestExcelFile(String[] headers, Object[][] data) throws IOException {
        Path excelFile = tempDir.resolve("test.xlsx");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sheet1");

            // Create header row
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            // Create data rows
            for (int rowIndex = 0; rowIndex < data.length; rowIndex++) {
                Row row = sheet.createRow(rowIndex + 1);
                for (int colIndex = 0; colIndex < data[rowIndex].length; colIndex++) {
                    Object value = data[rowIndex][colIndex];
                    if (value instanceof String) {
                        row.createCell(colIndex).setCellValue((String) value);
                    } else if (value instanceof Number) {
                        row.createCell(colIndex).setCellValue(((Number) value).doubleValue());
                    }
                }
            }

            try (FileOutputStream fos = new FileOutputStream(excelFile.toFile())) {
                workbook.write(fos);
            }
        }

        return excelFile;
    }

    private Path createMultiSheetExcelFile() throws IOException {
        Path excelFile = tempDir.resolve("multi-sheet.xlsx");

        try (Workbook workbook = new XSSFWorkbook()) {
            // First sheet
            Sheet sheet1 = workbook.createSheet("FirstSheet");
            Row header1 = sheet1.createRow(0);
            header1.createCell(0).setCellValue("FullName");
            header1.createCell(1).setCellValue("Email");
            header1.createCell(2).setCellValue("SendEmail");

            Row data1 = sheet1.createRow(1);
            data1.createCell(0).setCellValue("First Person");
            data1.createCell(1).setCellValue("first@example.com");
            data1.createCell(2).setCellValue("Yes");

            // Second sheet
            Sheet sheet2 = workbook.createSheet("SecondSheet");
            Row header2 = sheet2.createRow(0);
            header2.createCell(0).setCellValue("FullName");
            header2.createCell(1).setCellValue("Email");
            header2.createCell(2).setCellValue("SendEmail");

            Row data2 = sheet2.createRow(1);
            data2.createCell(0).setCellValue("Second Person");
            data2.createCell(1).setCellValue("second@example.com");
            data2.createCell(2).setCellValue("Yes");

            try (FileOutputStream fos = new FileOutputStream(excelFile.toFile())) {
                workbook.write(fos);
            }
        }

        return excelFile;
    }
}
