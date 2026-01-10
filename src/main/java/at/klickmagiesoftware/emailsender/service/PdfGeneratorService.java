package at.klickmagiesoftware.emailsender.service;

import at.klickmagiesoftware.emailsender.config.AppConfig;
import at.klickmagiesoftware.emailsender.exception.EmailSenderException;
import at.klickmagiesoftware.emailsender.model.EmailData;
import org.docx4j.Docx4J;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import at.klickmagiesoftware.emailsender.util.EmailSenderConstants;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * Service for generating PDFs from Word document templates.
 * Uses docx4j for template processing and PDF conversion.
 */
@Service
public class PdfGeneratorService {

    private static final Logger logger = LoggerFactory.getLogger(PdfGeneratorService.class);

    private final AppConfig appConfig;

    public PdfGeneratorService(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    /**
     * Generates a PDF from the Word template with data from the given EmailData.
     *
     * @param emailData the data to use for template substitution
     * @return the generated PDF as a byte array
     */
    public byte[] generatePdf(EmailData emailData) {
        String templatePath = appConfig.getTemplates().getAttachment();
        logger.debug("Generating PDF from template: {} for row {}", templatePath, emailData.getRowNumber());

        try {
            // Load the Word template
            WordprocessingMLPackage wordDoc = WordprocessingMLPackage.load(new File(templatePath));

            // Replace placeholders in the document
            replacePlaceholders(wordDoc, emailData);

            // Convert to PDF
            ByteArrayOutputStream pdfOutputStream = new ByteArrayOutputStream();
            Docx4J.toPDF(wordDoc, pdfOutputStream);

            byte[] pdfBytes = pdfOutputStream.toByteArray();
            logger.debug("Generated PDF: {} bytes for row {}", pdfBytes.length, emailData.getRowNumber());

            return pdfBytes;

        } catch (Exception e) {
            throw new EmailSenderException("Failed to generate PDF for row " + emailData.getRowNumber(), e);
        }
    }

    /**
     * Generates a personalized Word document (DOCX) from the template.
     * Useful for dry-run mode to inspect the processed document before PDF conversion.
     *
     * @param emailData the data to use for template substitution
     * @return the generated DOCX as a byte array
     */
    public byte[] generateDocx(EmailData emailData) {
        String templatePath = appConfig.getTemplates().getAttachment();
        logger.debug("Generating DOCX from template: {} for row {}", templatePath, emailData.getRowNumber());

        try {
            // Load the Word template
            WordprocessingMLPackage wordDoc = WordprocessingMLPackage.load(new File(templatePath));

            // Replace placeholders in the document
            replacePlaceholders(wordDoc, emailData);

            // Save to byte array
            ByteArrayOutputStream docxOutputStream = new ByteArrayOutputStream();
            wordDoc.save(docxOutputStream);

            byte[] docxBytes = docxOutputStream.toByteArray();
            logger.debug("Generated DOCX: {} bytes for row {}", docxBytes.length, emailData.getRowNumber());

            return docxBytes;

        } catch (Exception e) {
            throw new EmailSenderException("Failed to generate DOCX for row " + emailData.getRowNumber(), e);
        }
    }

    private void replacePlaceholders(WordprocessingMLPackage wordDoc, EmailData emailData) throws Exception {
        MainDocumentPart documentPart = wordDoc.getMainDocumentPart();
        Map<String, String> fields = emailData.getFields();
        Map<String, String> fieldMappings = appConfig.getFieldMappings();

        // Get the document XML once for both finding and replacing placeholders
        String xmlContent = documentPart.getXML();

        // Use the interrupted placeholder pattern to find all placeholders (including those with XML tags)
        Matcher matcher = EmailSenderConstants.INTERRUPTED_PLACEHOLDER_PATTERN.matcher(xmlContent);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String originalPlaceholder = matcher.group(0); // The full placeholder as it appears in the document
            String rawFieldContent = matcher.group(1);     // Content between {{ and }}

            // Strip any XML tags from the field name to get the clean field name
            String cleanFieldName = EmailSenderConstants.stripXmlTags(rawFieldContent);

            // Validate the clean field name
            if (!EmailSenderConstants.isValidPlaceholderFieldName(cleanFieldName)) {
                logger.debug("Skipping invalid placeholder content: '{}'", rawFieldContent);
                // Keep the original text unchanged
                matcher.appendReplacement(result, Matcher.quoteReplacement(originalPlaceholder));
                continue;
            }

            String value = null;

            // Build the clean placeholder for field mapping lookup
            String cleanPlaceholder = "{{" + cleanFieldName + "}}";

            // First check if there's a field mapping for this placeholder (using the clean version)
            if (fieldMappings.containsKey(cleanPlaceholder)) {
                String sourceColumn = fieldMappings.get(cleanPlaceholder);
                value = fields.get(sourceColumn);
            }
            // Also check with the original placeholder in case it was mapped that way
            if (value == null && fieldMappings.containsKey(originalPlaceholder)) {
                String sourceColumn = fieldMappings.get(originalPlaceholder);
                value = fields.get(sourceColumn);
            }

            // If no mapping or mapping didn't resolve, try direct field name
            if (value == null) {
                value = fields.get(cleanFieldName);
            }

            if (value != null) {
                // Escape XML special characters to prevent XML injection
                String escapedValue = EmailSenderConstants.escapeXml(value);
                matcher.appendReplacement(result, Matcher.quoteReplacement(escapedValue));
                logger.debug("Replaced placeholder '{}' (field: '{}') with value", originalPlaceholder, cleanFieldName);
            } else {
                logger.warn("No value found for placeholder '{}' (field name: '{}') in document template",
                        originalPlaceholder, cleanFieldName);
                matcher.appendReplacement(result, ""); // Replace with empty string
            }
        }
        matcher.appendTail(result);

        // Apply the modified XML back to the document
        documentPart.setContents((org.docx4j.wml.Document) XmlUtils.unmarshalString(result.toString()));
    }
}
