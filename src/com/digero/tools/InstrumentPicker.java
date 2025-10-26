package com.digero.tools;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;
import java.util.Scanner;

import com.digero.common.abc.LotroInstrument;

public class InstrumentPicker {
	@SuppressWarnings("resource")
	public static void main(String[] args) {
		File root = new File("F:\\Games\\LOTRO\\u16\\ogg");
		File sourceRoot = root;
		File instrumentsRoot = new File(root, "instruments");
		File notInstruments = new File(root, "not_instruments");
		File maybe = new File(root, "maybe_instruments");

		Map<LotroInstrument, File> dirs = new EnumMap<>(LotroInstrument.class);

		StringBuilder pickString = new StringBuilder("[0] Replay");
		final int REPLAY = 0;
		int i = 0;
		LotroInstrument[] instruments = LotroInstrument.values();
		for (LotroInstrument instrument : instruments) {
			File dir = new File(instrumentsRoot, instrument.name().toLowerCase());

			dirs.put(instrument, dir);

			i++;
			pickString.append(", ");
			if (i == 8)
				pickString.append("\n");
			pickString.append("[").append(i).append("] ").append(instrument);
		}

		i++;
		pickString.append(", [").append(i).append("] Nothing");
		final int NOT_INSTRUMENT = i;

		i++;
		pickString.append(", [").append(i).append("] Maybe");
		final int MAYBE_INSTRUMENT = i;

		for (File file : sourceRoot.listFiles()) {
			if (file.isDirectory())
				continue;

			int playCount = 0;
			while (true) {
				playCount++;
				System.out.print(pickString + ": ");
				int duration = 400;
				if (playCount == 2)
					duration = 1000;
				else if (playCount > 2)
					duration = 5000;
				AudioPlayer.playAudioFile(file, duration);
				String input = new Scanner(System.in).nextLine();

				int val;
				if (input.isEmpty()) {
					val = NOT_INSTRUMENT;
				} else {
					try {
						val = Integer.parseInt(input);
					} catch (NumberFormatException e) {
						continue;
					}
				}

				File targetDir = null;

				if (val == REPLAY)
					continue;
				else if (val == NOT_INSTRUMENT)
					targetDir = notInstruments;
				else if (val == MAYBE_INSTRUMENT)
					targetDir = maybe;
				else if (val > 0 && val <= instruments.length)
					targetDir = dirs.get(instruments[val - 1]);

				if (targetDir != null) {
					if (!targetDir.exists())
						targetDir.mkdirs();
					File destFile = new File(targetDir, file.getName());
					file.renameTo(destFile);// TODO: this will return false if failed, its ignored. Remember to fix.
					System.out.println(destFile);
					break;
				}
			}
		}
	}
}
