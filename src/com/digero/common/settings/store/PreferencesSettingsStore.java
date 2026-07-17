package com.digero.common.settings.store;

import com.digero.common.settings.SettingsException;

import java.util.Objects;
import com.digero.common.settings.SettingsStore;
import com.digero.common.settings.SettingKey;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public final class PreferencesSettingsStore implements SettingsStore {
    private final Preferences preferences;

    /**
     * Creates a new PreferencesSettingsStore with the specified Preferences node.
     *
     * @param preferences the Preferences node to use for storing settings
     * @throws NullPointerException if {@code preferences} is null
     */
    public PreferencesSettingsStore(Preferences preferences) {
        this.preferences = Objects.requireNonNull(preferences, "preferences cannot be null");
    }

    /**
     * Retrieves the value of the specified setting key from the store.
     *
     * @param key the setting key to retrieve
     * @param <T> the type of the setting value
     * @return the value of the setting, or the default value if not found
     * @throws NullPointerException if {@code key} is null
     * @throws SettingsException    if an error occurs while retrieving the setting
     */
    @Override
    public <T> T get(SettingKey<T> key) {
        Objects.requireNonNull(key, "key cannot be null");
        try {
            String value = preferences.get(key.getKey(), null);
            if (value == null) {
                return key.getDefaultValue();
            }
            return key.deserialize(value);
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new SettingsException("Failed to get setting for key: " + key.getKey(), e);
        }
    }

    /**
     * Checks if the specified setting key exists in the store.
     *
     * @param key the setting key to check
     * @return true if the setting exists, false otherwise
     * @throws NullPointerException if {@code key} is null
     * @throws SettingsException    if an error occurs while checking the setting
     */
    @Override
    public boolean contains(SettingKey<?> key) {
        Objects.requireNonNull(key, "key cannot be null");
        try {
            return preferences.get(key.getKey(), null) != null;
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new SettingsException("Failed to check if setting exists for key: " + key.getKey(), e);
        }
    }

    /**
     * Sets the value of the specified setting key.
     *
     * @param key   the setting key
     * @param value the value to set, or null to remove the setting
     * @throws NullPointerException if {@code key} is null
     * @throws SettingsException    if an error occurs while setting the value
     */
    @Override
    public <T> void set(SettingKey<T> key, T value) {
        Objects.requireNonNull(key, "key cannot be null");
        try {
            if (value == null) {
                remove(key);
                return;
            }
            preferences.put(key.getKey(), key.serialize(value));
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new SettingsException("Failed to set setting for key: " + key.getKey(), e);
        }
    }

    /**
     * Removes the specified setting key from the store.
     *
     * @param key the setting key to remove
     * @throws NullPointerException if {@code key} is null
     * @throws SettingsException    if an error occurs while removing the setting
     */
    @Override
    public void remove(SettingKey<?> key) {
        Objects.requireNonNull(key, "key cannot be null");
        try {
            preferences.remove(key.getKey());
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new SettingsException("Failed to remove setting for key: " + key.getKey(), e);
        }
    }

    /**
     * Clears all settings from the store.
     *
     * @throws SettingsException if an error occurs while clearing the settings
     */
    @Override
    public void clear() {
        try {
            preferences.clear();
        } catch (BackingStoreException | IllegalStateException e) {
            throw new SettingsException("Failed to clear preferences", e);
        }
    }

    @Override
    public void flush() {
        try {
            preferences.flush();
        } catch (BackingStoreException e) {
            throw new SettingsException("Failed to flush settings", e);
        }
    }
}
