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
     * Private constructor to enforce the use of factory methods.
     * 
     * @param key          the key for the setting
     * @param converter    the converter for the setting
     * @param defaultValue the default value for the setting
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
     * @exception NullPointerException if {@code key} or {@code converter} is null
     * @apiNote This method is a convenience method for creating a
     *          {@link SettingKey}
     *          without a default value.<br>
     *          If you want to create a SettingKey with a
     *          default value, use the {@link #of(String, Object, SettingConverter)}
     *          method instead.
     */
    public static <T> SettingKey<T> of(String key, SettingConverter<T> converter) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(converter, "converter must not be null");
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
     * @exception NullPointerException if {@code key} or {@code converter} is null
     * @apiNote This method is a convenience method for creating a
     *          {@link SettingKey}
     *          with a default value.<br>
     *          If you want to create a SettingKey without a default value, use the
     *          {@link #of(String, SettingConverter)} method instead.
     */
    public static <T> SettingKey<T> of(String key, T defaultValue, SettingConverter<T> converter) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(converter, "converter must not be null");
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
