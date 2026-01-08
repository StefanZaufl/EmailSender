# Email Sender CLI

A Java CLI tool that sends personalized emails with PDF attachments. The tool reads recipient data from CSV/Excel files, processes Word document templates, converts them to PDF, and sends emails via Microsoft 365 using OAuth2 authentication.

## Features

- **Multiple Data Sources**: Read recipient data from CSV or Excel files (.xlsx/.xls)
- **Template Processing**:
  - HTML email bodies using Thymeleaf templating
  - Word document (.docx) attachments with placeholder replacement
  - Automatic Word to PDF conversion
- **Microsoft 365 Integration**: Send emails via Microsoft Graph API with OAuth2 client credentials flow
- **Flexible Filtering**: Configure which rows to process based on column values
- **Field Mappings**: Map template placeholders to different column names
- **Dry Run Mode**: Test processing without sending emails
- **Error Handling**: Continues processing on failures and reports all errors at the end

## Requirements

- Java 25 (with preview features)
- Maven 3.8+
- Microsoft 365 account with Azure AD app registration

## Azure AD Setup

1. Register an application in Azure AD / Microsoft Entra ID
2. Add the following API permissions:
   - `Mail.Send` (Application permission)
3. Grant admin consent for the permissions
4. Create a client secret
5. Note down: Tenant ID, Client ID, and Client Secret

## Build

```bash
mvn clean package
```

This creates `target/email-sender-cli-1.0.0-SNAPSHOT.jar`.

## Configuration

Create a YAML configuration file with your settings:

```yaml
email-sender:
  microsoft:
    tenant-id: your-azure-tenant-id
    client-id: your-azure-client-id
    client-secret: your-azure-client-secret
    sender-email: sender@yourcompany.com

  datasource:
    type: excel  # or 'csv'
    path: /path/to/recipients.xlsx
    sheet-name: Sheet1  # optional, defaults to first sheet
    process-column: "SendEmail"
    process-value: "Yes"

  templates:
    email-body: /path/to/email-template.html
    attachment: /path/to/document-template.docx

  email:
    subject-template: "Hello {{name}}, your report is ready"
    recipient-column: "Email"
    attachment-filename: "report.pdf"  # optional

  # Optional: map placeholders to different column names
  field-mappings:
    "{{name}}": "FullName"
    "{{company}}": "CompanyName"
```

### Environment Variables

You can also use environment variables for sensitive values:

```yaml
email-sender:
  microsoft:
    tenant-id: ${AZURE_TENANT_ID}
    client-id: ${AZURE_CLIENT_ID}
    client-secret: ${AZURE_CLIENT_SECRET}
```

## Usage

### Send Emails

```bash
java --enable-preview -jar email-sender-cli.jar \
  --spring.config.import=/path/to/config.yml
```

### Dry Run (Test Without Sending)

```bash
java --enable-preview -jar email-sender-cli.jar \
  --spring.config.import=/path/to/config.yml \
  --dry-run \
  --output-dir=./test-output
```

This processes all templates and writes output files to the specified directory without sending emails.

### Verbose Mode

```bash
java --enable-preview -jar email-sender-cli.jar \
  --spring.config.import=/path/to/config.yml \
  --verbose
```

### Command Line Options

| Option | Description |
|--------|-------------|
| `--dry-run` | Process templates but don't send emails; write files to disk |
| `--output-dir`, `-o` | Output directory for dry-run mode (default: `./output`) |
| `--verbose`, `-v` | Enable detailed logging |
| `--help` | Show help message |
| `--version` | Show version |

## Templates

### HTML Email Template (Thymeleaf)

Use Thymeleaf syntax for the email body:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body>
    <p>Dear <span th:text="${FullName}">Name</span>,</p>
    <p>Please find attached your report for <span th:text="${CompanyName}">Company</span>.</p>
    <p>Report date: <span th:text="${ReportDate}">Date</span></p>
</body>
</html>
```

### Word Document Template

Use `{{placeholder}}` syntax in your Word document:

```
Dear {{FullName}},

This report is prepared for {{CompanyName}} for the period {{ReportDate}}.

Best regards,
The Reports Team
```

### Subject Line Template

Use `{{placeholder}}` syntax:

```
Hello {{name}}, your {{month}} report is ready
```

## Data Source Format

### CSV Example

```csv
FullName,Email,CompanyName,ReportDate,SendEmail
John Doe,john@example.com,Acme Corp,January 2024,Yes
Jane Smith,jane@example.com,Tech Inc,January 2024,Yes
Bob Wilson,bob@example.com,Global Ltd,January 2024,No
```

### Excel

Same structure as CSV, with column headers in the first row. Supports `.xlsx` and `.xls` formats.

## Project Structure

```
├── pom.xml
├── sample-config.yml
├── samples/
│   ├── email-template.html
│   ├── sample-data.csv
│   └── README.md
└── src/
    ├── main/
    │   ├── java/com/yourcompany/emailsender/
    │   │   ├── EmailSenderApplication.java
    │   │   ├── cli/
    │   │   │   └── SendEmailCommand.java
    │   │   ├── config/
    │   │   │   ├── AppConfig.java
    │   │   │   └── MicrosoftGraphConfig.java
    │   │   ├── exception/
    │   │   │   └── EmailSenderException.java
    │   │   ├── model/
    │   │   │   └── EmailData.java
    │   │   └── service/
    │   │       ├── CsvDataSourceReader.java
    │   │       ├── DataSourceReader.java
    │   │       ├── EmailService.java
    │   │       ├── ExcelDataSourceReader.java
    │   │       ├── PdfGeneratorService.java
    │   │       └── TemplateService.java
    │   └── resources/
    │       └── application.yml
    └── test/
        └── java/com/yourcompany/emailsender/
            ├── exception/
            ├── model/
            └── service/
```

## Running Tests

```bash
mvn test
```

## Technology Stack

- **Java 25** with preview features
- **Spring Boot 3.4.1** - Application framework
- **Picocli** - CLI framework with Spring Boot integration
- **Apache Commons CSV** - CSV file parsing
- **Apache POI** - Excel file parsing
- **docx4j** - Word document processing and PDF conversion
- **Thymeleaf** - HTML email templating
- **Microsoft Graph SDK** - Microsoft 365 email sending
- **Azure Identity** - OAuth2 authentication

## License

See [LICENSE](LICENSE) file.
