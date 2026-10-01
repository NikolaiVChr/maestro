package com.digero.common.abctomidi;

import com.digero.common.i18n.LocaleManager;
import com.digero.common.i18n.UIText;
import com.digero.common.util.Logging;

import java.io.IOException;

/** The one place that says what runs before every AbcToMidi test. */
final class TestSetup {
    private TestSetup() {
    }

    private static boolean done;

    /**
     * Logging, the locale and the UI texts: once per JVM. They're the same for every test, and each test class calls
     * this from its @BeforeEach, so doing it every time cost thousands of set-ups per run.
     */
    static synchronized void beforeEachTest() {
        if (done)
            return;
        done = true;
        try {
            Logging.configure("Unit-test", true);
        } catch (IOException ignored) {
        }
        LocaleManager.init(true);
        UIText.init();
    }
}