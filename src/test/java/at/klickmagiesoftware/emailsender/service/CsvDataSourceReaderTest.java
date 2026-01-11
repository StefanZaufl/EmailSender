package at.klickmagiesoftware.emailsender.service;

import at.klickmagiesoftware.emailsender.exception.EmailSenderException;
import at.klickmagiesoftware.emailsender.model.EmailData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvDataSourceReaderTest {

    private CsvDataSourceReader reader;
    private EmailAddressService emailAddressService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        emailAddressService = new EmailAddressService();
        reader = new CsvDataSourceReader(emailAddressService);
    }

    @Test
    void supports_csv_returnsTrue() {
        assertTrue(reader.supports("csv"));
        assertTrue(reader.supports("CSV"));
        assertTrue(reader.supports("Csv"));
    }

    @Test
    void supports_otherTypes_returnsFalse() {
        assertFalse(reader.supports("excel"));
        assertFalse(reader.supports("xlsx"));
        assertFalse(reader.supports("json"));
    }

    @Test
    void readData_validCsv_returnsFilteredRows() throws IOException {
        // Arrange
        String csvContent = """
                FullName,Email,CompanyName,SendEmail
                John Doe,john@example.com,Acme Corp,Yes
                Jane Smith,jane@example.com,Tech Inc,No
                Bob Wilson,bob@example.com,Global Ltd,Yes
                """;
        Path csvFile = tempDir.resolve("test.csv");
        Files.writeString(csvFile, csvContent);

        // Act
        List<EmailData> result = reader.readData(
                csvFile.toString(),
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
    void readData_validCsv_includesAllFields() throws IOException {
        // Arrange
        String csvContent = """
                FullName,Email,CompanyName,SendEmail
                John Doe,john@example.com,Acme Corp,Yes
                """;
        Path csvFile = tempDir.resolve("test.csv");
        Files.writeString(csvFile, csvContent);

        // Act
        List<EmailData> result = reader.readData(
                csvFile.toString(),
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
    void readData_emptyEmail_skipsRow() throws IOException {
        // Arrange
        String csvContent = """
                FullName,Email,CompanyName,SendEmail
                John Doe,,Acme Corp,Yes
                Jane Smith,jane@example.com,Tech Inc,Yes
                """;
        Path csvFile = tempDir.resolve("test.csv");
        Files.writeString(csvFile, csvContent);

        // Act
        List<EmailData> result = reader.readData(
                csvFile.toString(),
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
        String csvContent = """
                FullName,Email,CompanyName,SendEmail
                John Doe,john@example.com,Acme Corp,No
                Jane Smith,jane@example.com,Tech Inc,No
                """;
        Path csvFile = tempDir.resolve("test.csv");
        Files.writeString(csvFile, csvContent);

        // Act
        List<EmailData> result = reader.readData(
                csvFile.toString(),
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
        String csvContent = """
                FullName,Email,CompanyName
                John Doe,john@example.com,Acme Corp
                """;
        Path csvFile = tempDir.resolve("test.csv");
        Files.writeString(csvFile, csvContent);

        // Act & Assert
        EmailSenderException exception = assertThrows(
                EmailSenderException.class,
                () -> reader.readData(
                        csvFile.toString(),
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
    void readData_missingRecipientColumn_throwsException() throws IOException {
        // Arrange
        String csvContent = """
                FullName,CompanyName,SendEmail
                John Doe,Acme Corp,Yes
                """;
        Path csvFile = tempDir.resolve("test.csv");
        Files.writeString(csvFile, csvContent);

        // Act & Assert
        EmailSenderException exception = assertThrows(
                EmailSenderException.class,
                () -> reader.readData(
                        csvFile.toString(),
                        null,
                        "SendEmail",
                        "Yes",
                        "Email"
                )
        );
        assertTrue(exception.getMessage().contains("Email"));
        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    void readData_nonExistentFile_throwsException() {
        // Act & Assert
        assertThrows(
                EmailSenderException.class,
                () -> reader.readData(
                        "/nonexistent/path/file.csv",
                        null,
                        "SendEmail",
                        "Yes",
                        "Email"
                )
        );
    }

    @Test
    void readData_caseInsensitiveProcessValue() throws IOException {
        // Arrange
        String csvContent = """
                FullName,Email,CompanyName,SendEmail
                John Doe,john@example.com,Acme Corp,yes
                Jane Smith,jane@example.com,Tech Inc,YES
                Bob Wilson,bob@example.com,Global Ltd,Yes
                """;
        Path csvFile = tempDir.resolve("test.csv");
        Files.writeString(csvFile, csvContent);

        // Act
        List<EmailData> result = reader.readData(
                csvFile.toString(),
                null,
                "SendEmail",
                "Yes",
                "Email"
        );

        // Assert
        assertEquals(3, result.size());
    }

    @Test
    void readData_tracksRowNumbers() throws IOException {
        // Arrange
        String csvContent = """
                FullName,Email,CompanyName,SendEmail
                John Doe,john@example.com,Acme Corp,No
                Jane Smith,jane@example.com,Tech Inc,Yes
                Bob Wilson,bob@example.com,Global Ltd,No
                Alice Brown,alice@example.com,StartupXYZ,Yes
                """;
        Path csvFile = tempDir.resolve("test.csv");
        Files.writeString(csvFile, csvContent);

        // Act
        List<EmailData> result = reader.readData(
                csvFile.toString(),
                null,
                "SendEmail",
                "Yes",
                "Email"
        );

        // Assert
        assertEquals(2, result.size());
        assertEquals(3, result.get(0).getRowNumber()); // Jane is on row 3 (1-based, header is row 1)
        assertEquals(5, result.get(1).getRowNumber()); // Alice is on row 5
    }

    @Test
    void readData_invalidEmailFormat_skipsRow() throws IOException {
        // Arrange
        String csvContent = """
                FullName,Email,CompanyName,SendEmail
                John Doe,notanemail,Acme Corp,Yes
                Jane Smith,jane@example.com,Tech Inc,Yes
                Bob Wilson,user@,Global Ltd,Yes
                Alice Brown,alice@example.com,StartupXYZ,Yes
                """;
        Path csvFile = tempDir.resolve("test.csv");
        Files.writeString(csvFile, csvContent);

        // Act
        List<EmailData> result = reader.readData(
                csvFile.toString(),
                null,
                "SendEmail",
                "Yes",
                "Email"
        );

        // Assert - only valid emails should be included
        assertEquals(2, result.size());
        assertEquals("jane@example.com", result.get(0).getRecipientEmail());
        assertEquals("alice@example.com", result.get(1).getRecipientEmail());
    }

    @Test
    void readData_multipleRecipientsInRow_parsesAllValidEmails() throws IOException {
        // Arrange
        String csvContent = """
                FullName,Email,CompanyName,SendEmail
                John Doe,john@example.com;jane@example.com;bob@example.com,Acme Corp,Yes
                """;
        Path csvFile = tempDir.resolve("test.csv");
        Files.writeString(csvFile, csvContent);

        // Act
        List<EmailData> result = reader.readData(
                csvFile.toString(),
                null,
                "SendEmail",
                "Yes",
                "Email"
        );

        // Assert
        assertEquals(1, result.size());
        EmailData emailData = result.getFirst();
        assertTrue(emailData.hasMultipleRecipients());
        assertEquals(3, emailData.getRecipientEmails().size());
        assertEquals("john@example.com", emailData.getRecipientEmails().get(0));
        assertEquals("jane@example.com", emailData.getRecipientEmails().get(1));
        assertEquals("bob@example.com", emailData.getRecipientEmails().get(2));
    }

    @Test
    void readData_multipleRecipientsWithInvalid_keepsOnlyValidEmails() throws IOException {
        // Arrange
        String csvContent = """
                FullName,Email,CompanyName,SendEmail
                John Doe,john@example.com;notanemail;bob@example.com,Acme Corp,Yes
                """;
        Path csvFile = tempDir.resolve("test.csv");
        Files.writeString(csvFile, csvContent);

        // Act
        List<EmailData> result = reader.readData(
                csvFile.toString(),
                null,
                "SendEmail",
                "Yes",
                "Email"
        );

        // Assert - invalid email is skipped but valid ones are kept
        assertEquals(1, result.size());
        EmailData emailData = result.getFirst();
        assertTrue(emailData.hasMultipleRecipients());
        assertEquals(2, emailData.getRecipientEmails().size());
        assertEquals("john@example.com", emailData.getRecipientEmails().get(0));
        assertEquals("bob@example.com", emailData.getRecipientEmails().get(1));
    }

    @Test
    void readData_multipleRecipientsAllInvalid_skipsRow() throws IOException {
        // Arrange
        String csvContent = """
                FullName,Email,CompanyName,SendEmail
                John Doe,notanemail;alsoinvalid,Acme Corp,Yes
                Jane Smith,jane@example.com,Tech Inc,Yes
                """;
        Path csvFile = tempDir.resolve("test.csv");
        Files.writeString(csvFile, csvContent);

        // Act
        List<EmailData> result = reader.readData(
                csvFile.toString(),
                null,
                "SendEmail",
                "Yes",
                "Email"
        );

        // Assert - first row is skipped entirely, only second row is processed
        assertEquals(1, result.size());
        assertEquals("jane@example.com", result.getFirst().getRecipientEmail());
    }

    @Test
    void readData_multipleRecipientsWithSpaces_trimsWhitespace() throws IOException {
        // Arrange
        String csvContent = """
                FullName,Email,CompanyName,SendEmail
                John Doe, john@example.com ; jane@example.com ; bob@example.com ,Acme Corp,Yes
                """;
        Path csvFile = tempDir.resolve("test.csv");
        Files.writeString(csvFile, csvContent);

        // Act
        List<EmailData> result = reader.readData(
                csvFile.toString(),
                null,
                "SendEmail",
                "Yes",
                "Email"
        );

        // Assert
        assertEquals(1, result.size());
        EmailData emailData = result.getFirst();
        assertEquals(3, emailData.getRecipientEmails().size());
        assertEquals("john@example.com", emailData.getRecipientEmails().get(0));
        assertEquals("jane@example.com", emailData.getRecipientEmails().get(1));
        assertEquals("bob@example.com", emailData.getRecipientEmails().get(2));
    }

    @Test
    void readData_multipleRecipientsWithEmptyEntries_skipsEmptyEntries() throws IOException {
        // Arrange - note the double semicolon and trailing semicolon
        String csvContent = """
                FullName,Email,CompanyName,SendEmail
                John Doe,john@example.com;;bob@example.com;,Acme Corp,Yes
                """;
        Path csvFile = tempDir.resolve("test.csv");
        Files.writeString(csvFile, csvContent);

        // Act
        List<EmailData> result = reader.readData(
                csvFile.toString(),
                null,
                "SendEmail",
                "Yes",
                "Email"
        );

        // Assert
        assertEquals(1, result.size());
        EmailData emailData = result.getFirst();
        assertEquals(2, emailData.getRecipientEmails().size());
        assertEquals("john@example.com", emailData.getRecipientEmails().get(0));
        assertEquals("bob@example.com", emailData.getRecipientEmails().get(1));
    }

    @Test
    void readData_singleRecipient_worksAsExpected() throws IOException {
        // Arrange
        String csvContent = """
                FullName,Email,CompanyName,SendEmail
                John Doe,john@example.com,Acme Corp,Yes
                """;
        Path csvFile = tempDir.resolve("test.csv");
        Files.writeString(csvFile, csvContent);

        // Act
        List<EmailData> result = reader.readData(
                csvFile.toString(),
                null,
                "SendEmail",
                "Yes",
                "Email"
        );

        // Assert - single recipient should also work
        assertEquals(1, result.size());
        EmailData emailData = result.getFirst();
        assertFalse(emailData.hasMultipleRecipients());
        assertEquals(1, emailData.getRecipientEmails().size());
        assertEquals("john@example.com", emailData.getRecipientEmail());
    }
}
