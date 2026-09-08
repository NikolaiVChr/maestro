package com.digero.common.i18n;

import com.digero.maestro.MaestroMain;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * Utility class for handling UI text localization and retrieval.
 * Do NOT access this in static fields in main classes. (AbcPlayer, MaestroMain,
 * AbcTools)
 * And best wait AFTER the first use of Swing thread.
 * And after Logging has been init.
 */
public class UIText {
    private static final Logger LOGGER = Logger.getLogger(UIText.class.getName());
    private static final String BUNDLE_NAME = "uitext";

    private static ResourceBundle resourceBundle;
    private static boolean initialized;
    private static String locale = null;

    /**
     * App convention: numeric arguments in UI text are always
     * formatted in en-US, regardless of the selected UI language. This matches how
     * numbers are formatted all other places in the app. Do not replace this with
     * the
     * selected locale (for now), the fixed locale here is intentional.
     */
    private static final Locale NUMBER_LOCALE = Locale.US;

    private UIText() {
        // Prevent instantiation
    }

    public static void init() {
        if (initialized) {
            return;
        }

        Locale locale = LocaleManager.getLocale();
        resourceBundle = ResourceBundle.getBundle(BUNDLE_NAME, locale);

        initialized = true;
    }

    public static String get(@PropertyKey(resourceBundle = BUNDLE_NAME) String key, Locale local) {
        try {
            return ResourceBundle.getBundle(BUNDLE_NAME, local).getString(key);
        } catch (Exception e) {
            LOGGER.warning("Failed to load UI text for key \"" + key + "\", locale is " + local);
            return "!" + key + "!";
        }
    }

    public static String get(@PropertyKey(resourceBundle = BUNDLE_NAME) String key, Object... args) {
        if (!initialized) {
            throw new IllegalStateException("UIText has not been initialized");
        }

        String value;
        try {
            value = resourceBundle.getString(key);
        } catch (MissingResourceException e) {
            LOGGER.warning("Failed to load UI text for key \"" + key + "\", locale is " + locale);
            return "!" + key + "!";
        }

        // Only require escaped quotes if parameters are actually passed.
        if (args.length > 0) {
            // This expects "It''s {0}"
            return new MessageFormat(value, NUMBER_LOCALE).format(args);
        }

        // This expects "It's time" (no escaping needed)
        return value;
    }
}
