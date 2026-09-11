package com.digero.common.i18n;

import java.lang.reflect.Field;

final class I18nTestState {
	private I18nTestState() {
	}

	static void resetUIText() throws Exception {
		Field initialized = UIText.class.getDeclaredField("initialized");
		initialized.setAccessible(true);
		initialized.setBoolean(null, false);

		Field resourceBundle = UIText.class.getDeclaredField("resourceBundle");
		resourceBundle.setAccessible(true);
		resourceBundle.set(null, null);
	}

	static void resetLocaleManager() throws Exception {
		Field initialized = LocaleManager.class.getDeclaredField("initialized");
		initialized.setAccessible(true);
		initialized.setBoolean(null, false);
	}
}