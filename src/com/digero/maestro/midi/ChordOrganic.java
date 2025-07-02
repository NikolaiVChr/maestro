package com.digero.maestro.midi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

import com.digero.common.abc.AbcConstants;
import com.digero.common.abc.Dynamics;
import com.digero.common.midi.ITempoCache;
import com.digero.common.midi.Note;
import com.digero.common.util.Util;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.QuantizedTimingInfo;

/**
 * Only used by organic output
 */
public class ChordOrganic extends Chord {	
	private long startMicros = -1;
	private long endMicros = -1;
	public Long early = null; // organic
	public boolean dontMove1 = false;
	public boolean dontMove2 = false;
	public boolean glissando = false;
	public Long expandedMicros = null;
	public int arp = 0; // arp notes added to this
	public boolean delete = false;
	private boolean hadRest = false;
	private QuantizedTimingInfo qtm;

	public ChordOrganic(AbcNoteEvent firstNote, QuantizedTimingInfo qtm) {
		super(firstNote);
		this.qtm = qtm;
		startMicros = firstNote.startABCMicros;
		endMicros = firstNote.endABCMicros;
		if (firstNote.note == Note.REST) hadRest = true;
	}

	public long getStartMicros() {
		return startMicros;
	}

	public long getEndMicros() {
		return endMicros;
	}
	
	public void recalcEndMicros() {
		if (!notes.isEmpty()) {
			endMicros = notes.get(0).endABCMicros;
			for (int k = 1; k < notes.size(); k++) {
				AbcNoteEvent note = notes.get(k);
				if (note.endABCMicros < endMicros) {
					endMicros = note.endABCMicros;
				}
				note.setStartTick(qtm.microsToTickABCOrganic(note.startABCMicros));
				note.setEndTick(qtm.microsToTickABCOrganic(note.endABCMicros));
			}
		} else {
			endMicros = startMicros;
		}
		recalcEndTick();
	}
	
	/**
	 * Wont change anything if the chord is a rest with no notes
	 * 
	 * @param newEndMicros
	 */
	public void setEndMicros(long newEndMicros) {
		if (isRest()) return;
		for (AbcNoteEvent note : notes) {
			note.endABCMicros = newEndMicros;
		}
		endMicros = newEndMicros;
		super.setEndTick(qtm.microsToTickABCOrganic(newEndMicros));
	}
	
	public void setEndMicrosRetract(long newEndMicros) {
		long newEndTick = qtm.microsToTickABCOrganic(newEndMicros);
		for (AbcNoteEvent note : notes) {
			if (note.endABCMicros > newEndMicros) {
				note.endABCMicros = newEndMicros;
				note.setEndTick(newEndTick);
			}
		}
		endMicros = newEndMicros;
		endTick = newEndTick;
	}
	
	public void setEndMicrosExpand(long newEndMicros) {
		long newEndTick = qtm.microsToTickABCOrganic(newEndMicros);
		for (AbcNoteEvent note : notes) {
			if (note.endABCMicros < newEndMicros) {
				note.endABCMicros = newEndMicros;
				note.setEndTick(newEndTick);
			}
		}
		endMicros = newEndMicros;
		endTick = newEndTick;
	}
	
	public void setEarlyStartMicros(boolean useRestsInChords) {
		for (AbcNoteEvent note : notes) {
			note.startABCMicros = early;
			note.setStartTick(qtm.microsToTickABCOrganic(early));
			if (useRestsInChords && note.tiesFrom != null) {
				note.tiesFrom.endABCMicros = early;//require recalcEndMicros()
				note.tiesFrom.setEndTick(note.getStartTick());
			}
		}
		startMicros = early;
		startTick = qtm.microsToTickABCOrganic(early);
		early = null;
	}
	
	public boolean add(AbcNoteEvent ne) {
		super.add(ne);
		if (ne.endABCMicros < endMicros) {
			endMicros = ne.endABCMicros;
		}
		if (ne.note == Note.REST) hadRest = true;
		return true;
	}

	/**
	 * 
	 * @return micros of longest note ending. Rests ignored.
	 */
	public long getLongestEndMicros() {
		long endNoteMicros = startMicros;
		if (!notes.isEmpty()) {
			for (AbcNoteEvent note : notes) {
				if (note.note != Note.REST && note.endABCMicros > endNoteMicros) {
					endNoteMicros = note.endABCMicros;
				}
			}
		}
		return endNoteMicros;
	}
	
	/**
	 * 
	 * @return true if notes/rests differ in durations
	 */
	@Override
	public boolean isUneven() {
		long endNoteMicros = endMicros;
		if (!notes.isEmpty()) {
			for (AbcNoteEvent note : notes) {
				if (note.endABCMicros > endNoteMicros) {
					return true;
				}
			}
		}
		return false;
	}
	
	/**
	 * Only call this from organic multi-stage please.
	 * 
	 * @return
	 */
	public AbcNoteEvent getShortest() {
		long endNoteMicros = Long.MAX_VALUE;
		AbcNoteEvent shortest = null;
		if (!notes.isEmpty()) {
			for (AbcNoteEvent note : notes) {
				if (note.endABCMicros < endNoteMicros) {
					shortest = note;
					endNoteMicros = note.endABCMicros;
				}
			}
		}
		return shortest;
	}
	
