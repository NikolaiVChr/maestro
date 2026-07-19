package com.digero.common.settings;

import java.util.Objects;

/**
 * A class representing a key for a setting, including its default
 * value and its converter.
 */
public final class SettingKey<T> {
    private final String key;
    private final T defaultValue;
    private final SettingConverter<T> converter;
    private final boolean hasDefaultValue;

    /**
     * Constructs a new SettingKey with the specified key, default value, and
     * converter.
     *
     * @param key             the key for the setting
     * @param defaultValue    the default value for the setting
     * @param converter       the converter for the setting
     * @param hasDefaultValue whether this SettingKey has a default value
     * @exception NullPointerException if key or converter is null
     */
    private SettingKey(String key, T defaultValue, SettingConverter<T> converter, boolean hasDefaultValue) {
        this.key = Objects.requireNonNull(key, "key must not be null");
        this.converter = Objects.requireNonNull(converter, "converter must not be null");
        this.defaultValue = defaultValue;
        this.hasDefaultValue = hasDefaultValue;
    }

    /**
     * Creates a new SettingKey without a default value.
     *
     * @param key       the key for the setting
     * @param converter the converter for the setting
     * @return a new SettingKey without a default value
     */
    public static <T> SettingKey<T> of(String key, SettingConverter<T> converter) {
        return new SettingKey<>(key, null, converter, false);
    }

    /**
     * Creates a new SettingKey with the specified default value.
     * 
     * @param key          the key for the setting
     * @param converter    the converter for the setting
     * @param defaultValue the default value for the setting
     * @param <T>          the type of the setting value
     * @return a new SettingKey with the specified default value
     */
    public static <T> SettingKey<T> of(String key, T defaultValue, SettingConverter<T> converter) {
        return new SettingKey<>(key, defaultValue, converter, true);
    }

    /**
     * Returns the key for this SettingKey.
     *
     * @return the key for this SettingKey
     */
    public String getKey() {
        return key;
    }

    /**
     * Returns the default value for this SettingKey.
     *
     * @return the default value for this SettingKey, or null if no default value is
     *         set
     */
    public T getDefaultValue() {
        return defaultValue;
    }

    /**
     * Checks if this SettingKey has a default value.
     *
     * @return true if this SettingKey has a default value, false otherwise
     */
    public boolean hasDefaultValue() {
        return hasDefaultValue;
    }

    /**
     * Serializes the given value to a string using the converter.
     *
     * @param value the value to serialize
     * @return the serialized string representation of the value
     */
    public String serialize(T value) {
        return converter.serialize(value);
    }

    /**
     * Deserializes the given string to a value using the converter.
     *
     * @param value the string to deserialize
     * @return the deserialized value
     */
    public T deserialize(String value) {
        return converter.deserialize(value);
    }
}
