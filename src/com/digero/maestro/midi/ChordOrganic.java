package com.digero.maestro.midi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.digero.common.midi.Note;
import com.digero.common.util.Util;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.QuantizedTimingInfo;

/**
 * Only used by organic output
 */
public class ChordOrganic extends Chord {	
	private long startMicros = -1L;
	private long endMicros = -1L;
	public Long early = null; // organic
	public boolean dontMove1 = false;
	public boolean dontMove2 = false;
	public Long expandedMicros = null;
	public int arp = 0; // arp notes added to this
	public boolean delete = false;
	private boolean hadRest = false;
	private final QuantizedTimingInfo qtm;

	/**
	 * The chord's position in the original MIDI, captured at construction and never
	 * updated. exportPartToAbcOrganic needs this for tickToBarNumberFloat(); it must not
	 * be a value round-tripped through micros, and it must not be used in arithmetic
	 * alongside derived ticks.
	 */
	public final long origStartTick;

	public ChordOrganic(AbcNoteEvent firstNote, QuantizedTimingInfo qtm) {
		super(firstNote);
		this.qtm = qtm;
		startMicros = firstNote.startABCMicros;
		endMicros = firstNote.endABCMicros;
		origStartTick = firstNote.startTick;
		if (firstNote.note == Note.REST) hadRest = true;
	}

	public long getStartMicros() {
		return startMicros;
	}

	public long getEndMicros() {
		return endMicros;
	}
	
	public void recalcEndMicros() {
        endMicros = Long.MAX_VALUE;
		if (!notes.isEmpty()) {
			for (AbcNoteEvent note : notes) {
                endMicros = Math.min(endMicros, note.endABCMicros);
                assert note.startABCMicros == startMicros;
				note.setStartTick(qtm.microsToTickABCOrganic(note.startABCMicros));
				note.setEndTick(qtm.microsToTickABCOrganic(note.endABCMicros));
			}
		}
        if (endMicros == Long.MAX_VALUE) endMicros = startMicros;
        assert endMicros > -1L && startMicros > -1L;
		recalcEndTick();
	}

	/**
	 * Re-derive every note tick in this chord from its micros.
	 *
	 * Deliberately does not touch origStartTick: exportPartToAbcOrganic feeds
	 * origStartTick to tickToBarNumberFloat(), which needs the position in the original
	 * MIDI, not a value round-tripped through micros.
	 *
	 * Micros are authoritative in organic output; ticks are a lossy projection that
	 * exportPartToAbcOrganic still read. Call this wherever ticks are
	 * about to be consumed.
	 *
	 * Only used by processOrganic (single-stage).
	 */
	public void syncNoteTicksFromMicros(boolean first) {
		for (AbcNoteEvent note : notes) {
			if (!first) note.setStartTick(qtm.microsToTickABCOrganic(note.startABCMicros));
			note.setEndTick(qtm.microsToTickABCOrganic(note.endABCMicros));
		}
		recalcEndTick();
	}
	
