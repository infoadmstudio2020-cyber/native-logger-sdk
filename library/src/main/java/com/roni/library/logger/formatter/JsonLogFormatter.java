package com.roni.library.logger.formatter;

import com.roni.library.contracts.logger.LogEntry;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Formats a LogEntry into an escaped, structured JSON string.
 */
public class JsonLogFormatter implements ILogFormatter {
    private final PatternLogFormatter piiMasker;

    public JsonLogFormatter(boolean piiMasking) {
        this.piiMasker = piiMasking ? new PatternLogFormatter(true) : null;
    }

    public JsonLogFormatter() {
        this(true);
    }

    @Override
    public String format(LogEntry entry) {
        if (entry == null) {
            return "{}";
        }

        String rawMsg = entry.getMessage();
        if (piiMasker != null && rawMsg != null) {
            rawMsg = piiMasker.maskPII(rawMsg);
        }

        StringBuilder json = new StringBuilder(256);
        json.append("{")
            .append("\"timestamp\":").append(entry.getTimestamp()).append(",")
            .append("\"level\":\"").append(entry.getLevel().name()).append("\",")
            .append("\"tag\":\"").append(escapeJson(entry.getTag())).append("\",")
            .append("\"thread\":\"").append(escapeJson(entry.getThreadName())).append("\",")
            .append("\"message\":\"").append(escapeJson(rawMsg)).append("\"");

        if (entry.getCorrelationId() != null && !entry.getCorrelationId().isEmpty()) {
            json.append(",\"correlationId\":\"").append(escapeJson(entry.getCorrelationId())).append("\"");
        }

        if (entry.getThrowable() != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            entry.getThrowable().printStackTrace(pw);
            json.append(",\"stackTrace\":\"").append(escapeJson(sw.toString())).append("\"");
        }

        json.append("}");
        return json.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (ch < ' ') {
                        String t = "000" + Integer.toHexString(ch);
                        sb.append("\\u").append(t.substring(t.length() - 4));
                    } else {
                        sb.append(ch);
                    }
            }
        }
        return sb.toString();
    }
}
