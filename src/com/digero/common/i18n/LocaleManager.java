package com.digero.common.i18n;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Locale;
import java.util.prefs.Preferences;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.digero.maestro.MaestroMain;

/**
 * Handles UI text localization and retrieval.
 *
 * Initialize LocaleManager before creating Swing UI components.
 * LocaleManager.init() may show a language selection dialog on first run,
 * so it should be called from the application startup sequence.
 */
public final class LocaleManager {
    private static final Logger LOGGER = Logger.getLogger(LocaleManager.class.getName());

    // Keep preferences anchored to the application package so refactoring
    // common.i18n does not move user settings.
    private static final Preferences PREFS = Preferences.userNodeForPackage(MaestroMain.class)
            .node("miscSettings");

    private static final List<Locale> SUPPORTED_LOCALES = List.of(
            Locale.ENGLISH,
            Locale.FRENCH,
            Locale.GERMAN);

    private static volatile Locale locale = Locale.ENGLISH;
    private static volatile boolean initialized;

    private static final String LANGUAGE_SELECTION_MESSAGE = "Language/Langue/Sprache";
    private static final String LANGUAGE_SELECTION_TITLE = "Maestro Language";

    private LocaleManager() {
        // Prevent instantiation
    }

    /**
     * Initializes the locale settings. If a locale is already stored in
     * preferences, it will be used.
     * Otherwise, the user will be prompted to select a locale.
     */
    public static synchronized void init() {
        if (initialized) {
            return;
        }

        String lang = PREFS.get("locale", null);

        locale = SUPPORTED_LOCALES.stream()
                .filter(l -> l.getLanguage().equals(lang))
                .findFirst()
                .orElseGet(() -> {
                    Locale selected = promptUserForLocale();
                    PREFS.put("locale", selected.getLanguage());
                    return selected;
                });

        LOGGER.info("Using locale: " + locale);
        initialized = true;
    }

    private static Locale promptUserForLocale() {
        if (SwingUtilities.isEventDispatchThread()) {
            return showLocaleDialog();
        }

        final Locale[] result = { Locale.ENGLISH };

        try {
            SwingUtilities.invokeAndWait(() -> result[0] = showLocaleDialog());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "Interrupted while selecting locale", e);
        } catch (InvocationTargetException e) {
            LOGGER.log(Level.WARNING, "Failed to show locale dialog", e);
        }

        return result[0];
    }

    /**
     * Prompts the user to select a locale from the supported locales.
     * 
     * @return The selected locale, or Locale.ENGLISH if the user cancels the
     *         dialog.
     */
    private static Locale showLocaleDialog() {
        Object[] languages = SUPPORTED_LOCALES.stream()
                .map(locale -> locale.getDisplayLanguage(locale))
                .toArray();

        int selectedIndex = JOptionPane.showOptionDialog(
                null,
                LANGUAGE_SELECTION_MESSAGE,
                LANGUAGE_SELECTION_TITLE,
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                languages,
                languages[0]);

        if (selectedIndex >= 0 && selectedIndex < SUPPORTED_LOCALES.size()) {
            return SUPPORTED_LOCALES.get(selectedIndex);
        } else {
            return Locale.ENGLISH;
        }
    }

    /**
     * Returns the currently selected locale.
     * 
     * @return The current locale.
     */
    public static Locale getLocale() {
        return locale;
    }

    /**
     * Sets the current locale to the specified newLocale. If the newLocale is not
     * supported, it will not change the current locale.
     * 
     * @param newLocale The new locale to set.
     */
    public static void setLocale(Locale newLocale) {
        if (newLocale == null) {
            LOGGER.warning("Attempted to set null locale");
            return;
        }

        Locale supportedLocale = SUPPORTED_LOCALES.stream()
                .filter(l -> l.getLanguage().equals(newLocale.getLanguage()))
                .findFirst()
                .orElse(null);

        if (supportedLocale == null) {
            LOGGER.warning("Attempted to set unsupported locale: " + newLocale);
            return;
        }

        locale = supportedLocale;
        PREFS.put("locale", supportedLocale.getLanguage());
        LOGGER.info("Locale changed to: " + supportedLocale);
    }

    /**
     * Returns a list of supported locales.
     * 
     * @return A list of supported locales.
     */
    public static List<Locale> getSupportedLocales() {
        return List.copyOf(SUPPORTED_LOCALES);
    }
}
