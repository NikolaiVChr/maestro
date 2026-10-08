package com.digero.common.settings;

/**
 * Exception thrown when an error occurs while accessing or modifying settings.
 */
public class SettingsException extends RuntimeException {

    /**
     * Constructs a new SettingsException with the specified detail message.
     *
     * @param message the detail message
     */
    public SettingsException(String message) {
        super(message);

    }

    /**
     * Constructs a new SettingsException with the specified detail message and
     * cause.
     *
     * @param message the detail message
     * @param cause   the cause of the exception
     */
    public SettingsException(String message, Throwable cause) {
        super(message, cause);
    }

}
