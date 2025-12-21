package com.digero.common.view;

import java.awt.Color;

// Disable auto-formatting in this file
// @formatter:off

public enum ColorTable {
    /** Note currently being played */
	NOTE_ON(Color.WHITE,
			"Note currently being played"),
    /** Border of note currently being played */
	NOTE_ON_BORDER(new Color(0xAA000000, true),
			"Border of note currently being played"),
    /** Song position line color */
	INDICATOR(new Color(0x66FFFFFF, true),
			"Song position line color"),
    /** Song position line color when active */
	INDICATOR_ACTIVE(new Color(0xAAFFFFFF, true),
			"Song position line color when dragging"),
    /** Octave divider line color */
	OCTAVE_LINE(new Color(0xAA3C3C3C, true),
			"Octave divider line color"),
    /** Measure divider line color */
	BAR_LINE(new Color(0xAA3C3C3C, true),
			"Measure divider line color"),
    /** Import/export links color in settings panel */
	LINK(new Color(0x336699),
			"Import/export links color in settings panel"),
    /** Note color when track is enabled in current abc part */
	NOTE_ENABLED     (Color.getHSBColor(0.61f, 0.75f, 1.00f),
			"Note color when track is enabled in current abc part"),
    /** Note color when track is not enabled in current abc part */
	NOTE_DISABLED    (Color.getHSBColor(0.60f, 0.67f, 0.95f),
			"Note color when track is not enabled in current abc part"),
    /** Note color when track is not enabled in any abc part */
	NOTE_OFF         (Color.getHSBColor(0.62f, 0.00f, 0.50f),
			"Note color when track is not enabled in any abc part"),
    /** Note out of range color when track is enabled in current abc part */
	NOTE_BAD_ENABLED (Color.getHSBColor(0.05f, 1.00f, 1.00f),
			"Note out of range color when track is enabled in current abc part. Also drum notes with sound set to None."),
    /** Note out of range color when track is not enabled in current abc part */
	NOTE_BAD_DISABLED(Color.getHSBColor(0.95f, 0.65f, 0.75f),
			"Note out of range color when track is not enabled in current abc part"),
    /** Note out of range color when track is not enabled in any abc part */
	NOTE_BAD_OFF     (Color.getHSBColor(0.00f, 0.00f, 0.70f),
			"Note out of range color when track is not enabled in any abc part"),

    /** Not used */
	NOTE_ABC_ENABLED (Color.getHSBColor(0.12f, 0.77f, 0.90f),
			"Not used"),
    /** Not used */
	NOTE_ABC_DISABLED(Color.getHSBColor(0.12f, 0.60f, 0.75f),
			"Not used"),
    /** Not used */
	NOTE_ABC_OFF     (Color.getHSBColor(0.12f, 0.00f, 0.50f),
			"Not used"),

    /** Tempo graph foreground color */
	TEMPO       (new Color(0x999999),
			"Tempo graph color"),
    /** Tempo graph foreground color when song position line is playing in it */
	TEMPO_ON    (new Color(0xF2F2F2),
			"Tempo graph color when song position line is playing in it"),

    /** Drum note color when track is enabled in current abc part */
	NOTE_DRUM_ENABLED(NOTE_ENABLED,
			"Drum note color"),
    /** Not used */
	NOTE_DRUM_DISABLED(NOTE_DISABLED,
			"Not used"),
    /** Drum note color when track is not enabled in any abc part */
	NOTE_DRUM_OFF(NOTE_OFF,
			"Drum note color when drum sound disabled"),

    /**  */
	GRAPH_BACKGROUND_ENABLED(Color.BLACK,
			"Note graph background when selected by current part"),
    /**  */
	GRAPH_BACKGROUND_SOLO(new Color(0x181818),
			"Note graph background soloed when not selected by current part"),
    /**  */
	GRAPH_BACKGROUND_DISABLED(new Color(0x222222),
			"Polyphony, tempo and dissonance graph background"),
    /**  */
	GRAPH_BACKGROUND_OFF(new Color(0x222222),
			"Note graph background when no abc part has it selected. Also drum note background when drum sound disabled."),
	/** Measure on both sides section-edited */
	GRAPH_BACKGROUND_EDITED(new Color(0,32,0),
			"Section edited background"),
	/** Section-edited */
	GRAPH_BAR_LINE_EDITED(new Color(30,75,30),
			"Bar line when section-edited on both sides"),
	/** Section-edit silenced */
	GRAPH_SILENCED(new Color(32,0,0),
			"Section/tune-edit silenced"),

    /** Note graph border color when current abc part has it enabled */
	GRAPH_BORDER_ENABLED(Color.DARK_GRAY,
			"Not used"),
    /**  */
	GRAPH_BORDER_DISABLED(Color.DARK_GRAY,
			"Not used"),
    /**  */
	GRAPH_BORDER_OFF(Color.DARK_GRAY,
			"Note graph border color when current abc part do not have it enabled"),

    /** Not used */
	PANEL_BACKGROUND_ENABLED(GRAPH_BACKGROUND_ENABLED,
			"Not used"),
    /** Track panels background */
	PANEL_BACKGROUND_DISABLED(GRAPH_BACKGROUND_DISABLED,
			"Area under lowest track, and center area background in Maestro when no song is loaded."),

    /**  */
	PANEL_BORDER(new Color(0xEEEEEE),
			"Track panel border color outside"),
	/**  */
    PANEL_BORDER_INSIDE(new Color(0x555555),
			"Track panel border color inside"),

    /**  */
	PANEL_HIGHLIGHT(new Color(0xFFD83C),
			"Track gutter color when selected by current part"), //(Color.getHSBColor(0.60f, 0.50f, 1.00f)),
    /**  */
	PANEL_HIGHLIGHT_OTHER_PART(new Color(0xDDDDDD),
			"Track gutter color when selected but not by current part"),