	/**
	 * Remove all rests from chord and reset hadRest boolean.
	 */	
	public void removeRests() {
		List<AbcNoteEvent> rests = new ArrayList<>();
		for (AbcNoteEvent evt : notes) {
			if (Note.REST == evt.note) {
				rests.add(evt);
			}
		}
		notes.removeAll(rests);
		recalcEndMicros();
		hadRest = false;
	}
		
	/**
	 * Used only by organic1
	 * 
	 * @return
	 */
	public boolean hadRestAndNotes() {
		boolean hasNotes = false;
		for (AbcNoteEvent evt : notes) {
			if (Note.REST != evt.note) {
				hasNotes = true;
				break;
			}
		}
		return hadRest && hasNotes;
	}

	public void printIfUneven() {
		long endNoteMicros = endMicros;
		if (!notes.isEmpty()) {
			for (AbcNoteEvent note : notes) {
				if (note.note != Note.REST && note.endABCMicros != endNoteMicros) {
					System.out.println("Note in chord has bad length! " + (note.endABCMicros - endNoteMicros));
				}
			}
		}
	}
	
	@Override
	public List<AbcNoteEvent> prune(boolean sustained, boolean drum, boolean percussion, AbcPart abcPart, boolean keepShortest) {
		List<AbcNoteEvent> notes = getNotes();
		for (AbcNoteEvent note : notes) {
			note.setStartTick(qtm.microsToTickABCOrganic(note.startABCMicros));
			note.setEndTick(qtm.microsToTickABCOrganic(note.endABCMicros));
		}
		return super.prune(sustained, drum, percussion, abcPart, keepShortest);
	}
	
	@Override
	public int compareTo(Chord o) {
		ChordOrganic oo = (ChordOrganic)o;
		long starting = this.startMicros - oo.getStartMicros();
		if (starting == 0L) {
			starting = this.endMicros - oo.getEndMicros();
		}
		// we do this as comparing two longs that are really large can result in integer overflow if we just cast to int:
		if (starting < 0L) return -1;
		if (starting > 0L) return 1;
		return 0;
	}
	
	/*
	 * Check if all notes in chord start and end at same time
	 * Only use in assert statements
	 */
	@Override
	public boolean isConform() {
		for (AbcNoteEvent ne : notes) {
			if (ne.startABCMicros != startMicros || ne.endABCMicros != endMicros) {
				System.out.println("Chord "+startMicros+"-"+endMicros+" noteCount="+notes.size()+" restMix="+hasRestAndNotes());
				System.out.println("Note  "+ne.startABCMicros+"-"+ne.endABCMicros+" "+ne.note);
				return false;
			}
		}
		return true;
	}

	public String toStringDuraMicros() {
		String post = delete?" (delete)":"";
		return Util.formatDurationM(startMicros)+" -> "+Util.formatDurationM(endMicros)+post;
	}
	
	/**
	 * 
	 * @param note
	 * @return true if note is the shortest in the chord, and only note of that short duration.
	 */
	@Override
	public boolean isShortest(AbcNoteEvent note) {
		if (note.endABCMicros > endMicros) return false;
		for (AbcNoteEvent ne : notes) {
			if (ne.endABCMicros == note.endABCMicros && note != ne) {
				return false;
			}
		}
		return true;
	}
	
	@Override
	public boolean isLinked() {
		for (AbcNoteEvent ne : notes) {
			if (ne.tiesFrom == null) {
				AbcNoteEvent tie = ne;
				while (tie.tiesTo != null) {
					if (tie.tiesTo.getStartTick() != tie.getEndTick()) {
						System.out.println("yChord "+Util.formatDurationM(startMicros)+"-"+Util.formatDurationM(endMicros)+" noteCount="+notes.size()+" restMix="+hasRestAndNotes());
						System.out.println("Note to   "+tie.tiesTo.getStartTick()+"-"+tie.tiesTo.getEndTick()+" ticks, "+tie.tiesTo.note);
						System.out.println("Note from "+tie.getStartTick()+"-"+tie.getEndTick()+" ticks, "+ne.note);
						System.out.println("Note to   "+Util.formatDurationM(tie.tiesTo.startABCMicros)+"-"+Util.formatDurationM(tie.tiesTo.endABCMicros)+" "+tie.tiesTo.note);
						System.out.println("Note from "+Util.formatDurationM(tie.startABCMicros)+"-"+Util.formatDurationM(tie.endABCMicros)+" "+ne.note);
						return false; 
					}
					if (tie.tiesTo.startABCMicros != tie.endABCMicros) {
						System.out.println("xChord "+Util.formatDurationM(startMicros)+"-"+Util.formatDurationM(endMicros)+" noteCount="+notes.size()+" restMix="+hasRestAndNotes());
						System.out.println("Note to   "+tie.tiesTo.getStartTick()+"-"+tie.tiesTo.getEndTick()+" ticks, "+tie.tiesTo.note);
						System.out.println("Note from "+tie.getStartTick()+"-"+tie.getEndTick()+" ticks, "+ne.note);
						System.out.println("Note to   "+Util.formatDurationM(tie.tiesTo.startABCMicros)+"-"+Util.formatDurationM(tie.tiesTo.endABCMicros)+" "+tie.tiesTo.note);
						System.out.println("Note from "+Util.formatDurationM(tie.startABCMicros)+"-"+Util.formatDurationM(tie.endABCMicros)+" "+ne.note);
						return false; 
					}
					tie = tie.tiesTo;
				}
			}
		}
		return true;
	}
}