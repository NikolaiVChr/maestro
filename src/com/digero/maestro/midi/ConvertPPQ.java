package com.digero.maestro.midi;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.Track;

public class ConvertPPQ {

	private static int halfRequirement = 4;

	public static Sequence convert(Sequence orig) {
		if (orig.getDivisionType() != Sequence.PPQ) {
			// System.out.println("PPQ old");
			return orig;
		}

		int origPPQ = orig.getResolution();
		int newPPQ = origPPQ;
		
		if (newPPQ % 3 != 0) {
			newPPQ *= 3;
		}

		int halfTimes = 0;
		int tempResult = newPPQ / 3;
		for (int i = halfTimes; i <= halfRequirement; i++) {
			if (tempResult % 2 == 0) {
				tempResult /= 2;
				halfTimes++;
			} else {
				break;
			}
		}

		int doubleTimes = 0;
		if (halfTimes < halfRequirement) {
			doubleTimes = Math.max(0, halfRequirement - halfTimes);
		}

		int multi = 1 << doubleTimes; // Faster than (int)Math.pow(2, doubleTimes);

		newPPQ *= multi;

		if (newPPQ == origPPQ) return orig;

		//System.out.println("PPQ scaling. Old="+origPPQ+" New="+newPPQ);

		Sequence edit = null;
		try {
			edit = new Sequence(Sequence.PPQ, (int) newPPQ);
		} catch (InvalidMidiDataException e) {
			e.printStackTrace();
			return orig;
		}

		Track[] origTracks = orig.getTracks();

		for (Track origTrack : origTracks) {
			Track editTrack = edit.createTrack();
			int eventSize = origTrack.size();
			for (int j = 0; j < eventSize; j++) {
				MidiEvent origEvent = origTrack.get(j);
				origEvent.setTick(origEvent.getTick() * ((long)(newPPQ/origPPQ)));
				editTrack.add(origEvent);
			}
		}

		return edit;
	}
}