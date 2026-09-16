package com.digero.common.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digero.maestro.MaestroMain;
import java.util.Locale;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UiTextTest {

    private static final Preferences PREFS = Preferences.userNodeForPackage(MaestroMain.class).node("miscSettings");

    private String originalLocale;

    @BeforeEach
    void resetTextState() throws Exception {
        originalLocale = PREFS.get("locale", null);
        PREFS.put("locale", "en");
        I18nTestState.resetLocaleManager();
        I18nTestState.resetUIText();
    }

    @AfterEach
    void restoreTextState() throws Exception {
        if (originalLocale == null) {
            PREFS.remove("locale");
        } else {
            PREFS.put("locale", originalLocale);
        }
        I18nTestState.resetLocaleManager();
        I18nTestState.resetUIText();
    }

    @Test
    void initBeforeLocaleManagerInitThrows() {
        assertThrows(IllegalStateException.class, UIText::init);
    }

    @Test
    void initTwiceIsHarmless() {
        LocaleManager.init();

        UIText.init();
        UIText.init();

        assertEquals("ABC Browser", UIText.get("abcplayer.abc.browser"));
    }

    @Test
    void getsTextAfterCorrectInitialization() {
        LocaleManager.init();
        UIText.init();

        assertEquals("ABC Browser", UIText.get("abcplayer.abc.browser", Locale.ENGLISH));
        assertEquals("ABC Browser", UIText.get("abcplayer.abc.browser"));
    }

    @Test
    void explicitLocaleLookupStillWorks() {
        assertEquals("ABC Browser", UIText.get("abcplayer.abc.browser", Locale.ENGLISH));
        assertEquals("Navigateur ABC", UIText.get("abcplayer.abc.browser", Locale.FRENCH));
    }

    @Test
    void formatsArgumentsUsingUsNumberFormatting() {
        LocaleManager.init();
        UIText.init();

        String text = UIText.get("abcplayer.0.next.song.playing.in.1", "Song", 1234.5);
        assertTrue(text.contains("1,234.5"));
    }

    @Test
    void returnsMarkedKeyForMissingText() {
        assertEquals("!test.missing.key!", UIText.get("test.missing.key", Locale.ENGLISH));

        LocaleManager.init();
        UIText.init();
        assertEquals("!test.missing.key!", UIText.get("test.missing.key"));
    }
}
