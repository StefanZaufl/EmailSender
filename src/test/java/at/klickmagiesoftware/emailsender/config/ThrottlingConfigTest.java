package at.klickmagiesoftware.emailsender.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ThrottlingConfig.
 */
class ThrottlingConfigTest {

    @Test
    void defaultValues_areCorrect() {
        // Arrange & Act
        AppConfig.ThrottlingConfig config = new AppConfig.ThrottlingConfig();

        // Assert
        assertTrue(config.isEnabled());
        assertEquals(30, config.getEmailsPerMinute());
        assertEquals(3, config.getMaxRetries());
        assertEquals(2000, config.getInitialRetryDelayMs());
    }

    @Test
    void getDelayBetweenEmailsMs_default30PerMinute_returns2000ms() {
        // Arrange
        AppConfig.ThrottlingConfig config = new AppConfig.ThrottlingConfig();

        // Act
        long delayMs = config.getDelayBetweenEmailsMs();

        // Assert - 60000ms / 30 = 2000ms
        assertEquals(2000, delayMs);
    }

    @Test
    void getDelayBetweenEmailsMs_60PerMinute_returns1000ms() {
        // Arrange
        AppConfig.ThrottlingConfig config = new AppConfig.ThrottlingConfig();
        config.setEmailsPerMinute(60);

        // Act
        long delayMs = config.getDelayBetweenEmailsMs();

        // Assert - 60000ms / 60 = 1000ms
        assertEquals(1000, delayMs);
    }

    @Test
    void getDelayBetweenEmailsMs_10PerMinute_returns6000ms() {
        // Arrange
        AppConfig.ThrottlingConfig config = new AppConfig.ThrottlingConfig();
        config.setEmailsPerMinute(10);

        // Act
        long delayMs = config.getDelayBetweenEmailsMs();

        // Assert - 60000ms / 10 = 6000ms
        assertEquals(6000, delayMs);
    }

    @Test
    void setEnabled_false_disablesThrottling() {
        // Arrange
        AppConfig.ThrottlingConfig config = new AppConfig.ThrottlingConfig();

        // Act
        config.setEnabled(false);

        // Assert
        assertFalse(config.isEnabled());
    }

    @Test
    void setMaxRetries_customValue_returnsCustomValue() {
        // Arrange
        AppConfig.ThrottlingConfig config = new AppConfig.ThrottlingConfig();

        // Act
        config.setMaxRetries(5);

        // Assert
        assertEquals(5, config.getMaxRetries());
    }

    @Test
    void setInitialRetryDelayMs_customValue_returnsCustomValue() {
        // Arrange
        AppConfig.ThrottlingConfig config = new AppConfig.ThrottlingConfig();

        // Act
        config.setInitialRetryDelayMs(5000);

        // Assert
        assertEquals(5000, config.getInitialRetryDelayMs());
    }

    @Test
    void appConfig_defaultThrottlingConfig_notNull() {
        // Arrange
        AppConfig appConfig = new AppConfig();

        // Act
        AppConfig.ThrottlingConfig throttling = appConfig.getThrottling();

        // Assert
        assertNotNull(throttling);
    }

    @Test
    void appConfig_setThrottlingNull_createsDefault() {
        // Arrange
        AppConfig appConfig = new AppConfig();

        // Act
        appConfig.setThrottling(null);

        // Assert
        assertNotNull(appConfig.getThrottling());
        assertTrue(appConfig.getThrottling().isEnabled());
    }

    @Test
    void appConfig_setThrottling_returnsSet() {
        // Arrange
        AppConfig appConfig = new AppConfig();
        AppConfig.ThrottlingConfig customConfig = new AppConfig.ThrottlingConfig();
        customConfig.setEmailsPerMinute(15);

        // Act
        appConfig.setThrottling(customConfig);

        // Assert
        assertEquals(15, appConfig.getThrottling().getEmailsPerMinute());
    }
}
