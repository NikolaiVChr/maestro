package com.digero.common.view;

import java.awt.Color;

// Disable auto-formatting in this file
// @formatter:off

public enum ColorTable
{
    /** Note currently being played */
	NOTE_ON(Color.WHITE),
    /** Border of note currently being played */
	NOTE_ON_BORDER(new Color(0xAA000000, true)),
    /** Song position line color */
	INDICATOR(new Color(0x66FFFFFF, true)),
    /** Song position line color when active */
	INDICATOR_ACTIVE(new Color(0xAAFFFFFF, true)),
    /** Octave divider line color */
	OCTAVE_LINE(new Color(0xAA3C3C3C, true)),
    /** Measure divider line color */
	BAR_LINE(new Color(0xAA3C3C3C, true)),
    /** Import/export links color in settings panel */
	LINK(new Color(0x336699)),
    /** Note color when track is enabled in current abc part */
	NOTE_ENABLED     (Color.getHSBColor(0.61f, 0.75f, 1.00f)),
    /** Note color when track is not enabled in current abc part */
	NOTE_DISABLED    (Color.getHSBColor(0.60f, 0.67f, 0.95f)),
    /** Note color when track is not enabled in any abc part */
	NOTE_OFF         (Color.getHSBColor(0.62f, 0.00f, 0.50f)),
    /** Note out of range color when track is enabled in current abc part */
	NOTE_BAD_ENABLED (Color.getHSBColor(0.05f, 1.00f, 1.00f)),
    /** Note out of range color when track is not enabled in current abc part */
	NOTE_BAD_DISABLED(Color.getHSBColor(0.95f, 0.65f, 0.75f)),
    /** Note out of range color when track is not enabled in any abc part */
	NOTE_BAD_OFF     (Color.getHSBColor(0.00f, 0.00f, 0.70f)),

    /** Not used */
	NOTE_ABC_ENABLED (Color.getHSBColor(0.12f, 0.77f, 0.90f)),
    /** Not used */
	NOTE_ABC_DISABLED(Color.getHSBColor(0.12f, 0.60f, 0.75f)),
    /** Not used */
	NOTE_ABC_OFF     (Color.getHSBColor(0.12f, 0.00f, 0.50f)),

    /** Tempo graph foreground color */
	NOTE_TEMPO       (new Color(0x999999)),
    /** Tempo graph foreground color when song position line is playing in it */
	NOTE_TEMPO_ON    (new Color(0xF2F2F2)),

    /** Drum note color when track is enabled in current abc part */
	NOTE_DRUM_ENABLED(NOTE_ENABLED),
    /** Not used */
	NOTE_DRUM_DISABLED(NOTE_DISABLED),
    /** Drum note color when track is not enabled in any abc part */
	NOTE_DRUM_OFF(NOTE_OFF),

    /** Note graph background */
	GRAPH_BACKGROUND_ENABLED(Color.BLACK),
    /** Note graph background soloed */
	GRAPH_BACKGROUND_SOLO(new Color(0x181818)),
    /** Note graph background when current abc part don't have it selected */
	GRAPH_BACKGROUND_DISABLED(new Color(0x222222)),
    /** Note graph background when no abc part has it selected */
	GRAPH_BACKGROUND_OFF(new Color(0x222222)),

    /** Note graph border color when current abc part has it enabled */
	GRAPH_BORDER_ENABLED(Color.DARK_GRAY),
    /** Not used */
	GRAPH_BORDER_DISABLED(Color.DARK_GRAY),
    /** Note graph border color when current abc part do not have it enabled */
	GRAPH_BORDER_OFF(Color.DARK_GRAY),

    /** Not used */
	PANEL_BACKGROUND_ENABLED(GRAPH_BACKGROUND_ENABLED),
    /** Track panels background */
	PANEL_BACKGROUND_DISABLED(GRAPH_BACKGROUND_DISABLED),

    /** Track panel border color outside */
	PANEL_BORDER(new Color(0xEEEEEE)),
	/** Track panel border color inside */
    PANEL_BORDER_INSIDE(new Color(0x555555)),

