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
import com.digero.maestro.abc.AbcPart;

public class Chord implements AbcConstants, Comparable<Chord> {
	private ITempoCache tempoCache;
	private long startTick;
	private long endTick;
	private long endTickNotes;
	private List<AbcNoteEvent> notes = new ArrayList<>();
	private int highest = 0;// source midi highest and lowest pitch in the chord
	private int lowest = 200;
	public Long early = null; // organic
	public boolean dontMove1 = false;
	public boolean dontMove2 = false;
	public boolean glissando = false;
	public Long expandedMicros = null;
	public int arp = 0; // arp notes added to this
	public boolean delete = false;
	private boolean hadRest = false;

	public Chord(AbcNoteEvent firstNote) {
		tempoCache = firstNote.getTempoCache();
		startTick = firstNote.getStartTick();
		endTick = firstNote.getEndTick();
		notes.add(firstNote);
		if (firstNote.note == Note.REST) hadRest = true;
	}

	public long getStartTick() {
		return startTick;
	}

	public long getEndTick() {
		return endTick;
	}

	@Deprecated
	public long getStartMicros() {
		return tempoCache.tickToMicros(startTick);
	}

	@Deprecated
	public long getEndMicros() {
		return tempoCache.tickToMicros(endTick);
	}

	public int size() {
		return notes.size();
	}
	
	/*
	 * Size without rests
	 */
	public int sizeReal() {
		int sz = 0;
		for (NoteEvent ne : notes) {
			if (ne.note != Note.REST) sz++;
		}
		return sz;
	}

	public AbcNoteEvent get(int i) {
		return notes.get(i);
	}

	public boolean remove(AbcNoteEvent ne) {
		return notes.remove(ne);
	}
	
	public Dynamics calcDynamics(CalcDynamics method) {
		switch (method) {
			case LOUDEST:
				return calcDynamicsLoudest();
			case POWER_RMS_DB:
				return calcDynamicsDbBlending(2.0d);
			case POWER_MID_DB:
				return calcDynamicsDbBlending(1.5d);
			case WEIGHTED:
				return calcDynamicsWeightBlending();
		}
		return calcDynamicsLoudest();
	}

	public Dynamics calcDynamicsLoudest() {
		int velocity = Integer.MIN_VALUE;
		for (AbcNoteEvent ne : notes) {
			if (ne.note != Note.REST && ne.tiesFrom == null && ne.velocity > velocity)
				velocity = ne.velocity;
		}

		if (velocity == Integer.MIN_VALUE)
			return null;

		return Dynamics.fromMidiVelocity(velocity);
	}
	
	public Dynamics calcDynamicsWeightBlending() {
		int minVolume = Integer.MAX_VALUE;
		int maxVolume = Integer.MIN_VALUE;
		int total = 0;
		int number = 0;
		
		for (AbcNoteEvent ne : notes) {
			if (ne.note != Note.REST && ne.tiesFrom == null) {
				total += ne.velocity;
				minVolume = Math.min(minVolume, ne.velocity);
				maxVolume = Math.max(maxVolume, ne.velocity);
				number++;
			}
		}
		
		if (number == 0) return null;
		
		int averageVolume = total / number;
		int blendedVolume = (averageVolume * 2 + minVolume + maxVolume) / 4;
		
		return Dynamics.fromMidiVelocity(blendedVolume);
	}
	
	// Gamma must be between 1 and 2. 1.0 = 6 dB for adding two with same vol together. 1.5 = 4.5 dB. 2.0 = 3 dB (RMS).
    public Dynamics calcDynamicsDbBlending(double gamma) {
    	assert gamma >= 1.0d && gamma <= 2.0d;
    	int numberOfVelocities = 0;
    	double totalDB = 0;
        for (AbcNoteEvent ne : notes) {
			if (ne.note != Note.REST && ne.tiesFrom == null) {
				numberOfVelocities++;
				totalDB += Math.pow(10d, (midiToDb(ne.velocity) / 20) * gamma);
			}
        }
        if (numberOfVelocities == 0) return null;
        double mean = totalDB / numberOfVelocities;
        double targetAmp = Math.pow(mean, 1.0d / gamma);
        int target = (int) Math.round(127.0 * targetAmp);
        target = Math.max(0, Math.min(127, target));
        return Dynamics.fromMidiVelocity(target);
    }
	
	public double midiToDb(int midiVelocity) {
		if (midiVelocity == 0) return -100d;
        return 20 * Math.log10(midiVelocity / 127.0d);
    }

    public int dbToMidi(double db) {
        return Math.min(127, (int) (127d * Math.pow(10d, db / 20d)));
    }
    
