package com.digero.common.util;

import java.awt.Font;
import java.util.prefs.Preferences;

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.text.StyleContext;

import com.digero.maestro.MaestroMain;
import com.digero.maestro.view.MiscSettings;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;

public class Themer {
	public static final String[] themes = { "Flat Dark", "Flat Light", };

	public static final int[] fontSizes = { 10, 11, 12, 13, 14, 15, 16, 17, 18, 20, 22, 24, 26, 28, 36 };

	public static void setLookAndFeel() throws ClassNotFoundException, InstantiationException, IllegalAccessException,
			UnsupportedLookAndFeelException {
		MiscSettings settings = new MiscSettings(Preferences.userNodeForPackage(MaestroMain.class).node("miscSettings"),
				true);
		String theme = settings.theme;
		int fontSize = settings.fontSize;
		boolean isOldTheme = false;
		
		if ("Default".equals(theme)) {
			Preferences.userNodeForPackage(MaestroMain.class).node("miscSettings").put("theme", themes[1]);
			theme = themes[1];
		}

		if (theme.equals(themes[0])) {
			UIManager.setLookAndFeel(new FlatMacDarkLaf());
		} else if (theme.equals(themes[1])) {
			UIManager.setLookAndFeel(new FlatMacLightLaf());
		} else {
			isOldTheme = true;
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		}

		if (!isOldTheme) {
			Font font = UIManager.getFont("defaultFont");
			Font newFont = StyleContext.getDefaultStyleContext().getFont(font.getFamily(), font.getStyle(), fontSize);
			UIManager.put("defaultFont", newFont);
			FlatLaf.updateUI();
		}
	}

}
