package at.klickmagiesoftware.emailsender.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Collections;
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

    @Valid
    private ThrottlingConfig throttling = new ThrottlingConfig();

    @Valid
    private ReportConfig report;

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

    public ThrottlingConfig getThrottling() {
        return throttling;
    }

    public void setThrottling(ThrottlingConfig throttling) {
        this.throttling = throttling != null ? throttling : new ThrottlingConfig();
    }

    public ReportConfig getReport() {
        return report;
    }

    public void setReport(ReportConfig report) {
        this.report = report;
    }

    public Map<String, String> getFieldMappings() {
        return Collections.unmodifiableMap(fieldMappings);
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

        /**
         * The email address of the user who sends emails.
         * This user must have a mailbox in the Microsoft 365 tenant.
         * When sending from a group, this user must have "Send As" permission on the group.
         */
        @NotBlank
        private String senderEmail;

        /**
         * Optional: The email address of a group to send from.
         * When set, emails will appear to come from this group address,
         * but are sent via the user specified in sender-email.
         * The sender-email user must have "Send As" permission on this group in Exchange Online.
         */
        private String senderGroup;

        @NotBlank
        private String subjectTemplate;

        @NotBlank
        private String recipientColumn;

        private String attachmentFilename = "document.pdf";

        public String getSenderEmail() {
            return senderEmail;
        }

        public void setSenderEmail(String senderEmail) {
            this.senderEmail = senderEmail;
        }

        public String getSenderGroup() {
            return senderGroup;
        }

        public void setSenderGroup(String senderGroup) {
            this.senderGroup = senderGroup;
        }

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

    /**
     * Throttling configuration to prevent email rejection due to rate limits.
     * Based on Microsoft Graph API and Exchange Online limits:
     * - Exchange Online: 30 emails per minute (the actual bottleneck)
     * - Graph API: 10,000 requests per 10 minutes
     * - Max 4 concurrent requests
     */
    public static class ThrottlingConfig {

        /**
         * Whether throttling is enabled. Default: true
         */
        private boolean enabled = true;

        /**
         * Maximum emails per minute. Default: 30 (Exchange Online limit)
         * Must be at least 1 to avoid divide-by-zero errors.
         */
        @Min(value = 1, message = "emailsPerMinute must be at least 1")
        private int emailsPerMinute = 30;

        /**
         * Number of retry attempts when throttled (HTTP 429). Default: 3
         */
        @Min(value = 0, message = "maxRetries cannot be negative")
        private int maxRetries = 3;

        /**
         * Initial delay in milliseconds before first retry. Default: 2000 (2 seconds)
         * Subsequent retries use exponential backoff (2x multiplier)
         */
        @Positive(message = "initialRetryDelayMs must be positive")
        private long initialRetryDelayMs = 2000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getEmailsPerMinute() {
            return emailsPerMinute;
        }

        public void setEmailsPerMinute(int emailsPerMinute) {
            this.emailsPerMinute = emailsPerMinute;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public long getInitialRetryDelayMs() {
            return initialRetryDelayMs;
        }

        public void setInitialRetryDelayMs(long initialRetryDelayMs) {
            this.initialRetryDelayMs = initialRetryDelayMs;
        }

        /**
         * Calculate delay between emails in milliseconds based on emails per minute.
         * @return delay in milliseconds
         */
        public long getDelayBetweenEmailsMs() {
            return 60_000L / emailsPerMinute;
        }
    }

    /**
     * Report configuration for generating CSV reports of email sending results.
     */
    public static class ReportConfig {

        /**
         * Path to the output CSV report file.
         * If not set, no report will be generated.
         */
        private String outputPath;

        public String getOutputPath() {
            return outputPath;
        }

        public void setOutputPath(String outputPath) {
            this.outputPath = outputPath;
        }
    }
}
