package com.digero.maestro.view;

import java.util.Arrays;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.digero.common.midi.NoteFilterSequencerWrapper;

public class MiscSettings {
	public boolean showMaxPolyphony = true;
	public boolean ignoreExpressionMessages = false;
	public boolean showBadger = false;
	public boolean importLyrics = true;
	//public boolean allBadger = false;
	public String theme = "Flat Light";
	public int fontSize = 12;
	public int maxRangeForNewBendMethod = 12;
	public boolean autoplayOnOpen = true;

	private final Preferences prefs;
	public boolean checkForUpdates = true;
	

	public MiscSettings(Preferences prefs, boolean checkFallback) {
		this.prefs = prefs;
		boolean useFallback = false;
		Preferences saveExportNode = prefs.parent().node("saveAndExportSettings");
		if (checkFallback) {
			try {
				if (!Arrays.asList(prefs.keys()).contains("showMaxPolyphony")) {
					useFallback = true;
				}
			} catch (Exception e) {
				useFallback = true;
			}
		}

		loadPrefs(useFallback ? saveExportNode : prefs);
		saveToPrefs();
	}

	private void loadPrefs(Preferences prefs) {
		showMaxPolyphony = prefs.getBoolean("showMaxPolyphony", showMaxPolyphony);
		showBadger = prefs.getBoolean("showBadger", showBadger);
		//allBadger = prefs.getBoolean("allBadger", allBadger);
		ignoreExpressionMessages = prefs.getBoolean("ignoreExpressionMessages", ignoreExpressionMessages);
		theme = prefs.get("theme", theme);
		fontSize = prefs.getInt("fontSize", fontSize);
		maxRangeForNewBendMethod = prefs.getInt("maxRangeForNewBendMethod", maxRangeForNewBendMethod);
		if (maxRangeForNewBendMethod == 24) maxRangeForNewBendMethod = 16;// Due to student fiddle we can't go to 24.
		autoplayOnOpen = prefs.getBoolean("autoplayOnOpen", autoplayOnOpen);
		checkForUpdates = prefs.getBoolean("checkForUpdates", checkForUpdates);
		importLyrics = prefs.getBoolean("importLyrics", importLyrics);
	}

	public MiscSettings(MiscSettings that) {
		this.prefs = that.prefs;
		copyFrom(that);
	}

	public void copyFrom(MiscSettings that) {
		showMaxPolyphony = that.showMaxPolyphony;
		showBadger = that.showBadger;
		//allBadger = that.allBadger;
		theme = that.theme;
		fontSize = that.fontSize;
		ignoreExpressionMessages = that.ignoreExpressionMessages;
		maxRangeForNewBendMethod = that.maxRangeForNewBendMethod;
		autoplayOnOpen = that.autoplayOnOpen;
		checkForUpdates = that.checkForUpdates;
		importLyrics = that.importLyrics;
	}

	public void saveToPrefs() {
		prefs.putBoolean("showMaxPolyphony", showMaxPolyphony);
		prefs.putBoolean("showBadger", showBadger);
		//prefs.putBoolean("allBadger", allBadger);
		prefs.putBoolean("ignoreExpressionMessages", ignoreExpressionMessages);
		prefs.put("theme", theme);
		prefs.putInt("fontSize", fontSize);
		prefs.putInt("maxRangeForNewBendMethod", maxRangeForNewBendMethod);
		prefs.putBoolean("autoplayOnOpen", autoplayOnOpen);
		prefs.putBoolean("checkForUpdates", checkForUpdates);
		prefs.putBoolean("importLyrics", importLyrics);
	}

	public void restoreDefaults() {
		try {
			prefs.clear();
			NoteFilterSequencerWrapper.prefs.clear();
			Preferences node = NoteFilterSequencerWrapper.prefs.node(NoteFilterSequencerWrapper.prefMIDIHeader);
			if (node != null) node.clear();
			NoteFilterSequencerWrapper.prefs.flush();
		} catch (BackingStoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		MiscSettings fresh = new MiscSettings(prefs, false);
		this.copyFrom(fresh);
	}

	public MiscSettings getCopy() {
		return new MiscSettings(this);
	}
}
