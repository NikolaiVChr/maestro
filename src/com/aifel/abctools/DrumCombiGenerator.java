package com.aifel.abctools;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.digero.common.abc.LotroInstrument;
import com.digero.common.midi.Note;

public class DrumCombiGenerator {
	
	static Note[] testUs = {Note.Cs3,Note.As3, Note.D3, Note.E3, Note.C5, Note.A3, Note.Cs2,Note.C5,Note.E5, Note.Ds3,Note.F5, Note.As3,Note.F5, Note.Gs3,Note.Fs5, Note.Cs4,Note.Fs5, Note.D3,Note.G5, Note.E3,Note.G5, Note.Gs2,Note.Gs5, Note.C3,Note.Gs5, Note.Gs3};

	public static void main(String[] args) {
		Set<Note> them = new HashSet<>();
		for (Note zero : testUs) {
			if (LotroInstrument.LUTE_OF_AGES.isPlayable(zero.id))
			them.add(zero);
		}
		List<Note> l = new ArrayList<>(them);
		int count = 0;
		for (int i = 0; i < 1; i++) {
			Note one = l.get(i);
			for (int j = i+1; j < them.size(); j++) {
				Note two = l.get(j);
				if (one == two) continue;
				count++;
				System.out.println("["+one.abc+two.abc+"] ");
			}
		}
	}
}
