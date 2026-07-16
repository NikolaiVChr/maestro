package com.digero.common.i18n;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jetbrains.annotations.PropertyKey;

/**
 * Utility class for handling UI text localization and retrieval.
 * Initialize I18n after LocaleManager has been initialized to ensure the
 * correct locale is used.
 */
public final class I18n {
    private static final Logger LOGGER = Logger.getLogger(I18n.class.getName());
    private static final String BUNDLE_NAME = "uitext";
    private static volatile ResourceBundle RESOURCE_BUNDLE; // if the locale is changed at runtime, reload the resource
                                                            // bundle accordingly
    private static volatile boolean initialized;

    private I18n() {
        // Prevent instantiation
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        reload();
        initialized = true;
    }

    public static String get(@PropertyKey(resourceBundle = BUNDLE_NAME) String key, Object... args) {
        if (RESOURCE_BUNDLE == null) {
            throw new IllegalStateException("I18n has not been initialized");
        }
        try {
            String localizedString = RESOURCE_BUNDLE.getString(key);

            // If no arguments are provided, return the string directly
            if (args.length == 0) {
                return localizedString;
            }

            // If arguments are provided, format the string using MessageFormat
            MessageFormat messageFormat = new MessageFormat(
                    localizedString,
                    LocaleManager.getLocale());

            return messageFormat.format(args);

        } catch (MissingResourceException | IllegalArgumentException e) {
            LOGGER.log(Level.SEVERE, "Failed to resolve localized string for key: \"" + key + "\"", e);
        }
        return "!" + key + "!";
    }

    /**
     * Reloads the resource bundle to reflect any changes in the locale.
     * This should be called after changing the locale at runtime.
     */
    public static synchronized void reload() {
        try {
            RESOURCE_BUNDLE = ResourceBundle.getBundle(
                    BUNDLE_NAME,
                    LocaleManager.getLocale());
        } catch (MissingResourceException e) {
            LOGGER.log(Level.SEVERE, "Failed to reload resource bundle for locale: " + LocaleManager.getLocale(), e);
        }
    }

}
