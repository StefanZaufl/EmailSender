package at.klickmagiesoftware.emailsender.config;

/**
 * Font configuration mode for docx4j PDF generation.
 * Controls how fonts are discovered and loaded during document processing.
 */
public enum FontConfigMode {

    /**
     * Automatic mode (default): Uses minimal font list on non-Windows platforms
     * to avoid issues with certain fonts (like Noto emoji fonts) that can cause
     * AssertionError during font discovery. On Windows, uses full auto-discovery.
     */
    AUTO,

    /**
     * Use docx4j's default font auto-discovery.
     * May cause issues on some systems with certain fonts.
     */
    AUTO_DISCOVER_FONTS,

    /**
     * Use a minimal, safe font list to avoid font scanning issues.
     * Limits fonts to common safe fonts: Calibri, Courier, Arial, Times,
     * Comic Sans, Georgia, Impact, Tahoma, Trebuchet, Verdana, Symbol,
     * Webdings, Wingdings, Liberation, DejaVu, FreeSans, FreeSerif, FreeMono.
     */
    MINIMAL
}
