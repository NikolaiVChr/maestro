package com.digero.common.settings.converter;

import java.util.Locale;

import com.digero.common.settings.SettingConverter;

public final class DefaultConverters {

    private DefaultConverters() {
        // Prevent instantiation
    }

    public static final SettingConverter<String> STRING = new SettingConverter<>() {
        public String deserialize(String value) {
            return value;
        }

        public String serialize(String value) {
            return value;
        }
    };

    public static final SettingConverter<Integer> INTEGER = new SettingConverter<>() {
        public Integer deserialize(String value) {
            return Integer.valueOf(value);
        }

        public String serialize(Integer value) {
            return String.valueOf(value);
        }
    };

    public static final SettingConverter<Boolean> BOOLEAN = new SettingConverter<Boolean>() {
        @Override
        public String serialize(Boolean value) {
            return Boolean.toString(value);
        }

        @Override
        public Boolean deserialize(String value) {
            return Boolean.parseBoolean(value);
        }
    };

    public static final SettingConverter<Double> DOUBLE = new SettingConverter<>() {
        public Double deserialize(String value) {
            return Double.valueOf(value);
        }

        public String serialize(Double value) {
            return String.valueOf(value);
        }
    };

    public static final SettingConverter<Locale> LOCALE = new SettingConverter<>() {
        public Locale deserialize(String value) {
            return Locale.forLanguageTag(value);
        }

        public String serialize(Locale value) {
            return value.toLanguageTag();
        }
    };

}
