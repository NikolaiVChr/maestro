package com.digero.common.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.IdentityHashMap;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

public final class SanitizingFormatter extends SimpleFormatter {
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final String LINE_SEPARATOR = System.lineSeparator();

    @Override
    public String format(LogRecord record) {
        Object[] params = record.getParameters();
        Object[] sanitizedParams = params;

        if (params != null) {
            sanitizedParams = params.clone();
            for (int i = 0; i < sanitizedParams.length; i++) {
                if (sanitizedParams[i] != null) {
                    // Sanitize each parameter by converting it to a string and replacing dangerous
                    // characters
                    sanitizedParams[i] = sanitizeString(String.valueOf(sanitizedParams[i]));
                }
            }
        }

        LogRecord sanitizedRecord = new LogRecord(record.getLevel(), sanitizeString(record.getMessage()));
        sanitizedRecord.setLoggerName(record.getLoggerName());
        sanitizedRecord.setInstant(record.getInstant());
        sanitizedRecord.setParameters(sanitizedParams);
        sanitizedRecord.setResourceBundle(record.getResourceBundle());
        sanitizedRecord.setResourceBundleName(record.getResourceBundleName());
        sanitizedRecord.setSequenceNumber(record.getSequenceNumber());
        sanitizedRecord.setSourceClassName(record.getSourceClassName());
        sanitizedRecord.setSourceMethodName(record.getSourceMethodName());
        sanitizedRecord.setLongThreadID(record.getLongThreadID());
        sanitizedRecord.setThrown(sanitizeThrowable(record.getThrown()));

        return formatSanitizedRecord(sanitizedRecord, formatMessage(sanitizedRecord));
    }

    	private static String formatSanitizedRecord(LogRecord record, String message) {
        StringBuilder result = new StringBuilder();
        result.append(TIMESTAMP_FORMAT.format(record.getInstant()))
                .append(' ')
                .append(record.getLevel().getName());

        String loggerName = record.getLoggerName();
        if (loggerName != null && !loggerName.isEmpty()) {
            result.append(' ').append(loggerName);
        }

        if (message != null && !message.isEmpty()) {
            result.append(" - ").append(message);
        }

        result.append(LINE_SEPARATOR);

        Throwable thrown = record.getThrown();
        if (thrown != null) {
            StringWriter writer = new StringWriter();
            thrown.printStackTrace(new PrintWriter(writer));
            result.append(writer);
        }

        return result.toString();
    }

    /**
     * Sanitizes string by replacing dangerous characters with an underscore.
     * 
     * @param input the string to sanitize
     * @return the sanitized string
     */
    private static String sanitizeString(String input) {
        if (input == null) {
            return null;
        }

        StringBuilder result = new StringBuilder(input.length());
        for (int offset = 0; offset < input.length();) {
            int cp = input.codePointAt(offset);
            offset += Character.charCount(cp);

            if (isDangerousCodePoint(cp)) {
                // Replace dangerous characters with an underscore
                result.append("_");
            } else {
                result.appendCodePoint(cp);
            }

        }

        return result.toString();
    }

    private static Throwable sanitizeThrowable(Throwable thrown) {
        if (thrown == null) {
            return null;
        }

        return sanitizeThrowable(thrown, new IdentityHashMap<Throwable, SanitizedThrowable>());
    }

    /**
     * Recursively sanitizes a Throwable and its causes and suppressed exceptions.
     * 
     * @param thrown the Throwable to sanitize
     * @param seen   a map to track already sanitized Throwables to avoid infinite
     *               loops
     * @return the sanitized Throwable
     */
    private static Throwable sanitizeThrowable(Throwable thrown,
            IdentityHashMap<Throwable, SanitizedThrowable> seen) {
        SanitizedThrowable existing = seen.get(thrown);
        if (existing != null) {
            return existing;
        }

        SanitizedThrowable sanitized = new SanitizedThrowable(thrown);
        seen.put(thrown, sanitized);
        sanitized.setStackTrace(thrown.getStackTrace());

        Throwable cause = thrown.getCause();
        if (cause != null) {
            Throwable sanitizedCause = sanitizeThrowable(cause, seen);
            if (sanitizedCause != sanitized) {
                sanitized.initCause(sanitizedCause);
            }
        }

        for (Throwable suppressed : thrown.getSuppressed()) {
            Throwable sanitizedSuppressed = sanitizeThrowable(suppressed, seen);
            if (sanitizedSuppressed != sanitized) {
                sanitized.addSuppressed(sanitizedSuppressed);
            }
        }

        return sanitized;
    }

    /**
     * Checks if a code point is considered dangerous for logging purposes.
     * 
     * @param cp the code point to check
     * @return true if the code point is dangerous, false otherwise
     */
    private static boolean isDangerousCodePoint(int cp) {
        // Check for control characters
        if (Character.isISOControl(cp)) {
            return true;
        }

        // Check for zero-width and non-printable characters
        if (cp == 0x200B || cp == 0x200C || cp == 0x200D
                || cp == 0x2060 || cp == 0xFEFF
                || cp == 0x2061 || cp == 0x2062
                || cp == 0x2063 || cp == 0x2064) {
            return true;
        }

        // Check for bidirectional text control characters
        if ((cp >= 0x202A && cp <= 0x202E)
                || (cp >= 0x2066 && cp <= 0x2069)) {
            return true;
        }

        // Check for other formatting characters
        return cp == 0x2028
                || cp == 0x2029
                || Character.getType(cp) == Character.FORMAT;
    }

    /**
     * Appends the Unicode escape sequence for a given code point to a
     * StringBuilder.
     * 
     * @param sb the StringBuilder to append to
     * @param cp the code point to convert to a Unicode escape sequence
     */
    @SuppressWarnings("unused") // This method is not currently used, but may be useful for future enhancements
    private static void appendUnicodeEscape(StringBuilder sb, int cp) {
        sb.append("\\u");
        String hex = Integer.toHexString(cp);

        for (int i = hex.length(); i < 4; i++) {
            sb.append('0');
        }

        sb.append(hex);
    }

    /**
     * A Throwable subclass that sanitizes its string representation.
     */
    private static final class SanitizedThrowable extends Throwable {
        private final String sanitizedText;

        private SanitizedThrowable(Throwable original) {
            super();
            this.sanitizedText = sanitizeString(original.toString());
        }

        @Override
        public String toString() {
            return sanitizedText;
        }
    }
}
