package com.digero.maestro.view;

import java.util.Arrays;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.digero.common.i18n.UIText;
import com.digero.common.midi.NoteFilterSequencerWrapper;

public class MiscSettings {
	public boolean showMaxPolyphony = true;
	public boolean ignoreExpressionMessages = false;
	public boolean showBadger = false;
	//public boolean allBadger = false;
	public String theme = "Flat Light";
	public String locale = UIText.LANG_EN;
	public int fontSize = 12;
	public int maxRangeForNewBendMethod = 12;
	public boolean autoplayOnOpen = true;

	private final Preferences prefs;
	public boolean checkForUpdates = true;

    // dissonance settings
    public boolean dissModified = false;// not persistent

    public boolean dissEnabled = false;
	public boolean excludeShortestNotes = true;
	public int min2factor = 6;    // was 1
	public int maj2factor = 2;    // was 0
	public int maj7factor = 3;    // was 1
	public int trifactor  = 1;    // was 0
	public int min7factor = 1;    // was 0
	public int mudfactor = 0;         // was 0
	public int min2threshold = 1;
	public int min2penalty = 5;   // was 10
    //public int maj2threshold = 1;
    //public int maj2penalty = 0;


    public MiscSettings(Preferences prefs, boolean checkFallback) {
		this.prefs = prefs;
		boolean useFallback = false;
        if (prefs == null) return;//for unit-testing
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

	/**
	 * A dissonance weighting preset. Values only: applying it to the spinners is the
	 * dialog's job, so a preset can be previewed and then cancelled.
	 */
	public record DissonancePreset(boolean excludeShortestNotes, int min2factor, int maj7factor,
								   int maj2factor, int trifactor, int min7factor, int mudfactor,
								   int min2threshold, int min2penalty) {
	}

	/**
	 * Shows harmonic tension, not just collisions. Minor 2nds dominate, with sevenths and
	 * the tritone contributing lightly so that genuinely thick writing reads higher than
	 * clean writing. Calibrated against DissonancePanel's 50 clip: five simultaneous
	 * minor 2nds fill the bar.
	 */
	public static DissonancePreset dissonanceDefaultPreset() {
		return new DissonancePreset(true, 6, 3, 2, 1, 1, 0, 1, 5);
	}

	/**
	 * Only the genuinely jarring: minor 2nds beating inside the critical band. Sevenths,
	 * tritones and bass mud are silent, so ordinary dominant-seventh harmony leaves the
	 * graph flat and only real clashes show.
	 */
	public static DissonancePreset dissonanceMinimalPreset() {
		return new DissonancePreset(true, 10, 0, 0, 0, 0, 0, 1, 8);
	}

	@SuppressWarnings("HardCodedStringLiteral")
	private void loadPrefs(Preferences prefs) {
		showMaxPolyphony = prefs.getBoolean("showMaxPolyphony", showMaxPolyphony);
		showBadger = prefs.getBoolean("showBadger", showBadger);
		//allBadger = prefs.getBoolean("allBadger", allBadger);
		ignoreExpressionMessages = prefs.getBoolean("ignoreExpressionMessages", ignoreExpressionMessages);
		theme = prefs.get("theme", theme);
		locale = prefs.get("locale", locale);
		if ("FR".equals(locale)) locale = UIText.LANG_FR;
		if ("DE".equals(locale)) locale = UIText.LANG_DE;
		if ("US".equals(locale)) locale = UIText.LANG_EN;
		fontSize = prefs.getInt("fontSize", fontSize);
		maxRangeForNewBendMethod = prefs.getInt("maxRangeForNewBendMethod", maxRangeForNewBendMethod);
		if (maxRangeForNewBendMethod == 24) maxRangeForNewBendMethod = 16;// Due to student fiddle we can't go to 24.
		autoplayOnOpen = prefs.getBoolean("autoplayOnOpen", autoplayOnOpen);
		checkForUpdates = prefs.getBoolean("checkForUpdates", checkForUpdates);
        min2factor = prefs.getInt("min2factor", min2factor);
        maj2factor = prefs.getInt("maj2factor", maj2factor);
        trifactor = prefs.getInt("trifactor", trifactor);
        maj7factor = prefs.getInt("maj7factor", maj7factor);
        min7factor = prefs.getInt("min7factor", min7factor);
		mudfactor = prefs.getInt("mudfactor", mudfactor);
        min2threshold = prefs.getInt("min2threshold", min2threshold);
        min2penalty = prefs.getInt("min2penalty", min2penalty);
        //maj2threshold = prefs.getInt("maj2threshold", maj2threshold);
        //maj2penalty = prefs.getInt("maj2penalty", maj2penalty);
        dissEnabled = prefs.getBoolean("dissonanceGraphEnabled", dissEnabled);
		excludeShortestNotes = prefs.getBoolean("excludeShortestNotes", excludeShortestNotes);
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
		locale = that.locale;
		fontSize = that.fontSize;
		ignoreExpressionMessages = that.ignoreExpressionMessages;
		maxRangeForNewBendMethod = that.maxRangeForNewBendMethod;
		autoplayOnOpen = that.autoplayOnOpen;
		checkForUpdates = that.checkForUpdates;
        min2factor = that.min2factor;
        maj2factor = that.maj2factor;
        trifactor = that.trifactor;
        maj7factor = that.maj7factor;
        min7factor = that.min7factor;
		mudfactor = that.mudfactor;
        min2threshold = that.min2threshold;
        min2penalty = that.min2penalty;
        //maj2threshold = that.maj2threshold;
        //maj2penalty = that.maj2penalty;
        dissEnabled = that.dissEnabled;
        dissModified = that.dissModified;
		excludeShortestNotes = that.excludeShortestNotes;
	}

	@SuppressWarnings("HardCodedStringLiteral")
	public void saveToPrefs() {
		prefs.putBoolean("showMaxPolyphony", showMaxPolyphony);
		prefs.putBoolean("showBadger", showBadger);
		//prefs.putBoolean("allBadger", allBadger);
		prefs.putBoolean("ignoreExpressionMessages", ignoreExpressionMessages);
		prefs.put("theme", theme);
		if (locale == null) {
			prefs.remove("locale");
		} else {
			prefs.put("locale", locale);
		}
		prefs.putInt("fontSize", fontSize);
		prefs.putInt("maxRangeForNewBendMethod", maxRangeForNewBendMethod);
		prefs.putBoolean("autoplayOnOpen", autoplayOnOpen);
		prefs.putBoolean("checkForUpdates", checkForUpdates);
        prefs.putInt("min2factor", min2factor);
        prefs.putInt("maj2factor", maj2factor);
        prefs.putInt("trifactor", trifactor);
        prefs.putInt("maj7factor", maj7factor);
        prefs.putInt("min7factor", min7factor);
		prefs.putInt("mudfactor", mudfactor);
        prefs.putInt("min2threshold", min2threshold);
        prefs.putInt("min2penalty", min2penalty);
        //prefs.putInt("maj2threshold", maj2threshold);
        //prefs.putInt("maj2penalty", maj2penalty);
        prefs.putBoolean("dissonanceGraphEnabled", dissEnabled);
		prefs.putBoolean("excludeShortestNotes", excludeShortestNotes);
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
