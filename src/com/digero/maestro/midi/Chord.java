/* Copyright (c) 2010 Ben Howell
 * This software is licensed under the MIT License
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a 
 * copy of this software and associated documentation files (the "Software"), 
 * to deal in the Software without restriction, including without limitation 
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, 
 * and/or sell copies of the Software, and to permit persons to whom the 
 * Software is furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in 
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR 
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, 
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE 
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER 
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING 
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER 
 * DEALINGS IN THE SOFTWARE.
 */

package com.digero.maestro.midi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.digero.common.abc.AbcConstants;
import com.digero.common.abc.Dynamics;
import com.digero.common.midi.ITempoCache;
import com.digero.common.midi.Note;

public class Chord implements AbcConstants {
	private ITempoCache tempoCache;
	private long startTick;
	private long endTick;
	private boolean hasTooManyNotes = false;
	private List<AbcNoteEvent> notes = new ArrayList<>();
	private int highest = 0;
	private int lowest = 200;

	public Chord(AbcNoteEvent firstNote) {
		tempoCache = firstNote.getTempoCache();
		startTick = firstNote.getStartTick();
		endTick = firstNote.getEndTick();
		notes.add(firstNote);
	}

	public boolean isRest() {
		for (AbcNoteEvent evt : notes) {
			if (Note.REST != evt.note) {
				return false;
			}
		}
		return true;
	}

	public boolean hasRestAndNotes() {
		boolean hasNotes = false;
		boolean hasRests = false;
		for (AbcNoteEvent evt : notes) {
			if (Note.REST == evt.note) {
				hasRests = true;
			} else if (Note.REST != evt.note) {
				hasNotes = true;
			}
		}
		/*
		 * if (hasRests && hasNotes) { for (NoteEvent evt : notes) {
		 * System.out.println(evt.note.getDisplayName()+" "+evt.getStartTick()+" to "+evt.getEndTick()); } }
		 */
		return hasRests && hasNotes;
	}

	public long getStartTick() {
		return startTick;
	}

	public long getEndTick() {
		return endTick;
	}

	public long getStartMicros() {
		return tempoCache.tickToMicros(startTick);
	}

	public long getEndMicros() {
		return tempoCache.tickToMicros(endTick);
	}

	public int size() {
		return notes.size();
	}

	public boolean hasTooManyNotes() {
		return hasTooManyNotes;
	}

	public AbcNoteEvent get(int i) {
		return notes.get(i);
	}

	public boolean add(AbcNoteEvent ne, boolean force) {
		while (force && size() >= MAX_CHORD_NOTES) {
			remove(size() - 1);
			hasTooManyNotes = true;
		}
		return add(ne);
	}

	public boolean add(AbcNoteEvent ne) {
		if (ne.getLengthTicks() == 0) {
			hasTooManyNotes = true;
			return false;
		}

		if (size() >= MAX_CHORD_NOTES) {
			return false;
		}

		notes.add(ne);
		if (ne.getEndTick() < endTick) {
			endTick = ne.getEndTick();
		}
		return true;
	}

	public AbcNoteEvent remove(int i) {
		if (size() <= 1)
			return null;

		AbcNoteEvent ne = notes.remove(i);
		if (ne.getEndTick() == endTick)
			recalcEndTick();

		return ne;
	}

	public boolean remove(AbcNoteEvent ne) {
		return notes.remove(ne);
	}

	public Dynamics calcDynamics() {
		int velocity = Integer.MIN_VALUE;
		for (AbcNoteEvent ne : notes) {
			if (ne.note != Note.REST && ne.tiesFrom == null && ne.velocity > velocity)
				velocity = ne.velocity;
		}

		if (velocity == Integer.MIN_VALUE)
			return null;

		return Dynamics.fromMidiVelocity(velocity);
	}

	public void recalcEndTick() {
		if (!notes.isEmpty()) {
			endTick = notes.get(0).getEndTick();
			for (int k = 1; k < notes.size(); k++) {
				if (notes.get(k).getEndTick() < endTick) {
					endTick = notes.get(k).getEndTick();
				}
			}
		}
	}

	public void sort() {
		Collections.sort(notes);
	}