	public void recalcEndTick() {
		if (!notes.isEmpty()) {
			endTick = notes.get(0).getEndTick();
			if (notes.get(0).note == Note.REST) {
				if (notes.size() > 1) {
					endTickNotes = notes.get(1).getEndTick();
				} else {
					endTickNotes = getStartTick();
					return;
				}
			}
			for (int k = 1; k < notes.size(); k++) {
				if (notes.get(k).getEndTick() < endTick) {
					endTick = notes.get(k).getEndTick();
				}
				if (notes.get(k).note != Note.REST) {
					if (notes.get(k).getEndTick() < endTickNotes) {
						endTickNotes = notes.get(k).getEndTick();
					}
				}
			}
		} else {
			endTick = startTick;
			endTickNotes = startTick;
		}
	}
	
	public void setEndTick(long newEndTick) {
		if (isRest()) return;
		for (AbcNoteEvent note : notes) {
			note.setEndTick(newEndTick);
		}
		endTick = newEndTick;
	}
	
	public void setEndTickRetract(long newEndTick) {
		//assert newEndTick >= startTick;
		// organic
		for (AbcNoteEvent note : notes) {
			if (note.getEndTick() > newEndTick) {
				note.setEndTick(newEndTick);
			}
		}
		endTick = newEndTick;
	}
	
	public void setEndTickExpand(long newEndTick) {
		// organic
		for (AbcNoteEvent note : notes) {
			if (note.getEndTick() < newEndTick) {
				note.setEndTick(newEndTick);
			}
		}
		endTick = newEndTick;
	}
	
	public void setEarlyStartTick() {
		//assert early <= endTick;
		// organic
		for (AbcNoteEvent note : notes) {
			note.setStartTick(early);
		}
		startTick = early;
		early = null;
	}

	public void sort() {
		Collections.sort(notes);
	}
	
