package com.digero.common.settings.store;

import com.digero.common.settings.SettingKey;
import com.digero.common.settings.SettingsException;
import com.digero.common.settings.SettingsStore;
import java.util.Objects;
import java.util.HashMap;
import java.util.Map;

/**
 * An in-memory implementation of the SettingsStore interface. This store keeps
 * settings in memory and does not persist them.
 */
public class InMemorySettingsStore implements SettingsStore {

    private final Map<String, String> values = new HashMap<>();

    public InMemorySettingsStore() {
    }

    /**
     * Retrieves the value of the specified setting key from the store.
     *
     * @param key the setting key to retrieve
     * @param <T> the type of the setting value
     * @return the value of the setting, or the default value if not found
     * @throws NullPointerException if {@code key} is null
     * @throws SettingsException    if an error occurs while deserializing the
     *                              setting
     */
    @Override
    public <T> T get(SettingKey<T> key) {
        Objects.requireNonNull(key, "key cannot be null");

        String value = values.get(key.getKey());
        if (value == null) {
            return key.getDefaultValue();
        }
        try {
            return key.deserialize(value);
        } catch (ClassCastException e) {
            throw new SettingsException("Failed to deserialize setting for key: " + key.getKey(), e);
        }
    }

    /**
     * Checks if the specified setting key exists in the store.
     *
     * @param key the setting key to check
     * @return true if the setting exists, false otherwise
     * @throws NullPointerException if {@code key} is null
     */
    @Override
    public boolean contains(SettingKey<?> key) {
        Objects.requireNonNull(key, "key cannot be null");
        return values.containsKey(key.getKey());
    }

    /**
     * Sets the value of the specified setting key in the store.
     *
     * @param key   the setting key to set
     * @param value the value to set for the setting key
     * @param <T>   the type of the setting value
     * @throws NullPointerException if {@code key} is null
     * @throws SettingsException    if an error occurs while serializing the value
     */
    @Override
    public <T> void set(SettingKey<T> key, T value) {
        Objects.requireNonNull(key, "key cannot be null");

        if (value == null) {
            remove(key);
            return;
        }
        try {
            values.put(key.getKey(), key.serialize(value));
        } catch (UnsupportedOperationException | ClassCastException | IllegalArgumentException e) {
            throw new SettingsException("Failed to serialize setting for key: " + key.getKey(), e);
        }
    }

    /**
     * Removes the specified setting key from the store.
     *
     * @param key the setting key to remove
     * @throws NullPointerException if {@code key} is null
     */
    @Override
    public void remove(SettingKey<?> key) {
        Objects.requireNonNull(key, "key cannot be null");
        values.remove(key.getKey());
    }

    /**
     * Clears all settings from the store.
     */
    @Override
    public void clear() {
        values.clear();
    }

    /**
     * Flushes the settings to the underlying storage. This is a no-op for the
     * InMemorySettingsStore since it does not support persistent storage.
     */
    @Override
    public void flush() {
        // No-op for in-memory store; nothing to flush
    }

}
