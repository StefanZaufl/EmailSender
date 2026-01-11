# Email Sender CLI

A Java CLI tool that sends personalized emails with PDF attachments. The tool reads recipient data from CSV/Excel files, processes Word document templates, converts them to PDF, and sends emails via Microsoft 365 using OAuth2 authentication.

## Features

- **Multiple Data Sources**: Read recipient data from CSV or Excel files (.xlsx/.xls)
- **Multiple Recipients per Row**: Support for semicolon-separated email addresses in a single row
- **Template Processing**:
  - HTML email bodies using Thymeleaf templating
  - Word document (.docx) attachments with placeholder replacement
  - Automatic Word to PDF conversion
- **Microsoft 365 Integration**: Send emails via Microsoft Graph API with OAuth2 client credentials flow
- **Group Mailbox Support**: Send from Microsoft 365 Group mailboxes by configuring `sender-group`
- **Flexible Filtering**: Configure which rows to process based on column values
- **Field Mappings**: Map template placeholders to different column names
- **CSV Report Generation**: Generate a report of sent emails with success/failure status
- **Dry Run Mode**: Test processing without sending emails
- **Error Handling**: Continues processing on failures and reports all errors at the end

## Requirements

- Java 25 (with preview features)
- Maven 3.8+
- Microsoft 365 account with Azure AD app registration

## Azure AD / Microsoft Entra ID Setup

To send emails via Microsoft 365, you need to register an application in Azure AD (now called Microsoft Entra ID) and configure the appropriate permissions.

### Step 1: Register a New Application