    /**  */
	PANEL_TEXT_ENABLED(new Color(0xFFD83C),
			"Track title, enabled by current part, color"), //(Color.getHSBColor(0.60f, 0.40f, 1.00f)),
    /**  */
	PANEL_TEXT_DISABLED(new Color(0xEEEEEE),
			"Track title, enabled but not by current part, color"),
    /**  */
	PANEL_TEXT_OFF(new Color(0x777777),
			"Track title, not selected by any parts, color"),
    /**  */
	PANEL_TEXT_ERROR(Color.getHSBColor(0.01f, 0.98f, 1.00f),
			"Color for error text displayed in center view of maestro"),
    /**  */
	PANEL_LINK(Color.getHSBColor(0.60f, 0.70f, 1.00f),
			"Not used"),


    /** Not used */
    ABC_BORDER_SELECTED_ENABLED(PANEL_TEXT_ENABLED,
			"Not used"),
    /** Not used */
	ABC_BORDER_SELECTED_OFF(PANEL_TEXT_ENABLED,
			"Not used"),
    /** Not used */
	ABC_BORDER_UNSELECTED_ENABLED(GRAPH_BACKGROUND_ENABLED,
			"Not used"),
    /** Not used */
	ABC_BORDER_UNSELECTED_OFF(GRAPH_BACKGROUND_OFF,
			"Not used"),

    /** track UI controls foreground */
	CONTROLS_TEXT(Color.WHITE,
			"Not used"),
    /** track UI controls background */
	CONTROLS_BACKGROUND(new Color(0x222222),
			"Not used"),
    /**  */
    CONTROLS_EDITED(new Color(0.2f, 0.8f, 0.2f),
			"Tune/section/part/pan editors edited text on buttons/slider"),


	PARTS_LIST_DND_LINE(Color.LIGHT_GRAY,
			"Parts list drag'n'drop divider line"),
	PARTS_LIST_MUTE(Color.decode("#ff7777"),
			"Parts list mute"),
	PARTS_LIST_SOLO(Color.decode("#7e7eff"),
			"Parts list solo"),


	PAN_LISTENER(Color.LIGHT_GRAY,
			"Pan listener head"),
	PAN_ACTIVE(new Color(0.2f, 0.8f, 0.2f),
			"Current part pan disc"),
    PAN_USER(new Color(255, 215, 0),
			"Manual pan disc"),       // Bright Yellow (Gold)
    PAN_AUTO(new Color(120, 120, 120, 150),
			"Auto assigned pan disc"), // Dull Grey (Auto)
    PAN_TEXT(Color.WHITE,
			"Other pan disc texts"),
    PAN_TEXT_ACTIVE(Color.BLACK,
			"Current pan disc text"),
    PAN_TEXT_ON_DARK(new Color(200, 200, 200),
			"Pan number. For dark theme"),
    PAN_TEXT_ON_LIGHT(new Color(55, 55, 55),
			"Pan number. For light theme"),
    PAN_STEM(new Color(255, 255, 255, 50),
			"Little line when pan positions are stacked"),
    PAN_ARC(new Color(60, 60, 60, 100),
			"The circle arc"), //
    PAN_SHADOW(new Color(0, 0, 0, 60),
			"Shadow of current part pan disc"), //
    PAN_BORDER(new Color(0, 0, 0, 50),
			"Current pan disc border"), //

    /** Standard poly color */
	NOTE_POLYPHONY         (new Color(100,170,100),
			"Standard poly color"),
    /** Poly being high */
	NOTE_POLYPHONY_WARNING (new Color(0xFECE19),
			"Poly being high"),
    /** Poly more notes than lotro can handle */
	NOTE_POLYPHONY_OVER    (Color.getHSBColor(0f, 1.00f, 1.00f),
			"Poly more notes than lotro can handle"),
	/** Poly being played */
	NOTE_POLYPHONY_ON      (new Color(0xF2F2F2),
			"Poly being played"),

	DISSONANCE_FEW         (new Color(100,170,100),
			"Dissonance color"),
	DISSONANCE_WARNING (new Color(0xFECE19),
			"Dissonance high"),
	DISSONANCE_SEVERE    (Color.getHSBColor(0f, 1.00f, 1.00f),
			"Dissonance very high"),
	DISSONANCE_ON      (new Color(0xF2F2F2),
			"Dissonance being played"),

	/**  */
	LYRICS_HIGHLIGHT_LIGHT(new Color(0xFFD83C),
			"currently playing lyrics highlight color, light mode"),
	/**  */
	LYRICS_HIGHLIGHT_DARK (new Color(100,170,100),
			"currently playing lyrics highlight color, dark mode"),
	;

	//NOTE_PRUNED (new Color(1f,1f,0f));

	private Color value;
	private Color defaultValue;
	private String info;

	ColorTable(Color value)	{
		this.value = value;
		this.defaultValue = value;
		info = "";
	}

	ColorTable(Color value, String info) {
		this.value = value;
		this.defaultValue = value;
		this.info = info;
	}

	ColorTable(ColorTable copyFrom, String info)	{
		this.value = copyFrom.value;
		this.info = info;
		this.defaultValue = copyFrom.defaultValue;
	}

	public void set(Color value) {
		this.value = value;
	}

	public Color get() {
		return this.value;
	}

	public Color getDefaultValue() {
		return this.defaultValue;
	}

	public String getInfo() {
    	return info;
	}

	public String getHtml()	{
		return String.format("#%02X%02X%02X", value.getRed(), value.getGreen(), value.getBlue());
	}
}
