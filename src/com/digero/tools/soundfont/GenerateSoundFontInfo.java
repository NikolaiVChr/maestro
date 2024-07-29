package com.digero.tools.soundfont;

import java.io.File;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.digero.common.abc.AbcConstants;
import com.digero.common.abc.LotroInstrument;
import com.digero.common.util.ExtensionFileFilter;

public class GenerateSoundFontInfo {
	public static void main(String[] args) {
		try {
			System.exit(run(args));
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	private static int getNotesPerSample(LotroInstrument lotroInstrument) {
		switch (lotroInstrument) {
		case BASIC_DRUM:
			return 1;

		// A larger return mean smaller .sf2 file footprint
		// A larger return mean more CPU usage
		// A smaller return means more accurate preview of different note timbres
		// It is a balancing act..

		case BRUSQUE_BASSOON:// short notes
		case BASIC_LUTE:// short notes, differ semi-alot
		case BASIC_HARP:// short notes
		case MISTY_MOUNTAIN_HARP:// short notes
		case LUTE_OF_AGES:// short notes, differ alot
		case TRAVELLERS_TRUSTY_FIDDLE:// short notes, differ alot
		case BASIC_THEORBO:// short notes
		case BASIC_HORN:// The notes in high octave differs a bit
		case BASIC_BAGPIPE:
		case BARDIC_FIDDLE:
		case LONELY_MOUNTAIN_FIDDLE:// differ medium
			return 1;

		case BASIC_FLUTE:// differ medium
		case SPRIGHTLY_FIDDLE:// short notes
			return 2;

		case BASIC_FIDDLE:
		case LONELY_MOUNTAIN_BASSOON:// does not differ alot
			return 2;

		case BASIC_BASSOON:
			return 6;

		case BASIC_CLARINET:// long notes but differ alot plus bad notes
		case BASIC_PIBGORN:// long notes but differ alot plus bad notes
			return 1;

		case STUDENT_FIDDLE:
		case BASIC_COWBELL:
		case MOOR_COWBELL:
		default:
			throw new RuntimeException();
		}
	}

	private static int run(String[] args) throws Exception {
		File sampleDir = new File(args[0]);
		File outputFile = new File(args[1]);

		System.out.println("Sample Directory: " + sampleDir.getCanonicalPath());

		Map<SampleInfo.Key, SampleInfo> samples = new HashMap<>();

		SampleInfo cowbellSample = null;
		SampleInfo moorCowbellSample = null;
		for (File file : sampleDir.listFiles(new ExtensionFileFilter("", false, "wav"))) {
			if (!SampleInfo.isSampleFile(file))
				continue;

			SampleInfo sample = new SampleInfo(file);
			samples.put(sample.key, sample);

			if (cowbellSample == null || sample.key.lotroInstrument == LotroInstrument.BASIC_COWBELL)
				cowbellSample = sample;

			if (moorCowbellSample == null || sample.key.lotroInstrument == LotroInstrument.MOOR_COWBELL)
				moorCowbellSample = sample;
		}

		SortedSet<SampleInfo> usedSamples = new TreeSet<>();
		SortedSet<InstrumentInfo> instruments = new TreeSet<>();
		SortedSet<PresetInfo> presets = new TreeSet<>();
		InstrumentInfo basicFiddleInfo = null;

		for (LotroInstrument li : LotroInstrument.values()) {
			if (li == LotroInstrument.BASIC_COWBELL || li == LotroInstrument.MOOR_COWBELL) {
				SampleInfo sample = (li == LotroInstrument.BASIC_COWBELL) ? cowbellSample : moorCowbellSample;
				CowbellInfo info = new CowbellInfo(sample);
				instruments.add(info);
				usedSamples.add(sample);

				presets.add(new PresetInfo(info));
			} else if (li == LotroInstrument.BASIC_BAGPIPE) {
				StandardInstrumentInfo drones = new StandardInstrumentInfo(li, li + " Drones", li.lowestPlayable.id,
						AbcConstants.BAGPIPE_LAST_DRONE_NOTE_ID, getNotesPerSample(li), samples);
				instruments.add(drones);
				usedSamples.addAll(drones.usedSamples);

				StandardInstrumentInfo bagpipe = new StandardInstrumentInfo(li, li.toString(),
						AbcConstants.BAGPIPE_LAST_DRONE_NOTE_ID + 1, li.highestPlayable.id, getNotesPerSample(li),
						samples);
				instruments.add(bagpipe);
				usedSamples.addAll(bagpipe.usedSamples);

				presets.add(new PresetInfo(drones, bagpipe));
			} else if (li == LotroInstrument.STUDENT_FIDDLE) {
				StandardInstrumentInfo flubs = new StandardInstrumentInfo(li, li + " Flubs", li.lowestPlayable.id,
						AbcConstants.STUDENT_FIDDLE_LAST_FLUB_NOTE_ID, 1, samples);
				instruments.add(flubs);
				usedSamples.addAll(flubs.usedSamples);

				// Share the part of the Basic Fiddle instrument that's playable on the Student's Fiddle
				InstrumentInfoSubrange basicFiddleSubrange = new InstrumentInfoSubrange(li, basicFiddleInfo,
						AbcConstants.STUDENT_FIDDLE_LAST_FLUB_NOTE_ID + 1, li.highestPlayable.id);

				presets.add(new PresetInfo(flubs, basicFiddleSubrange));
			} else {
				StandardInstrumentInfo info = new StandardInstrumentInfo(li, getNotesPerSample(li), samples);
				instruments.add(info);
				usedSamples.addAll(info.usedSamples);

				presets.add(new PresetInfo(info));

				if (li == LotroInstrument.BASIC_FIDDLE)
					basicFiddleInfo = info;
			}
		}

		// OUTPUT

		System.out.println("Writing: " + outputFile.getCanonicalPath());

		try (PrintStream out = new PrintStream(outputFile)) {
			out.println("[Samples]");
			out.println();
			for (SampleInfo sample : usedSamples) {
				sample.print(out);
			}

			out.println();
			out.println("[Instruments]");
			for (InstrumentInfo instrument : instruments) {
				instrument.print(out);
			}

			out.println();
			out.println("[Presets]");
			for (PresetInfo preset : presets) {
				preset.print(out);
				// Also add Lute of Ages as the default instrument (program 0)
				if (preset.lotroInstrument == LotroInstrument.LUTE_OF_AGES)
					preset.print(out, preset.lotroInstrument + "1", 0);
			}

			out.println();
			out.println("[Info]");
			out.println("Version=2.1");
			out.println("Engine=E-mu 10K1");
			out.println("Name=LotroInstruments.sf2");
			out.println("ROMName=");
			out.println("ROMVersion=0.0");
			out.println("Date=" + new SimpleDateFormat("MM/dd/yyyy hh:mm:ss a").format(new Date()));
			out.println("Designer=Digero");
			out.println("Product=Maestro");
			out.println("Copyright=Standing Stone Games");
			out.println("Editor=Digero,Aifel");
			out.println("Comments=");
		}

		return 0;
	}
}