1. Sign in to the [Azure Portal](https://portal.azure.com)
2. Navigate to **Microsoft Entra ID** (formerly Azure Active Directory)
3. In the left menu, select **App registrations**
4. Click **+ New registration**
5. Fill in the registration form:
   - **Name**: `Email Sender CLI` (or your preferred name)
   - **Supported account types**: Select "Accounts in this organizational directory only"
   - **Redirect URI**: Leave blank (not needed for client credentials flow)
6. Click **Register**

### Step 2: Note Your Application IDs

After registration, you'll be on the application's Overview page. Note down:

- **Application (client) ID** → This is your `client-id`
- **Directory (tenant) ID** → This is your `tenant-id`

### Step 3: Create a Client Secret

1. In your app registration, go to **Certificates & secrets**
2. Under **Client secrets**, click **+ New client secret**
3. Add a description (e.g., "Email Sender CLI Secret")
4. Choose an expiration period (recommended: 12 or 24 months)
5. Click **Add**
6. **Important**: Copy the secret **Value** immediately → This is your `client-secret`
   - The value is only shown once; if you lose it, you'll need to create a new secret

### Step 4: Configure API Permissions

1. In your app registration, go to **API permissions**
2. Click **+ Add a permission**
3. Select **Microsoft Graph**
4. Choose **Application permissions** (not Delegated)
5. Search for and select the following permission:
   - `Mail.Send` - Required to send emails on behalf of users
6. Click **Add permissions**

> **Note**: When sending from a group mailbox (using `sender-group`), you also need to grant "Send As" permission in Exchange Online (see Step 6).

### Step 5: Grant Admin Consent

Application permissions require admin consent:

1. Still on the **API permissions** page
2. Click **Grant admin consent for [Your Organization]**
3. Confirm by clicking **Yes**
4. The status should change to "Granted for [Your Organization]"

> **Note**: You need Global Administrator or Privileged Role Administrator rights to grant admin consent. If you don't have these permissions, contact your IT administrator.

### Step 6: Configure the Sender

The `sender-email` in your configuration must be a valid user mailbox in your Microsoft 365 tenant. This is the user that will send the emails.

Optionally, you can configure a `sender-group` to make emails appear as if they come from a group mailbox. When `sender-group` is set:
- Emails are sent via the `sender-email` user's sendMail endpoint
- The "from" field of the email is set to the group's address
- The `sender-email` user must have **"Send As"** permission on the group in Exchange Online

| Scenario | Configuration | API Used | Permission Required |
|----------|---------------|----------|---------------------|
| Send from user | `sender-email` only | `POST /users/{sender-email}/sendMail` | `Mail.Send` |
| Send from group | `sender-email` + `sender-group` | `POST /users/{sender-email}/sendMail` with `from` set to group | `Mail.Send` and Exchange "Send As" permission |

### Configuration Example

Once you have all the values, configure the application:

```yaml
email-sender:
  microsoft:
    tenant-id: 12345678-1234-1234-1234-123456789abc
    client-id: 87654321-4321-4321-4321-cba987654321
    client-secret: your-client-secret-value
  email:
    sender-email: noreply@yourcompany.com
```

To send from a group mailbox:

```yaml
email-sender:
  microsoft:
    tenant-id: 12345678-1234-1234-1234-123456789abc
    client-id: 87654321-4321-4321-4321-cba987654321
    client-secret: your-client-secret-value
  email:
    sender-email: service-account@yourcompany.com  # User who sends the email
    sender-group: team@yourcompany.com  # Group address that appears in "from" field
```

To grant "Send As" permission on the group to the sender user, run this PowerShell command (requires Exchange Online PowerShell module):

```powershell
Add-RecipientPermission -Identity "team@yourcompany.com" -Trustee "service-account@yourcompany.com" -AccessRights SendAs
```

Or using environment variables for security:

```bash
export AZURE_TENANT_ID="12345678-1234-1234-1234-123456789abc"
export AZURE_CLIENT_ID="87654321-4321-4321-4321-cba987654321"
export AZURE_CLIENT_SECRET="your-client-secret-value"
```

```yaml
email-sender:
  microsoft:
    tenant-id: ${AZURE_TENANT_ID}
    client-id: ${AZURE_CLIENT_ID}
    client-secret: ${AZURE_CLIENT_SECRET}
  email:
    sender-email: noreply@yourcompany.com
```

### Troubleshooting

| Error | Cause | Solution |
|-------|-------|----------|
| `AADSTS7000215: Invalid client secret` | Client secret is incorrect or expired | Create a new client secret |
| `AADSTS700016: Application not found` | Wrong client ID or tenant ID | Verify the IDs in Azure portal |
| `Authorization_RequestDenied` | Missing admin consent | Grant admin consent for required permissions |
| `ErrorAccessDenied` | Sender email doesn't exist or no permission | Verify the sender-email mailbox exists |
| `The requested user 'x@y.com' is invalid` | The sender-email is not a valid user mailbox | Verify the sender-email is a user mailbox (not a group) |
| `HTTP 403` when sending from group | Sender user lacks "Send As" permission on the group | Grant "Send As" permission in Exchange: `Add-RecipientPermission -Identity "group@..." -Trustee "user@..." -AccessRights SendAs` |

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
    sender-email: sender@yourcompany.com
    # sender-group: team@yourcompany.com  # Optional: set to send from a group
    subject-template: "Hello {{name}}, your report is ready"
    recipient-column: "Email"
    attachment-filename: "report.pdf"  # optional

  # Optional: map placeholders to different column names
  field-mappings:
    "{{name}}": "FullName"
    "{{company}}": "CompanyName"

  # Optional: generate a CSV report of email sending results
  report:
    output-path: /path/to/email-report.csv

  # Optional: PDF generation settings
  pdf:
    # Additional font patterns to include when using 'auto' or 'minimal' font-config mode
    additional-fonts:
      - roboto
      - opensans
      - sourcesans
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

### CSV Report

When configured, the application generates a CSV report after processing all emails. The report contains two columns:
- `Email` - The recipient email address
- `Status` - Either `Success` or `Failed`

Configure the report output path in your configuration file:

```yaml
email-sender:
  report:
    output-path: ./reports/email-report.csv
```

Example report output:
```csv
Email,Status
john@example.com,Success
jane@example.com,Success
bob@example.com,Failed
```

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
| `--font-config` | Font configuration mode for PDF generation (see below) |
| `--verbose`, `-v` | Enable detailed logging |
| `--help` | Show help message |
| `--version` | Show version |

### Font Configuration

The `--font-config` option controls how docx4j discovers and loads fonts for PDF generation. Some fonts (like Noto emoji fonts) have complex glyph tables that can cause errors during font discovery.

| Mode | Description |
|------|-------------|
| `auto` (default) | Uses minimal font list on non-Windows platforms, full auto-discovery on Windows |
| `autoDiscoverFonts` | Use all system fonts (may cause errors with certain fonts) |
| `minimal` | Always use a minimal, safe font list |

Example:
```bash
java --enable-preview -jar email-sender-cli.jar \
  --spring.config.import=/path/to/config.yml \
  --font-config=minimal
```

You can also specify additional fonts to include in the allow-list via configuration (see below).

### Logging

The application logs to both the console and a rolling log file. By default, log files are created in the current working directory:

- **Log file**: `email-sender.log` (in the current directory)
- **Rolling policy**: Daily rollover, max 10MB per file
- **Retention**: 30 days of history, max 100MB total

To customize the log file location:

```bash
# Specify a custom log directory
java -DLOG_PATH=/var/log/email-sender -jar email-sender-cli.jar

# Or via command line
java -jar email-sender-cli.jar --logging.file.path=/var/log/email-sender
```

To enable debug logging:

```bash
java -jar email-sender-cli.jar --logging.level.at.klickmagiesoftware.emailsender=DEBUG
```

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

### Multiple Recipients per Row

You can specify multiple email recipients in a single row by separating them with semicolons:

```csv
FullName,Email,CompanyName,ReportDate,SendEmail
Team Alpha,john@example.com;jane@example.com;bob@example.com,Acme Corp,January 2024,Yes
Alice Johnson,alice@example.com,Tech Inc,January 2024,Yes
```

When multiple recipients are specified:
- All recipients receive the same email with the same personalized content (based on the row's field values)
- Each email address is validated individually
- Invalid email addresses are logged as warnings but processing continues with the valid ones
- If all email addresses in a row are invalid, the entire row is skipped
- In the CSV report, each recipient is recorded as a separate entry

This works for both CSV and Excel data sources.

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
    │   │       ├── ReportService.java
    │   │       ├── SenderTypeResolver.java
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
- **Spring Boot 3.5.9** - Application framework
- **Picocli** - CLI framework with Spring Boot integration
- **Apache Commons CSV** - CSV file parsing
- **Apache POI** - Excel file parsing
- **docx4j** - Word document processing and PDF conversion
- **Thymeleaf** - HTML email templating
- **Microsoft Graph SDK** - Microsoft 365 email sending
- **Azure Identity** - OAuth2 authentication

## License

See [LICENSE](LICENSE) file.
