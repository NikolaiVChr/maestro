package com.digero.common.abctomidi;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;
import javax.sound.midi.Track;

import com.digero.common.midi.Note;

/**
 * Runs {@link AbcToMidi#convert(AbcToMidi.Params)} and renders everything observable about the result as stable,
 * human-readable text: every MIDI event of every track (in track order, which is also the order the sequencer plays
 * same-tick events in), the {@link AbcInfo} metadata, the {@link AbcRegion}s, and WARNING+ log records. On failure,
 * the exception class and message are rendered instead of the result.
 * <p>
 * Deliberately independent of production formatting code (MidiUtils etc.), so refactoring those can't change the
 * snapshots.
 */
final class ConversionDump {
	private ConversionDump() {
	}

	/** Same logger name as AbcToMidi and AbcInfo use. */
	private static final Logger ABC_LOG = Logger.getLogger("import.abc");

	record Result(String error, String sequence, String abcInfo, String regions, String log) {
		String text() {
			if (error != null)
				return error + log;
			return sequence + abcInfo + regions + log;
		}
	}

	/** The complete snapshot text for a case: its ABC, then one section per profile. */
	static String snapshot(AbcCase abcCase) {
		StringBuilder sb = new StringBuilder();
		sb.append("case ").append(abcCase.name()).append('\n');
		// The input, so a snapshot can be read on its own (indented; trailing spaces dropped, which editors strip)
		for (AbcCase.Source source : abcCase.sources()) {
			sb.append("\n== abc ").append(source.fileName()).append('\n');
			for (String line : source.lines())
				sb.append(("  " + line).stripTrailing()).append('\n');
		}
		for (Profile profile : abcCase.profiles()) {
			sb.append("\n### profile ").append(profile).append(" (").append(profile.describe()).append(")\n");
			sb.append(run(abcCase, profile, true, new AbcInfo()).text());
		}
		return sb.toString();
	}

	/** The complete snapshot text for a real ABC file on disk: one section per profile. */
	static String snapshot(File abcFile) {
		StringBuilder sb = new StringBuilder();
		sb.append("file ").append(abcFile.getName()).append('\n');
		for (Profile profile : Profile.values()) {
			sb.append("\n### profile ").append(profile).append(" (").append(profile.describe()).append(")\n");
			sb.append(run(() -> new AbcToMidi.Params(abcFile), profile, p -> {
			}, true, new AbcInfo()).text());
		}
		return sb.toString();
	}

	static Result run(AbcCase abcCase, Profile profile, boolean generateRegions, AbcInfo abcInfo) {
		return run(() -> new AbcToMidi.Params(abcCase.filesData()), profile, abcCase.tweak(), generateRegions,
				abcInfo);
	}

	/** Converts without rendering; for tests that inspect the Sequence directly. */
	static Sequence convert(AbcCase abcCase, Profile profile) throws Exception {
		AbcToMidi.Params params = new AbcToMidi.Params(abcCase.filesData());
		profile.applyTo(params);
		abcCase.tweak().accept(params);
		params.generateRegions = true;
		params.abcInfo = new AbcInfo();
		return AbcToMidi.convert(params);
	}

	@FunctionalInterface
	private interface ParamsFactory {
		AbcToMidi.Params create() throws Exception;
	}

	private static Result run(ParamsFactory factory, Profile profile, java.util.function.Consumer<AbcToMidi.Params> tweak,
			boolean generateRegions, AbcInfo abcInfo) {
		List<String> logLines = new ArrayList<>();
		Handler capture = new Handler() {
			private final SimpleFormatter formatter = new SimpleFormatter();

			@Override
			public void publish(LogRecord logRecord) {
				if (logRecord.getLevel().intValue() >= Level.WARNING.intValue())
					logLines.add(logRecord.getLevel() + ": " + formatter.formatMessage(logRecord));
			}

			@Override
			public void flush() {
			}

			@Override
			public void close() {
			}
		};

		ABC_LOG.addHandler(capture);
		try {
			AbcToMidi.Params params = factory.create();
			profile.applyTo(params);
			tweak.accept(params);
			params.generateRegions = generateRegions;
			params.abcInfo = abcInfo;
			Sequence sequence = AbcToMidi.convert(params);
			return new Result(null, renderSequence(sequence), renderAbcInfo(abcInfo), renderRegions(abcInfo),
					renderLog(logLines));
		} catch (Exception e) {
			return new Result(renderError(e), null, null, null, renderLog(logLines));
		} finally {
			ABC_LOG.removeHandler(capture);
		}
	}

