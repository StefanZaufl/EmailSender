# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Email Sender CLI - A Java CLI application for sending personalized emails with PDF attachments via Microsoft 365 Graph API. Reads recipient data from CSV/Excel files, processes Word templates with placeholders, converts to PDF, and sends via OAuth2 authentication.

## Project Structure

Multi-module Maven project:
- **`core/`** — Services, configuration, models, exceptions, utilities (reusable library)
- **`cli/`** — Spring Boot application entry point + Picocli CLI command (executable)

## Build & Development Commands

```bash
# Build the project (from root)
mvn clean package

# Run tests (all modules)
mvn test

# Run tests for a single module
mvn test -pl core
mvn test -pl cli

# Run a single test class
mvn test -pl core -Dtest=TemplateServiceTest

# Run a single test method
mvn test -pl core -Dtest=TemplateServiceTest#testProcessSubjectWithPlaceholders

# Run the application
java --enable-preview -jar cli/target/email-sender-cli-2.0.1-SNAPSHOT.jar --spring.config.import=/path/to/config.yml

# Run with dry-run mode (no emails sent, writes to disk)
java --enable-preview -jar cli/target/email-sender-cli-2.0.1-SNAPSHOT.jar --spring.config.import=/path/to/config.yml --dry-run -o ./output

# Run with verbose logging
java --enable-preview -jar cli/target/email-sender-cli-2.0.1-SNAPSHOT.jar --spring.config.import=/path/to/config.yml --verbose
```

**Note:** Java 25 with `--enable-preview` flag is required.

## Architecture

### Strategy Pattern for Email Processing
- `EmailProcessingStrategy` interface with two implementations:
  - `LiveEmailProcessor`: Sends real emails via Microsoft Graph
  - `DryRunEmailProcessor`: Writes output files to disk for testing
- Selected in `SendEmailCommand` based on `--dry-run` flag

### Adapter Pattern for Data Sources
- `DataSourceReader` interface with implementations:
  - `CsvDataSourceReader`: Apache Commons CSV
  - `ExcelDataSourceReader`: Apache POI (.xlsx, .xls)

### Service Layer
- `EmailService`: Orchestrates email sending with exponential backoff retry for HTTP 429
- `TemplateService`: Thymeleaf HTML + placeholder replacement for subjects
- `PdfGeneratorService`: Word to PDF conversion via docx4j
- `ReportService`: CSV report generation
- `SenderTypeResolver`: Routes between user vs. group sender

### Configuration
- `AppConfig`: Nested static inner classes with `@ConfigurationProperties`
- Uses Jakarta Bean Validation (@NotBlank, @Min, @Positive)
- Environment variable support for sensitive values

## Template Processing Syntax

- **Email Body (HTML)**: Thymeleaf syntax `${fieldName}` or `[[${fieldName}]]`
- **Subject & Attachments**: Placeholder syntax `{{fieldName}}`

## Key Files

| File | Purpose |
|------|---------|
| `SendEmailCommand.java` | CLI orchestration, processor selection, main loop |
| `AppConfig.java` | Configuration schema with all YAML mappings |
| `EmailService.java` | Core email sending with retry logic |
| `TemplateService.java` | Thymeleaf + placeholder processing |
| `PdfGeneratorService.java` | Word to PDF conversion with font config |
| `EmailSenderConstants.java` | Regex patterns, XML escaping utilities |

## CLI Options

| Option | Description |
|--------|-------------|
| `--dry-run` | Process templates but don't send; write to disk |
| `-o, --output-dir` | Output directory for dry-run (default: `./output`) |
| `-v, --verbose` | Enable DEBUG logging |
| `--font-config` | Font mode: `auto`, `autoDiscoverFonts`, `minimal` |
| `--spring.config.import=PATH` | Load configuration from YAML file |

## Exit Codes

- `0`: Success
- `1`: Some emails failed (partial success)
- `2`: Fatal error

## Testing

Tests use JUnit 5 with Mockito. Key test areas:
- `DryRunIntegrationTest`: End-to-end dry-run workflow
- `TemplateServiceTest`: Subject and body template processing
- `CsvDataSourceReaderTest`, `ExcelDataSourceReaderTest`: Data parsing
- `PdfGeneratorServiceTest`: PDF generation verification with PDFBox

## Throttling

Default: 30 emails/minute to respect Exchange Online limits. Configurable via `email-sender.throttling` in YAML config.

## Documentation

Documenation is inside the README.md file.