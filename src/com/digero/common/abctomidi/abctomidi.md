# ABC to MIDI (`com.digero.common.abctomidi`)

**Rule:** Lotro errors on (ABC Player) = play as Lotro does, and throw `LotroFileParseException` for anything Lotro refuses or plays differently. Lotro errors off (Maestro) = play as ABC 2.1 says.

## Entry points (`AbcToMidi`, static)
- `convert(Params)` → `Sequence`. Fills `params.abcInfo` as a side effect.
- `parseAbcMetadata(files)` → `AbcInfo` with titles/instruments only, no notes (ABC Player playlist).
- `isMadeForLotro(files)`: written for Lotro instruments, or standard ABC (folk)? Sets up `Params.standardPitch`.

## `AbcToMidi.Params`: how to read the file
| Field | ABC Player | Maestro |
|---|---|---|
| `enableLotroErrors` | on (user setting) | off |
| `useLotroInstruments` | on: notes in Lotro notation, sample lengths | off: notes at the instrument's real pitch (+`octaveDelta`) |
| `expandRepeats`, `specTempo`, `chordAccompaniment` | off | on if project `abcImportVersion > 1` |
| `standardPitch` | off | project's saved `abcStandardPitch` (from `isMadeForLotro`) |

## `AbcToMidi`: the parser (large method)
- One pass over all lines of all files: `%%` fields → info fields (`X:` starts a part) → music, char by char (`switch`).
- **Tracks:** 0 = tempo, song title, `W:`/verse text lines. 1..n = one per `X:` part, same index in `AbcInfo`. Then the accompaniment tracks.
- **Two pitches per note:** `noteId` (MIDI, what plays) and `lotroNoteId` (Lotro notation, used for the Lotro range/length checks).
- **Inner classes:**
  - `Repeats`: expands repeats by jumping back (`lineLoop`, `startColumn`).
  - `LyricNote`: `w:` syllables, sung at the part's end (`singLyrics`).
  - `Tuplet`.
  - `ChordSymbol`: chord name parsing for the accompaniment.
- **After the loop:** accompaniment tracks, `endTrack` (plucked note ends, end-of-track), tempo events, pan, time/key signature.

## `TuneInfo`: parse state
- What is in force while reading: key, meter, `L:`, tempo (+ tempo maps), transposition (K: clef/transpose/octave), instrument, dynamics.
- The file header's values are kept apart: every part starts from them (`newFile`, `newPart`), not from the part before (tested in Lotro).
- Internal to the parser; nothing outside reads it.

## `AbcInfo`: the result, besides the `Sequence`
- **Song:** titles, composer, transcriber, genre, mood, tempo, meter, key, length, and the `%%` timing flags (mix, organic, swing/triplet guess).
- **Per part, by track index:** name, number, instrument (and whether it came from `%%made-for`), pan, ABC line range.
- **Also:** bar ticks, regions, and `abcTrackInfos` for Maestro.

## `AbcRegion`
One per note: MIDI ticks to/from line/columns in the ABC text. The ABC Player uses them to highlight the text while playing. Only made with `Params.generateRegions`.

## Small ones
- `FileAndData`: a file and its lines.
- `common.abc.AbcText`: text escapes in lyrics (`\'e`, `&eacute;`, `é`).

## Tests (`srcTest`, package `com.digero.common.abc`)
- `AbcCases`: one tiny tune per feature or error.
- Snapshot tests: every case in 3 `Profile`s (LOTRO, LOTRO_STRICT, PLAIN_MIDI), compared with golden files. Record them with `-Dabc.golden.update=true`.
- `AbcToMidiBehaviourTest`: exact checks; "Tested in Lotro" comments mark behaviour confirmed in game.
