package com.digero.maestro.abc;

import com.digero.common.abc.LotroInstrument;
import com.digero.common.midi.MidiConstants;
import com.digero.common.util.ParseException;
import com.digero.common.util.Version;
import org.w3c.dom.Element;

import javax.xml.xpath.XPathExpressionException;
import java.util.Arrays;
import java.util.prefs.Preferences;

public class JauntyHandKnellsFXNoteMap extends DrumNoteMap {
	protected static final byte DISABLED_NOTE_ID = (byte) LotroChromaticFXInfo.DISABLED.note.id;
	private static final String MAP_PREFS_KEY = "JauntyHandKnellsFXNoteMap.map";


	public static String getXmlName() {
		return "jauntyHandKnellsFxMap";
	}

	@Override
	public byte get(int midiNoteId) {
		if (midiNoteId < Byte.MIN_VALUE || midiNoteId > Byte.MAX_VALUE) {
			throw new IllegalArgumentException();
		}
		return get((byte) midiNoteId);
	}

	@Override
	public byte get(byte midiNoteId) {
		// Map hasn't been initialized yet, use defaults
		if (map == null)
			return getDefaultMapping(midiNoteId);

		return map[midiNoteId];
	}

	@Override
	public void set(int midiNoteId, int value) {
		if ((midiNoteId < Byte.MIN_VALUE || midiNoteId > Byte.MAX_VALUE)
				|| (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE)) {
			throw new IllegalArgumentException();
		}
		set((byte) midiNoteId, (byte) value);
	}

	@Override
	public void set(byte midiNoteId, byte value) {
		if (get(midiNoteId) != value) {
			ensureMap();
			map[midiNoteId] = value;
			fireChangeEvent();
		}
	}

	@Override
    protected byte getDefaultMapping(byte noteId) {
        byte octaveDelta = (byte) (LotroInstrument.JAUNTY_HAND_KNELLS.octaveDelta * 12);
        if (noteId >= LotroInstrument.JAUNTY_HAND_KNELLS.lowestPlayable.id + octaveDelta
                && noteId <= LotroInstrument.JAUNTY_HAND_KNELLS.highestPlayable.id + octaveDelta)
            return (byte) (noteId - octaveDelta);
        else
            return DISABLED_NOTE_ID;
    }

    /**
     * This can be used as a backup in the event that loading the note map from a file fails.
     */
    @Override
    public byte[] getFailsafeDefault() {
        byte[] failsafe = new byte[MidiConstants.NOTE_COUNT];

        for (int i = 0; i < failsafe.length; i++) {
            failsafe[i] = getDefaultMapping((byte) i);
        }

        return failsafe;
    }

	@Override
	public boolean equals(Object obj) {
		if (obj == null || obj.getClass() != this.getClass())
			return false;

		return Arrays.equals(map, ((JauntyHandKnellsFXNoteMap) obj).map);
	}

	@Override
	public void save(Preferences prefs) {
		ensureMap();
		prefs.putByteArray(MAP_PREFS_KEY, map);
	}

	@Override
	public void load(Preferences prefs) {
		setLoadedByteArray(prefs.getByteArray(MAP_PREFS_KEY, null), LotroInstrument.JAUNTY_HAND_KNELLS);
	}

	@Override
	public void saveToXml(Element ele) {
		if (map == null) {
			return;
		}

		for (int midiId = 0; midiId < MidiConstants.NOTE_COUNT; midiId++) {
			int lotroId = get(midiId);
			if (lotroId == DISABLED_NOTE_ID)
				continue;

			Element noteEle = ele.getOwnerDocument().createElement("note");
			ele.appendChild(noteEle);
			noteEle.setAttribute("id", String.valueOf(midiId));
			noteEle.setAttribute("lotroId", String.valueOf(lotroId));
		}
	}

	public static JauntyHandKnellsFXNoteMap loadFromXml(Element ele, Version fileVersion) throws ParseException {
		try {
			JauntyHandKnellsFXNoteMap retVal = new JauntyHandKnellsFXNoteMap();
			retVal.loadFromXmlInternal(ele, fileVersion, LotroInstrument.JAUNTY_HAND_KNELLS);
			return retVal;
		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
	}
}
