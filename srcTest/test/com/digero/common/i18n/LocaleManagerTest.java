package com.digero.common.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.digero.maestro.MaestroMain;
import java.util.List;
import java.util.Locale;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class LocaleManagerTest {

    private static final Preferences PREFS = Preferences.userNodeForPackage(MaestroMain.class).node("miscSettings");

    private String originalLocale;

    @BeforeEach
    void resetLocaleManager() throws Exception {
        originalLocale = PREFS.get("locale", null);
        PREFS.remove("locale");
        I18nTestState.resetLocaleManager();
    }

    @AfterEach
    void restoreLocaleManager() throws Exception {
        if (originalLocale == null) {
            PREFS.remove("locale");
        } else {
            PREFS.put("locale", originalLocale);
        }
        I18nTestState.resetLocaleManager();
    }

    @Test
    void supportedLocalesAreEnglishFrenchAndGerman() {
        assertEquals(List.of(Locale.ENGLISH, Locale.FRENCH, Locale.GERMAN), LocaleManager.getSupportedLocales());
    }

    @Test
    void getLocaleBeforeInitThrows() {
        assertThrows(IllegalStateException.class, LocaleManager::getLocale);
    }

    @ParameterizedTest
    @CsvSource({ "en, en", "fr, fr", "de, de", "FR, fr", "DE, de", "US, en" })
    void initReadsStoredLocale(String storedLocale, String expectedLanguage) {
        PREFS.put("locale", storedLocale);
        LocaleManager.init();
        assertEquals(expectedLanguage, LocaleManager.getLocale().getLanguage());
    }

    @Test
    void headlessInitWithoutPreferenceDefaultsToEnglishWithoutDialog() {
        assumeTrue(java.awt.GraphicsEnvironment.isHeadless());
        LocaleManager.init();
        assertEquals(Locale.ENGLISH, LocaleManager.getLocale());
    }

    @Test
    void initTwiceDoesNotChangeLocale() {
        PREFS.put("locale", "fr");
        LocaleManager.init();
        assertEquals(Locale.FRENCH, LocaleManager.getLocale());
        PREFS.put("locale", "de");
        LocaleManager.init();
        assertEquals(Locale.FRENCH, LocaleManager.getLocale());
    }
}
