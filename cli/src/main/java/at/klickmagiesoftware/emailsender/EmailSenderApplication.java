package at.klickmagiesoftware.emailsender;

import at.klickmagiesoftware.emailsender.cli.SendEmailCommand;
import at.klickmagiesoftware.emailsender.config.AppConfig;
import at.klickmagiesoftware.emailsender.config.FontConfigMode;
import org.docx4j.Docx4jProperties;
import org.docx4j.fonts.PhysicalFonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Main application class for the Email Sender CLI tool.
 * Uses Spring Boot with Picocli for command-line interface handling.
 */
@SpringBootApplication
@EnableConfigurationProperties(AppConfig.class)
public class EmailSenderApplication implements CommandLineRunner, ExitCodeGenerator {

    private static final Logger logger = LoggerFactory.getLogger(EmailSenderApplication.class);

    /**
     * Base font regex pattern for common safe fonts.
     */
    private static final String BASE_FONT_REGEX = "calibri|cour|arial|times|comic|georgia|impact|tahoma|trebuc|verdana|symbol|webdings|wingding|liberation|dejavu|freesans|freeserif|freemono";

    /**
     * Stores the font config mode parsed from command line for later use.
     */
    private static FontConfigMode initialFontConfigMode = FontConfigMode.AUTO;

    private final IFactory factory;
    private final SendEmailCommand sendEmailCommand;
    private final AppConfig appConfig;
    private int exitCode;

    public EmailSenderApplication(IFactory factory, SendEmailCommand sendEmailCommand, AppConfig appConfig) {
        this.factory = factory;
        this.sendEmailCommand = sendEmailCommand;
        this.appConfig = appConfig;
    }

    static void main(String[] args) {
        // Parse --font-config early before Spring starts
        initialFontConfigMode = parseFontConfigFromArgs(args);
        configureDocx4jFonts(initialFontConfigMode, List.of());
        System.exit(SpringApplication.exit(SpringApplication.run(EmailSenderApplication.class, args)));
    }

    /**
     * Parse the --font-config argument from command line args before Spring starts.
     * This allows early font configuration before the Spring context is loaded.
     *
     * @param args command line arguments
     * @return the parsed FontConfigMode, defaults to AUTO
     */
    private static FontConfigMode parseFontConfigFromArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--font-config") && i + 1 < args.length) {
                return parseFontConfigMode(args[i + 1]);
            } else if (arg.startsWith("--font-config=")) {
                return parseFontConfigMode(arg.substring("--font-config=".length()));
            }
        }
        return FontConfigMode.AUTO;
    }

    /**
     * Parse a font config mode string to enum value.
     */
    private static FontConfigMode parseFontConfigMode(String value) {
        if (value == null || value.isEmpty()) {
            return FontConfigMode.AUTO;
        }
        String normalized = value.toUpperCase(Locale.ROOT).replace("-", "_");
        try {
            return FontConfigMode.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            // Try case-insensitive match
            for (FontConfigMode mode : FontConfigMode.values()) {
                if (mode.name().equalsIgnoreCase(normalized)) {
                    return mode;
                }
            }
            return FontConfigMode.AUTO;
        }
    }

    /**
     * Configure docx4j font scanning based on the specified mode.
     * Some fonts (like Noto emoji fonts) have complex glyph tables that can cause
     * AssertionError during font discovery. The minimal mode limits scanning to common safe fonts.
     *
     * @param mode the font configuration mode
     * @param additionalFonts additional font patterns to include in the allow-list
     */
    static void configureDocx4jFonts(FontConfigMode mode, List<String> additionalFonts) {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

        boolean useMinimalFonts = switch (mode) {
            case AUTO -> !isWindows;
            case AUTO_DISCOVER_FONTS -> false;
            case MINIMAL -> true;
        };

        if (useMinimalFonts) {
            String fontRegex = buildFontRegex(additionalFonts);
            logger.debug("Configuring docx4j with minimal font list: {}", fontRegex);
            PhysicalFonts.setRegex(fontRegex);
            Docx4jProperties.setProperty("docx4j.fonts.fop.util.autodetect.FontFileFinder.fontDirFinder.ignore", true);
        } else {
            logger.debug("Configuring docx4j with auto font discovery");
            // Reset to allow all fonts
            PhysicalFonts.setRegex(null);
            Docx4jProperties.setProperty("docx4j.fonts.fop.util.autodetect.FontFileFinder.fontDirFinder.ignore", false);
        }
    }

    /**
     * Build the font regex pattern including base fonts and any additional fonts.
     *
     * @param additionalFonts additional font patterns to include
     * @return the complete font regex pattern
     */
    private static String buildFontRegex(List<String> additionalFonts) {
        if (additionalFonts == null || additionalFonts.isEmpty()) {
            return ".*(" + BASE_FONT_REGEX + ").*";
        }

        StringBuilder regex = new StringBuilder(BASE_FONT_REGEX);
        for (String font : additionalFonts) {
            if (font != null && !font.isBlank()) {
                regex.append("|").append(font.toLowerCase(Locale.ROOT).trim());
            }
        }
        return ".*(" + regex + ").*";
    }

    @Override
    public void run(String... args) {
        // Reconfigure fonts with additional fonts from YAML config if specified
        List<String> additionalFonts = appConfig.getPdf().getAdditionalFonts();
        if (!additionalFonts.isEmpty()) {
            logger.debug("Reconfiguring fonts with additional fonts from config: {}", additionalFonts);
            configureDocx4jFonts(initialFontConfigMode, additionalFonts);
        }

        // Filter out Spring Boot arguments before passing to picocli
        String[] filteredArgs = filterSpringArguments(args);
        exitCode = new CommandLine(sendEmailCommand, factory)
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(filteredArgs);
    }

    /**
     * Filters out Spring Boot-specific command-line arguments that should not be
     * passed to picocli. Spring Boot processes these arguments during startup,
     * but they would cause "unknown option" errors in picocli.
     *
     * @param args the original command-line arguments
     * @return filtered arguments without Spring Boot-specific options
     */
    String[] filterSpringArguments(String[] args) {
        return Arrays.stream(args)
                .filter(arg -> !isSpringArgument(arg))
                .toArray(String[]::new);
    }

    /**
     * Checks if an argument is a Spring Boot-specific argument.
     *
     * @param arg the argument to check
     * @return true if the argument is Spring Boot-specific
     */
    boolean isSpringArgument(String arg) {
        return arg.startsWith("--spring.") || arg.startsWith("-Dspring.");
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
