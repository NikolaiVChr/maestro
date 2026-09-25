package com.digero.common.abc;

import static com.digero.common.abc.AbcCase.concat;
import static com.digero.common.abc.AbcCase.of;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.digero.common.abc.LotroInstrument;

/**
 * The hand-written inputs for the snapshot tests. Each case aims at one feature or one error path of AbcToMidi, so a
 * failing snapshot points at what changed.
 * <p>
 * These are characterization cases: they record what the code does now, including errors and known quirks. The
 * snapshot is the expectation; nothing here asserts that the current behaviour is correct.
 * <p>
 * To add a case: add it here, run {@code mvn test -Dabc.golden.update=true}, and review the new .golden.txt file.
 */
final class AbcCases {
	private AbcCases() {
	}

	/** The default header; see {@link #header(String...)}. */
	private static final String[] DEFAULT_HEADER = { "X:1", "T:Test", "M:4/4", "L:1/8", "Q:120", "K:C" };

	/**
	 * The default header with fields replaced or removed. "M:6/8" replaces the M: line, "-L" removes the L: line.
	 * Order of the default header is kept.
	 */
	static String[] header(String... overrides) {
		Map<Character, String> fields = new LinkedHashMap<>();
		for (String line : DEFAULT_HEADER)
			fields.put(line.charAt(0), line);
		for (String override : overrides) {
			if (override.startsWith("-"))
				fields.remove(override.charAt(1));
			else
				fields.put(override.charAt(0), override);
		}
		return fields.values().toArray(String[]::new);
	}

	/** Default header followed by the body lines. */
	static AbcCase tune(String name, String... body) {
		return of(name, concat(DEFAULT_HEADER, body));
	}

	/** A modified header followed by the body lines. */
	static AbcCase tune(String name, String[] header, String... body) {
		return of(name, concat(header, body));
	}

	/** One part (X: to notes) with the given number and title. */
	static String[] part(int number, String title, String... body) {
		return concat(new String[] { "X:" + number, "T:" + title, "M:4/4", "L:1/8", "Q:120", "K:C" }, body);
	}

