package com.digero.common.settings;

import java.util.Locale;

import com.digero.common.settings.converter.DefaultConverters;
import com.digero.common.settings.store.InMemorySettingsStore;

/**
 * Example implementation of a settings manager using the
 * MaestroSettingsExampleImplementation class.
 * This class demonstrates how to define and manage application settings using
 * the provided Settings API.
 */
public class MaestroSettingsExample {

    // The underlying Settings instance used for managing settings
    private final Settings settings;

    // Singleton instance of MaestroSettingsExampleImplementation
    private static volatile MaestroSettingsExample instance;

    // Define setting keys with default values and converters
    private static final SettingKey<Integer> FONT_SIZE = SettingKey.of("fontSize", 12, DefaultConverters.INTEGER);
    private static final SettingKey<Boolean> DARK_MODE = SettingKey.of("darkMode", false, DefaultConverters.BOOLEAN);
    private static final SettingKey<String> USERNAME = SettingKey.of("username", "", DefaultConverters.STRING);
    private static final SettingKey<Locale> LOCALE = SettingKey.of("locale", Locale.ENGLISH, DefaultConverters.LOCALE);

    /**
     * Initializes the singleton instance of MaestroSettingsExampleImplementation
     * with the provided SettingsStore.
     * This method must be called before calling get().
     *
     * @param store The SettingsStore to use for storing settings.
     * @return The initialized instance of MaestroSettingsExampleImplementation or
     *         the existing instance if it has already been initialized.
     */
    public static MaestroSettingsExample init(SettingsStore store) {
        if (instance == null) {
            synchronized (MaestroSettingsExample.class) {
                if (instance == null) {
                    instance = new MaestroSettingsExample(store);
                }
            }
        }
        return instance;
    }

    /**
     * Returns the singleton instance of MaestroSettingsExampleImplementation.
     * This method should only be called after init() has been called.
     *
     * @return The singleton instance of MaestroSettingsExampleImplementation.
     * @throws IllegalStateException if init() has not been called before this
     *                               method.
     */
    public static MaestroSettingsExample get() {
        if (instance == null) {
            throw new IllegalStateException("MaestroSettingsExampleImplementation not initialized");
        }
        return instance;
    }

    /**
     * Private constructor to enforce singleton pattern.
     *
     * @param store The SettingsStore to use for storing settings.
     */
    private MaestroSettingsExample(SettingsStore store) {
        this.settings = new Settings(store);
    }

    public int getFontSize() {
        return settings.get(FONT_SIZE);
    }

    public void setFontSize(int size) {
        settings.set(FONT_SIZE, size);
    }

    public boolean isDarkMode() {
        return settings.get(DARK_MODE);
    }

    public void setDarkMode(boolean enabled) {
        settings.set(DARK_MODE, enabled);
    }

    public String getUsername() {
        return settings.get(USERNAME);
    }

    public void setUsername(String username) {
        settings.set(USERNAME, username);
    }

    public Locale getLocale() {
        return settings.get(LOCALE);
    }

    public void setLocale(Locale locale) {
        settings.set(LOCALE, locale);
    }

    public void remove(SettingKey<?> key) {
        settings.remove(key);
    }

    public void clear() {
        settings.clear();
    }

    public void flush() {
        settings.flush();
    }

    /**
     * Example usage of the MaestroSettingsExampleImplementation class.
     * This inner class demonstrates how to initialize and use the settings
     * manager.
     */
    @SuppressWarnings("unused")
    private static class UsageExample {
        public void exampleUsage() {
            // Initialize the settings manager with a SettingsStore implementation
            SettingsStore store = new InMemorySettingsStore(); // Replace with actual implementation
            MaestroSettingsExample.init(store);

            // Access the singleton instance
            MaestroSettingsExample settingsManager = MaestroSettingsExample.get();

            // Set and get settings
            settingsManager.setFontSize(14);
            int fontSize = settingsManager.getFontSize();

            settingsManager.setDarkMode(true);
            boolean darkModeEnabled = settingsManager.isDarkMode();

            settingsManager.setUsername("JohnDoe");
            String username = settingsManager.getUsername();

            settingsManager.setLocale(Locale.FRENCH);
            Locale locale = settingsManager.getLocale();

            // Remove a setting
            settingsManager.remove(FONT_SIZE);

            // Clear all settings
            settingsManager.clear();

            // Flush changes to the underlying store
            settingsManager.flush();
        }
    }
}
