package com.digero.common.view;

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
 * Do NOT access this in static fields in main classes. (AbcPlayer, MaestroMain, AbcTools)
 * And best wait AFTER the first use of Swing thread.
 * And after Logging has been init.
 */
public class UIText {
    private static final Logger log = Logger.getLogger("locale"); //NON-NLS
    private static String locale = null;
    private static final @NonNls String BUNDLE_NAME = "uitext";
    private static ResourceBundle uiText = null;
    public static @NonNls String LANG_EN = "en";
    public static @NonNls String LANG_FR = "fr";
    public static @NonNls String LANG_DE = "de";
    private static final @NonNls String LANG_EN_NAME = "English";
    private static final @NonNls String LANG_FR_NAME = "Français";
    private static final @NonNls String LANG_DE_NAME = "Deutsch";
    private static final @NonNls String mainKey = "locale";

    /**
     * App convention: numeric arguments in UI text are always
     * formatted in en-US, regardless of the selected UI language. This matches how
     * numbers are formatted all other places in the app. Do not replace this with the
     * selected locale (for now), the fixed locale here is intentional.
     */
    private static final Locale NUMBER_LOCALE = Locale.US;

    static {
        //Preferences.userNodeForPackage(MaestroMain.class).node("miscSettings").remove(mainKey);
        locale = Preferences.userNodeForPackage(MaestroMain.class).node("miscSettings").get(mainKey, null); //NON-NLS
        log.info("Stored locale: " + (locale==null?"null":locale));

        if (locale == null) {
            int[] choice = {-1};
            if (!GraphicsEnvironment.isHeadless()) {// test if we are in code testing
                log.info("No locale stored, prompting user");
                try {
                    if (SwingUtilities.isEventDispatchThread()) {
                        choice[0] = JOptionPane.showOptionDialog(null, "Language/Langue/Sprache", "Maestro Language", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, new Object[]{LANG_EN_NAME, LANG_FR_NAME, LANG_DE_NAME}, LANG_EN_NAME); //NON-NLS  //NON-NLS
                    } else {
                        SwingUtilities.invokeAndWait(() -> {
                            choice[0] = JOptionPane.showOptionDialog(null, "Language/Langue/Sprache", "Maestro Language", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, new Object[]{LANG_EN_NAME, LANG_FR_NAME, LANG_DE_NAME}, LANG_EN_NAME); //NON-NLS  //NON-NLS
                        });
                    }
                } catch (InterruptedException | InvocationTargetException e) {
                    log.log(Level.WARNING, "Failed to initialize locale, EN will be used.", e);
                }
            }

            if (choice[0] == 0) locale = LANG_EN;
            else if (choice[0] == 1) locale = LANG_FR;
            else if (choice[0] == 2) locale = LANG_DE;
            else locale = LANG_EN;

            if (!GraphicsEnvironment.isHeadless()) {
                log.fine("saving "+locale);
                Preferences.userNodeForPackage(MaestroMain.class).node("miscSettings").put(mainKey, locale);
            }
        }
        Locale selectedLocale;

        if (locale == null || "US".equals(locale)) {
            // Backwards compat; Initially used "US" instead of "en".
            locale = LANG_EN;
        }
        locale = locale.toLowerCase();//handle FR and DE

        if (LANG_EN.equals(locale)) {
            //selectedLocale = Locale.ROOT; // Forces use of uitext.properties
            selectedLocale = Locale.of(locale);
        } else {
            selectedLocale = Locale.of(locale);
        }
        log.info("Using locale: " + selectedLocale);
        uiText = ResourceBundle.getBundle(BUNDLE_NAME, selectedLocale);
    }

    public static @NotNull String get(@PropertyKey(resourceBundle = BUNDLE_NAME)String key, Locale local) {
        try {
            return ResourceBundle.getBundle(BUNDLE_NAME, local).getString(key);
        } catch (Exception e) {
            log.warning("Failed to load UI text for key \"" + key + "\", locale is " + local); //NON-NLS //NON-NLS
            return "!" + key + "!";
        }
    }

    public static @NotNull String get(@PropertyKey(resourceBundle = BUNDLE_NAME)String key, Object... args) {
        String value;
        try {
            value = uiText.getString(key);
        } catch (MissingResourceException e) {
            log.warning("Failed to load UI text for key \"" + key + "\", locale is " + locale); //NON-NLS //NON-NLS
            return "!" + key + "!";
        }

        // Only require escaped quotes
        // if parameters are actually passed.
        if (args.length > 0) {
            // This expects "It''s {0}"
            return new MessageFormat(value, NUMBER_LOCALE).format(args);
        } else {
            // This expects "It's time" (no escaping needed)
            return value;
        }
    }
}
