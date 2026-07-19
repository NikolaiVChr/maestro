package com.digero.common.settings.store;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;
import com.digero.common.settings.SettingKey;
import com.digero.common.settings.SettingsException;
import com.digero.common.settings.SettingsStore;

/**
 * A settings store that uses a properties file to persist settings.
 */
public final class PropertiesSettingsStore implements SettingsStore {
    private final Properties properties;
    private final Path filePath;

    /**
     * Constructs a new PropertiesSettingsStore with the specified file path.
     *
     * @param filePath the path to the properties file
     * @throws NullPointerException if {@code filePath} is null
     * @throws SettingsException    if an error occurs while loading the properties
     */
    public PropertiesSettingsStore(Path filePath) {
        this.properties = new Properties();
        this.filePath = Objects.requireNonNull(filePath, "filePath cannot be null");
        load();
    }

    /**
     * Retrieves the value associated with the specified setting key.
     *
     * @param <T> the type of the setting value
     * @param key the setting key
     * @return the value associated with the key, the default value if not found,
     *         otherwise null if no default value is defined
     * @throws NullPointerException if {@code key} is null
     * @throws SettingsException    if an error occurs while deserializing the value
     */
    @Override
    public <T> T get(SettingKey<T> key) {
        Objects.requireNonNull(key, "key cannot be null");
        String value = properties.getProperty(key.getKey());

        if (value == null) {
            return key.hasDefaultValue()
                    ? key.getDefaultValue()
                    : null;
        }

        try {
            return key.deserialize(value);
        } catch (RuntimeException e) {
            throw new SettingsException(
                    "Failed to deserialize setting: " + key.getKey(), e);
        }
    }

    /**
     * Checks if the specified setting key exists in the properties.
     *
     * @param key the setting key
     * @return true if the key exists, false otherwise
     * @throws NullPointerException if {@code key} is null
     */
    @Override
    public boolean contains(SettingKey<?> key) {
        Objects.requireNonNull(key, "key cannot be null");
        return properties.containsKey(key.getKey());
    }

    /**
     * Sets the value associated with the specified setting key.
     *
     * @param <T>   the type of the setting value
     * @param key   the setting key
     * @param value the value to set; if null, the key will be removed
     * @throws NullPointerException if {@code key} is null
     */
    @Override
    public <T> void set(SettingKey<T> key, T value) {
        Objects.requireNonNull(key, "key cannot be null");

        if (value == null) {
            remove(key);
            return;
        }
        try {
            properties.setProperty(key.getKey(), key.serialize(value));
        } catch (RuntimeException e) {
            throw new SettingsException(
                    "Failed to serialize setting: " + key.getKey(), e);
        }
    }

    /**
     * Removes the specified setting key from the properties.
     *
     * @param key the setting key to remove
     * @throws NullPointerException if {@code key} is null
     */
    @Override
    public void remove(SettingKey<?> key) {
        Objects.requireNonNull(key, "key cannot be null");
        properties.remove(key.getKey());
    }

    /**
     * Clears all settings from the properties.
     */
    @Override
    public void clear() {
        properties.clear();
    }

    /**
     * Saves the properties to the specified file path.
     *
     * @throws SettingsException if an error occurs while saving
     */
    @Override
    public void flush() {
        try {

            // Ensure the parent directory exists before saving the properties file
            Path parentDir = filePath.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }

            try (Writer writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
                properties.store(writer, "Application Settings");
            }

        } catch (IOException e) {
            throw new SettingsException("Failed to save settings to " + filePath, e);
        }
    }

    /**
     * Loads the properties from the specified file path.
     *
     * @throws SettingsException if an error occurs while loading
     */
    private void load() {
        if (!Files.exists(filePath)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            properties.load(reader);

        } catch (IOException e) {
            throw new SettingsException("Failed to load settings from " + filePath, e);
        }
    }

}
