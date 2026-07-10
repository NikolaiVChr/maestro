package com.digero.common.util;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

public final class SanitizingFormatter extends SimpleFormatter {
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
        sanitizedRecord.setThrown(null);

        StringBuilder formatted = new StringBuilder(super.format(sanitizedRecord));
        Throwable thrown = record.getThrown();
        if (thrown != null) {
            // Append the sanitized stack trace of the thrown exception to the formatted log
            // message
            appendSanitizedThrowable(formatted, thrown,
                    Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>()), "");
        }

        return formatted.toString();
    }

    /**
     * Appends a sanitized representation of a throwable to the log message.
     *
     * @param sb     the StringBuilder to append to
     * @param thrown the throwable to append
     * @param seen   a set of already seen throwables to detect circular references
     * @param prefix the prefix for each line
     */
    private static void appendSanitizedThrowable(StringBuilder sb, Throwable thrown,
            Set<Throwable> seen, String prefix) {
        if (!seen.add(thrown)) {
            // Circular reference detected, append a message indicating the circular
            // reference
            sb.append(prefix).append("[CIRCULAR REFERENCE: ")
                    .append(sanitizeString(thrown.toString())).append("]").append(LINE_SEPARATOR);
            return;
        }

        // Append the sanitized representation of the throwable to the log message
        sb.append(prefix).append(sanitizeString(thrown.toString())).append(LINE_SEPARATOR);
        appendStackTraceElements(sb, thrown.getStackTrace(), prefix);

        for (Throwable suppressed : thrown.getSuppressed()) {
            // Append the sanitized representation of the suppressed throwable to the log
            // message with the appropriate label
            appendEnclosedThrowable(sb, suppressed, seen, prefix, "Suppressed: ");
        }

        Throwable cause = thrown.getCause();
        if (cause != null) {
            // Append the sanitized representation of the cause throwable to the log message
            // with the appropriate label
            appendEnclosedThrowable(sb, cause, seen, prefix, "Caused by: ");
        }
    }

    /**
     * Appends a sanitized representation of an enclosed throwable (cause or
     * suppressed) to the log message.
     *
     * @param sb     the StringBuilder to append to
     * @param thrown the throwable to append
     * @param seen   a set of already seen throwables to detect circular references
     * @param prefix the prefix for each line
     * @param label  the label for the enclosed throwable (e.g., "Caused by: ",
     *               "Suppressed: ")
     */
    private static void appendEnclosedThrowable(StringBuilder sb, Throwable thrown,
            Set<Throwable> seen, String prefix, String label) {
        String childPrefix = prefix + "\t";
        if (!seen.add(thrown)) {
            // Circular reference detected, append a message indicating the circular
            // reference
            sb.append(childPrefix).append(label).append("[CIRCULAR REFERENCE: ")
                    .append(sanitizeString(thrown.toString())).append("]").append(LINE_SEPARATOR);
            return;
        }

        sb.append(childPrefix).append(label).append(sanitizeString(thrown.toString())).append(LINE_SEPARATOR);
        appendStackTraceElements(sb, thrown.getStackTrace(), childPrefix);

        for (Throwable suppressed : thrown.getSuppressed()) {
            // Append the sanitized representation of the suppressed throwable to the log
            // message with the appropriate label
            appendEnclosedThrowable(sb, suppressed, seen, childPrefix, "Suppressed: ");
        }

        Throwable cause = thrown.getCause();
        if (cause != null) {
            // Append the sanitized representation of the cause throwable to the log message
            // with the appropriate label
            appendEnclosedThrowable(sb, cause, seen, prefix, "Caused by: ");
        }
    }

    /**
     * Appends the stack trace elements of a throwable to the log message.
     *
     * @param sb         the StringBuilder to append to
     * @param stackTrace the stack trace elements to append
     * @param prefix     the prefix for each line
     */
    private static void appendStackTraceElements(StringBuilder sb, StackTraceElement[] stackTrace, String prefix) {
        for (StackTraceElement element : stackTrace) {
            sb.append(prefix).append("\tat ").append(sanitizeString(String.valueOf(element)))
                    .append(LINE_SEPARATOR);
        }
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
}
