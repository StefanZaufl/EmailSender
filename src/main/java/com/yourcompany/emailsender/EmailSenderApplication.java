package com.yourcompany.emailsender;

import com.yourcompany.emailsender.cli.SendEmailCommand;
import com.yourcompany.emailsender.config.AppConfig;
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
        System.exit(SpringApplication.exit(SpringApplication.run(EmailSenderApplication.class, args)));
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
