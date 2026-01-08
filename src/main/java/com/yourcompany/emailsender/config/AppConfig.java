package com.yourcompany.emailsender.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.Map;

/**
 * Main configuration class for the email sender application.
 * Maps to the 'email-sender' prefix in YAML configuration.
 */
@ConfigurationProperties(prefix = "email-sender")
@Validated
public class AppConfig {

    @Valid
    @NotNull
    private MicrosoftConfig microsoft;

    @Valid
    @NotNull
    private DatasourceConfig datasource;

    @Valid
    @NotNull
    private TemplatesConfig templates;

    @Valid
    @NotNull
    private EmailConfig email;

    private Map<String, String> fieldMappings = new HashMap<>();

    public MicrosoftConfig getMicrosoft() {
        return microsoft;
    }

    public void setMicrosoft(MicrosoftConfig microsoft) {
        this.microsoft = microsoft;
    }

    public DatasourceConfig getDatasource() {
        return datasource;
    }

    public void setDatasource(DatasourceConfig datasource) {
        this.datasource = datasource;
    }

    public TemplatesConfig getTemplates() {
        return templates;
    }

    public void setTemplates(TemplatesConfig templates) {
        this.templates = templates;
    }

    public EmailConfig getEmail() {
        return email;
    }

    public void setEmail(EmailConfig email) {
        this.email = email;
    }

    public Map<String, String> getFieldMappings() {
        return fieldMappings;
    }

    public void setFieldMappings(Map<String, String> fieldMappings) {
        this.fieldMappings = fieldMappings != null ? fieldMappings : new HashMap<>();
    }

    /**
     * Microsoft OAuth2 configuration for Microsoft 365 authentication.
     */
    public static class MicrosoftConfig {

        @NotBlank
        private String tenantId;

        @NotBlank
        private String clientId;

        @NotBlank
        private String clientSecret;

        @NotBlank
        private String senderEmail;

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getSenderEmail() {
            return senderEmail;
        }

        public void setSenderEmail(String senderEmail) {
            this.senderEmail = senderEmail;
        }
    }

    /**
     * Data source configuration for CSV/Excel files.
     */
    public static class DatasourceConfig {

        @NotBlank
        private String type;

        @NotBlank
        private String path;

        private String sheetName;

        @NotBlank
        private String processColumn;

        @NotBlank
        private String processValue;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getSheetName() {
            return sheetName;
        }

        public void setSheetName(String sheetName) {
            this.sheetName = sheetName;
        }

        public String getProcessColumn() {
            return processColumn;
        }

        public void setProcessColumn(String processColumn) {
            this.processColumn = processColumn;
        }

        public String getProcessValue() {
            return processValue;
        }

        public void setProcessValue(String processValue) {
            this.processValue = processValue;
        }
    }

    /**
     * Template file paths configuration.
     */
    public static class TemplatesConfig {

        @NotBlank
        private String emailBody;

        @NotBlank
        private String attachment;

        public String getEmailBody() {
            return emailBody;
        }

        public void setEmailBody(String emailBody) {
            this.emailBody = emailBody;
        }

        public String getAttachment() {
            return attachment;
        }

        public void setAttachment(String attachment) {
            this.attachment = attachment;
        }
    }

    /**
     * Email-specific configuration.
     */
    public static class EmailConfig {

        @NotBlank
        private String subjectTemplate;

        @NotBlank
        private String recipientColumn;

        private String attachmentFilename = "document.pdf";

        public String getSubjectTemplate() {
            return subjectTemplate;
        }

        public void setSubjectTemplate(String subjectTemplate) {
            this.subjectTemplate = subjectTemplate;
        }

        public String getRecipientColumn() {
            return recipientColumn;
        }

        public void setRecipientColumn(String recipientColumn) {
            this.recipientColumn = recipientColumn;
        }

        public String getAttachmentFilename() {
            return attachmentFilename;
        }

        public void setAttachmentFilename(String attachmentFilename) {
            this.attachmentFilename = attachmentFilename;
        }
    }
}
