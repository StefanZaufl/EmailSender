package at.klickmagiesoftware.emailsender;

import at.klickmagiesoftware.emailsender.cli.SendEmailCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine.IFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for EmailSenderApplication, focusing on Spring argument filtering.
 */
class EmailSenderApplicationTest {

    private EmailSenderApplication application;

    @BeforeEach
    void setUp() {
        IFactory factory = mock(IFactory.class);
        SendEmailCommand command = mock(SendEmailCommand.class);
        application = new EmailSenderApplication(factory, command);
    }

    // Tests for isSpringArgument()

    @ParameterizedTest
    @ValueSource(strings = {
            "--spring.config.import=config.yml",
            "--spring.config.location=/path/to/config",
            "--spring.profiles.active=prod",
            "--spring.datasource.url=jdbc:mysql://localhost/db",
            "--spring.mail.host=smtp.example.com"
    })
    void isSpringArgument_springDoubleDashArgs_returnsTrue(String arg) {
        assertTrue(application.isSpringArgument(arg),
                "Expected '" + arg + "' to be identified as Spring argument");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "-Dspring.config.import=config.yml",
            "-Dspring.profiles.active=dev",
            "-Dspring.main.banner-mode=off"
    })
    void isSpringArgument_springSystemPropertyArgs_returnsTrue(String arg) {
        assertTrue(application.isSpringArgument(arg),
                "Expected '" + arg + "' to be identified as Spring argument");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "--dry-run",
            "--verbose",
            "-v",
            "--output-dir=/tmp",
            "-o=/tmp",
            "--help",
            "-h",
            "--version",
            "-V",
            "--some-other-option"
    })
    void isSpringArgument_nonSpringArgs_returnsFalse(String arg) {
        assertFalse(application.isSpringArgument(arg),
                "Expected '" + arg + "' to NOT be identified as Spring argument");
    }

    @Test
    void isSpringArgument_emptyString_returnsFalse() {
        assertFalse(application.isSpringArgument(""));
    }

    // Tests for filterSpringArguments()

    @Test
    void filterSpringArguments_mixedArgs_filtersOnlySpringArgs() {
        String[] input = {
                "--spring.config.import=sample-config.yml",
                "--dry-run",
                "--spring.profiles.active=test",
                "-v",
                "--output-dir=/tmp/output"
        };

        String[] result = application.filterSpringArguments(input);

        assertArrayEquals(new String[]{"--dry-run", "-v", "--output-dir=/tmp/output"}, result);
    }

    @Test
    void filterSpringArguments_onlySpringArgs_returnsEmptyArray() {
        String[] input = {
                "--spring.config.import=config.yml",
                "--spring.profiles.active=prod"
        };

        String[] result = application.filterSpringArguments(input);

        assertEquals(0, result.length);
    }

    @Test
    void filterSpringArguments_noSpringArgs_returnsAllArgs() {
        String[] input = {"--dry-run", "-v", "--output-dir=/tmp"};

        String[] result = application.filterSpringArguments(input);

        assertArrayEquals(input, result);
    }

    @Test
    void filterSpringArguments_emptyArray_returnsEmptyArray() {
        String[] input = {};

        String[] result = application.filterSpringArguments(input);

        assertEquals(0, result.length);
    }

    @Test
    void filterSpringArguments_preservesOrder() {
        String[] input = {"--first", "--spring.config.import=x", "--second", "--third"};

        String[] result = application.filterSpringArguments(input);

        assertArrayEquals(new String[]{"--first", "--second", "--third"}, result);
    }

    @Test
    void filterSpringArguments_systemPropertyStyle_alsoFiltered() {
        String[] input = {
                "-Dspring.config.import=config.yml",
                "--dry-run",
                "-Dspring.profiles.active=test"
        };

        String[] result = application.filterSpringArguments(input);

        assertArrayEquals(new String[]{"--dry-run"}, result);
    }

    @Test
    void filterSpringArguments_realWorldScenario_configImport() {
        // This is the exact scenario from the bug report
        String[] input = {"--spring.config.import=sample-config.yml"};

        String[] result = application.filterSpringArguments(input);

        assertEquals(0, result.length,
                "Spring config import argument should be filtered out");
    }

    @Test
    void filterSpringArguments_realWorldScenario_configImportWithDryRun() {
        // Typical usage: config file + dry-run mode
        String[] input = {
                "--spring.config.import=sample-config.yml",
                "--dry-run",
                "--output-dir=./test-output"
        };

        String[] result = application.filterSpringArguments(input);

        assertArrayEquals(new String[]{"--dry-run", "--output-dir=./test-output"}, result,
                "Only application-specific arguments should remain");
    }
}
