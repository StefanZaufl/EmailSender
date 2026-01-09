# Sample Templates

This directory contains sample templates for the Email Sender CLI.

## Files

- `email-template.html` - Sample Thymeleaf HTML template for email body
- `sample-data.csv` - Sample CSV data file with recipient information

## Word Document Template

For the Word document template (.docx), create a document with placeholders using the `{{fieldName}}` syntax. For example:

```
Dear {{FullName}},

This report is prepared for {{CompanyName}} for the period {{ReportDate}}.

...
```

The placeholders will be replaced with values from your data source when generating the PDF attachment.

## Usage

1. Copy `sample-config.yml` from the project root
2. Customize the paths and credentials
3. Create your Word document template with placeholders
4. Run: `java -jar email-sender-cli.jar --spring.config.import=/path/to/your-config.yml`

Use `--dry-run` to test without sending emails.

## CSV Report

Configure the `report.output-path` in your configuration to generate a CSV report after processing. The report contains each email address and whether the send was successful or failed:

```csv
Email,Status
john@example.com,Success
jane@example.com,Success
bob@example.com,Failed
```
