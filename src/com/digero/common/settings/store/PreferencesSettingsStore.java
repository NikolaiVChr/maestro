package com.digero.common.settings.store;

import java.util.logging.Logger;
import java.util.Objects;
import com.digero.common.settings.SettingsStore;
import com.digero.common.settings.SettingKey;
import java.util.prefs.Preferences;

public final class PreferencesSettingsStore implements SettingsStore {

    private static final Logger LOGGER = Logger.getLogger(PreferencesSettingsStore.class.getName());
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

    @Override
    public <T> T get(SettingKey<T> key) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'get'");
    }

    @Override
    public boolean contains(SettingKey<?> key) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'contains'");
    }

    @Override
    public <T> void set(SettingKey<T> key, T value) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'set'");
    }

    @Override
    public void remove(SettingKey<?> key) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'remove'");
    }

    @Override
    public void clear() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'clear'");
    }
}
