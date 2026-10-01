# ABC to MIDI (`com.digero.common.abctomidi`)

**Rule:** Lotro errors on (ABC Player) = play as Lotro does, and throw `LotroFileParseException` for anything Lotro refuses or plays differently. Lotro errors off (Maestro) = play as ABC 2.1 says. Files made for Lotro and existing projects keep Lotro's reading; only new projects from standard ABC get the ABC 2.1 one (the flags below).

## Entry points (`AbcToMidi`, static)
- `convert(Params)` → `Sequence`. Fills `params.abcInfo` as a side effect.
- `parseAbcMetadata(files)` → `AbcInfo` with titles/instruments only, no notes (ABC Player playlist).
- `isMadeForLotro(files)`: written for Lotro instruments, or standard ABC (folk)? Sets up `Params.standardPitch`.

## `AbcToMidi.Params`: how to read the file
| Field | ABC Player | Maestro |
|---|---|---|
| `enableLotroErrors` | on (user setting) | off |
| `useLotroInstruments` | on: notes in Lotro notation, sample lengths | off: notes at the instrument's real pitch (+`octaveDelta`) |
| `expandRepeats`, `specTempo`, `chordAccompaniment`, `standardPitch`, `standard2011` | off | on for a new project from standard ABC (`abcImportVersion > 1` and not made for Lotro) |

`abc21` in the parser = `standard2011 && !enableLotroErrors`: the ABC 2.1 reading (voices, part order, free text, loose `!`, ...).

## `AbcToMidi`: the parser (large method)
- With `abc21`, each file's lines first go through `VoiceSplitter` then `PartOrder` (text transforms; `sourceLineNumbers` keep messages and regions on the file's lines).
- One pass over all lines of all files: `%%` fields → info fields (`X:` starts a part) → music, char by char (`switch`).
- **Tracks:** 0 = tempo, song title, `W:`/verse text lines. 1..n = one per `X:` part, same index in `AbcInfo` (a part without notes gets an empty track). Then the accompaniment tracks.
- **Two pitches per note:** `noteId` (MIDI, what plays) and `lotroNoteId` (Lotro notation, used for the Lotro range/length checks).
- **Inner classes:**
  - `Repeats`: expands repeats by jumping back (`lineLoop`, `startColumn`).
  - `LyricNote`: `w:` syllables, sung at the part's end (`singLyrics`).
  - `Tuplet`.
  - `ChordSymbol`: chord name parsing for the accompaniment.
- **After the loop:** accompaniment tracks, `endTrack` (plucked note ends, end-of-track), tempo events, pan, time/key signature.

## `TuneInfo`: parse state
- What is in force while reading: key (+ explicit accidentals), meter (+ beat groups of `M:2+2+3/8`), `L:`, tempo (+ tempo maps; without `Q:` from `R:` via `RhythmTempo`, else 1/4=120), transposition (K: clef/transpose/octave), instrument, dynamics, `I:` instructions.
- The file header's values are kept apart: every part starts from them (`newFile`, `newPart`), not from the part before (tested in Lotro).
- Internal to the parser; nothing outside reads it.

## `AbcInfo`: the result, besides the `Sequence`
- **Song:** titles, composer, transcriber, genre, mood, tempo, meter, key, length, and the `%%` timing flags (mix, organic, swing/triplet guess).
- **Per part, by track index:** name, number, instrument (and whether it came from `%%made-for`), pan, ABC line range.
- **Also:** bar ticks, regions, and `abcTrackInfos` for Maestro.

## `AbcRegion`
One per note: MIDI ticks to/from line/columns in the ABC text. The ABC Player uses them to highlight the text while playing. Only made with `Params.generateRegions`.

## Helpers (each small, own tests)
- `VoiceSplitter`: a tune's voices (`V:`, `[V:]`) → one `X:` part each, played together.
- `PartOrder`: header `P:ABA` (or The Session's `P:` right after `K:`) → body sections written out in that order.
- `RhythmTempo`: tempo for a tune type in `R:` (reel, jig, waltz, Balkan dances ...), as a `Q:` value.
- `MidiProgramGuess`: MIDI program for standard ABC parts (`%%MIDI program`, names, `G:`, `V:`, `T:`, `R:`).
- `AbcInstructions`: `I:linebreak`, `I:decoration`.
- `AbcSongbook`: a tune book → its tunes (Maestro's songbook dialog, ABC Tools' split). (`AbcSongbookSplitter`: old, to delete.)
- `FileAndData`: a file and its lines.
- `common.abc.AbcText`: text escapes in lyrics (`\'e`, `&eacute;`, `é`).

## 10,000+ Tests (`srcTest`, package `com.digero.common.abc`)
- `AbcCases`: one tiny tune per feature or error.
- Snapshot tests: every case in 5 `Profile`s (ABC_PLAYER, ABC_PLAYER_STRICT, MAESTRO_LEGACY, MAESTRO_NEW_LOTRO, MAESTRO_NEW_STANDARD), compared with golden files. Record them with `-Dabc.golden.update=true`.
- `AbcToMidiBehaviourTest`: exact checks; "Tested in Lotro" comments mark behaviour confirmed in game (B-numbers in `abc-todo.txt`).
- `VoiceSplitterTest`, `PartOrderTest`, `AbcSongbookTest`, `AbcTextTest`: the helpers.
- `TestSetup`: logging, locale, UI texts, once per JVM.
