package com.yourcompany.emailsender.service;

import com.yourcompany.emailsender.config.AppConfig;
import com.yourcompany.emailsender.exception.EmailSenderException;
import com.yourcompany.emailsender.model.EmailData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.FileTemplateResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for processing email templates.
 * Handles HTML body templates using Thymeleaf and simple placeholder replacement for subject lines.
 */
@Service
public class TemplateService {

    private static final Logger logger = LoggerFactory.getLogger(TemplateService.class);
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    private final AppConfig appConfig;
    private final TemplateEngine templateEngine;

    public TemplateService(AppConfig appConfig) {
        this.appConfig = appConfig;
        this.templateEngine = createTemplateEngine();
    }

    private TemplateEngine createTemplateEngine() {
        FileTemplateResolver resolver = new FileTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    /**
     * Processes the HTML email body template with the given email data.
     *
     * @param emailData the data to use for template substitution
     * @return the processed HTML content
     */
    public String processEmailBody(EmailData emailData) {
        String templatePath = appConfig.getTemplates().getEmailBody();
        logger.debug("Processing email body template: {}", templatePath);

        try {
            // Verify template file exists
            if (!Files.exists(Path.of(templatePath))) {
                throw new EmailSenderException("Email body template not found: " + templatePath);
            }

            Context context = new Context();

            // Add all fields to the context
            Map<String, String> fields = emailData.getFields();
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                context.setVariable(entry.getKey(), entry.getValue());
            }

            // Apply field mappings if configured
            Map<String, String> fieldMappings = appConfig.getFieldMappings();
            for (Map.Entry<String, String> mapping : fieldMappings.entrySet()) {
                String placeholder = mapping.getKey().replace("{{", "").replace("}}", "");
                String sourceColumn = mapping.getValue();
                if (fields.containsKey(sourceColumn)) {
                    context.setVariable(placeholder, fields.get(sourceColumn));
                }
            }

            return templateEngine.process(templatePath, context);

        } catch (Exception e) {
            throw new EmailSenderException("Failed to process email body template for row " +
                    emailData.getRowNumber(), e);
        }
    }

    /**
     * Processes the email subject template with simple placeholder replacement.
     * Uses {{fieldName}} syntax for placeholders.
     *
     * @param emailData the data to use for template substitution
     * @return the processed subject line
     */
    public String processSubject(EmailData emailData) {
        String subjectTemplate = appConfig.getEmail().getSubjectTemplate();
        logger.debug("Processing subject template: {}", subjectTemplate);

        String result = subjectTemplate;
        Map<String, String> fields = emailData.getFields();
        Map<String, String> fieldMappings = appConfig.getFieldMappings();

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(subjectTemplate);
        while (matcher.find()) {
            String placeholder = matcher.group(0);
            String fieldName = matcher.group(1);

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
                result = result.replace(placeholder, value);
            } else {
                logger.warn("No value found for placeholder '{}' in subject template", placeholder);
            }
        }

        return result;
    }

    /**
     * Reads the raw template content from a file.
     *
     * @param templatePath the path to the template file
     * @return the template content as a string
     */
    public String readTemplateContent(String templatePath) {
        try {
            return Files.readString(Path.of(templatePath));
        } catch (IOException e) {
            throw new EmailSenderException("Failed to read template file: " + templatePath, e);
        }
    }
}