	static List<AbcCase> all() {
		List<AbcCase> c = new ArrayList<>();

		// ------------------------------------------------------------ header fields
		c.add(tune("notes_basic", "CDEF GABc|"));
		c.add(of("no_x_line", "T:Test", "M:4/4", "L:1/8", "Q:120", "K:C", "c d e|"));
		c.add(of("no_header_at_all", "c d e|"));
		c.add(of("only_header_no_notes", DEFAULT_HEADER));
		c.add(of("x_not_a_number", "X:abc", "T:Test", "K:C", "c d|"));
		c.add(tune("title_inside_part", "c d|", "T:Late title", "e f|"));
		c.add(of("title_twice_in_header", "X:1", "T:First", "T:Second", "K:C", "c d|"));

		c.add(tune("meter_2_4_default_l", header("M:2/4", "-L"), "c d e f|"));
		c.add(tune("meter_3_4_default_l", header("M:3/4", "-L"), "c d e f|"));
		c.add(tune("meter_6_8_default_l", header("M:6/8", "-L"), "c d e f|"));
		c.add(tune("meter_common_time", header("M:C"), "c d e f|"));
		c.add(tune("meter_cut_time", header("M:C|"), "c d e f|"));
		c.add(tune("meter_and_l_absent", header("-M", "-L"), "c d e f|"));
		c.add(tune("meter_invalid", header("M:4"), "c d|"));
		c.add(tune("meter_change_same_denominator", "c d|", "M:3/4", "e f|"));
		c.add(tune("meter_change_other_denominator", "c d|", "M:6/8", "e f|"));

		c.add(tune("note_length_1_4", header("L:1/4"), "c d e f|"));
		c.add(tune("note_length_3_8", header("L:3/8"), "c d e f|"));
		c.add(tune("note_length_1_16", header("L:1/16"), "c d e f|"));
		c.add(tune("note_length_zero", header("L:0/8"), "c d|"));
		c.add(tune("note_length_no_slash", header("L:1"), "c d|"));
		c.add(tune("note_length_change_mid_part", "c d|", "L:1/4", "e f|"));

		c.add(tune("tempo_with_note_length", header("Q:1/4=100"), "c d e f|"));
		c.add(tune("tempo_77", header("Q:77"), "c d e f|"));
		c.add(tune("tempo_zero", header("Q:0"), "c d|"));
		c.add(tune("tempo_garbage", header("Q:fast"), "c d|"));
		c.add(tune("tempo_twice_before_notes", concat(header(), new String[] { "Q:90" }), "c d|"));
		c.add(tune("tempo_change_mid_part", "c d|", "Q:90", "e f|"));

		c.add(tune("key_d_major", header("K:D"), "f c F C|"));
		c.add(tune("key_b_flat", header("K:Bb"), "B e b E|"));
		c.add(tune("key_a_minor", header("K:Am"), "A c e g|"));
		c.add(tune("key_d_mixolydian", header("K:Dmix"), "f c F C|"));
		c.add(tune("key_invalid", header("K:H"), "c d|"));
		c.add(tune("key_change_mid_part", "f c|", "K:D", "f c|"));

		c.add(tune("info_fields_ignored", concat(header(), new String[] { "C:Composer", "Z:Transcribed by Me",
				"N:genre: folk", "N:mood: happy", "N: TS 2, 1 2" }), "c d|"));

		// ------------------------------------------------------------ notes, rests, lengths
		c.add(tune("notes_octaves", "C, C c c'|"));
		c.add(tune("notes_octaves_extreme", "C,,, C,, c'' c'''|"));
		c.add(tune("notes_lengths", "c2 c/ c// c/4 c3/2 c3 c4 c8|"));
		c.add(tune("notes_lengths_odd", "c/3 c2/3 c5/4 c3/8 c16|"));
		c.add(tune("notes_double_slash_with_digit", "c//3 d|"));
		c.add(tune("notes_no_spaces", "cdefgab|"));
		c.add(tune("notes_many_lines", "c d|", "e f|", "g a|"));
		c.add(tune("rests", "z z2 z/ x x2 c|"));
		c.add(tune("rest_with_accidental", "^z c|"));
		c.add(tune("rest_with_octave", "z' c|"));

		// ------------------------------------------------------------ accidentals
		c.add(tune("accidentals", "^c c _c c =c c ^^c __c|"));
		c.add(tune("accidentals_reset_at_bar", "^c c | c ^c|| c|"));
		c.add(tune("accidentals_per_octave", "^c c' C c|"));
		c.add(tune("accidentals_with_key", header("K:D"), "f F =f f | f|"));
		c.add(tune("accidentals_across_lines", "^c c", "c|"));

		// ------------------------------------------------------------ broken rhythm
		c.add(tune("broken_gt", "c>d c>>d c>>>d|"));
		c.add(tune("broken_lt", "c<d c<<d c<<<d|"));
		c.add(tune("broken_with_lengths", "c2>d2 c/<d/|"));
		c.add(tune("broken_with_rests", "z>c c<z|"));
		c.add(tune("broken_chained", "c>d>e|"));
		c.add(tune("broken_at_line_end", "c>", "d|"));
		c.add(tune("broken_before_chord", "c>[ce]|"));
		c.add(tune("broken_inside_chord", "[c>e]|"));
		c.add(tune("broken_and_tie", "c>-c|"));

		// ------------------------------------------------------------ tuplets
		c.add(tune("tuplet_3", "(3cde c|"));
		c.add(tune("tuplet_2", "(2cd c|"));
		c.add(tune("tuplet_4", "(4cdef c|"));
		c.add(tune("tuplet_5_simple_meter", "(5cdefg c|"));
		c.add(tune("tuplet_5_compound_meter", header("M:6/8"), "(5cdefg c|"));
		c.add(tune("tuplet_p_q_r", "(3:2:2c2d c|"));
		c.add(tune("tuplet_p_r", "(3::2c2d c|"));
		c.add(tune("tuplet_mixed_lengths", "(3c2de c|"));
		c.add(tune("tuplet_with_rest", "(3czd c|"));
		c.add(tune("tuplet_over_chords", "(3[ce][df][eg] c|"));
		c.add(tune("tuplet_over_chords_with_lengths", "(3[c2e2][d2f2][e2g2] c|"));
		c.add(tune("tuplet_across_bar", "(3c|de c|"));
		c.add(tune("tuplet_unfinished", "(3cd|"));
		c.add(tune("tuplet_nested", "(3c(3def|"));
		c.add(tune("tuplet_p_too_small", "(1cd|"));
		c.add(tune("tuplet_p_too_large", "(10cd|"));

		// ------------------------------------------------------------ chords
		c.add(tune("chord_basic", "[ceg] [C2E2G2] [g/b/c'/]|"));
		c.add(tune("chord_shortest_note_wins", "[c2e] [ce2] [c/e2] c|"));
		c.add(tune("chord_single_note", "[c] d|"));
		c.add(tune("chord_with_rest", "[cz] d|"));
		c.add(tune("chord_seven_notes", "[CEGcegb] c|"));
		c.add(tune("chord_accidentals", "[^ce] c [_eg] e|"));
		c.add(tune("chord_same_note_twice", "[cc] c|"));
		c.add(tune("chord_whitespace_inside", "[c e] c|"));
		c.add(tune("chord_bar_inside", "[c|e] c|"));
		c.add(tune("chord_unclosed", "[ce"));
		c.add(tune("chord_close_without_open", "c] d|"));
		c.add(tune("chord_nested", "[[ce]] d|"));
		c.add(tune("chord_slur_inside", "[(ce)] d|"));
		c.add(tune("chord_dynamics_inside", "[+f+ce] d|"));
		c.add(tune("chord_empty", "[] c|"));
		c.add(tune("chord_length_suffix_fraction", "[ceg]3/4 c|"));
		c.add(tune("chord_length_suffix_integer", "[ceg]2 c|"));
		c.add(tune("chord_length_suffix_mixed", "[c2eg]3/4 c|"));
		c.add(tune("chord_length_suffix_short", "[c2eg]/ [abf]// c|"));

		// ------------------------------------------------------------ ties
		c.add(tune("tie_simple", "c-c d|"));
		c.add(tune("tie_across_bar", "c2-|c2 d|"));
		c.add(tune("tie_across_lines", "c2-", "c2 d|"));
		c.add(tune("tie_chain", "c-c-c d|"));
		c.add(tune("tie_to_other_note", "c-d e|"));
		c.add(tune("tie_unconnected_at_end", "c d-"));
		c.add(tune("tie_with_accidental", "^c-c d|"));
		c.add(tune("tie_in_chord", "[c-e-][ce] d|"));
		c.add(tune("tie_partial_chord", "[c-e][cg] d|"));
		c.add(tune("tie_into_rest", "c-z c|"));
		c.add(tune("tie_long", "c8-c8-c8|"));
		c.add(of("tie_into_next_part", concat(part(1, "One", "c d-"), part(2, "Two", "d e|"))));

		// ------------------------------------------------------------ bars
		c.add(tune("bars_variants", "c|d||e|]f|:g:|a::|b:::|c|"));
		c.add(tune("bar_colon_without_pipe", "c:d|"));
		c.add(tune("bar_bracket_pipe", "[|c d|"));
		c.add(tune("bars_many", "c d|e f|g a|b c'|c' b|a g|"));

		// ------------------------------------------------------------ decorations and other syntax
		c.add(tune("dynamics_all", "+ppp+c +pp+c +p+c +mp+c +mf+c +f+c +ff+c +fff+c|"));
		c.add(tune("dynamics_unknown", "+trill+c|"));
		c.add(tune("dynamics_unclosed", "+f c|"));
		c.add(tune("slurs", "(cd) (c d)e|"));
		c.add(tune("slur_close_only", "c) d|"));
		c.add(tune("backslash", "cd\\", "ef|"));
		c.add(tune("comments", "cd % comment", "% full line comment", "ef|"));
		c.add(tune("blank_lines", "cd|", "", "   ", "ef|"));
		c.add(tune("tabs", "c\td\te|"));
		c.add(tune("unknown_character", "c ! d|"));
		c.add(tune("guitar_chord_text", "\"C\"c d|"));
		c.add(tune("grace_notes", "{g}c d|"));

		// ------------------------------------------------------------ LotRO limits (interesting in LOTRO_STRICT)
		c.add(tune("lotro_note_too_low", "C,, c|"));
		c.add(tune("lotro_note_too_high", "c'' c|"));
		c.add(tune("lotro_note_too_short", "c/8 c|"));
		c.add(tune("lotro_rest_too_short", "z/8 c|"));
		c.add(tune("lotro_note_8s_then_too_long", "c32 c33|"));
		c.add(tune("lotro_rest_too_long", "z33 c|"));
		c.add(tune("lotro_tied_too_long", "c20-c20|"));
		c.add(tune("lotro_too_many_chord_notes", "[CEGcegc'] c|"));
		c.add(tune("lotro_overlapping_notes", "[c2c] d|"));
		// 10,001 notes: only the strict profile, otherwise the snapshot would be enormous
		c.add(tune("lotro_too_many_notes", "c/ ".repeat(10_001)).only(Profile.LOTRO_STRICT));

		// ------------------------------------------------------------ instruments
		String[] instrumentParts = concat(
				part(1, "Test Harp", "C, C c c'|"),
				part(2, "Test Bagpipe", "C, C c c'|"),
				part(3, "Test Flute", "C, C c c'|"),
				part(4, "Test Theorbo", "C, C c c'|"),
				part(5, "Test Drum", "C, C c c'|"),
				part(6, "Test Student Fiddle", "C, C c c'|"),
				part(7, "Test Misty Mountain Harp", "C, C c c'|"),
				part(8, "Test Clarinet", "C, C c c'|"));
		c.add(of("instruments_by_title", instrumentParts));
		c.add(of("instruments_stereo_0", instrumentParts).with(p -> p.stereo = 0));
		c.add(of("instruments_stereo_50", instrumentParts).with(p -> p.stereo = 50));
		c.add(tune("instrument_override", header("T:Test Harp"), "c d e f|")
				.with(p -> p.instrumentOverrideMap = Map.of(1, LotroInstrument.BASIC_FLUTE)));
		// Cowbell notes are randomized when useLotroInstruments is on, unless tied
		c.add(tune("cowbell", header("T:Test Cowbell"), "c d e|").only(Profile.PLAIN_MIDI));
		c.add(tune("moor_cowbell", header("T:Test Moor Cowbell"), "c d e|").only(Profile.PLAIN_MIDI));
		c.add(tune("cowbell_tied", header("T:Test Cowbell"), "c-c|"));

		// ------------------------------------------------------------ parts and files
		c.add(of("parts_two", concat(part(1, "Song - One", "c d|"), part(2, "Song - Two", "e f|"))));
		c.add(of("parts_eleven", parts(11)));
		c.add(of("parts_seventeen", parts(17)));
		c.add(of("parts_numbers_out_of_order", concat(part(5, "Five", "c d|"), part(2, "Two", "e f|"))));
		c.add(of("parts_same_number", concat(part(1, "A", "c d|"), part(1, "B", "e f|"))));
		c.add(of("parts_different_length",
				concat(part(1, "One", "c d e f g|"), part(2, "Two", "c|"))));
		c.add(of("parts_different_tempo", concat(part(1, "One", "c d|"),
				new String[] { "X:2", "T:Two", "M:4/4", "L:1/8", "Q:100", "K:C", "e f|" })));
		c.add(of("parts_different_meter_denominator", concat(part(1, "One", "c d|"),
				new String[] { "X:2", "T:Two", "M:6/8", "L:1/8", "Q:120", "K:C", "e f|" })));
		c.add(of("parts_different_note_length", concat(part(1, "One", "c d|"),
				new String[] { "X:2", "T:Two", "M:4/4", "L:1/16", "Q:120", "K:C", "e f|" })));
		c.add(of("parts_key_carries_over", concat(
				new String[] { "X:1", "T:One", "M:4/4", "L:1/8", "Q:120", "K:D", "f c|" },
				new String[] { "X:2", "T:Two", "f c|" })));
		c.add(of("multiple_files", part(1, "Song - One", "c d|"))
				.plusFile("second.abc", part(2, "Song - Two", "e f|")));

		// ------------------------------------------------------------ extended %% fields
		// The field names are best guesses; if AbcField doesn't know one, the snapshot records that it was ignored.
		// Real exported files (AbcToMidiFileSnapshotTest) cover these properly.
		c.add(tune("extended_fields", concat(header(),
				new String[] { "%%song-title Custom Title", "%%song-composer Someone", "%%song-transcriber Me",
						"%%part-name Lead", "%%made-for Basic Flute" }),
				"c d|"));
		c.add(tune("extended_tempo_change", "c d|", "%%Q: 90", "e f|"));

		// 1: a tuplet must apply to every note of its last chord
		c.add(tune("tuplet_ends_on_chord", "(3c d[eg] c|"));
		c.add(tune("tuplet_ends_on_chord_different_lengths", "(3c d[e2g] c|"));
		c.add(tune("tuplet_p_q_r_ends_on_chord", "(3:2:2c[e2g2] c|"));
		c.add(tune("tuplet_ends_on_chord_with_rest", "(3c d[ez] c|"));

		// 2: a tie must continue on the very next note of that pitch, starting where the tied note ends
		c.add(tune("tie_over_other_note", "c- d c|"));
		c.add(tune("tie_over_other_chord", "[c-e] [dg] c|"));
		c.add(tune("tie_continuation_starts_early", "[c2-z] c d|"));
		c.add(tune("tie_long_note_in_rest_chord", "[c2-z]z c d|"));
		c.add(tune("tie_over_other_note_next_line", "c- d", "c|"));

		// 3: a note tied across a bar line keeps its accidental; only that note
		c.add(tune("tie_accidental_across_bar", "^c-|c d|"));
		c.add(tune("tie_accidental_across_bar_then_same_note", "^c-|c c|"));
		c.add(tune("tie_accidental_across_bar_explicit_natural", "^c-|=c d|"));
		c.add(tune("tie_flat_across_bar", "_B-|B c|"));
		c.add(tune("tie_accidental_across_bar_in_chord", "[^c-e]|[ce] d|"));
		c.add(tune("tie_key_signature_across_bar", header("K:D"), "f-|f d|"));

		// 4: % starts a comment in info fields too (ABC 2.1); \% is a literal percent
		c.add(tune("percent_in_title", header("T:100% Harp"), "c d|"));
		c.add(tune("escaped_percent_in_title", header("T:100\\% Harp"), "c d|"));
		c.add(tune("escaped_percent_in_composer", concat(header(), new String[] { "C:50\\% Me % comment" }), "c d|"));

		// 5: the LotRO length limits apply to the played length, including tuplets and broken rhythm
		c.add(tune("lotro_tuplet_too_short", "(3c/4d/4e/4 c|"));
		c.add(tune("lotro_tuplet_long_enough", "(3c/2d/2e/2 c|"));
		c.add(tune("lotro_broken_rhythm_too_short", "c>>>d/ c|"));
		c.add(tune("lotro_broken_rhythm_too_long", "c24>c8|"));

		// 6: each X: part starts from the file header, not from the previous part
		c.add(of("part_length_does_not_carry_over", concat(
				new String[] { "X:1", "T:One", "M:4/4", "L:1/4", "Q:120", "K:C", "c d|" },
				new String[] { "X:2", "T:Two", "c d|" })));
		c.add(of("part_meter_does_not_carry_over", concat(
				new String[] { "X:1", "T:One", "M:2/4", "Q:120", "K:C", "c d|" },
				new String[] { "X:2", "T:Two", "c d|" })));
		c.add(of("file_header_applies_to_all_parts", concat(
				new String[] { "M:4/4", "L:1/4", "Q:120", "K:D" },
				new String[] { "X:1", "T:One", "f c|" },
				new String[] { "X:2", "T:Two", "K:C", "f c|" },
				new String[] { "X:3", "T:Three", "f c|" })));
		c.add(of("file_header_per_file", "X:1", "T:One", "M:4/4", "L:1/8", "Q:120", "K:D", "f c|")
				.plusFile("second.abc", "X:2", "T:Two", "M:4/4", "L:1/8", "Q:120", "f c|"));

		// 8: input that used to be accepted silently
		c.add(tune("note_length_zero_numerator", "c0 d|"));
		c.add(tune("note_length_zero_denominator", "c/0 d|"));
		c.add(tune("note_length_too_large", "c99999999999 d|"));
		c.add(tune("backslash_mid_line", "c\\d|"));
		c.add(tune("backslash_end_of_line_with_spaces", "cd\\  ", "ef|"));
		c.add(tune("chord_same_note_enharmonic", "[^c_d] e|"));
		c.add(tune("chord_rest_only", "[z] c|"));
		c.add(tune("chord_rest_sets_length_overlap", "[c2z] c d|"));

		// Misleading error messages
		c.add(tune("tuplet_at_end_of_line", "c (3", "cde|"));
		c.add(tune("tuplet_ratio_at_end_of_line", "c (3:2", "cde|"));
		c.add(tune("meter_invalid_number", header("M:a/4"), "c d|"));
		c.add(tune("note_length_invalid_number", header("L:1/x"), "c d|"));

		return c;
	}

	/** n minimal parts; for channel allocation and the part-count limit. */
	private static String[] parts(int n) {
		List<String> lines = new ArrayList<>();
		for (int i = 1; i <= n; i++)
			lines.addAll(List.of(part(i, "Part " + i, "c d|")));
		return lines.toArray(String[]::new);
	}
}