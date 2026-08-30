package com.digero.maestro.abc;

import org.w3c.dom.Element;
import com.digero.common.abc.LotroInstrument;
import com.digero.common.midi.MidiConstants;

/**
 * A drummap used for when loading abc as source. Drum sounds will per default map to themselves.
 */
public class PassThroughDrumNoteMap extends DrumNoteMap {

    public PassThroughDrumNoteMap(LotroCombiDrumInfo combiInfo) {
        super(combiInfo);
    }

    protected PassThroughDrumNoteMap(PassThroughDrumNoteMap orig) {
        super(orig);
    }

    @Override
    public PassThroughDrumNoteMap copy() {
        return new PassThroughDrumNoteMap(this);
    }

	@Override
	protected byte getDefaultMapping(byte noteId) {
		if (LotroInstrument.BASIC_DRUM.isPlayable(noteId))
			return noteId;
		else
			return DISABLED_NOTE_ID;
	}

	@Override
	public byte[] getFailsafeDefault() {
		byte[] failsafe = new byte[MidiConstants.NOTE_COUNT];

		for (int i = 0; i < failsafe.length; i++) {
			failsafe[i] = getDefaultMapping((byte) i);
		}

		return failsafe;
	}

	@Override
	public void saveToXml(Element ele) {
		ele.setAttribute("isPassthrough", String.valueOf(true));
		super.saveToXml(ele);
	}
}
