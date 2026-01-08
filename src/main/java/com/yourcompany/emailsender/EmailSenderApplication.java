package com.yourcompany.emailsender;

import com.yourcompany.emailsender.cli.SendEmailCommand;
import com.yourcompany.emailsender.config.AppConfig;
import org.docx4j.Docx4jProperties;
import org.docx4j.fonts.PhysicalFonts;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

/**
 * Main application class for the Email Sender CLI tool.
 * Uses Spring Boot with Picocli for command-line interface handling.
 */
@SpringBootApplication
@EnableConfigurationProperties(AppConfig.class)
public class EmailSenderApplication implements CommandLineRunner, ExitCodeGenerator {

    private final IFactory factory;
    private final SendEmailCommand sendEmailCommand;
    private int exitCode;

    public EmailSenderApplication(IFactory factory, SendEmailCommand sendEmailCommand) {
        this.factory = factory;
        this.sendEmailCommand = sendEmailCommand;
    }

    public static void main(String[] args) {
        configureDocx4jFonts();
        System.exit(SpringApplication.exit(SpringApplication.run(EmailSenderApplication.class, args)));
    }

    /**
     * Configure docx4j font scanning to avoid issues with certain fonts.
     * Some fonts (like Noto emoji fonts) have complex glyph tables that can cause
     * AssertionError during font discovery. This limits scanning to common safe fonts.
     */
    private static void configureDocx4jFonts() {
        PhysicalFonts.setRegex(".*(calibri|cour|arial|times|comic|georgia|impact|tahoma|trebuc|verdana|symbol|webdings|wingding|liberation|dejavu|freesans|freeserif|freemono).*");
        Docx4jProperties.setProperty("docx4j.fonts.fop.util.autodetect.FontFileFinder.fontDirFinder.ignore", true);
    }

    @Override
    public void run(String... args) {
        exitCode = new CommandLine(sendEmailCommand, factory)
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(args);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
