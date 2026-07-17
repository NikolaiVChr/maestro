package com.digero.common.settings;

/**
 * A store for application settings. Implementations of this interface are
 * responsible for persisting settings to a storage medium (e.g., file,
 * database).
 */
public interface SettingsStore {

    /**
     * Returns the value of the setting for the given key, or null if no value is
     * set.
     * 
     * @param key the setting key
     * @param <T> the type of the setting value
     * @return the value of the setting, or null if no value is set
     */
    <T> T get(SettingKey<T> key);

    /**
     * Returns true if the store contains a value for the given key.
     * 
     * @param key the setting key
     * @return true if the store contains a value for the key, false otherwise
     */
    boolean contains(SettingKey<?> key);

    /**
     * Sets the value of the setting for the given key.
     * 
     * @param key   the setting key
     * @param value the value to set
     * @param <T>   the type of the setting value
     */
    <T> void set(SettingKey<T> key, T value);

    /**
     * Removes the setting for the given key.
     * 
     * @param key the setting key
     */
    void remove(SettingKey<?> key);

    /**
     * Removes all settings.
     */
    void clear();
}
