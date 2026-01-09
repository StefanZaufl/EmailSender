package at.klickmagiesoftware.emailsender.config;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for Microsoft Graph API client.
 * Sets up OAuth2 client credentials flow for Microsoft 365 authentication.
 */
@Configuration
public class MicrosoftGraphConfig {

    private final AppConfig appConfig;

    public MicrosoftGraphConfig(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @Bean
    public GraphServiceClient graphServiceClient() {
        AppConfig.MicrosoftConfig microsoftConfig = appConfig.getMicrosoft();

        ClientSecretCredential credential = new ClientSecretCredentialBuilder()
                .tenantId(microsoftConfig.getTenantId())
                .clientId(microsoftConfig.getClientId())
                .clientSecret(microsoftConfig.getClientSecret())
                .build();

        String[] scopes = new String[]{"https://graph.microsoft.com/.default"};

        return new GraphServiceClient(credential, scopes);
    }
}