	/**
	 * Wont change anything if the chord is a rest with no notes
	 *
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

    /**
     * Goes into effect immediately
     */
    public void setForceEarlyStartMicros(long stMicros, long stTick) {
        for (AbcNoteEvent note : notes) {
            note.startABCMicros = stMicros;
            note.setStartTick(stTick);
        }
        startMicros = stMicros;
        startTick = stTick;
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
        return Long.compare(starting, 0L);
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

	/**
	 * Should be kept uptodate with Chord.prune()
	 **/
	public List<AbcNoteEvent> pruneWithMicros(boolean sustained, boolean drum, boolean percussion, AbcPart abcPart, boolean keepShortest) {
		// Determine which notes to prune to remain with a max of noteMax
		List<AbcNoteEvent> deadNotes = new ArrayList<>();

		int noteMax = abcPart.getNoteMax();

		long oldEndMicros = Long.MAX_VALUE;
		for (AbcNoteEvent ne : notes) {
			if (ne.endABCMicros < oldEndMicros) {
				oldEndMicros = ne.endABCMicros;
			}
			// Pruning removes notes, it never moves the chord. All notes share the chord's
			// start (see recalcEndMicros/isConform), so startMicros must not be reassigned
			// here -- doing so would silently adopt an arbitrary surviving note's start
			// instead of reporting the broken invariant.
			assert ne.startABCMicros == startMicros:"prune: note off chord start. note="+ne.startABCMicros+" chord="+startMicros;
		}

		boolean both = hasRestAndNotes();
		if (size() > noteMax) {
			//System.out.println(" prune needed");
			recalcEdges();

			List<AbcNoteEvent> newNotes = new ArrayList<>();

			PruneComparatorMicros keepMe = new PruneComparatorMicros(sustained, drum, percussion, both, oldEndMicros-startMicros);

			notes.sort(keepMe);

			// notes are now sorted by most important last

			List<AbcNoteEvent> deadRests = keepOnlyRestIfShortestMicros();
			deadNotes.addAll(deadRests);

			NoteEvent keptShort = null;
			if (keepShortest) {
				for (int i = notes.size() - 1; i >= 0; i--) {
					if (oldEndMicros == notes.get(i).endABCMicros) {
						keptShort = notes.get(i);
						newNotes.add(notes.get(i));
						break;
					}
				}
			}

			for (int i = notes.size() - 1; i >= 0; i--) {
				if (keptShort != notes.get(i)) {
					if (newNotes.size() < noteMax) {
						newNotes.add(notes.get(i));
					} else {
						deadNotes.add(notes.get(i));
					}
				}
			}
			notes = newNotes;

		} else {
			deadNotes = keepOnlyRestIfShortestMicros();
		}

		long newEndMicros = Long.MAX_VALUE;
		long newStartMicros = notes.getFirst().startABCMicros;
		for (AbcNoteEvent ne : notes) {
			if (ne.endABCMicros < newEndMicros) {
				newEndMicros = ne.endABCMicros;
			}
		}
		endMicros = newEndMicros;
		startMicros = newStartMicros;
		recalcEndTick();//just to keep it fresh for possible debug output.

		assert endMicros == oldEndMicros || !both:"old="+oldEndMicros+"new="+endMicros+" start="+startMicros+" both="+hasRestAndNotes();

		return deadNotes;
	}

	/**
	 * Keep only 1 rest and only if it's the shortest event in the chord,
	 * and no note is equally short.
	 *
	 * @return rests that were removed
	 */
	private List<AbcNoteEvent> keepOnlyRestIfShortestMicros() {
		List<AbcNoteEvent> rests = new ArrayList<>();
		AbcNoteEvent shortestRest = null;
		long restDura = Long.MAX_VALUE;
		for (AbcNoteEvent ne : notes) {
			long dura = ne.endABCMicros - ne.startABCMicros;
			if (dura < restDura || (dura == restDura && ne.note != Note.REST)) {
				// we only keep the shortest
				restDura = dura;
				if (ne.note == Note.REST) {
					shortestRest = ne;
				} else {
					shortestRest = null;
				}
			}
			if (ne.note == Note.REST) rests.add(ne);
		}

		notes.removeAll(rests);// no need to add the rests to deadnotes

		if (shortestRest != null && shortestRest.endABCMicros - shortestRest.startABCMicros == restDura) {
			notes.add(shortestRest);// add at the end, so most important
			rests.remove(shortestRest);
			//System.out.println("kept rest");
		} else {
			//System.out.println("no shortest rest");
		}
		return rests;
	}

	class PruneComparatorMicros implements Comparator<AbcNoteEvent> {
		final boolean sustained;
		final boolean drum;
		final boolean percussion;
		final boolean restPresent;
		final long restDura;

		PruneComparatorMicros (boolean sustained, boolean drum, boolean percussion, boolean restPresent, long restDura) {
			this.drum = drum;
			this.sustained = sustained;
			this.percussion = percussion;
			this.restPresent = restPresent;
			this.restDura = restDura;
		}

		@Override
		public int compare(AbcNoteEvent n1, AbcNoteEvent n2) {

			if (n1.note == Note.REST && n2.note == Note.REST) {
				if (n1.endABCMicros - n1.startABCMicros < n2.endABCMicros - n2.startABCMicros) {
					return 1; //keep n1
				} else {
					return -1;
				}
			}

			if (n1.note == Note.REST) {
				if (n2.endABCMicros - n2.startABCMicros > n1.endABCMicros - n1.startABCMicros) {
					return 1;
				} else {
					//System.out.println("  keep n2 over rest");
					return -1;
				}
			}
			if (n2.note == Note.REST) {
				if (n1.endABCMicros - n1.startABCMicros > n2.endABCMicros - n2.startABCMicros) {
					return -1;
				} else {
					//System.out.println("  keep n1 over rest. rest="+n2.getLengthTicks()+" note="+n1.getLengthTicks());
					return 1;
				}
			}
			if (restPresent) {
				if (n1.endABCMicros - n1.startABCMicros != n2.endABCMicros - n2.startABCMicros) {
					// we might be pruning the rest, so lets make sure we keep a note with same short dura
					if (n1.endABCMicros - n1.startABCMicros == restDura) {
						return 1;
					} else if (n2.endABCMicros - n2.startABCMicros == restDura) {
						return -1;
					}
				}
			}

			int abcNote1 = n1.note.id;
			int abcNote2 = n2.note.id;



			/*
			 * if (n1.doubledNote && !n2.doubledNote) { return -1; } if (n2.doubledNote && !n1.doubledNote) { return
			 * 1; }
			 */

			/*
			 * This was commented as experiments in lotro shows that can start a new note even if 6 prev. is still
			 * playing. boolean n1Finished = false; boolean n2Finished = false; if (!sustained) { // Lets find out
			 * if the notes have already finished long dura = 0; for (NoteEvent neTie = n1.tiesFrom; neTie != null;
			 * neTie = neTie.tiesFrom) { dura += neTie.getLengthMicros(); } if (dura >
			 * AbcConstants.NON_SUSTAINED_NOTE_HOLD_SECONDS) { n1Finished = true; } dura = 0; for (NoteEvent neTie =
			 * n2.tiesFrom; neTie != null; neTie = neTie.tiesFrom) { dura += neTie.getLengthMicros(); } if (dura >
			 * AbcConstants.NON_SUSTAINED_NOTE_HOLD_SECONDS) { n2Finished = true; } } if (n1Finished && !n2Finished)
			 * { return -1; } else if (n2Finished && !n1Finished) { return 1; }
			 */

			if (!sustained) {
				// discard tiedFrom notes.
				// Although we already checked for finished notes,
				// we don't mind stopping note and not let it decay
				// to prioritize a new sound.
				if (n1.tiesFrom != null && n2.tiesFrom == null) {
					return -1;
				}
				if (n2.tiesFrom != null && n1.tiesFrom == null) {
					return 1;
				}
			}

			if (n1.velocity != n2.velocity) {
				// The notes differ in volume, return the loudest
				return n1.velocity - n2.velocity;
			}

			if (percussion && !drum) {
				return 0;
			} else if (!drum) {

				int midiNote1 = n1.origNote.note.id;
				int midiNote2 = n2.origNote.note.id;

				// The orig midi notes might have been bended, so we fetch that initial bend and apply it.
				if (!percussion && n1.origNote instanceof BentMidiNoteEvent && n1.getOrigBend() != null) {
					midiNote1 += n1.getOrigBend();
				}
				if (!percussion && n2.origNote instanceof BentMidiNoteEvent && n2.getOrigBend() != null) {
					midiNote2 += n2.getOrigBend();
				}


				if (midiNote1 != midiNote2) {
					// return the note if its the highest in the chord
					if (midiNote1 == highest) {
						return 1;
					}
					if (midiNote2 == highest) {
						return -1;
					}
					// return the note if its the lowest in the chord
					if (midiNote1 == lowest) {
						return 1;
					}
					if (midiNote2 == lowest) {
						return -1;
					}
				}

				if (abcNote1 == abcNote2) {
					// The notes have same pitch and same volume. Return the longest.
					// The code should not get in here.
					assert false: "Comparing two notes in a chord that only has room for one of them. equal="+(n1 == n2);
					return (int) (n1.getFullLengthTicks() - n2.getFullLengthTicks());
				}

				int points = 0;

				/*
				// If coming from multiple tracks, a note is likely important
				if (n1.fromHowManyTracks > n2.fromHowManyTracks) {
					points += 2;
				} else if (n1.fromHowManyTracks < n2.fromHowManyTracks) {
					points -= 2;
				}*/

				boolean n1OctSpacing = (highest - midiNote1)%12 == 0 || (midiNote1 - lowest)%12 == 0;
				boolean n2OctSpacing = (highest - midiNote2)%12 == 0 || (midiNote2 - lowest)%12 == 0;

				// Lower prio for notes that has octave spacing from highest or lowest notes
				if (n1OctSpacing && highest != midiNote1 && lowest != midiNote1) {
					// orig n1 has that spacing
					points -= 2;
				}
				if (n2OctSpacing && highest != midiNote2 && lowest != midiNote2) {
					// orig n2 has that spacing
					points += 2;
				}

				if (sustained) {
					// Deliberately tick-based, and deliberately not resynced from micros first:
					// this only asks whether one note was originally much longer than the other,
					// and stale ticks still answer that. Do not "fix" this to micros.

					// We keep the longest note, including continuation from notes broken up
					if (n1.getFullLengthTicks() + n1.continues > n2.getFullLengthTicks() + n2.continues) {
						points += 2;
					} else if (n2.getFullLengthTicks() + n2.continues > n1.getFullLengthTicks() + n1.continues) {
						points -= 2;
					}
				}

				if (Math.abs(abcNote1 - abcNote2) % 12 == 0) {
					// If 2 notes have octave spacing, keep the highest pitched.
					if (abcNote1 > abcNote2) {
						points += 2;
					} else if (abcNote2 > abcNote1) {
						points -= 2;
					}
				}

				if (sustained) {
					if (n1.tiesFrom != null) {
						points += 1;
					}
					if (n2.tiesFrom != null) {
						points -= 1;
					}
				}

				if (points > 0)
					return 1;
				if (points < 0)
					return -1;

			} else {

				// Bass drums get priority:
				if (n1.note == Note.As3) {// Open bass
					return 1;
				} else if (n2.note == Note.As3) {
					return -1;
				} else if (n1.note == Note.D3) {// Bass slap 2
					return 1;
				} else if (n2.note == Note.D3) {
					return -1;
				} else if (n1.note == Note.Gs3) {// Bass
					return 1;
				} else if (n2.note == Note.Gs3) {
					return -1;
				} else if (n1.note == Note.Cs3) {// Bass slap 1
					return 1;
				} else if (n2.note == Note.Cs3) {
					return -1;
				} else if (n1.note == Note.Cs4) {// Muted 2
					return 1;
				} else if (n2.note == Note.Cs4) {
					return -1;
				} else if (n1.note == Note.C3) {// Muted Mid
					return 1;
				} else if (n2.note == Note.C3) {
					return -1;
				}

				// Its too constrained to prioritize the rest.
				// No way to really prioritize them, depends
				// on song and transcribers taste.
				//
				// Note that muted 1 is not included on purpose.
			}

			// Random choice if they got even scores.
			// PS. They were not added in random order to this chord, so it wont be fully random.
			return 0;

			// 1: n1 wins -1: n2 wins 0:equal
		}
	}
}