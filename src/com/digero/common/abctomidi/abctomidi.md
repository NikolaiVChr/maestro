# ABC to MIDI

Package `com.digero.common.abctomidi`. It reads ABC text and turns it into a MIDI `Sequence`, for the ABC Player and
for Maestro.

## Two ways to read a file

ABC is read in one of two ways, and nearly every rule in the parser depends on which.

- **Lotro's reading.** For files made for Lotro, and for every project made before this version. Notes play the way
  Lotro plays them, even where that differs from the ABC standard.
- **The standard reading (ABC 2.1).** Only for a *new* Maestro project from a file that is *not* made for Lotro, such as
  a folk tune book. Repeats are played out, voices play together, chord symbols get an accompaniment, and so on.

On top of either reading, **Lotro errors** can be switched on (the ABC Player's setting). Then anything Lotro refuses,
or plays in silence, or stops at, is an error that says what Lotro does (`LotroFileParseException`). The messages use
the words of the in-game tests: "refuses", "in silence (no error)", "stops playing".

Release rule: once released, changing how a file that loads today is read needs a new flag, so old projects keep
their sound. Fixing something that is an error today needs no flag.

## How to call it

All entry points are static methods of `AbcToMidi`.

| Method | What it does |
|---|---|
| `convert(Params)` | Reads the ABC and returns the `Sequence`. Also fills `params.abcInfo`. |
| `parseAbcMetadata(files)` | Titles, part names and instruments only, no notes: the ABC Player's playlist. |
| `isMadeForLotro(files)` | Made for Lotro? `TRUE`, `FALSE`, or `null` when it can't tell (Maestro then asks the user). |
| `bareTempo(files)` | For a `Q:` without a note length that the standard reading counts differently: Maestro's notice. |
| `readLines(file)` | Reads a file as UTF-8, else Windows-1252, without a byte order mark. |

## The settings: `AbcToMidi.Params`

| Field | ABC Player | Maestro |
|---|---|---|
| `enableLotroErrors` | the user's setting | off |
| `useLotroInstruments` | on: Lotro's octaves and sample lengths | off: each note at its real pitch |
| `standardPitch`, `standard2011`, `expandRepeats`, `specTempo`, `chordAccompaniment` | off | on only for a new project from a file not made for Lotro |

In the parser, `abc21` means `standard2011 && !enableLotroErrors`: the standard reading is on.

The tests name the five combinations in use (`Profile`): `ABC_PLAYER`, `ABC_PLAYER_STRICT` (Lotro errors on),
`MAESTRO_LEGACY` (an old project), `MAESTRO_NEW_LOTRO` (a new project, file made for Lotro) and `MAESTRO_NEW_STANDARD`
(a new project, standard ABC).

## Inside `AbcToMidi`

`convert` is one long pass over all lines of all files.

1. **Before the pass, standard reading only:** each file's text goes through `VoiceSplitter` (voices become parts),
   then `PartOrder` (`P:ABA` is written out in order). Both only rewrite text; a table of line numbers keeps error
   messages and highlighting on the file's own lines.
2. **The pass:** `%%` lines, then fields (`X:` starts a new part), then music, character by character.
3. **After the pass:** the chord accompaniment and drone tracks, the end of each track (where plucked notes stop
   ringing), tempo events, pan, and the time and key signatures.

**Tracks.** Track 0 holds the tempo, the song title and the `W:` text. Tracks 1 to n are the parts, one per `X:`, with
the same index as in `AbcInfo`; a part without notes still gets an empty track. The accompaniment and drones come after.

**Two pitches per note.** `noteId` is the MIDI note that plays. `lotroNoteId` is the note in Lotro's notation, used to
check Lotro's range and note lengths.

**`%%Q:` (Maestro's tempo changes).** A `%%Q:` line puts a tempo change in the tempo map. A note keeps the seconds its
ABC length gives at the main tempo; only the beats and bars follow the new tempo. That makes the round trip work: MIDI
→ Maestro → ABC → back into Maestro gives the MIDI's tempo map again. Grace notes and ornaments are counted in seconds
too.

**Helpers inside the class:**
- `Repeats`: plays repeats out by jumping back in the text. On the second pass the section is read as it was on the
  first (key, meter, `L:`, `I:`); dynamics carry over. A first ending that is skipped changes nothing.
- `LyricNote`, `singLyrics`, `sing`: `w:` lyrics, one syllable per note, a verse per pass. Written when the part ends.
- `ChordSymbol`: chord names ("Am", "G/B") for the accompaniment.
- `Tuplet`: `(3`, `(3:2:3` and the like.

## Other classes

| Class | What it is |
|---|---|
| `TuneInfo` | What is in force while reading: key, meter, `L:`, tempo, transposition, instrument, dynamics, `I:`. Every part starts from the file header's values, not the part before's (as Lotro does). `ReadState` is a snapshot of it, for repeats. Only the parser uses it. |
| `AbcInfo` | The result besides the `Sequence`: song titles, composer, tempo, meter, key, length, the `%%` timing flags; per part its name, number, instrument, pan and line range; the bars and the regions. |
| `AbcRegion` | One per note: where it is in the text and when it plays. The ABC Player uses them to highlight the text while playing. Only made with `Params.generateRegions`. |
| `VoiceSplitter` | A tune's voices (`V:`, `[V:]`) → one part each, played together. |
| `PartOrder` | `P:ABA` in the header → the sections written out in that order. |
| `RhythmTempo` | A tempo for the tune type in `R:` (reel, jig, waltz, Balkan dances ...), for a tune without `Q:`. |
| `MidiProgramGuess` | The MIDI sound of a standard ABC part: from `%%MIDI program`, `%%MIDI voice`, instrument names, `G:`, `V:`, `T:`, `R:`. |
| `Drone` | A bagpipe drone under a part (`%%MIDI droneon`, or Highland pipes). Never for Lotro files. |
| `AbcInstructions` | `I:linebreak`, `I:decoration`, `I:propagate-accidentals`. |
| `AbcTunebook` | A tune book split into its tunes: Maestro's tunebook dialog and its "Split into files". |
| `FileAndData` | A file and its lines. |
| `AbcSongbookSplitter` | Old, replaced by `AbcTunebook`: to delete once ABC Tools uses `AbcTunebook`. |

Outside the package: `common.abc.AbcText` decodes the text escapes in titles and lyrics (`\'e`, `&eacute;`, `é`).

## Tests

About 4,750 tests, in `srcTest/test/com/digero/common/abctomidi`.

- **`AbcCases`:** one tiny tune per feature or error.
- **Snapshot tests:** every case is converted in all five profiles and compared with a golden file in
  `srcTest/resources/com/digero/abctomidi/golden`. `AbcToMidiFileSnapshotTest` does the same for real files (such as
  Canzonetta, the ABC 2.1 standard's own example). After a change you meant to make, record the new output with
  `-Dabc.golden.update=true` and read the diff.
- **`AbcToMidiBehaviourTest`:** exact checks of notes, ticks, lyrics and errors. `ReviewBugs` holds one test per bug of
  the review of 2026-10-02. "Tested in Lotro" comments point to the in-game tests B1 to B81 (`lotro_tests.md`).
- **`AbcToMidiRoundTripTest`:** a MIDI with 155 tempo changes and Maestro's export of it (resources folder `roundtrip`).
  Reading the export back must give the MIDI's tempo map and notes.
- **The helpers:** `VoiceSplitterTest`, `PartOrderTest`, `AbcTunebookTest`, `AbcInstructionsTest`, `AbcTextTest`,
  `AbcXmlTextTest`, `LotroMinimumLengthTest`.
- **`TestSetup`:** logging, locale and UI texts, once per test run.

## Not supported (ABC 2.1)

- M: change with another denominator mid-tune, [M:C|] after 3/4
- Q: tempo change mid-tune (the tempo map is shared by all parts, so tricky)
- & voice overlays
- m: macros
- U: user-defined symbols, U:W=!trill! then Wc
