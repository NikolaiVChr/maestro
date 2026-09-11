package com.digero.common.settings;

/**
 * A converter interface for serializing and deserializing setting values.
 *
 * @param <T> the type of the setting value
 */
public interface SettingConverter<T> {

    /**
     * Serializes the given value to a string representation.
     *
     * @param value the value to serialize
     * @return the string representation of the value
     */
    String serialize(T value);

    /**
     * Deserializes the given string representation to a value of type T.
     *
     * @param value the string representation of the value
     * @return the deserialized value
     */
    T deserialize(String value);

}
