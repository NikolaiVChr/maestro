package com.digero.common.abc;

import static com.digero.common.abc.AbcCase.concat;
import static com.digero.common.abc.AbcCase.of;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The hand-written inputs for the snapshot tests. Each case aims at one feature or one error path of AbcToMidi, so a
 * failing snapshot points at what changed.
 * <p>
 * These are characterization cases: they record what the code does now, including errors and known quirks. The
 * snapshot is the expectation; nothing here asserts that the current behaviour is correct.
 * <p>
 * To add a case: add it here, run {@code mvn test "-Dabc.golden.update=true"} in powershell, and review the new .golden.txt file.
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

	/** The default header followed by extended %% field lines (still in the header, before the first note). */
	static String[] extended(String... fieldLines) {
		return concat(DEFAULT_HEADER, fieldLines);
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
		// Maestro plays the repeats of a song made from an ABC file (Params.expandRepeats)
		c.add(tune("repeats_expanded", "|: c d |1 e :|2 f |]", "w:a b c", "w:d e * f", "|: g :: a :|")
				.with(p -> p.expandRepeats = true));
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
		c.add(tune("decoration_volume", "+ppp+c d e f !fff!c d e f|"));
		c.add(tune("decoration_first", "!f!c d e f g a b c'|"));
		c.add(tune("decoration_ends_part", "c d e f !trill!g a b c'|", "c d e f g a b c'|"));
		c.add(tune("lyrics", concat(header(), new String[] { "W:", "W:1. Verse one" }), "c d|", "w: la la", "e f|",
				"  w:la-la_ la % comment", "W:2. Verse two"));

		// ------------------------------------------------------------ Lotro limits (interesting in LOTRO_STRICT)
		c.add(tune("lotro_note_too_low", "C,, c|"));
		c.add(tune("lotro_note_too_high", "c'' c|"));
		c.add(tune("lotro_note_too_short", "c/8 c|"));
		c.add(tune("lotro_rest_too_short", "z/8 c|"));
		c.add(tune("lotro_note_8s_then_too_long", "c32 c33|"));
		c.add(tune("lotro_rest_too_long", "z33 c|"));
		c.add(tune("lotro_tied_too_long", "c20-c20|"));
		c.add(tune("lotro_too_many_chord_notes", "[CEGcegc'] c|"));
		c.add(tune("lotro_overlapping_notes", "[c2c] d|"));
		// Tested in Lotro: a sounding note started again at another volume silences the part; a tie continuation doesn't
		c.add(tune("lotro_restart_at_other_volume", "[c2z] +ff+ c d|"));
		c.add(tune("lotro_tie_continuation_at_other_volume", "[c2-z] +ff+ c d|"));
		// 10,001 notes: only the strict profile, otherwise the snapshot would be enormous
		c.add(tune("lotro_too_many_notes", "c/ ".repeat(10_001)).only(Profile.ABC_PLAYER_STRICT));

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
		c.add(tune("cowbell", header("T:Test Cowbell"), "c d e|").only(Profile.MAESTRO_LEGACY));
		c.add(tune("moor_cowbell", header("T:Test Moor Cowbell"), "c d e|").only(Profile.MAESTRO_LEGACY));
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
		// The names are AbcField's: the enum name in lower case with - for _, in any case, then a space and the value.
		// TEMPO is "%%Q:". Maestro writes these; real exported files (AbcToMidiFileSnapshotTest) cover them too.
		c.add(tune("extended_fields", concat(header(),
						new String[] { "%%song-title Custom Title", "%%song-composer Someone", "%%song-transcriber Me",
								"%%part-name Lead", "%%made-for Basic Flute" }),
				"c d|"));
		c.add(tune("extended_tempo_change", "c d|", "%%Q: 90", "e f|"));
		c.add(tune("extended_tempo_change_twice", "c d|", "%%Q: 60", "e|", "%%Q: 240", "f g|"));
		c.add(tune("extended_tempo_before_first_note", extended("%%Q: 60"), "c d|"));
		c.add(tune("extended_tempo_lower_case", "c d|", "%%q: 90", "e f|"));
		c.add(tune("extended_tempo_no_space", "c d|", "%%Q:90", "e f|"));
		c.add(of("extended_tempo_in_second_part", concat(part(1, "Song - One", "c d e f|"),
				part(2, "Song - Two", "c d|", "%%Q: 60", "e f|"))));
		c.add(tune("extended_field_upper_case", extended("%%SONG-TITLE Loud Title", "%%PART-NAME Loud Harp"), "c d|"));
		c.add(tune("extended_unknown_field", extended("%%unknown-field something", "%%MIDI program 1"), "c d|"));

		// part-name: the part's name, and like T: it picks the instrument and a left/right/center pan
		c.add(tune("extended_part_name_instrument", extended("%%part-name Lead Harp"), "c d|"));
		c.add(tune("extended_part_name_wins_over_title", header("T:Test Flute"), "%%part-name Harp", "c d|"));
		c.add(tune("extended_part_name_pan_left", extended("%%part-name Lute left"), "c d|"));
		c.add(tune("extended_part_name_pan_right", extended("%%part-name Lute right"), "c d|"));
		c.add(tune("extended_part_name_pan_center", extended("%%part-name Lute center"), "c d|"));
		c.add(of("extended_part_name_before_title", "X:1", "%%part-name Harp", "T:Test Flute", "M:4/4", "L:1/8",
				"Q:120", "K:C", "c d|"));
		c.add(tune("extended_part_name_empty", extended("%%part-name"), "c d|"));
		c.add(tune("extended_part_name_in_body", "c d|", "%%part-name Harp", "e f|"));
		c.add(of("extended_part_name_per_part", concat(part(1, "Song - One", "%%part-name Harp", "c d|"),
				part(2, "Song - Two", "%%part-name Flute", "e f|"))));

		// made-for: the instrument, stronger than part-name and T:
		c.add(tune("extended_made_for_before_part_name", extended("%%made-for Basic Flute", "%%part-name Harp"),
				"c d|"));
		c.add(tune("extended_made_for_after_part_name", extended("%%part-name Harp", "%%made-for Basic Flute"),
				"c d|"));
		c.add(tune("extended_made_for_unknown_instrument", extended("%%made-for Kazoo"), "c d|"));
		c.add(tune("extended_made_for_in_body", "c d|", "%%made-for Basic Harp", "e f|"));

		// user-pan: 0..127 or auto; stronger than the pan from part-name
		c.add(tune("extended_user_pan", extended("%%user-pan 30"), "c d|"));
		c.add(tune("extended_user_pan_too_high", extended("%%user-pan 200"), "c d|"));
		c.add(tune("extended_user_pan_too_low", extended("%%user-pan -5"), "c d|"));
		c.add(tune("extended_user_pan_not_a_number", extended("%%user-pan abc"), "c d|"));
		c.add(tune("extended_user_pan_decimal", extended("%%user-pan 64.5"), "c d|"));
		c.add(tune("extended_user_pan_auto_after_part_name", extended("%%part-name Lute left", "%%user-pan auto"),
				"c d|"));
		c.add(tune("extended_user_pan_before_part_name", extended("%%user-pan 100", "%%part-name Lute left"), "c d|"));

		// swing-rhythm sets hasTriplets; without it, hasTriplets is guessed from the note lengths
		c.add(tune("extended_swing_rhythm_true", extended("%%swing-rhythm true"), "c d e f|"));
		c.add(tune("extended_swing_rhythm_false_with_triplet", extended("%%swing-rhythm false"), "(3cde f|"));
		c.add(tune("extended_swing_rhythm_not_a_boolean", extended("%%swing-rhythm yes"), "(3cde f|"));
		c.add(tune("triplet_guess_q90", header("Q:90"), "(3cde f|"));
		c.add(tune("triplet_guess_q100", header("Q:100"), "(3cde f|"));
		c.add(tune("triplet_guess_after_tempo_change", "c d|", "%%Q: 100", "e f|"));
		c.add(tune("extended_mix_timings", extended("%%mix-timings true"), "c d|"));
		c.add(tune("extended_organic_version_not_a_number", extended("%%organic-version two"), "c d|"));

		// Quirks, recorded as they are today
		c.add(tune("extended_field_with_colon_is_ignored", extended("%%song-title: Colon Title"), "c d|"));
		c.add(tune("extended_tempo_without_colon_is_ignored", "c d|", "%%Q 60", "e f|"));
		c.add(tune("extended_tempo_not_a_number_is_ignored", "c d|", "%%Q: fast", "e f|"));
		c.add(tune("extended_tempo_zero_is_ignored", "c d|", "%%Q: 0", "e f|"));
		c.add(tune("extended_escaped_percent_is_kept", extended("%%song-title 100\\% Harp"), "c d|"));
		c.add(tune("extended_percent_is_not_a_comment", extended("%%part-name Lute % left"), "c d|"));

		// 1: a tuplet must apply to every note of its last chord
		c.add(tune("tuplet_ends_on_chord", "(3c d[eg] c|"));
		c.add(tune("tuplet_ends_on_chord_different_lengths", "(3c d[e2g] c|"));
		c.add(tune("tuplet_p_q_r_ends_on_chord", "(3:2:2c[e2g2] c|"));
		c.add(tune("tuplet_ends_on_chord_with_rest", "(3c d[ez] c|"));

		// 2: a tie joins the next note of the same pitch, wherever it is, for the sum of their lengths (tested in Lotro)
		c.add(tune("tie_over_other_note", "c- d c|"));
		c.add(tune("tie_over_other_chord", "[c-e] [dg] c|"));
		c.add(tune("tie_continuation_starts_early", "[c2-z] c d|"));
		c.add(tune("tie_long_note_in_rest_chord", "[c2-z]z c d|"));
		c.add(tune("tie_over_other_note_next_line", "c- d", "c|"));

		// 3: a bar line resets the accidental, so after the bar the continuation must repeat it (tested in Lotro)
		c.add(tune("tie_accidental_across_bar", "^c-|c d|"));
		c.add(tune("tie_accidental_across_bar_then_same_note", "^c-|c c|"));
		c.add(tune("tie_accidental_across_bar_explicit_natural", "^c-|=c d|"));
		c.add(tune("tie_flat_across_bar", "_B-|B c|"));
		c.add(tune("tie_accidental_across_bar_in_chord", "[^c-e]|[ce] d|"));
		c.add(tune("tie_key_signature_across_bar", header("K:D"), "f-|f d|"));
		// Lotro's reading, which Maestro follows: an accidental lasts until the bar line, so a continuation after
		// the bar must repeat it. Within the measure it may be repeated or not.
		c.add(tune("tie_accidental_same_bar", "^c-c d|"));
		c.add(tune("tie_accidental_retyped_same_bar", "^c-^c d|"));
		c.add(tune("tie_accidental_retyped_across_bar", "^c-|^c d|"));

		// 4: % starts a comment in info fields too (ABC 2.1); \% is a literal percent
		c.add(tune("percent_in_title", header("T:100% Harp"), "c d|"));
		c.add(tune("escaped_percent_in_title", header("T:100\\% Harp"), "c d|"));
		c.add(tune("escaped_percent_in_composer", concat(header(), new String[] { "C:50\\% Me % comment" }), "c d|"));

		// 5: Lotro's length limits apply to the written length, not the played one (tested in Lotro)
		c.add(tune("lotro_tuplet_too_short", "(3c/4d/4e/4 c|"));
		c.add(tune("lotro_tuplet_long_enough", "(3c/2d/2e/2 c|"));
		c.add(tune("lotro_broken_rhythm_too_short", "c>>>d/ c|"));
		c.add(tune("lotro_broken_rhythm_too_long", "c24>c8|"));

		// 6: every part starts from the file header's K:, M: and L: (before the first X:), not from the previous part.
		// An M: in a header without its own L: gives the default length. (Tested in Lotro.)
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
		// A part without M: gets the file header's meter, not the previous part's 6/8: an error, pointing at its X:
		c.add(of("part_meter_denominator_from_file_header", concat(
				new String[] { "X:1", "T:One", "M:6/8", "L:1/8", "Q:120", "K:C", "c d|" },
				new String[] { "X:2", "T:Two", "Q:120", "K:C", "c d|" })));

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

		// Large length numbers (fine L: denominators). Every note is well under 8 s; the products of the numbers
		// used to overflow int, giving negative lengths.
		c.add(tune("large_l_plain", header("L:1/2834674"), "c1417337 d1417337 e2834674|"));
		c.add(tune("large_l_broken_rhythm", header("L:1/2834674"), "c1417337>>>d1417337 e1417337|"));
		c.add(tune("large_l_fast_tempo", header("L:1/2834674", "Q:1000"), "c5669348 d5669348|"));
		c.add(tune("large_l_tuplet_fast_tempo", header("L:1/909090", "Q:400"), "(3c454545d454545e454545 c454545>>>d454545|"));

		// ------------------------------------------------------------ ABC 2.1 syntax (added 2026-09-26/27)
		// Standard ABC with Lotro errors off; with them on, a Lotro error for what Lotro refuses (tested in game, B1-B37)

		// Repeats (Maestro: Params.expandRepeats). Without it they play once, as in Lotro.
		c.add(tune("repeats_ending_list", "|: c [1,3 d :|[2 e :|] f|").with(p -> p.expandRepeats = true));
		c.add(tune("repeats_ending_range", "|: c [1-2 d :|[3 e |] f|").with(p -> p.expandRepeats = true));
		c.add(tune("repeats_ending_list_not_expanded", "|: c [1,3 d :|[2 e :|] f|"));
		c.add(tune("repeats_end_without_start", "c d :| e f|").with(p -> p.expandRepeats = true));
		c.add(tune("repeats_end_start_variants", "|: c :|: d :||: e :|] f|").with(p -> p.expandRepeats = true));
		c.add(tune("repeats_endings_across_lines", "|: c d |1 e", "f :|2 g", "a |]").with(p -> p.expandRepeats = true));
		c.add(tune("repeats_from_mid_line", "c |: d e :| f|").with(p -> p.expandRepeats = true));
		// Verse 1 is sung on every pass; verses 2 and more are text lines after the last note (ABC 2.1, 5.2)
		c.add(tune("repeats_verses", "|: c d e f :|", "w:one two three four", "w:five six sev-en")
				.with(p -> p.expandRepeats = true));

		// Inline fields
		c.add(tune("inline_key", "f f [K:G] f f|"));
		c.add(tune("inline_note_length", "c d [L:1/16] e f g a|"));
		c.add(tune("inline_meter", "c d e f|[M:3/4] g a b|"));
		c.add(tune("inline_part_and_instruction", "c [P:A] d [I:linebreak $] e f|"));
		c.add(tune("inline_unclosed", "c [K:G d|"));

		// Broken rhythm and chords (broken_before_chord above)
		c.add(tune("broken_after_chord", "[ce]>d e|"));

		// Layout, decorations and rests that change nothing (or stop Lotro)
		c.add(tune("score_line_break", "c d $ e f|"));
		c.add(tune("back_quote_in_beam", "c`d`e f|"));
		c.add(tune("invisible_bar", "c d [|] e f|"));
		c.add(tune("dotted_bar", "c d .| e f|"));
		c.add(tune("symbol_line", "c d e f|", "s:!f! * * *"));
		c.add(tune("letter_decorations", "Tc Hd Le Mf Og Pa Sb uc vd|"));
		c.add(tune("plus_decorations", "+fermata+c +accent+d +trill+e|"));
		c.add(tune("plus_old_chord", "+ceg+ c|"));
		c.add(tune("multi_measure_rest", "Z c|Z2 d|"));
		c.add(tune("multi_measure_rest_3_4", header("M:3/4"), "Z c|"));

		// Header fields
		c.add(tune("tempo_word", header("Q:\"Allegro\""), "c d e f|"));
		c.add(tune("tempo_word_with_value", header("Q:\"Allegro\" 1/4=100"), "c d e f|"));
		c.add(tune("tempo_word_unknown", header("Q:\"Fast-ish\""), "c d e f|"));
		c.add(tune("meter_none", header("M:none"), "c d e f g a b c'|"));
		// The Q: note length is the beat (ABC 2.1, 3.1.8) with Params.specTempo (a Maestro project setting); Lotro,
		// and Maestro without it, take the meter's denominator whatever it says (A15)
		c.add(tune("tempo_compound_beat", header("M:6/8", "Q:3/8=120"), "c d e f g a|").with(p -> p.specTempo = true));
		c.add(tune("tempo_compound_beat_without_spec_tempo", header("M:6/8", "Q:3/8=120"), "c d e f g a|"));
		c.add(tune("tempo_cut_time_quarter_beat", header("M:2/2", "Q:1/4=120"), "c d e f|").with(p -> p.specTempo = true));
		c.add(tune("tempo_several_beats", header("M:5/4", "Q:1/4 3/8 1/4 3/8=40"), "c d e f g|")
				.with(p -> p.specTempo = true));
		c.add(tune("tempo_compound_default", header("M:6/8", "-Q"), "c d e f g a|").with(p -> p.specTempo = true));
		c.add(of("tempo_before_meter", "X:1", "T:Test", "Q:3/8=120", "M:6/8", "L:1/8", "K:C", "c d e f g a|")
				.with(p -> p.specTempo = true));
		c.add(tune("field_continuation", "c d e f|", "w:one two", "+:three four"));
		c.add(tune("field_continuation_w_upper", concat(header(), new String[] { "W:A verse that", "+:goes on" }),
				"c d|"));
		// Standard pitch (Params.standardPitch, Maestro with standard ABC): C is middle C, and T: names no instrument
		c.add(tune("standard_pitch", header("T:The Flute Player"), "C c|").with(p -> p.standardPitch = true));
		c.add(tune("standard_pitch_made_for", extended("%%made-for Basic Flute"), "C c|")
				.with(p -> p.standardPitch = true));

		// Chord symbols as an accompaniment (Params.chordAccompaniment, Maestro): bass and chords tracks
		c.add(tune("chord_accompaniment_waltz", header("M:3/4", "L:1/4"), "\"G\"G B d|\"D7\"c A F|\"G\"G3|]")
				.with(p -> p.chordAccompaniment = true));
		c.add(tune("chord_accompaniment_jig", header("M:6/8"), "|:\"D\"d2f fed|\"G\"B2d \"A7\"cBA:|")
				.with(p -> { p.chordAccompaniment = true; p.expandRepeats = true; }));
		c.add(tune("chord_accompaniment_bass_and_text", header("L:1/4"), "\"C\"c \"a.\"d \"G/B\"e \"^text\"f|\"Fine\"c4|]")
				.with(p -> p.chordAccompaniment = true));
		// One chord a bar of 2/4: the bass on beat 1, the chord's notes on beat 2. "Cxyz" isn't a chord: Eb continues.
		c.add(tune("chord_accompaniment_qualities", header("M:2/4", "L:1/4"),
				"\"Cm\"c c|\"C7\"c c|\"Cmaj7\"c c|\"Cm7\"c c|\"Cdim\"c c|\"Cdim7\"c c|\"Cm7b5\"c c|\"Caug\"c c|",
				"\"Csus4\"c c|\"Csus2\"c c|\"C6\"c c|\"C5\"c c|\"F#m\"c c|\"Bb7\"c c|\"Eb\"c c|\"Cxyz\"c c|]")
				.with(p -> p.chordAccompaniment = true));
		// A chord chart: chords over invisible rests (x), no notes of its own
		c.add(tune("chord_accompaniment_chart_x", header("L:1/4"), "\"G\"x4|\"D7\"x4|\"G\"x2 \"C\"x2|\"G\"x4|]")
				.with(p -> p.chordAccompaniment = true));
		// A chord chart: chords over rests (z), no notes of its own
		c.add(tune("chord_accompaniment_chart_z", header("L:1/4"), "\"G\"z4|\"D7\"z4|\"G\"z2 \"C\"z2|\"G\"z4|]")
				.with(p -> p.chordAccompaniment = true));
		// A hymn: full chords (bass and chord together, held). Also a quick chord (D7 for a beat) in the jig above.
		c.add(tune("chord_accompaniment_hymn", header("T:Evening Hymn", "M:3/4", "L:1/4"),
				"\"G\"G2 B|\"C\"c2 A|\"G\"B \"D7\"A F|\"G\"G3|]").with(p -> p.chordAccompaniment = true));
		// Only the first part has chords: its bass and chords tracks come after both parts
		c.add(of("chord_accompaniment_two_parts", concat(part(1, "Song - Tune", "\"G\"G2 B2 \"C\"c2 \"D\"d2|"),
				part(2, "Song - Second", "B,2 D2 E2 F2|"))).with(p -> p.chordAccompaniment = true));

		// K: with clef and transposition (ABC 2.1, 4.6)
		c.add(tune("key_clef_bass", header("K:C clef=bass middle=d"), "c d|"));
		c.add(tune("key_transpose", header("K:C transpose=2"), "c d|"));
		c.add(tune("key_octave", header("K:C octave=-1"), "c d|"));
		c.add(tune("key_treble_minus_8", header("K:C treble-8"), "c d|"));
		c.add(tune("key_none", header("K:none"), "f c|"));
		c.add(tune("key_empty", header("K:"), "f c|"));
		c.add(tune("key_highland_pipes", header("K:HP"), "f c g|"));
		c.add(tune("key_mode_after_space", header("K:D mix"), "f c|"));
		c.add(tune("key_explicit_accidentals", header("K:G ^c"), "c d|"));
		c.add(tune("key_clef_only_keeps_key", header("K:G"), "f|", "K:bass", "f|"));
		c.add(tune("key_treble_after_treble_minus_8", header("K:G treble-8"), "f|", "K:treble", "f|"));

		// A tie after a chord ties every note in it (ABC 2.1, 4.11 and 4.17); Lotro refuses it (B65)
		c.add(tune("tie_after_chord1", "[ce]2- [ce]2 [df]2- [df]2|"));
		c.add(tune("tie_after_chord2", "[c2e2]- [c2e2] [d2f2]- [d2f2]|"));

		// Grace notes (grace_notes above): 65 ms on the beat, at most half the note; not played with Lotro errors
		c.add(tune("grace_notes_several", "{gfe}c2 d|"));
		c.add(tune("grace_notes_lengths", "{g2a}c4 {g>a}c4|"));
		c.add(tune("grace_notes_slash", "{/g}c d|"));
		c.add(tune("grace_notes_before_chord", "{g}[ce] d|"));
		c.add(tune("grace_notes_before_rest", "{g}z c|"));
		c.add(tune("grace_notes_short_note", "{g}c/4 d|"));
		c.add(tune("grace_notes_accidental", "{^f}f f|"));
		c.add(tune("grace_notes_rest_inside", "{Z}c d|"));
		// ! as a score line break (A25): older ABC without %abc-2.1, e.g. pipe collections; with standard2011 only
		c.add(tune("bang_line_break", "{g}A2 B2|  !", "c2 d2|]  !"));
		c.add(tune("bang_line_break_std2011", "{g}A2 B2|  !", "c2 d2|]  !").with(p -> p.standard2011 = true));
		c.add(of("bang_line_break_strict_std2011", "%abc-2.1", "X:1", "T:t", "M:4/4", "L:1/8", "Q:120", "K:C", "A2 B2|  !",
				"c2 d2|]").with(p -> p.standard2011 = true));
		// A slur over grace notes (Village Music Project): layout only; with Lotro errors an error (untested)
		c.add(tune("grace_notes_slurred", "B2 {(B/c/B/^A/)} Be B2|"));

		// Lyrics
		c.add(tune("lyrics_tied_note", "c-c d e|", "w:one two three"));
		c.add(tune("lyrics_word_across_lines", "c d|", "w:hel-", "e f|", "w:lo you"));
		c.add(tune("lyrics_text_escapes", "c d e f|", "w:caf\\'e na\\\"ive &eacute;t&eacute; \\u00e9"));

		// Not ABC 2.1: a tie apart from its note (Nottingham Music Database); Lotro refuses it too
		c.add(tune("tie_after_space", "c2 -c2 d|"));

		// ------------------------------------------------------------ Params.standard2011 (added 2026-09-29)
		// Where Lotro plays ABC otherwise than ABC 2.1 says (task list F2, F3). NOT IMPLEMENTED YET: today the _std2011
		// snapshots are the same as their twins without the flag, the reading of Lotro. When it is implemented only
		// the _std2011 snapshots change (and the broken rhythm with chords without the flag: Lotro's reading, F2l).
		// Each has a twin without the flag, here or above.

		// F2a: a chord lasts as long as its first note (4.17); Lotro: its shortest (chord_shortest_note_wins above)
		c.add(tune("chord_first_note_longer", "[c2e] g [ce2] g|"));
		c.add(tune("chord_first_note_longer_std2011", "[c2e] g [ce2] g|").with(p -> p.standard2011 = true));
		c.add(tune("chord_shortest_note_wins_std2011", "[c2e] [ce2] [c/e2] c|").with(p -> p.standard2011 = true));
		// F2b: a unison sounds both notes (4.17); one MIDI channel can't, so the longer one plays. Lotro: the first.
		c.add(tune("chord_unison_variants", "[cc2] z2 [c2c] z2 [^c_d2] z2|"));
		c.add(tune("chord_unison_variants_std2011", "[cc2] z2 [c2c] z2 [^c_d2] z2|").with(p -> p.standard2011 = true));
		// F2d, no difference after all: ABC 2.1 (4.11) ties two notes of the same pitch, and the bar line ends the
		// accidental, so ^c-|c doesn't connect in either reading (as in Lotro). The twins show that they agree.
		c.add(tune("tie_accidental_across_bar_std2011", "^c-|c d|").with(p -> p.standard2011 = true));
		c.add(tune("tie_accidental_across_bar_then_same_note_std2011", "^c-|c c|").with(p -> p.standard2011 = true));
		c.add(tune("tie_key_signature_across_bar_std2011", header("K:D"), "=f-|f d|").with(p -> p.standard2011 = true));
		// F2e: an accidental applies in every octave to the bar line (11.3, %%propagate-accidentals pitch). Lotro:
		// in its own octave only (tested, B66).
		c.add(tune("accidentals_per_octave_std2011", "^c c' C c|").with(p -> p.standard2011 = true));
		c.add(tune("accidentals_other_octave_next_bar", "^c|c' ^c|c,|"));
		c.add(tune("accidentals_other_octave_next_bar_std2011", "^c|c' ^c|c,|").with(p -> p.standard2011 = true));
		c.add(tune("accidentals_key_natural_other_octave", header("K:D"), "=f F f|f F|"));
		c.add(tune("accidentals_key_natural_other_octave_std2011", header("K:D"), "=f F f|f F|")
				.with(p -> p.standard2011 = true));
		c.add(tune("accidentals_double_sharp_other_octave", "^^c c' C|"));
		c.add(tune("accidentals_double_sharp_other_octave_std2011", "^^c c' C|").with(p -> p.standard2011 = true));
		// F2f: Q:120 without a note length counts unit notes (L:), 10.1. Lotro: beats of the meter's denominator. By
		// Params.specTempo, not standard2011: the _std2011 twins stay as Lotro's reading; MAESTRO_NEW_STANDARD has both.
		c.add(tune("tempo_without_length", "c8|"));
		c.add(tune("tempo_without_length_std2011", "c8|").with(p -> p.standard2011 = true));
		c.add(tune("tempo_c_equals", header("Q:C=120"), "c8|"));
		c.add(tune("tempo_c_equals_std2011", header("Q:C=120"), "c8|").with(p -> p.standard2011 = true));
		c.add(tune("tempo_without_length_l_1_16", header("L:1/16"), "c16|"));
		c.add(tune("tempo_without_length_l_1_16_std2011", header("L:1/16"), "c16|").with(p -> p.standard2011 = true));
		c.add(tune("tempo_without_length_l_1_4_std2011", header("L:1/4"), "c4|").with(p -> p.standard2011 = true));
		c.add(tune("tempo_without_length_6_8_std2011", header("M:6/8"), "c6|").with(p -> p.standard2011 = true));
		c.add(tune("tempo_77_std2011", header("Q:77"), "c d e f|").with(p -> p.standard2011 = true));
		c.add(tune("tempo_with_note_length_std2011", header("Q:1/4=100"), "c d e f|").with(p -> p.standard2011 = true));
		// F2g: a blank line ends the tune (2.2.1); what follows up to the next X: is free text, and so is text before
		// the first X: (2.2). Lotro plays on after a blank line (B14).
		c.add(tune("blank_line_then_notes", "c d|", "", "e f|"));
		c.add(tune("blank_line_then_notes_std2011", "c d|", "", "e f|").with(p -> p.standard2011 = true));
		c.add(tune("blank_line_then_free_text", "c d|", "", "Notes: play it slowly."));
		c.add(tune("blank_line_then_free_text_std2011", "c d|", "", "Notes: play it slowly.")
				.with(p -> p.standard2011 = true));
		c.add(tune("blank_lines_std2011", "cd|", "", "   ", "ef|").with(p -> p.standard2011 = true));
		String[] book = concat(new String[] { "These are my tunes.", "(c) 2026 Me", "" }, part(1, "One", "c d|"),
				new String[] { "", "Played at every session.", "" }, part(2, "Two", "e f|"));
		c.add(of("free_text_around_tunes", book));
		c.add(of("free_text_around_tunes_std2011", book).with(p -> p.standard2011 = true));
		// The usual tune book: a blank line between the tunes
		String[] blankBetween = concat(part(1, "One", "c d|"), new String[] { "" }, part(2, "Two", "e f|"));
		c.add(of("parts_blank_line_between", blankBetween));
		c.add(of("parts_blank_line_between_std2011", blankBetween).with(p -> p.standard2011 = true));
		// F2l: a chord takes a broken rhythm like a note (4.4). Lotro halves only the chord's first note in c>[ce],
		// and refuses [ce]>d.
		c.add(tune("broken_before_chord_std2011", "c>[ce]|").with(p -> p.standard2011 = true));
		c.add(tune("broken_after_chord_std2011", "[ce]>d e|").with(p -> p.standard2011 = true));
		c.add(tune("broken_chord_variants", "c>[ce] d [ce]<d f [ce]>>d f|"));
		c.add(tune("broken_chord_variants_std2011", "c>[ce] d [ce]<d f [ce]>>d f|").with(p -> p.standard2011 = true));
		c.add(tune("broken_between_chords", "[ce]>[df] e|"));
		c.add(tune("broken_between_chords_std2011", "[ce]>[df] e|").with(p -> p.standard2011 = true));
		c.add(tune("broken_after_chord_with_length", "[c2e2]>d f [ce]2<d f|"));
		c.add(tune("broken_after_chord_with_length_std2011", "[c2e2]>d f [ce]2<d f|").with(p -> p.standard2011 = true));
		// F3a: !p! !f! (ABC 2.1) set the volume like +p+ +f+ (4.14). Lotro skips them without Lotro errors.
		c.add(tune("dynamics_bang_all", "!pppp!c !ppp!c !pp!c !p!c !mp!c !mf!c !f!c !ff!c !fff!c !ffff!c|"));
		c.add(tune("dynamics_bang_all_std2011", "!pppp!c !ppp!c !pp!c !p!c !mp!c !mf!c !f!c !ff!c !fff!c !ffff!c|")
				.with(p -> p.standard2011 = true));
		c.add(tune("decoration_volume_std2011", "+ppp+c d e f !fff!c d e f|").with(p -> p.standard2011 = true));
		// F3b: accent and staccato are played (4.14); how much is to decide. Lotro: nothing.
		c.add(tune("accent_and_staccato", "!accent!c Ld !>!e .f !staccato!g !tenuto!a b c'|"));
		c.add(tune("accent_and_staccato_std2011", "!accent!c Ld !>!e .f !staccato!g !tenuto!a b c'|")
				.with(p -> p.standard2011 = true));
		c.add(tune("staccato_on_chord_and_tie", ".[ce] .c-c d|"));
		c.add(tune("staccato_on_chord_and_tie_std2011", ".[ce] .c-c d|").with(p -> p.standard2011 = true));
		// F3c: Q: may change the tempo mid-tune, on a line or inline (3.1.8). Without the flag: an error.
		c.add(tune("tempo_change_mid_part_std2011", "c d|", "Q:90", "e f|").with(p -> p.standard2011 = true));
		c.add(tune("inline_tempo_change", "c d [Q:60] e f|"));
		c.add(tune("inline_tempo_change_std2011", "c d [Q:60] e f|").with(p -> p.standard2011 = true));
		c.add(tune("tempo_change_with_length_std2011", "c d|", "Q:1/4=60", "e f|").with(p -> p.standard2011 = true));
		// F3d: the meter's denominator may change mid-tune. Without the flag: an error.
		c.add(tune("meter_change_other_denominator_std2011", "c d|", "M:6/8", "e f|").with(p -> p.standard2011 = true));
		c.add(tune("inline_meter_other_denominator", "c d e f|[M:6/8] g a b|"));
		c.add(tune("inline_meter_other_denominator_std2011", "c d e f|[M:6/8] g a b|").with(p -> p.standard2011 = true));
		// F3e: K: with explicit accidentals (3.1.14). Without the flag: an error (A12).
		c.add(tune("key_explicit_accidentals_std2011", header("K:G ^c"), "c f|").with(p -> p.standard2011 = true));
		c.add(tune("key_exp", header("K:D exp _b"), "f c B|"));
		c.add(tune("key_exp_std2011", header("K:D exp _b"), "f c B|").with(p -> p.standard2011 = true));
		c.add(tune("key_mode_and_accidentals_std2011", header("K:Am ^g"), "g G a|").with(p -> p.standard2011 = true));
		// F3f: A{g}<A is the same as A<{g}A (4.12). Without the flag: an error.
		c.add(tune("grace_between_note_and_broken", "c{g}<d e|"));
		c.add(tune("grace_between_note_and_broken_std2011", "c{g}<d e|").with(p -> p.standard2011 = true));
		c.add(tune("grace_after_broken", "c<{g}d e|"));
		c.add(tune("grace_after_broken_std2011", "c<{g}d e|").with(p -> p.standard2011 = true));
		// F2c: kept in every mode: a tie joins the next note of its pitch wherever it is
		c.add(tune("tie_over_other_note_std2011", "c- d c|").with(p -> p.standard2011 = true));
		// The flag and standard pitch together, as Maestro sets them
		c.add(tune("standard_pitch_and_2011", "[C2E] G ^C C' C, C|").with(p -> {
			p.standardPitch = true;
			p.standard2011 = true;
		}));

		// ------------------------------------------------------------ ornaments (A31)
		// Played in steps of GRACE_NOTE_SECONDS, from the note's pitch; a chord or a rest plays plain. Lotro refuses
		// T M P and !...!, plays nothing of a part with +trill+, and plays ~ plain.
		c.add(tune("ornament_trill_lengths", "Tc4 Tc2 Tc Tc/|"));
		c.add(tune("ornament_all_names", "!trill!c2 !roll!d2 !lowermordent!e2 !mordent!f2 !uppermordent!g2",
				"!pralltriller!a2 !turn!b2 !invertedturn!c'2|"));
		c.add(tune("ornament_plus_form", "+trill+c2 +mordent+d2 +turn+e2 +invertedturn+f2|"));
		c.add(tune("ornament_roll_tilde", "~c2 ~d ~e4|"));
		c.add(tune("ornament_mordent_letters", "Mc2 Pd2 Te2 f2|"));
		c.add(tune("ornament_on_chord", "T[ce]2 !turn![df]2 c2|"));
		c.add(tune("ornament_on_rest", "Tz2 c2|"));
		c.add(tune("ornament_with_accidental", "T^c2 c2 !mordent!_B2 B2|"));
		c.add(tune("ornament_in_key", header("K:D"), "Tf2 !turn!c2 Mb2|"));
		c.add(tune("ornament_on_tied_note", "Tc2-c2 d2|"));
		c.add(tune("ornament_in_tuplet", "(3Tcde c|"));
		c.add(tune("ornament_and_grace_notes", "{g}Tc2 T{g}d2|"));
		c.add(tune("ornament_broken_rhythm", "Tc>d !turn!e<f|"));
		c.add(tune("ornament_lotro_range", "TC,2 Tc'2|"));
		c.add(tune("ornament_unknown_skipped", "!wiggle!c2 +arpeggio+d2|"));
		c.add(tune("ornament_fast_tempo", header("Q:300"), "Tc/ Td/ Te|"));
		c.add(tune("ornament_two_on_one_note", "T!turn!c2 d|"));
		c.add(tune("ornament_at_end_of_line", "c T", "d2|"));
		c.add(tune("ornament_in_skipped_ending", "|: c |1 Td :|2 !turn!e |]").with(p -> p.expandRepeats = true));

		// ------------------------------------------------------------ grace notes, more
		c.add(tune("grace_notes_in_tuplet", "(3{g}cde c|"));
		c.add(tune("grace_notes_before_tie_continuation", "c2-{g}c2 d|"));
		c.add(tune("grace_notes_octaves", "{C,c'}c2 d|"));
		c.add(tune("grace_notes_at_end_of_line", "c {g}", "d|"));
		c.add(tune("grace_notes_tie_inside", "{g-}c d|"));
		c.add(tune("grace_notes_long_group", "{gfedcBA}c4 d|"));
		c.add(tune("grace_notes_lotro_range", "{C,,}c d|"));
		c.add(tune("grace_notes_unclosed", "{gc d|"));
		c.add(tune("grace_notes_empty", "{} c|"));
		c.add(tune("grace_notes_rest_z", "{z}c|"));
		c.add(tune("grace_notes_broken_at_end", "{g>}c|"));
		c.add(tune("grace_notes_in_chord", "[{g}ce]|"));
		c.add(tune("grace_notes_before_bar", "c {g}|d|"));
		c.add(tune("grace_notes_fast_tempo", header("Q:300"), "{g}c/ {a}d/ e|"));

		// ------------------------------------------------------------ Lotro errors not covered above
		// y: a spacer, layout only (a width after it, y6, is abcm2ps, not ABC 2.1: A26). Lotro refuses it (B58).
		c.add(tune("spacer_y", "c y d y e|"));
		// Lotro's beat is the meter's denominator (B15): Q:1/8=120 in 4/4 is a Lotro error
		c.add(tune("tempo_length_not_the_denominator", header("Q:1/8=120"), "c d e f|"));
		c.add(tune("tempo_half_in_cut_time", header("M:2/2", "Q:1/2=60"), "c d e f|"));
		c.add(tune("inline_field_lower_case", "c [k:G] f|"));
		c.add(tune("inline_remark", "c [r:a remark] d|"));
		c.add(tune("inline_field_in_chord", "[c[K:G]e]|"));

		// ------------------------------------------------------------ information fields (A28)
		// Written as MidiText header lines (@...) in track 0
		c.add(tune("info_all_labels", concat(header(), new String[] { "A:Area", "B:Book", "C:Composer", "D:Record",
				"F:http://example.com/tune.abc", "G:flute", "H:History", "N:A note", "O:Scotland", "R:Reel", "S:Source",
				"Z:Me" }), "c d|"));
		c.add(tune("info_history_lines", concat(header(), new String[] { "H:Written in 1800", "H:in Scotland",
				"C:Someone", "H:A second history" }), "c d|"));
		c.add(tune("info_two_composers", concat(header(), new String[] { "C:One", "C:Two" }), "c d|"));
		c.add(tune("info_continuation", concat(header(), new String[] { "C:Someone", "+:and another" }), "c d|"));
		c.add(tune("info_maestro_notes_hidden", concat(header(), new String[] { "N:TS 2, 1 2", "N:genre: folk",
				"N:mood: calm", "N:A real note" }), "c d|"));
		c.add(of("info_titles", "X:1", "T:Main Title", "T:Other Title", "T:Third", "M:4/4", "L:1/8", "Q:120", "K:C",
				"c d|"));
		c.add(tune("info_fields_in_body", "c d|", "N:A note in the tune", "C:Late composer", "e f|"));
		c.add(tune("info_text_escapes", concat(header(), new String[] { "C:Andr\\'e & Jos\\\"e", "Z:caf&eacute; \\u00e9" }),
				"c d|"));
		c.add(tune("info_verses_before_and_after", concat(header(), new String[] { "W:Before the notes" }), "c d|",
				"e f|", "W:After the notes", "W:", "W:Second verse"));
		c.add(of("info_per_part", concat(part(1, "One", "C:First composer", "c d|"),
				part(2, "Two", "C:Second composer", "e f|"))));

		// ------------------------------------------------------------ fields and directives that aren't played
		// r: a remark on a line of its own, skipped in every mode (Lotro plays on, B68). Errors today, all ABC 2.1:
		// U: symbols in the notes (A35), m: macros (A36), a ! line break after I:linebreak ! (A25)
		c.add(tune("user_defined_symbol", concat(header(), new String[] { "U:W=!trill!" }), "Wc2 d|"));
		c.add(tune("info_remark_line", "c d|", "r:a remark", "e f|"));
		c.add(tune("macro_field", concat(header(), new String[] { "m:~n2 = (3o/n/m/ n" }), "c d|"));
		c.add(tune("instruction_linebreak_bang", concat(header(), new String[] { "I:linebreak !" }), "c d!e f|"));
		c.add(tune("propagate_accidentals_directive", extended("%%propagate-accidentals not"), "^c c' c|"));
		c.add(of("abc_version_line", concat(new String[] { "%abc-2.1" }, header(), new String[] { "c d|" })));
		c.add(of("key_before_other_fields", "X:1", "T:Test", "K:D", "M:4/4", "L:1/8", "Q:120", "f c|"));
		c.add(tune("directive_in_body", "c d|", "%%MIDI program 73", "e f|"));

		// ------------------------------------------------------------ MIDI program of standard ABC (A30)
		// Only with standard pitch (MidiProgramGuess); Lotro files keep their Lotro instrument's program
		c.add(tune("midi_program_directive", extended("%%MIDI program 73"), "C D|").with(p -> p.standardPitch = true));
		c.add(tune("midi_program_directive_lotro_file", extended("%%MIDI program 73"), "C D|"));
		c.add(tune("midi_program_instruction", concat(header(), new String[] { "I:MIDI program 40" }), "C D|")
				.with(p -> p.standardPitch = true));
		c.add(tune("midi_program_instruction_equals", concat(header(), new String[] { "I:MIDI= program 40" }), "C D|")
				.with(p -> p.standardPitch = true));
		c.add(tune("midi_program_of_channel", extended("%%MIDI channel 2", "%%MIDI program 2 71"), "C D|")
				.with(p -> p.standardPitch = true));
		c.add(tune("midi_program_of_other_channel", extended("%%MIDI program 2 71"), "C D|")
				.with(p -> p.standardPitch = true));
		c.add(tune("midi_program_out_of_range", extended("%%MIDI program 200"), "C D|").with(p -> p.standardPitch = true));
		c.add(tune("midi_channel_out_of_range", extended("%%MIDI channel 17"), "C D|").with(p -> p.standardPitch = true));
		c.add(tune("midi_program_last_directive_wins", extended("%%MIDI program 73", "%%MIDI program 40"), "C D|")
				.with(p -> p.standardPitch = true));
		c.add(tune("midi_drums_channel_10", extended("%%MIDI channel 10"), "C,, D,, C,, D,, F,, F,,|")
				.with(p -> p.standardPitch = true));
		c.add(tune("midi_drums_with_kit", extended("%%MIDI channel 10", "%%MIDI program 25"), "C,, D,,|")
				.with(p -> p.standardPitch = true));
		c.add(tune("midi_drums_chord_symbols_skipped", extended("%%MIDI channel 10"), "\"G\"C,, D,, \"C\"C,, D,,|")
				.with(p -> {
					p.standardPitch = true;
					p.chordAccompaniment = true;
				}));
		c.add(tune("midi_bassprog_chordprog", extended("%%MIDI bassprog 33", "%%MIDI chordprog 0"), "\"G\"G2 B2 \"C\"c4|")
				.with(p -> {
					p.standardPitch = true;
					p.chordAccompaniment = true;
				}));
		c.add(tune("midi_bassprog_with_octave", extended("%%MIDI bassprog 33 octave=-1"), "\"G\"G2 B2 c4|")
				.with(p -> {
					p.standardPitch = true;
					p.chordAccompaniment = true;
				}));
		c.add(of("midi_program_file_header", concat(new String[] { "%%MIDI program 73" }, part(1, "One", "C D|"),
				part(2, "Two", "%%MIDI program 40", "E F|"))).with(p -> p.standardPitch = true));
		c.add(tune("midi_program_highland_pipes", header("K:HP"), "F C G|").with(p -> p.standardPitch = true));
		c.add(tune("midi_program_voice_name", concat(header(), new String[] { "V:1 name=\"Violin\"" }), "C D|")
				.with(p -> p.standardPitch = true));
		c.add(tune("midi_program_group", concat(header(), new String[] { "G:flute" }), "C D|")
				.with(p -> p.standardPitch = true));
		c.add(tune("midi_program_title_for", header("T:Air for the harp"), "C D|").with(p -> p.standardPitch = true));
		c.add(tune("midi_program_first_name_wins", concat(header(), new String[] { "G:fiddle", "V:1 nm=Flute" }), "C D|")
				.with(p -> p.standardPitch = true));
		c.add(tune("midi_program_directive_beats_name", concat(header(), new String[] { "G:fiddle",
				"%%MIDI program 73" }), "C D|").with(p -> p.standardPitch = true));
		c.add(tune("midi_program_made_for_wins", extended("%%MIDI program 73", "%%made-for Basic Harp"), "C D|")
				.with(p -> p.standardPitch = true));
		c.add(tune("midi_program_in_body_ignored", "C D|", "%%MIDI program 73", "E F|")
				.with(p -> p.standardPitch = true));
		// The tune's type in R: (A41), the weakest clue; the file header's for every part, a part's own wins
		c.add(tune("midi_program_rhythm", header("R:Slip Jig"), "C D|").with(p -> p.standardPitch = true));
		c.add(of("midi_program_rhythm_file_header", concat(new String[] { "R:reel" }, part(1, "One", "C D|"),
				part(2, "Two", "R:hornpipe", "E F|"))).with(p -> p.standardPitch = true));

		// Cowbells (A37), Maestro only (the ABC Player's pitch is random): one pitch without standard pitch (as
		// before), the written pitch with it
		c.add(tune("cowbell_chord", extended("%%made-for Basic Cowbell"), "[ce] g c'|")
				.only(Profile.MAESTRO_LEGACY, Profile.MAESTRO_NEW_LOTRO, Profile.MAESTRO_NEW_STANDARD));
		c.add(tune("moor_cowbell_chord", extended("%%made-for Moor Cowbell"), "[ce] g c'|")
				.only(Profile.MAESTRO_LEGACY, Profile.MAESTRO_NEW_LOTRO, Profile.MAESTRO_NEW_STANDARD));

		// ------------------------------------------------------------ voices (A13), as they are read today
		c.add(tune("voices_in_body", "V:1", "c d e f|", "V:2", "C D E F|"));
		c.add(tune("voices_inline", "[V:1] c d e f|", "[V:2] C D E F|"));
		c.add(tune("voices_defined_in_header", concat(header(), new String[] { "V:1 name=\"Fiddle\"",
				"V:2 name=\"Bass\" clef=bass" }), "V:1", "c d e f|", "V:2", "C, D, E, F,|"));
		c.add(tune("voices_with_lyrics", "V:1", "c d|", "w:one two", "V:2", "e f|", "w:three four"));

		// ------------------------------------------------------------ parts order (A16), as they are read today
		c.add(tune("parts_order", concat(header(), new String[] { "P:ABA" }), "P:A", "c d|", "P:B", "e f|"));
		c.add(tune("parts_order_expanded", concat(header(), new String[] { "P:ABA" }), "P:A", "c d|", "P:B", "e f|")
				.with(p -> p.expandRepeats = true));
		c.add(tune("parts_order_repeat_count", concat(header(), new String[] { "P:A2B" }), "P:A", "c d|", "P:B",
				"e f|").with(p -> p.expandRepeats = true));
		c.add(tune("parts_order_group", concat(header(), new String[] { "P:(AB)2" }), "P:A", "c d|", "P:B", "e f|")
				.with(p -> p.expandRepeats = true));
		c.add(tune("parts_order_dots", concat(header(), new String[] { "P:A.B.A" }), "P:A", "c d|", "P:B", "e f|")
				.with(p -> p.expandRepeats = true));
		c.add(tune("parts_order_missing_section", concat(header(), new String[] { "P:ABC" }), "P:A", "c d|", "P:B",
				"e f|").with(p -> p.expandRepeats = true));
		c.add(tune("parts_order_music_before_first_p", concat(header(), new String[] { "P:AB" }), "g a|", "P:A",
				"c d|", "P:B", "e f|").with(p -> p.expandRepeats = true));
		c.add(tune("parts_order_with_repeats", concat(header(), new String[] { "P:AB" }), "P:A", "|: c d :|", "P:B",
				"e f|").with(p -> p.expandRepeats = true));

		// ------------------------------------------------------------ lyrics, more
		c.add(tune("lyrics_rests_and_graces", "c z {g}d e|", "w:one two three"));
		c.add(tune("lyrics_star_skips_a_note", "c d e f|", "w:one * three four"));
		c.add(tune("lyrics_bar_syncs", "c d|e f|", "w:one|three four"));
		c.add(tune("lyrics_underscore_holds", "c d e f|", "w:one__ four"));
		c.add(tune("lyrics_tilde_and_escaped_hyphen", "c d e|", "w:one~word two\\-three four"));
		c.add(tune("lyrics_on_chords", "[ce] d [df]|", "w:one two three"));
		c.add(tune("lyrics_more_syllables_than_notes", "c d|", "w:one two three four"));
		c.add(tune("lyrics_before_notes", "w:early words", "c d|"));
		c.add(tune("lyrics_with_ornaments", "Tc2 d2|", "w:one two"));

		// ------------------------------------------------------------ dynamics, more
		// Lotro takes +pppp+ and +ffff+ too
		c.add(tune("dynamics_plus_extremes", "+pppp+c +ffff+d +mf+e|"));
		c.add(tune("dynamics_hairpins", "!crescendo(!c d !crescendo)!e !<(!f !<)!g !diminuendo(!a !>)!b c'|"));
		c.add(tune("dynamics_bang_in_chord", "[!f!ce] d|"));
		c.add(tune("dynamics_before_bar", "c d +f+|e f|"));
		c.add(tune("dynamics_across_repeat", "|: +p+c d +f+e f :|").with(p -> p.expandRepeats = true));

		// ------------------------------------------------------------ chords and ties, more
		c.add(tune("chord_tie_partial_continuation", "[ce]-[cg] d|"));
		c.add(tune("chord_tie_across_bar", "[ce]-|[ce] d|"));
		c.add(tune("chord_tie_into_single_note", "[ce]-c d|"));
		c.add(tune("tie_across_repeat", "|: c2- :| c2 d|").with(p -> p.expandRepeats = true));
		c.add(tune("tie_into_ending", "|: c |1 d- :|2 d e|]").with(p -> p.expandRepeats = true));
		c.add(tune("tie_to_grace_note", "c-{c}c d|"));
		c.add(tune("tie_in_tuplet", "(3c d e- e|"));
		c.add(tune("tuplet_with_broken_rhythm", "(3c>d e f|"));

		// ------------------------------------------------------------ repeats, more
		c.add(tune("repeats_nested_start", "|: c |: d :| e :|").with(p -> p.expandRepeats = true));
		c.add(tune("repeats_endings_without_repeat", "c |1 d |2 e |]").with(p -> p.expandRepeats = true));
		c.add(tune("repeats_colons_at_line_start", "|: c d", ":: e f :|").with(p -> p.expandRepeats = true));
		c.add(tune("repeats_three_endings", "|: c [1 d :|[2 e :|[3 f |]").with(p -> p.expandRepeats = true));
		c.add(tune("repeats_start_only", "|: c d|e f|").with(p -> p.expandRepeats = true));
		c.add(tune("repeats_multi_measure_rest", "|: Z c :|").with(p -> p.expandRepeats = true));

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