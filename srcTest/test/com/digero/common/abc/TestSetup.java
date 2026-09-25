package com.digero.common.abc;

import com.digero.common.i18n.LocaleManager;
import com.digero.common.i18n.UIText;

/** The one place that says what runs before every AbcToMidi test. */
final class TestSetup {
	private TestSetup() {
	}

	static void beforeEachTest() {
		LocaleManager.init();
		UIText.init();
	}
}