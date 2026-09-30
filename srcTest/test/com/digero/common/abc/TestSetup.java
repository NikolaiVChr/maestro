package com.digero.common.abc;

import com.digero.common.i18n.LocaleManager;
import com.digero.common.i18n.UIText;
import com.digero.common.util.Logging;

import java.io.IOException;

/** The one place that says what runs before every AbcToMidi test. */
final class TestSetup {
	private TestSetup() {
	}

	static void beforeEachTest() {
        try {
            Logging.configure("Unit-test", true);
        } catch (IOException ignored) {
        }
        LocaleManager.init();
		UIText.init();
	}
}