	public boolean add(AbcNoteEvent ne) {
		if (ne.getLengthTicks() == 0) {
			//System.err.println("Attempted to add zero duration note to chord!");
			//return false;
		}
		//assert ne.getStartTick() == startTick;
		//assert ne.getEndTick() >= startTick;
		/*
		for (AbcNoteEvent evt : notes) {
			assert evt.note != ne.note;
		}
		*/
		notes.add(ne);
		if (ne.note == Note.REST) hadRest = true;

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
				int oldID = evt.origNote.note.id;
				if (evt.origNote instanceof BentMidiNoteEvent && evt.getOrigBend() != null) {
					oldID += evt.getOrigBend();
				}
				if (oldID > highest) {
					highest = oldID;
				}
				if (oldID < lowest) {
					lowest = oldID;
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
	
	public AbcNoteEvent getShortest() {
		long endNoteMicros = Long.MAX_VALUE;
		AbcNoteEvent shortest = null;
		if (!notes.isEmpty()) {
			for (AbcNoteEvent note : notes) {
				if (note.origEndABCMicros < endNoteMicros) {
					shortest = note;
					endNoteMicros = note.origEndABCMicros;
				}
			}
		}
		return shortest;
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
		long endNoteTick = getEndTick();
		if (!notes.isEmpty()) {
			for (AbcNoteEvent note : notes) {
				if (note.note != Note.REST && note.getEndTick() != endNoteTick) {
					System.out.println("Note in chord has bad length! " + (note.getEndTick() - endNoteTick));
				}
			}
		}
	}
	
	public List<AbcNoteEvent> prune(boolean sustained, boolean drum, boolean percussion, AbcPart abcPart) {
		// Determine which notes to prune to remain with a max of 6
		List<AbcNoteEvent> deadNotes = new ArrayList<>();
		//System.out.println("Starting prune");
		int noteMax = abcPart.getNoteMax();
		
		boolean removeRests = false;
		if (endTickNotes <= endTick) {
			// there is notes that are at least as short as the shortest rest,
			// so lets down-prioritize rests as they not needed.
			// ..unless, shortest note gets pruned, which we must prevent
			removeRests = true;
		}
		long oldEndTick = endTick;
		boolean both = hasRestAndNotes();
		if (size() > noteMax) {
			//System.out.println(" prune needed");
			recalcEdges();

			List<AbcNoteEvent> newNotes = new ArrayList<>();

			PruneComparator keepMe = new PruneComparator(sustained, drum, percussion, both, getShortest().getLengthTicks());

			notes.sort(keepMe);
			
			// commented due to we assume only 1 rest present max
			/*
			List<AbcNoteEvent> rests = new ArrayList<>();
			AbcNoteEvent shortestRest = null;
			long restDura = Long.MAX_VALUE;
			for (AbcNoteEvent ne : notes) {
				if (ne.note == Note.REST) {
					if (ne.getLengthTicks() < restDura) {
						// we only keep the shortest rest
						restDura = ne.getLengthTicks();
						shortestRest = ne;
					}
					rests.add(ne);
				}
			}
			
			notes.removeAll(rests);// no need to add the rests to deadnotes
			
			
			*/
			
			for (int i = notes.size() - 1; i >= 0; i--) {
				if (newNotes.size() < noteMax) {
					newNotes.add(notes.get(i));
				} else {
					deadNotes.add(notes.get(i));
				}
			}
			notes = newNotes;
			
			//if (shortestRest != null) notes.add(shortestRest);
			
			
		}
		recalcEndTick();
		if (endTickNotes == endTick && endTickNotes > startTick) {
			// remove any rests since they not needed
			List<AbcNoteEvent> rests = new ArrayList<>();
			for (AbcNoteEvent ne : notes) {
				if (ne.note == Note.REST) {
					rests.add(ne);
					//System.out.println(" removing rest due to note bing same dura");
				}
			}
			notes.removeAll(rests);// no need to add the rests to deadnotes
		}
		assert oldEndTick == endTick || !both:"Old="+oldEndTick+" new="+endTick+" start="+startTick+" both="+hasRestAndNotes();
		/*
		if (hasRestAndNotes()) {
			System.out.println();
			for (AbcNoteEvent ne : notes) {
				if (ne.note == Note.REST) {
					System.out.println(" rest="+ne.getLengthTicks());
				} else {
					System.out.println(" note="+ne.getLengthTicks());
				}
			}
		}
		*/
		return deadNotes;
	}
	
	class PruneComparator implements Comparator<AbcNoteEvent> {
		final boolean sustained;
		final boolean drum;
		final boolean percussion;
		final boolean restPresent;
		final long restDura;

		PruneComparator (boolean sustained, boolean drum, boolean percussion, boolean restPresent, long restDura) {
			this.drum = drum;
			this.sustained = sustained;
			this.percussion = percussion;
			this.restPresent = restPresent;
			this.restDura = restDura;
		}

		@Override
		public int compare(AbcNoteEvent n1, AbcNoteEvent n2) {
			
			if (n1.note == Note.REST && n2.note == Note.REST) {
				if (n1.getLengthTicks() < n2.getLengthTicks()) {
					return 1; //keep n1
				} else {
					return -1;
				}
			}

			if (n1.note == Note.REST) {
				if (n2.getLengthTicks() > n1.getLengthTicks()) {
					return 1;
				} else {
					//System.out.println("  keep n2 over rest");
					return -1;
				}
			}
			if (n2.note == Note.REST) {
				if (n1.getLengthTicks() > n2.getLengthTicks()) {
					return -1;
				} else {
					//System.out.println("  keep n1 over rest. rest="+n2.getLengthTicks()+" note="+n1.getLengthTicks());
					return 1;
				}
			}
			if (restPresent && n1.note != Note.REST && n2.note != Note.REST) {
				if (n1.getLengthTicks() != n2.getLengthTicks()) {
					// we might be pruning the rest, so lets make sure we keep a note with same short dura
					if (n1.getLengthTicks() == restDura) {
						return 1;
					} else if (n2.getLengthTicks() == restDura) {
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
					points += -2;
				}
				if (n2OctSpacing && highest != midiNote2 && lowest != midiNote2) {
					// orig n2 has that spacing
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

				if (Math.abs(abcNote1 - abcNote2) % 12 == 0) {
					// If 2 notes have octave spacing, keep the highest pitched.
					if (abcNote1 > abcNote2) {
						points += 2;
					} else if (abcNote2 > abcNote1) {
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

			// Random choice if they got even scores.
			// PS. They were not added in random order to this chord, so it wont be fully random.
			return 0;

			// 1: n1 wins -1: n2 wins 0:equal
		}
	}

	public List<AbcNoteEvent> getNotes() {
		return notes;
	}

	@Override
	public int compareTo(Chord o) {
		long starting = this.getStartTick() - o.getStartTick();
		if (starting == 0L) {
			starting = this.getEndTick() - o.getEndTick();
		}
		// we do this as comparing two longs that are really large can result in integer overflow if we just cast to int:
		if (starting < 0L) return -1;
		if (starting > 0L) return 1;
		return 0;
	}
	
	public static enum CalcDynamics {
		// name() is saved in project files
		// label is shown in UI
		
		LOUDEST("Loudest"),
		POWER_RMS_DB("Power RMS dB"),
		POWER_MID_DB("Power mid dB"),
		WEIGHTED("Weighted");
		
		private final String label;
		
		CalcDynamics(String label) {
	        this.label = label;
	    }
		
		@Override
	    public String toString() {
	        return label;
	    }
		
		public static CalcDynamics fromString(String name) {
	        for (CalcDynamics c : values()) {
	            if (c.label.equalsIgnoreCase(name) || c.name().equalsIgnoreCase(name)) {
	                return c;
	            }
	        }
	        throw new IllegalArgumentException("Unknown CalcDynamics: " + name);
	    }
	}
}