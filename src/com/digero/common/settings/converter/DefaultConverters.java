package com.digero.common.settings.converter;

import java.util.Locale;

import com.digero.common.settings.SettingConverter;

public final class DefaultConverters {

    private DefaultConverters() {
        // Prevent instantiation
    }

    public static final SettingConverter<String> STRING = new SettingConverter<>() {
        @Override
        public String deserialize(String value) {
            return value;
        }

        @Override
        public String serialize(String value) {
            return value;
        }
    };

    public static final SettingConverter<Integer> INTEGER = new SettingConverter<>() {
        @Override
        public Integer deserialize(String value) {
            return Integer.valueOf(value);
        }

        @Override
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
        @Override
        public Double deserialize(String value) {
            return Double.valueOf(value);
        }

        @Override
        public String serialize(Double value) {
            return String.valueOf(value);
        }
    };

    public static final SettingConverter<Locale> LOCALE = new SettingConverter<>() {
        @Override
        public Locale deserialize(String value) {
            return Locale.forLanguageTag(value);
        }

        @Override
        public String serialize(Locale value) {
            return value.toLanguageTag();
        }
    };
}