    /** Track title background color when selected by current part */
	PANEL_HIGHLIGHT(new Color(0xFFD83C)), //(Color.getHSBColor(0.60f, 0.50f, 1.00f)),
    /** Track title background color when selected but not by current part */
	PANEL_HIGHLIGHT_OTHER_PART(new Color(0xDDDDDD)),

    /** Track title, enabled by current part, color */
	PANEL_TEXT_ENABLED(new Color(0xFFD83C)), //(Color.getHSBColor(0.60f, 0.40f, 1.00f)),
    /** Track title, enabled but not by current part, color */
	PANEL_TEXT_DISABLED(new Color(0xEEEEEE)),
    /** Track title, not selected by any parts, color */
	PANEL_TEXT_OFF(new Color(0x777777)),
    /** Color for error text displayed in center view of maestro */
	PANEL_TEXT_ERROR(Color.getHSBColor(0.01f, 0.98f, 1.00f)),
    /** Not used */
	PANEL_LINK(Color.getHSBColor(0.60f, 0.70f, 1.00f)),

	/** currently playing lyrics highlight color, light mode */
	LYRICS_HIGHLIGHT_LIGHT(new Color(0xFFD83C)),
	/** currently playing lyrics highlight color, dark mode */
	LYRICS_HIGHLIGHT_DARK (new Color(100,170,100)),

    /** Not used */
    ABC_BORDER_SELECTED_ENABLED(PANEL_TEXT_ENABLED),
    /** Not used */
	ABC_BORDER_SELECTED_OFF(PANEL_TEXT_ENABLED),
    /** Not used */
	ABC_BORDER_UNSELECTED_ENABLED(GRAPH_BACKGROUND_ENABLED),
    /** Not used */
	ABC_BORDER_UNSELECTED_OFF(GRAPH_BACKGROUND_OFF),

    /** track UI controls foreground */
	CONTROLS_TEXT(Color.WHITE),
    /** track UI controls background */
	CONTROLS_BACKGROUND(new Color(0x222222)),
    /** editor edited */
    CONTROLS_EDITED(new Color(0.2f, 0.8f, 0.2f)),

    PAN_USER(new Color(255, 215, 0)),       // Bright Yellow (Gold)
    PAN_AUTO(new Color(120, 120, 120, 150)), // Dull Grey (Auto)
    PAN_TEXT(Color.WHITE),
    PAN_TEXT_ACTIVE(Color.BLACK),
    PAN_TEXT_ON_DARK(new Color(200, 200, 200)),
    PAN_TEXT_ON_LIGHT(new Color(55, 55, 55)),
    PAN_STEM(new Color(255, 255, 255, 50)), // Connector line color
    PAN_ARC(new Color(60, 60, 60, 100)), //
    PAN_SHADOW(new Color(0, 0, 0, 60)), //
    PAN_BORDER(new Color(0, 0, 0, 50)), //

    /** Measure on both sides section-edited */
	BAR_EDITED(new Color(0,32,0)),
    /** Section-edited */
	BAR_LINE_EDITED(new Color(30,75,30)),
    /** Section-edit silenced */
	BAR_SILENCED(new Color(32,0,0)),

    /** Standard poly color */
	NOTE_POLYPHONY         (new Color(100,170,100)),
    /** Poly being played */
	NOTE_POLYPHONY_ON      (new Color(0xF2F2F2)),
    /** Poly being high */
	NOTE_POLYPHONY_WARNING (new Color(0xFECE19)),
    /** Poly more notes than lotro can handle */
	NOTE_POLYPHONY_OVER    (Color.getHSBColor(0f, 1.00f, 1.00f));

	//NOTE_PRUNED (new Color(1f,1f,0f));

	private Color value;

	ColorTable(Color value)
	{
		this.value = value;
	}

	ColorTable(ColorTable copyFrom)
	{
		this.value = copyFrom.value;
	}

	public void set(Color value)
	{
		this.value = value;
	}

	public Color get()
	{
		return this.value;
	}

	public String getHtml()
	{
		return String.format("#%02X%02X%02X", value.getRed(), value.getGreen(), value.getBlue());
	}
}