	// ---------------------------------------------------------------- rendering

	private static String renderError(Exception e) {
		return "== EXCEPTION " + e.getClass().getSimpleName() + ": " + e.getMessage() + "\n";
	}

	private static String renderLog(List<String> logLines) {
		if (logLines.isEmpty())
			return "== log: empty\n";
		StringBuilder sb = new StringBuilder("== log\n");
		for (String line : logLines)
			sb.append("  ").append(line.replace("\n", "\\n")).append('\n');
		return sb.toString();
	}

	static String renderSequence(Sequence sequence) {
		StringBuilder sb = new StringBuilder("== sequence\n");
		sb.append("divisionType=").append(sequence.getDivisionType());
		sb.append(" resolution=").append(sequence.getResolution());
		sb.append(" tickLength=").append(sequence.getTickLength());
		sb.append(" tracks=").append(sequence.getTracks().length).append('\n');

		Track[] tracks = sequence.getTracks();
		for (int t = 0; t < tracks.length; t++) {
			Track track = tracks[t];
			sb.append("-- track ").append(t).append(" (").append(track.size()).append(" events)\n");
			for (int i = 0; i < track.size(); i++)
				sb.append("  ").append(renderEvent(track.get(i))).append('\n');
		}
		return sb.toString();
	}

	private static String renderEvent(MidiEvent event) {
		return event.getTick() + " " + renderMessage(event.getMessage());
	}

	private static String renderMessage(MidiMessage message) {
		if (message instanceof ShortMessage sm) {
			String command = switch (sm.getCommand()) {
			case ShortMessage.NOTE_ON -> "NOTE_ON";
			case ShortMessage.NOTE_OFF -> "NOTE_OFF";
			case ShortMessage.CONTROL_CHANGE -> "CONTROL_CHANGE";
			case ShortMessage.PROGRAM_CHANGE -> "PROGRAM_CHANGE";
			case ShortMessage.PITCH_BEND -> "PITCH_BEND";
			default -> String.format("CMD_%02X", sm.getCommand());
			};
			// The message class is included because LotroShortMessage vs ShortMessage matters to the player
			return String.format("%s ch=%d d1=%d d2=%d [%s]", command, sm.getChannel(), sm.getData1(), sm.getData2(),
					sm.getClass().getSimpleName());
		}
		if (message instanceof MetaMessage mm) {
			byte[] data = mm.getData();
			return switch (mm.getType()) {
			case 0x51 -> "META tempo mpq="
					+ (((data[0] & 0xFF) << 16) | ((data[1] & 0xFF) << 8) | (data[2] & 0xFF));
			case 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07 -> String.format("META text type=0x%02X \"%s\"",
					mm.getType(), escape(new String(data, StandardCharsets.UTF_8)));
			case 0x2F -> "META end_of_track";
			default -> String.format("META type=0x%02X data=%s", mm.getType(), hex(data));
			};
		}
		if (message instanceof SysexMessage sx)
			return "SYSEX " + hex(sx.getMessage());
		return message.getClass().getSimpleName() + " " + hex(message.getMessage());
	}

