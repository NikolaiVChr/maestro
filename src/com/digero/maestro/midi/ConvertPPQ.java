package com.digero.maestro.midi;

import java.util.logging.Logger;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.Track;

public class ConvertPPQ {
	
	private static final Logger log = Logger.getLogger("import.midi");

	private static int halfRequirement = 6;

	public static Sequence convert(Sequence orig) {
		if (orig.getDivisionType() != Sequence.PPQ) {
			log.info("Midi not using PPQ resolution");
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

		log.info("Old PPQ="+origPPQ+", New PPQ="+newPPQ);

		Sequence edit = null;
		try {
			edit = new Sequence(Sequence.PPQ, newPPQ);
		} catch (InvalidMidiDataException e) {
			e.printStackTrace();
			return orig;
		}

		Track[] origTracks = orig.getTracks();
		
		long overflowGuard = Long.MAX_VALUE / (newPPQ/origPPQ);

		for (Track origTrack : origTracks) {
			Track editTrack = edit.createTrack();
			int eventSize = origTrack.size();
			for (int j = 0; j < eventSize; j++) {
				MidiEvent origEvent = origTrack.get(j);
				if (origEvent.getTick() < overflowGuard) {
					// we disgard events at ticks that will make long overflow, they will represent months of duration anyway, no song is that long.
					long newTick = origEvent.getTick() * ((long)(newPPQ/origPPQ));
					if (newTick > 0L && origEvent.getTick() > 0L) {
						origEvent.setTick(newTick);
						editTrack.add(origEvent);
					}
				} else {
					log.fine("Prevented tick overflow");
				}
			}
		}

		return edit;
	}
}