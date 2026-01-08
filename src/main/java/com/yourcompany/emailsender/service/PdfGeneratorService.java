package com.yourcompany.emailsender.service;

import com.yourcompany.emailsender.config.AppConfig;
import com.yourcompany.emailsender.exception.EmailSenderException;
import com.yourcompany.emailsender.model.EmailData;
import org.docx4j.Docx4J;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for generating PDFs from Word document templates.
 * Uses docx4j for template processing and PDF conversion.
 */
@Service
public class PdfGeneratorService {

    private static final Logger logger = LoggerFactory.getLogger(PdfGeneratorService.class);
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

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

        // Build the replacement map
        Map<String, String> replacements = new HashMap<>();

        // Get all placeholders that exist in the document
        String documentXml = documentPart.getXML();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(documentXml);

        while (matcher.find()) {
            String placeholder = matcher.group(0);
            String fieldName = matcher.group(1);

            if (!replacements.containsKey(placeholder)) {
                String value = null;

                // First check if there's a field mapping for this placeholder
                if (fieldMappings.containsKey(placeholder)) {
                    String sourceColumn = fieldMappings.get(placeholder);
                    value = fields.get(sourceColumn);
                }

                // If no mapping or mapping didn't resolve, try direct field name
                if (value == null) {
                    value = fields.get(fieldName);
                }

                if (value != null) {
                    replacements.put(placeholder, value);
                } else {
                    logger.warn("No value found for placeholder '{}' in document template", placeholder);
                    replacements.put(placeholder, ""); // Replace with empty string
                }
            }
        }

        // Perform the replacements
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            documentPart.variableReplace(
                    Map.of(entry.getKey().replace("{{", "").replace("}}", ""), entry.getValue())
            );
        }

        // Direct string replacement for {{variable}} placeholders
        String xmlContent = documentPart.getXML();
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            xmlContent = xmlContent.replace(entry.getKey(), entry.getValue());
        }

        // Apply the modified XML back to the document
        documentPart.setContents(XmlUtils.unmarshalString(xmlContent));
    }
}