	static String renderAbcInfo(AbcInfo info) {
		StringBuilder sb = new StringBuilder("== abcInfo\n");
		sb.append("  title=\"").append(escape(info.getTitle())).append("\"\n");
		sb.append("  composer=\"").append(escape(info.getComposer())).append("\"\n");
		sb.append("  transcriber=\"").append(escape(info.getTranscriber())).append("\"\n");
		sb.append("  genre=\"").append(escape(info.getGenre())).append("\" mood=\"").append(escape(info.getMood()))
				.append("\"\n");
		sb.append("  primaryTempoBPM=").append(info.getPrimaryTempoBPM());
		sb.append(" timeSignature=").append(stable(info.getTimeSignature()));
		sb.append(" keySignature=").append(stable(info.getKeySignature())).append('\n');
		sb.append("  hasTriplets=").append(info.hasTriplets());
		sb.append(" hasMixTimings=").append(info.hasMixTimings());
		sb.append(" barCount=").append(info.getBarCount());
		sb.append(" partCount=").append(info.getPartCount());
		sb.append(" partSetups=").append(info.getPartSetupsMin()).append("..").append(info.getPartSetupsMax());
		sb.append(" empty=").append(info.isEmpty()).append('\n');

		StringBuilder files = new StringBuilder();
		if (info.getSourceFiles() != null)
			for (File f : info.getSourceFiles())
				files.append(files.isEmpty() ? "" : ", ").append(f.getName());
		sb.append("  sourceFiles=[").append(files).append("]\n");

		for (int i = 0; i < info.getPartCount(); i++) {
			sb.append("  part ").append(i).append(": number=").append(info.getPartNumber(i));
			sb.append(" name=\"").append(escape(info.getPartName(i))).append('"');
			sb.append(" fullName=\"").append(escape(info.getPartFullName(i))).append('"');
			sb.append(" instrument=").append(info.getPartInstrument(i).name());
			sb.append(" madeFor=").append(info.getPartInstrumentFromMadeFor(i));
			sb.append(" userPan=").append(info.getUserPan(i));
			sb.append(" lines=").append(info.getPartStartLine(i)).append("..").append(info.getPartEndLine(i));
			if (i > 0) {
				// Track 0 is the tempo/metadata track and never gets a pan event
				MidiEvent pan = info.getPartPanEvent(i);
				sb.append(" pan=").append(pan == null ? "null" : renderEvent(pan));
			}
			sb.append('\n');
		}
		return sb.toString();
	}

	static String renderRegions(AbcInfo info) {
		NavigableSet<AbcRegion> regions = info.getRegions();
		if (regions == null)
			return "== regions: none\n";

		Map<AbcRegion, Integer> index = new IdentityHashMap<>();
		for (AbcRegion region : regions)
			index.put(region, index.size());

		StringBuilder sb = new StringBuilder("== regions (" + regions.size() + ")\n");
		for (AbcRegion r : regions) {
			sb.append(String.format("  #%d line=%d cols=[%d,%d) ticks=%d..%d %s track=%d bar=%d tiesFrom=%s tiesTo=%s%n",
					index.get(r), r.getLine(), r.getStartIndex(), r.getEndIndex(), r.getStartTick(), r.getEndTick(),
					renderNote(r.getNote()), r.getTrackNumber(), info.tickToBarNumber(r.getStartTick()),
					ref(r.getTiesFrom(), index), ref(r.getTiesTo(), index)));
		}
		return sb.toString();
	}

	private static String renderNote(Note note) {
		if (note == null)
			return "CHORD";
		if (note == Note.REST)
			return "REST";
		return "note=" + note.id;
	}

	/** A reference to another region; regions that were de-duplicated out of the TreeSet are shown by position. */
	private static String ref(AbcRegion region, Map<AbcRegion, Integer> index) {
		if (region == null)
			return "-";
		Integer i = index.get(region);
		if (i != null)
			return "#" + i;
		return "outside(line=" + region.getLine() + ",cols=[" + region.getStartIndex() + "," + region.getEndIndex()
				+ "))";
	}

	/** toString() of a value object; fails loudly instead of letting an identity hash into a snapshot. */
	private static String stable(Object value) {
		if (value == null)
			return "null";
		String s = value.toString();
		if (s.matches(".*@[0-9a-f]{4,}$"))
			throw new IllegalStateException(value.getClass().getName()
					+ " has no toString(); render its fields explicitly in ConversionDump");
		return s;
	}

	private static String escape(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
	}

	private static String hex(byte[] data) {
		StringBuilder sb = new StringBuilder();
		for (byte b : data)
			sb.append(String.format("%02X", b & 0xFF));
		return sb.toString();
	}
}