	public List<AbcNoteEvent> prune(final boolean sustained, final boolean drum) {
		// Determine which notes to prune to remain with a max of 6
		List<AbcNoteEvent> deadNotes = new ArrayList<>();
		if (size() > MAX_CHORD_NOTES) {
			recalcEdges();

			List<AbcNoteEvent> newNotes = new ArrayList<>();

			Comparator<AbcNoteEvent> keepMe = (n1, n2) -> {

				if (n1.note == Note.REST) {
					return 1;
				}
				if (n2.note == Note.REST) {
					return -1;
				}

				/*
				 * if (n1.doubledNote && !n2.doubledNote) { return -1; } if (n2.doubledNote && !n1.doubledNote) { return
				 * 1; }
				 */

				// At the point in time when prune() is run, there is very few tiedTo, mostly just tiedFrom.
				// assert n1.tiesTo == null;
				// assert n2.tiesTo == null;
				/*
				 * This was commented as experiments in lotro shows than can start a new note even if 6 prev. is still
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

				if (!drum) {
					if (n1.note.id != n2.note.id) {
						// return the note if its the highest in the chord
						if ((n1.origPitch == 0 && highest == n1.note.id)
								|| (n1.origPitch != 0 && highest == n1.origPitch)) {
							return 1;
						}
						if ((n2.origPitch == 0 && highest == n2.note.id)
								|| (n2.origPitch != 0 && highest == n2.origPitch)) {
							return -1;
						}
						// return the note if its the lowest in the chord
						if ((n1.origPitch == 0 && lowest == n1.note.id)
								|| (n1.origPitch != 0 && lowest == n1.origPitch)) {
							return 1;
						}
						if ((n2.origPitch == 0 && lowest == n2.note.id)
								|| (n2.origPitch != 0 && lowest == n2.origPitch)) {
							return -1;
						}
					}

					if (n1.note.id == n2.note.id) {
						// The notes have same pitch and same volume. Return the longest.
						// The code should not get in here.
						return (int) (n1.getFullLengthTicks() - n2.getFullLengthTicks());
					}

					int points = 0;

					List<Integer> removeFirst = new ArrayList<>();

					removeFirst.add(highest - 32);
					removeFirst.add(highest - 24);
					removeFirst.add(highest - 12);
					removeFirst.add(lowest + 32);
					removeFirst.add(lowest + 24);
					removeFirst.add(lowest + 12);

					int index1 = removeFirst.indexOf(n1.note.id);
					int index2 = removeFirst.indexOf(n2.note.id);

					// Discard notes first that has octave spacing from highest or lowest notes
					if (index1 != -1) {
						points += -2;
					}
					if (index2 != -1) {
						points += 2;
					}

					if (sustained) {
						// We keep the longest note, including continuation from notes broken up
						if (n1.getFullLengthTicks() + n1.continues > n2.getFullLengthTicks() + n2.continues) {
							points += 2;
						} else if (n2.getFullLengthTicks() + n2.continues > n1.getFullLengthTicks() + n1.continues) {
							points += -2;
						}
					}

					if ((Math.abs(n1.note.id - n2.note.id) == 12 || Math.abs(n1.note.id - n2.note.id) == 24
							|| Math.abs(n1.note.id - n2.note.id) == 36)) {
						// If 2 notes have octave spacing, keep the highest pitched.
						if (n1.note.id > n2.note.id) {
							points += 2;
						} else if (n2.note.id > n1.note.id) {
							points += -2;
						}
					}

					if (sustained) {
						if (n1.tiesFrom != null) {
							points += 1;
						}
						if (n2.tiesFrom != null) {
							points += -1;
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

				// discard the center-most note (for drum this is very random)
				int center = Math.abs(n1.note.id - (lowest + (highest - lowest) / 2))
						- Math.abs(n2.note.id - (lowest + (highest - lowest) / 2));

				return Integer.compare(center, 0);

				// 1: n1 wins -1: n2 wins 0:equal
			};

			notes.sort(keepMe);

			// System.err.print("Prune\n");
			for (int i = notes.size() - 1; i >= 0; i--) {
				if (newNotes.size() < MAX_CHORD_NOTES) {
					newNotes.add(notes.get(i));
					// System.err.print(" keep " + notes.get(i).printout()+"\n");
				} else {
					deadNotes.add(notes.get(i));
					// System.err.print(" prune " + notes.get(i).printout()+"\n");
				}
			}
			notes = newNotes;
			recalcEndTick();
		}
		return deadNotes;
	}

	public boolean addAlways(AbcNoteEvent ne) {
		if (ne.getLengthTicks() == 0) {
			hasTooManyNotes = true;
			return false;
		}
		notes.add(ne);

		if (ne.getEndTick() < endTick) {
			endTick = ne.getEndTick();
		}
		return true;
	}

	/**
	 * Called only on demand when the edge values is needed.
	 */
	private void recalcEdges() {
		highest = 0;
		lowest = 200;
		for (AbcNoteEvent evt : notes) {
			if (evt.note != Note.REST) {
				if (evt.origPitch != 0) {
					if (evt.origPitch > highest) {
						highest = evt.origPitch;
					}
					if (evt.origPitch < lowest) {
						lowest = evt.origPitch;
					}
				} else {
					if (evt.note.id > highest) {
						highest = evt.note.id;
					}
					if (evt.note.id < lowest) {
						lowest = evt.note.id;
					}
				}
			}
		}
	}

	public Long getLongestEndTick() {
		long endNoteTick = getStartTick();
		if (!notes.isEmpty()) {
			for (AbcNoteEvent note : notes) {
				if (note.note != Note.REST && note.getEndTick() > endNoteTick) {
					endNoteTick = note.getEndTick();
				}
			}
		}
		return endNoteTick;
	}

	public void removeRests() {
		List<AbcNoteEvent> rests = new ArrayList<>();
		for (AbcNoteEvent evt : notes) {
			if (Note.REST == evt.note) {
				rests.add(evt);
			}
		}
		notes.removeAll(rests);
		recalcEndTick();
	}

	public void printIfUneven() {
		long endNoteTick = getEndTick();
		if (!notes.isEmpty()) {
			for (AbcNoteEvent note : notes) {
				if (note.note != Note.REST && note.getEndTick() != endNoteTick) {
					System.out.println("Note in chord has bad length! " + (note.getEndTick() - endNoteTick));
				}
			}
		}
	}
}