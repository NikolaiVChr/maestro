package com.digero.maestro.view;

import java.util.Arrays;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.digero.common.midi.NoteFilterSequencerWrapper;

public class MiscSettings {
	public boolean showMaxPolyphony = true;
	public boolean ignoreExpressionMessages = false;
	public boolean showBadger = false;
	//public boolean allBadger = false;
	public String theme = "Flat Light";
	public int fontSize = 12;
	public int maxRangeForNewBendMethod = 12;
	public boolean autoplayOnOpen = true;

	private final Preferences prefs;
	public boolean checkForUpdates = true;

    // dissonance settings
    public boolean dissModified = false;// not persistent

    public boolean dissEnabled = false;
	public boolean excludeShortestNotes = true;
    public int min2factor = 1;
    public int maj2factor = 0;
    public int maj7factor = 1;
    public int min7factor = 0;
    public int trifactor = 0;
	public int mudfactor = 0;
    public int min2threshold = 1;
    public int min2penalty = 10;
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
