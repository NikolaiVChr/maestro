package com.digero.common.view;

import com.digero.maestro.MaestroMain;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

public class UIText {
    private static final Logger log = Logger.getLogger("locale"); //NON-NLS
    private static final String locale = Preferences.userNodeForPackage(MaestroMain.class).node("miscSettings").get("locale", null); //NON-NLS
    private static final @NonNls String BUNDLE_NAME = "uitext";
    private static final ResourceBundle uiText = ResourceBundle.getBundle(BUNDLE_NAME, locale==null? Locale.getDefault():Locale.of(locale)); //NON-NLS

    public static @NotNull String get(@PropertyKey(resourceBundle = BUNDLE_NAME)String key) {
        try {
            return uiText.getString(key);
        } catch (Exception e) {
            log.warning("Failed to load UI text for key \"" + key + "\", locale is " + locale); //NON-NLS //NON-NLS
            return "ERROR";
        }
    }

    public static @NotNull String get(@PropertyKey(resourceBundle = BUNDLE_NAME)String key, Locale local) {
        try {
            return ResourceBundle.getBundle(BUNDLE_NAME, local).getString(key);
        } catch (Exception e) {
            log.warning("Failed to load UI text for key \"" + key + "\", locale is " + local); //NON-NLS //NON-NLS
            return "ERROR";
        }
    }
}
