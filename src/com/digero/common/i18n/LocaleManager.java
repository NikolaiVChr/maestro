package com.digero.common.i18n;

import java.awt.GraphicsEnvironment;
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
 * Manages the application's current locale and locale preferences.
 *
 * Initialize before creating Swing UI components.
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
    private static boolean initialized = false;

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

        // Check if the locale has already been initialized to avoid redundant work.
        if (initialized) {
            return;
        }

        // Retrieve the stored locale preference, if any.
        String lang = PREFS.get("locale", null);

        // Migrate legacy locale setting "US" to Locale.ENGLISH
        final String LEGACY_LOCALE_US = "US";
        if (LEGACY_LOCALE_US.equalsIgnoreCase(lang)) {
            LOGGER.log(Level.INFO, "Migrating legacy locale setting US to " + Locale.ENGLISH);
            lang = Locale.ENGLISH.getLanguage();
            PREFS.put("locale", lang);
        }

        final String selectedLanguage = lang;

        // Determine the locale to use: either the stored preference or prompt the user.
        locale = SUPPORTED_LOCALES.stream()
                .filter(l -> l.getLanguage().equals(selectedLanguage))
                .findFirst()
                .orElseGet(() -> {
                    if (GraphicsEnvironment.isHeadless()) {
                        LOGGER.info("Running in headless mode, defaulting to Locale.ENGLISH.");
                        return Locale.ENGLISH;
                    } else {
                        Locale selected = promptUserForLocale();
                        PREFS.put("locale", selected.getLanguage());
                        return selected;
                    }
                });

        LOGGER.info("Using locale: " + locale);
        initialized = true;
    }

    /**
     * Prompts the user to select a locale, ensuring the dialog is shown on the
     * Event Dispatch Thread if necessary.
     *
     * @return The selected locale, or Locale.ENGLISH if the user cancels the
     *         dialog.
     */
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
     * Creates a dialog that displays the supported locales, where the language
     * names are displayed in their respective languages.
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
     * @return The currently selected locale.
     * @throws IllegalStateException if the LocaleManager has not been initialized.
     */
    public static Locale getLocale() {
        if (!initialized) {
            throw new IllegalStateException("LocaleManager has not been initialized.");
        }
        return locale;
    }

    /**
     * Sets the preferred locale. If the preferred locale is not supported, an
     * IllegalArgumentException is thrown.
     * 
     * @param preferredLocale The preferred locale to set.
     */
    public static void setPreferredLocale(Locale preferredLocale) {
        if (!SUPPORTED_LOCALES.contains(preferredLocale)) {
            throw new IllegalArgumentException("Unsupported locale: " + preferredLocale);
        }

        PREFS.put("locale", preferredLocale.getLanguage());
        LOGGER.info("Locale changed to " + preferredLocale + " it will take effect after restart.");
    }

    /**
     * Removes the preferred locale, if it exists.
     * 
     * @return true if a preferred locale existed and was removed, false otherwise.
     */
    public static boolean removePreferredLocale() {
        boolean existed = PREFS.get("locale", null) != null;
        PREFS.remove("locale");
        if (existed) {
            LOGGER.info("Locale preference removed, default will be used.");
        }
        return existed;
    }

    /**
     * Returns a list of supported locales.
     * 
     * @return A list of supported locales.
     */
    public static List<Locale> getSupportedLocales() {
        return List.copyOf(SUPPORTED_LOCALES);
    }

    public static String getLanguageSelectionMessage() {
        return LANGUAGE_SELECTION_MESSAGE;
    }
}
