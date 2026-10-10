package com.roni.library.logger.formatter;

import com.roni.library.contracts.logger.LogEntry;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Standard pattern-based log formatter with built-in regex PII masking.
 */
public class PatternLogFormatter implements ILogFormatter {
    private static final String DATE_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS";
    private final SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_PATTERN, Locale.US);

    // PII Masking regular expressions
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile("\\b(?:\\d{4}[- ]?){3}\\d{4}\\b");
    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile("(?i)(bearer\\s+)[a-zA-Z0-9._~+/-]+=*");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("(?i)(password[\"']?\\s*[:=]\\s*[\"'])([^\"'\\s]+)([\"'])");
    private static final Pattern PIN_PATTERN = Pattern.compile("(?i)(pin[\"']?\\s*[:=]\\s*[\"'])([^\"'\\s]+)([\"'])");

    private final boolean piiMaskingEnabled;

    public PatternLogFormatter(boolean piiMaskingEnabled) {
        this.piiMaskingEnabled = piiMaskingEnabled;
    }

    public PatternLogFormatter() {
        this(true);
    }

    @Override
    public String format(LogEntry entry) {
        if (entry == null) {
            return "";
        }

        String formattedDate;
        synchronized (dateFormat) {
            formattedDate = dateFormat.format(new Date(entry.getTimestamp()));
        }

        String message = entry.getMessage();
        if (piiMaskingEnabled && message != null && !message.isEmpty()) {
            message = maskPII(message);
        }

        StringBuilder sb = new StringBuilder(256);
        sb.append(formattedDate)
          .append(" [").append(entry.getThreadName()).append("] ")
          .append(entry.getLevel().getShortName())
          .append("/").append(entry.getTag()).append(": ")
          .append(message);

        if (entry.getCorrelationId() != null && !entry.getCorrelationId().isEmpty()) {
            sb.append(" (corr: ").append(entry.getCorrelationId()).append(")");
        }

        if (entry.getThrowable() != null) {
            sb.append("\n");
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            entry.getThrowable().printStackTrace(pw);
            sb.append(sw.toString());
        }

        return sb.toString();
    }

    /**
     * Sanitizes known sensitive data patterns to ensure GDPR/CCPA privacy compliance.
     */
    public String maskPII(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // Mask Credit Cards (PAN)
        String sanitized = CREDIT_CARD_PATTERN.matcher(text).replaceAll("[REDACTED_PAN]");
        // Mask Authorization Bearer Tokens
        sanitized = BEARER_TOKEN_PATTERN.matcher(sanitized).replaceAll("$1[REDACTED_AUTH_TOKEN]");
        // Mask Passwords
        sanitized = PASSWORD_PATTERN.matcher(sanitized).replaceAll("$1[REDACTED_SECRET]$3");
        // Mask PINs
        sanitized = PIN_PATTERN.matcher(sanitized).replaceAll("$1[REDACTED_SECRET]$3");

        return sanitized;
    }
}
