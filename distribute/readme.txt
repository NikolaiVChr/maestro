############
##  MAESTRO, ABC PLAYER and ABC TOOLS
############

The install instructions only apply to version 4.2.0 or later.

Install instruction (.msi)
===================
1: Run the .msi file
2: Enjoy.
3: Notice this will uninstall any older version of Maestro you have installed.
   (but not versions that was unpacked from zip unless you install into same folder)


Install instruction (.zip)
===================
1: Copy the content of this ZIP into a any new folder.
2: Use the executable files to start.
(3): Make Windows shortcuts to start Maestro, Abc-Tools or Abc-Player in the unzipped folder.


Linux/Mac instruction (.zip)
===================
1: Install a java 21 jdk (From https://www.oracle.com/java/technologies/downloads/ or your distro's package manager)
2: Use the scripts (sh for linux, command for mac) in the linux_or_mac folder to launch Maestro, AbcTools, or AbcPlayer


Starting from command prompt
============================
- Install java jdk 21 or 25.0.1
- Then use command-line prompt from the install folder:
java --patch-module java.desktop=java.midi.patch.jar --enable-native-access=ALL-UNNAMED --add-exports=java.desktop/com.sun.media.sound=ALL-UNNAMED -jar app/Maestro.jar



Special Thanks
==============
Thanks to Digero for an amazing original Maestro and ABC Player.
Thanks to Jersiel for teaching Aifel music theory and giving ideas and feedback,
          without which he could not have contributed to much of this.
Thanks to all who gave feedback of features and bugs plus suggestions.
Special thanks to the folks who have tested beta builds and gave feedback.


Authors
=======
Digero of Landroval
Aifel of Meriadoc
Elamond of Peregrin
Asgloin of Meriadoc
Karloman


Changelog
=========
Release notes after 4.6.21 should be in markdown format. Keep the version headers strictly as they are.

This changelog is only partial, to see all the way back to v1.0.0 go here:
  https://maestro.miraheze.org/wiki/Version_history

Version 4.6.25
* Significant less CPU usage during audio playback.
* Allow part delay to be negative.
* Highlight connected parts when hover over note tracks.
* Added support for custom drum combos in drum-maps. Maestro can hold 79, but if loading a project and maestro is full, the project will be degraded. Recommend to never exceed 70 combos.
* Added editor for custom drum combos, up to 79. Access it in the drum-map menu. It will stop preview playback, but will allow midi playback to continue.
* Better simulate lotro not stopping samples that already are running, so e.g. a series of fast lute note with same pitch will now allow the previous to keep playing at full volume till their sample ends. This requires an updated soundfont that will download first time this version is ran.
* Fix that when skipping in song, the highlighted current notes could stay highlighted, even though the song was now having new position.
* Added TR-808 and TR-909 drum kit hit names.

Version 4.6.22
* Downloads of the apps are now found on github. [https://github.com/NikolaiVChr/maestro/releases](https://github.com/NikolaiVChr/maestro/releases)

Version 4.6.21
- Fixed that Jaunty FX could save empty note assignments.

Version 4.6.20
- Stop scaling notes in drum/fx panels vertically.
- Added leading-edge note color markers to distinguish sequential notes.
- Made default timing setting in options (for newly installed Maestro apps) be Organic Single-stage instead of Mix timings.
- Set default stereo to 25% when installing fresh maestro.
- Fixed that some GS midi files channels could be silent during midi playback. (likely connected to Win 11 new midi engine)
- Much more expansive support of midi ports. Only fresh projects will benefit from all the changes to keep backward compatibility.
- Fixed some edge cases of misbehaving pitch bends. That combined with the port update means that some bends can change or even go away.
- More spec. compliant XG/GM2 processing while at the same time try to account for badly made midi files.
- Improved midi expansion export.
- Added some more guards against 7bit midi bytes using 8th bit.
- Fixed that sometimes it would not draw the notes of tracks that were not selected.
- Prevent XG and GS device volume commands from being sent to the midi player, as that will change Maestros master volume, which the user should control.
- Exporting expanded midi now put XG, GS and GM2 drum tracks onto new ports so they can be assigned to GM midi 10th channel, and windows midi player can figure out to play at least standard drumkit from them.
- Plus exporting expanded midi now convert XG sysex bank/patch changes into regular midi control changes. This will allow for better support of playing the expanded midi with own custom sf2
- Better grace-note handling in org multi-stage 2. If it did not overlap before backwards bounce, it now also wont after.
- Fixed several reasons ABC preview could become muted.
- Fixed that drum sounds could become muted.
- Allow longer note durations for certain instruments when using organic single-stage too.
- Fixed that when getting "Undefined External Error" message due to no audio out on PC, the message now informs better.
- Increase default size of Maestro as time was not even shown and there was only room for 1 part in parts-list.
- Added support for custom soundfont for playback of midi.
  Name it: midi.sf2
  Put it in:
  C:\Users\[username]\AppData\Local\MaestroCommon
  Before starting Maestro.

Version 4.6.14
- Fixed that Maestro allowed zero part number.
- Added an option to timestamp every lyric line, even when there is no rest between them.
- Made 'reduced filesize' default setting be false (for newly installed Maestro apps). As it is suspected that notes can get skipped with it enabled.
- Major upgrade of organic multi-stage 2.

Version 4.6.13
- The abc save-as window will now show the path as title.
- Re-added missing octave and bar lines to tracks when dragging volume slider.
- Added context menu to 'Lock' option. Right click to chose Lock/Unlock all.
- Show full path of source song file in stats view.

Version 4.6.12
- When zoom vertically the note heights will now also zoom. Percussion notes are now taller too.
- Major backend rework of note painting, it now have significant less CPU and GPU usage.
- Fixed that loading in a 15+ part abc song into Maestro would fail with exception.
- Fixed that sometimes when resizing maestro the note graph zoom lvl did not match what it was before resizing.

Version 4.6.11
- Stop setting project modified when reduce file size is toggled in options.
- Fixed that when auto-exporting the first song, might fail to export.
- Added batch export of playlist to mp3 files
- Fixed that the quarter song empty test algorithm did not take extremely long sustained notes into account.
- Fixed that if certain accessibility features were enabled in windows, apps would not start.

Version 4.6.10
- Fix for when saving expanded midi, but it sometimes sounded like piano on some tracks.

Version 4.6.9
- Fixed that the download progress window for soundfont was not always shown. That could result in app hanging with no visible window to close it.
- Stopped using Documents folder for log files. They are now next to where the soundfont is.

Version 4.6.8
- Fixed having to run Maestro twice to get soundfont.

Version 4.6.7
- Before installing this using the MSI installer, please remove old maestro from your windows manually.
    (search "Add or remove programs" in windows and remove "Maestro")
    This only needs to be done once, next versions will just update the previous version automatically again.
- The MSI now installs to per-user "AppData/Local" instead of global "Program Files".
    This also means it no longer need admin privilege when it install and uninstall,
    and therefore is also not able to be installed in "C:\Program Files", so don't attempt that.
- Fixed that when uninstalling in Windows when installed to non-default folder that the folder did not get removed.
- The soundfont that make the instruments sound like lotro is now downloaded first time the app is started. 240 MB approx.
    The soundfont don't get updated often, so when installing a new maestro version,
    most of the time it will just use one from a previous Maestro.
    The download location on Windows is C:\Users\[username]\AppData\Local\MaestroCommon
    If you use from ZIP then you have option to manually use your own soundfont by putting
    it into the app folder with the name "LotroInstruments.sf2". Then it will not attempt to download one.
- Fix that v4.5.24 would not run on Linux with earlier than Java 25 without changing the shell scrips.
- When loading abc file into maestro also set the user pan on a new project.
- Allow editing of lyrics and to copy them for Poetical with timestamps.
- Allow changing colors of Maestro. Note that most colors are dictated by dark and light themes and can't be changed.
- Added fr and de localizations. Consider this a beta feature please, as it's mostly auto translated.
    Not translated: MIDI instruments and drum hit names, color editor, statistics.
- Abc Player recent list now has the most recent at top instead of bottom (like Maestro).
- Fixed that soloing a part and then switching from preview to MIDI playback would solo one of the midi tracks.

Version 4.5.24
- Fixed a bug in splitting of long notes in organic multi-stage and made it slightly smarter.
- Fixed another solo/mute issue.
- Allowed some instruments to have longer notes than 5 seconds in organic multi-stage.
- Made the pan window slightly larger to show 4 stacked parts.
- Many various performance optimizations.
- Organic multi-stage 2.
- Made pitch bend handling for organic much smarter.
- Made pitch bend handling for large pitch range pitch bends slightly smarter.
- MSI and ZIP is now smaller in size.
- Added polyphony graph tooltip.
- Added dissonance graph option.
- Added number in pan window showing pan position.
- Preview notes release curves is now linear decreasing amplitudes to match lotro.
- Removed the 6.8 ms attack on all notes, this fix can make the clarinet clicking be more audible in maestro.
- Stopped filtering of all notes, the HP cutoff was really high, but it might have distorted slightly.
- Fixed that Student's Fiddle notes sounded different than basic fiddles.
- A possible fix for "horn bug". Don't get your hope up though, probably wont work.

Version 4.5.18
- Fixed a bug where playlists with files which had been moved/renamed wouldn't load.
- Added an "Export Playlist to CSV Sheet..." option in the playlist menu in AbcPlayer, to export a CSV sheet only without also exporting a set
- Each note in abc output is no longer written as a fraction. This reduces file size.
- Added note counts to stats.
- Prevent preview generation each time a user changes part title, unless the user pan actually changed.
- Added option to auto-exporter to force a volume method upon all exported abc.
- Added a part pan slider to control stereo manually, in future using hints in part name will not work.
- Added button to jump to highest polyphony peak.
- Fixed that solo/muting could in some circumstances solo/mute the wrong parts in v4.5.11.
- Fixed that auto-exporter could bring up multiple windows asking choices at the same time.
- Changing stereo slider or part pan will no longer produce stutters in the playback.
- Update of auto-panning, it now uses many more factors into deciding where to place a part in the stereo field.
- Fixed that organic in v4.5.11 and later sometimes did not play the very first note in ABC preview when there was initial silence.

Version 4.5.11
- Offloaded Maestro ABC exporting to a worker thread.
- Offloaded Maestro ABC preview generating to a worker thread.
- Auto-exporter is now threaded, expect a speed-up of 3 to 12 times.
- Various internal event optimizations.
- Play-head follow feature no longer jitters.
- Make play-head sync with count-in (when playback in progress).
- Increased max number of sections in section-editor to 150.
- Organic multi-stage now handles initial silence a bit better.
- Added option in AbcPlayer "More Options" menu to disable ABC potential corruption popup messages.
- The potential corrupt ABC message can be viewed by hovering over the ABC title, or the playlist lines.
- Added option for reduced exported abc file size. No bar or time progress output when enabled.
- Added tabs to the side-panel in Maestro and made import lyrics default enabled when installing a Maestro for first time.
- Made the part-editor button green if it has been modified.
- Fixed that count-in did not account for user changes to the main tempo in the correct way.

Version 4.4.6
- Removed warnings for bad Jaunty notes.
- Update Jaunty note durations for polyphony to match lotro update 45.5.0
- Make Maestro release lock on midi files as soon as they are read, so they can be moved or renamed, while Maestro is still running.
- Combine timing checkboxes into a dropdown menu.
- Add version number to abc player window title.
- Added FX option to Jaunty hand-knells. In FX state it allows section-editor octave transposing and doubling.
    Note range limits in section editor wont be enabled in FX state.
    Tune-editor transposing (key-change) will affect it unless its a drum track. Track or song transposing will not.
- Disabled doubling, transpose, legato and note range control inputs in section-editor for Student FX.
- Disabled legato control for non-sustained instruments as it have no effect for them anyway.
- Fixed that the abc playback audio and visible play-head was out of sync with 0.25 seconds.
- Fixed that maestro did not handle midi with meter numerator higher than 127 correctly.
- Replaced the stereo menu item in Abc Player with a slider.

Version 4.4.3
- Closing yes/no/cancel dialogs will now act as choosing cancel.
- Replace jaunty G#2 note and attenuate the instrument.

Version 4.4.2
- Added per project option to restrict to only using tempo changes from the first track.
    Changing this option can change the layout of the bar lines,
    so be sure to review the section/tune edits after changing this option.
- Fix playback of midi if project is using tempos from beyond first track,
    so it matches with what is actually being shown in the editor.
- Fix handling of rare case of tempo changes in midi that are not well formed.
    Projects started in Maestro before v4.0.0 are not affected.
    These fixes are not backward compatible.
    It can result in a different main tempo than a project was last saved with.
    As for projects where user had modified the main tempo; since main tempo change is saved as an relative factor,
    the fixes should not ruin the song for that reason.
    Maestro and Auto-exporter will popup a warning if edits might be needed.
- Abc Tools auto-exporter will now summarize a list of skipped or failed exports after finishing.
- Added new option to part numbering; part ordering sort.
- Added setting to lock a part-number to prevent it from ever getting reassigned automatically.
- Added count-in control for drum to part-editor. Note: Using it will make the play-head out of sync with abc preview.
- Right mouse click in a to/from bar field will now paste current song position.
- Added Jaunty Hand-knells instrument.
- Added 2 bad jaunty hand-knells notes to track title tooltip. A2 and G#2
- Allow file-choosers to select individual file filters.
- Set Java MIDI playback for abc preview to use point interpolation for less smoothing and truer lotro rendition.

Version 4.3.8
- Made a popup window warning if loading a project saved with v4.3.0
- Fixed when loading project, midis with em dash got converted to double dashes and the midi file could not be found.
- Abc Player was a little too confidently saying that notes were overlapping when they were not. This has been improved.
- Renamed 'Changes since Maestro 2.5.0.txt' file to 'readme.txt'.
- Improve how organic restart too long notes.
- Auto-exporter force timings now overrides any project setting.
- Fixed that ABC player playback duration display didn't always match up with outputted ABC meta data duration.
- Made subdividing of pitch-bends have more details when using organic output (in some cases).
- Fixed that when importing part numbering config, the instruments did not update in the parts list after applying settings.
- Made automatic emergency reset of part numbering scheme if it is corrupt.
- Remove slow bottleneck of updating part numbers when running renumberAllParts().
- Make maestro react faster when deleting a part in a song with many parts.
- Fix that when expanding midi, lyrics and tempo changes got all lumped up into first track.
- New option added for export warning if two parts are named the same.
- New export warning if polyphony goes above 64. Abc Tools auto-exporter will not warn though.
- Increased max number of sections in section-editor to 120.

Version 4.3.1
- Fixed severe volume bug in Maestro in 4.3.0

Version 4.3.0
- Fixed Abc Merge Tool button for testing was no longer working.
- Fixed Abc Merge Tool refused to overwrite.
- New import lyrics option in settings that can help to determine song-title/artist when filename is short and insufficient.
- Fixed that some midi files could generate error in Maestro if they had events weeks beyond end of song.
- Fixed that wav and mp3 export would always be mono. Now they will be whatever maestro/abc-player setting is.
- Reduced average abc filesize of organic output when poly 6+ is disabled.
- Improvements to multi and single-stage organic timing.
- If a midi note has no note OFF signal, but there is a EndOfTrack signal, end it at EOT signal instead of deleting it.
- Output midi bar numbers with timestamp in abc when outputting with organic.
- If part auto-sort is enabled, sort the parts when loading project and auto-exporting projects.
- Fix minor inaccuracy of song position time display in Maestro.
- Fix some cases where the song display in maestro would be significantly longer than its actual duration. It can still happen, but more rarely now.
- Fix that maestro not always play very first note when opening project.
- Fix that after pressing stop when not in abc preview, midi playback would play initial silence in the midi.
- Prevent midi playback from skipping midi playback to first enabled note when enabling first lotro part.
- Add Maestro version to window title.
- Fix old bug that loading a project while maestro is running, could make first note(s) repeat in abc preview.
- Added same pitch note overlap as a lotro error in abc player.
- Abc Player will now notify if loading in an abc made with a Maestro that had a known issue.

Version 4.2.13
- Fix that some songs could still change midi playback volume, overriding user's volume slider setting.
- Reduced msi and zip filesize by only including icu in Maestro.jar

Version 4.2.12
- Settings template examples now more accurate reflect final results.
- Add option for softest note to chord volume choices.
- ABC Tools now uses Maestro's setting for light or dark theme.
- About window now show Java vendor and link to wiki.
- In Abc Tools Auto-exporter, disable force mix/multistage check-boxes, depending on if force organic is enabled.
- Added $PartCount variable to part name template, to help Songbook know which songs to pick for songs with identical names but different #s of parts.
- Added extra info, composer, mood, genre, to AbcPlayer song title in the main view
- Added up/down arrow buttons to the AbcPlayer playlist as an alternate way to move songs up and down in the playlist besides dragging
- Part list can now be reordered by using drag'n'drop, or resorted by clicking sort button.
- Fix for organic multistage dropping notes it should not when poly 6+ is enabled.
- Fix not being able to load project saved with track names that have unicode escape chars not allowed in xml 1.1.
- Fix when saving xml, that chars that are not legal in xml 1.1. could still sneak in.
- Better detection of charset in midi track titles.

Version 4.2.11
- Some code improvement to organic single-stage.

Version 4.2.10
- Another fix for same issue as 4.2.9
- Partial fix for notegraphs sometimes being messed up.

Version 4.2.9
- Fix for organic singlestage could export entire parts that lotro would silence (with poly 6+ option enabled).

Version 4.2.8
- Organic can now place rests in chords when its advantagious to allow for more than 6 notes to be playing at once in a part.
- Added option to enable using more than 6 polyphony in organic parts, default is off.
- ABC Tools Autoexport will now notify which songs make use of the 6+ polyphony feature.
- Better handling of charsets in tracknames and semi-fix for what previous handling might have broken.
- Added option to set default timing for new projects.
- Made Maestro use custom GM drum hit names for GS or XG as they are not always the same as GM. Its not complete, but includes the most popular kits.

Version 4.2.3
- Fixed countdown instead of count up timer in ABC Player would show enabled even if disabled if user had never toggled the option.
- Some improvements to organic multistage.
- Better decoding of midi track titles when its ISO-8859-1 charset.
- Selecting MIDI out device should now also work when midi out is indexed before its midi in.
- MIDI out device now take effect immediately when clicking Okay in options.
- Will now autodetect if preferred MIDI device is turned on and switch to it. And opposite.
- When reseting misc options, midi device list will be cleared and re-populated.
- Added error handling that show a clickable message to user.
- Use mono soundfont samples internally.
- Disable light reverb in preview.
- Add option for how to export the volume of notes starting at same time in a part. Its applied to whole song, perhaps in future can put in overrides in part-editor or section-editor.
- Fix that preview sequence could be longer than End-early, it played, but song muted.

Version 4.2.2
- Version check can no longer delay the start of Maestro.
- Part editor will no longer use comma as decimal point despite any locale settings. It will still accept comma as input though.
- Fix handling of midis with extremely long pauses between messages. Those midi will now end when such pause start.

Version 4.2.1
- Fix bug where the "remove spaces and capitalize first letter" option in Part Naming would cause an internal crash
- Fix pitch bends going deeper than lowest midi note would make Maestro act erratic.
- Added menu item for opening help in browser.
- Added option to disable version check at startup.
- Switched to Java 21, which is now distributed with the app. Java is no longer needed to be installed.
- Increased max allowed tempo to 10000, useful for removing pauses with tune editor.

Version 4.1.5
- Add ability to sort playlist based on columns, in right-click playlist header menu
- Add a playlist menu toggle to auto-expand folders with matched songs when searching
- Fix bug where the playlist "Refresh Browser" function wouldn't work until you searched
- Fix bug where the set exporter wouldn't honor the option to not rename files
- Fermata now applies to all last notes that ends within 5 ms of last ending.
- Merged part buttons into one that opens a window with fields for all parts, it also have the setup priorities.
- Note graph horizontal zoom now uses a quartic curve, for more natural adjusting
- The default folder in AbcTools is now the lotro music folder, if it exists
- Fix bug where the "remove spaces and capitalize first letter" option in File Naming or Set Export settings would cause a crash

Version 4.1.4
- Invert how priorites of setups work.

Version 4.1.3
- Support extended songbook part setup priorities.

Version 4.1.2
- More work on drum handling of bent notes.

Version 4.1.1
- Default Music folder for abcplayer and maestro will now try to find lotro path in onedrive if thats used for documents folder.
- Fix drum were not handling bent notes correctly when put on chromatic track.

Version 4.1.0
- Make Abc Tools auto-exporter fast again, by disabling checks for abc validity.
- Use OpenGl instead of DirectX to render graphics.
- Option to remove spaces and capitalize first letter in filename export and part names.
- Auto-exporter dialog to locate source file now will filter on abc/mid files.

Version 4.0.18
- Make section-editor 'Rest of Track' pin to bottom instead of being inside scrollable area.
- The checkbox for force mix timings in autoexporter tool is now unchecked by default.
- Increase minimum upscaled resolution of source midi before we work on it. This will help some midis where tempo drops very low.
- Fix for auto-exporter would not show dialog for locating midis.
- Added option for exporting organically (BETA). ABC files exported with this will be much less readable by humans.
- Multi-stage is another organic export mode. Use your ear to choose which mode you prefer.
- Removed limitation of minimum 50 bpm to enable delay.
- Duration in part names and metadata now includes conclusion fermata.
- Fix that if two student fiddle FX noises came right after each other, the second might have been muted.
- Change that tempos meta messages not in first track would be ignored. Some MIDI software like FL Studio can output them in other tracks.
    Old project will continue to only read them from first track for backwards compatibility.
- Auto-exporter now skips recursive folders that start with . in their name.
- Fix that Maestro would force a sorting of parts when loading project or using AbcTools auto-exporter.
    This also means if you change the auto part numbering and then load an old project,
    you will have to change a part number to trigger a sort with the new auto part numbering scheme.
- Maestro now warns user if loading a project where the parts use tracks that has no notes.
- Adjusted note ending hold and exponential release for preview and abc player playback, to better match lotro linear power release.
- Added a search feature to the ABC browser, it will search as you type for song files which match (case insensitive)
    If you add a folder to the playlist, it will only add files which match the search under that folder (recursively)
- Adding a folder to the playlist will now add the files using the same sort order as is set in the abc file tree
- The ABC browser will keep folders expanded when you change the sort type or refresh files (or use search)
- The ABC playlist table will scroll to the end when new songs are added which would appear off screen
- You can now press tab to switch between the song view and the browser/playlist view
- Opening an ABC song through dragging or the file->open menu will switch to the song view if nothing's playing
- Opening a playlist will switch to the playlist view if nothing's playing
- Fixed that double clicking on a playlist file wouldn't open it if AbcPlayer was already open
- If song files are missing in a playlist, AbcPlayer can prompt you to search for them in another folder
- Add a playlist menu option, "Append Playlist...", which will open and append a playlist to the existing playlist contents
- Added a set export wizard feature to the playlist
    Accessible through the Playlist menu, "Export Playlist as Set..."
    Allows you to export the currently loaded playlist's songs into a new folder or zipped file
        File renaming pattern lets you reorder the songs to match the playlist using the $SongIndex variable
        Optionally export a CSV part sheet file (importable into Excel, Google Docs, etc.) which adds a row for each song:
         The song filename
         Optional columns for song fields such as composer, duration, etc.,
          Either using the columns that are visible in your playlist view, or using custom columns
         Optionally, one or more columns containing either the part names or the instrument names of each song


Version 3.6.5
- Solo/mute buttons will no longer reset when reloading MIDI file.
- Changed the algorithm for removing duplicate notes (same pitch, playing at same time),
    when combining tracks with sustained instruments.
    It is still quite easy to get bad sounding parts doing this still,
    especially when the volume difference between them are large.
- Fixed a visual bug where buttons and UI elements had no spacing between them.
- Fixed a bug where exporting an ABC playlist would ask you to confirm overwriting a file even if the file doesn't exist.
- Added more column options to the ABC playlist. Setups min, Setups max, Genre, Mood, Export Date, Exported By.
    All are hidden by default, right-click on the header of the playlist table to show/hide them.
- Section-editor now supports up to 80 sections.
- Included all samples from basic fiddle, lm bassoon, basic bassoon, basic flute and sprightly fiddle. The soundfont is now no longer interpolating.
    This means Maestro and Abc Player will use more RAM and more diskspace.
- Fixed a bug where the Section/Tune editors don't work if they are left open while a file is opened.
- Update sounds in Standard Drum Kit to follow official spec. names more closely.
- Fix that Maestro could sometimes output invalid abc song, if a midi pitch bend would have a extremely big range.
- Fix that having max range for pitch bend at 24 meant that it could put some student fiddle notes into the silent or special effect range. So 16 is now max possible.
- Added option in Abc Tool auto exporter to Force save msx (abc location). It will always save project files and also include abc output location used by the tool.
- Prevented cascading dialogs when double clicking from windows explorer and close project or find midi dialogs is already open.
- When scrolling in section-editor, tabs and title line no longer scroll along.

Version 3.4.3
- Fix max part notes window had title from delay part window.
- Added legato option to section-editor. Will max extend notes 1 second.
- Added possibility to add a conclusion fermata up to 5 second on a part.
    This can be used to extend final note(s) of a part.
    If a lotro note sample runs out during the fermata, it might not be broken up and renewed,
    then the result will be a shorter fermata than specified, for that note.
- AbcPlayer Playlist changes
  - Add close song option to AbcPlayer similar to Maestro's close project.
  - Playlist switching will close the open song if it's played from the playlist.
  - Add option to sleep in between songs based on part switch delay to playlist menu.
  - Move playlist autoplay checkbox to playlist menu.
  - Add showing/hiding playlist columns using right-click menu on playlist column header.
  - Persist playlist table column sizes.
  

Version 3.4.1
- Section and tune editor windows are positioned better relative to the Maestro window, and will always appear fully on screen
- AbcPlayer playlist fixes and changes
  - Moved several playlist controls to a new playlist menu at the top of the window
  - Added a sort-by option for files in the abc browser, accessible through the playlist menu
  - Files and folders are now grouped separately in the abc browser
  - Folder contents can be added recursively to a playlist, by dragging or adding a folder to the playlist table
  - Fix a bug where AbcPlayer would crash if a directory pinned to the ABC browser does not exist
  - Fixed the order of songs being reversed when dragging songs to the playlist
  - Double clicking on folders in the ABC browser will expand or collapse them
  - ABC playlist files (.abcp) can now be loaded from the ABC browser or the Open menu in AbcPlayer
  - AbcPlayer installed through the MSX will set an icon for abc playlist files, and you can open by double-clicking in windows explorer

Version 3.4.0
- Add a playlist feature to AbcPlayer
  - Access the playlist panel by pressing the new button next to the stop button
  - Browse and add songs to the playlist in the file explorer on the left side
    - Drag and drop songs to the right side, or use the Add Selected button
    - Play songs directly from the ABC browser by double-clicking, or right-clicking
    - Customize which folders show up in the browser using the Directories button
  - Drag and drop songs within the playlist to reorder them
  - Play the playlist and move forward and backward with the playlist control buttons under the playlist panel
    - if the Autoplay checkbox is selected, the playlist will automatically start playing the next song until the end
    - You can also double-click on songs in the playlist to start playing from that song
  - Save and load playlist files with the .abcp extension
  - Use the playlist to plan your sets
    - The song switch delay feature lets you simulate part switch delay for your band, and calculates the total runtime of the playlist including switches 

Version 3.3.21
- Add option to disable auto playing files on open in Maestro, accessible in the misc settings panel
- Fix for abc preview could break without mix timings enabled.

Version 3.3.17
- Add a new Xtra Clap drum sound
- Fix that cowbells would count as 1.0s duration in histogram. They are 0.247s and 0.291s.
- Fix that at some specific bpms lotro wont accept 0.06 second notes or rests.
- Made abc player also complain when note or rest is 0.06s at one of those bpm.  

Version 3.3.14
- When using HZoom slider, and song position is in view, then keep it centered in view.
- When zooming using ctrl-mousewheel, target what mousepointer is aiming at.
    Known issue: When doing this the note graphs will flicker while being zoomed.
- Added accelerando/ritardando to tune-editor.
- When tempo-events are so closely clustered that some have to be removed, its now done smarter.
- Tune-editor: Added sort button. Added add button. Added tabs to columns. Reduced default tunelines to 8 from 20.
- Tempopanel will now indicate actual BPM when in abc preview mode. So tune-editors changes will be shown unless "Hide Edits" is active.
- Fix histogram label could show wrong count at song position when tune-editor had changed song tempos.
- Fix for Java process not exiting when Maestro is quit using File -> Exit

Version 3.3.8
- Fix notes were missing

Version 3.3.7
- Fix tune-editor late start and early ending got broken in v3.3.6
- Make tune-editor late start and early ending allow decimal points.

Version 3.3.6
- Fix that track tooltips would say zero bad notes before selecting a track.
- Tune-editor and section-editor now accept bar numbers with decimal point.
- Note graph tooltip now show bar number with 2 decimals.
- Fix that "Hide edits" checkbox would also hide natural instrument octave transposing.
- Add zoom slider controls for horizontal and vertical zoom
- Add a "follow" checkbox, the sequencer head will stay centered while Maestro is zoomed and playing if selected
- Support for horizontal and vertical zooming with ctrl + scroll and shift + scroll

Version 3.3.3
- Support 128 ports in GM+ midi files instead of 16.
- Track view now uses custom layout managers to layout the tracks and controls.
- Fixed the zoom issues in v3.2.11
- Added same kind of border to tempo/histogram control panels as track control panels.
- Fixed zoom would reset when doing certain operations.
- Zoom is now fixed at x6,x2 for now. Drum/studentFx tracks wont be zoomed vertically, same with tempo and histogram.
- Moved student FX checkbox to under the volume slider.
- Added option in Abc Player to countdown time instead of up.

Version 3.2.11
- Merge Student and Student FX fiddles into one. There will be a FX checkbox in the tracks to control which type of sounds is wanted.
- Fix drum/cowbell/studentFX notes sometimes were grayed out when active.
- Added two more Xtra drums.
- Song duration display is now rounded up to nearest second.
- Look also recursively in midi folder in ABC Auto export tool if recursive is checked.
- Add cancel option to auto-exporter.

Version 3.2.8
- You can hold shift while clicking a solo/mute button to deselect all the other buttons of that type. Works in Maestro and AbcPlayer.
- Flat light and dark themes are added for AbcPlayer, and the old default theme is removed.
- Fixed a bug where Maestro would report incorrect song lengths for arrangements where the main tempo was changed.
- Add options to tune-editor to start late or stop early at certain bar.
- More reliable white color change for currently playing notes.
- Added an option to hide edits in tracks.

Version 3.2.6
- Polyphony histogram now reacts to muting or soloing parts.
- Minimum and maximum note used can now be set in section editor.
    Note that for bended notes of small range (as set in settings),
    it will be the lowest pitch that will decide if the note is in range.
    Both note names like 'C4' can be entered (case sensitive),
    or a number like 60.
- Fix for 2 identical parts would make 1 of them not counted in histogram.
- No longer have to reopen the section-editor if changing between percussion
    and non-percussion instruments for a part.
- Retired Swing default look and feel. Flatlaf light is now default.
- Add button to increase number of sections in section-editor. Default is now 8.
- Section-editor inputs are now organized in tabs.
- Added ability to set the max number of concurrent notes in an part. From 1 to 6 notes.
    Note that this wont be shown visually.

Version 3.2.1
- Fix partname song duration were often some seconds too long.

Version 3.2.0
- Improved the visual updating of auto exporter.
- Abc Tools auto exporter no longer save a new abc export filename to msx.
- Fix for Abc Tools UI freeze or slows down when using auto exporter.
- More strict parsing of zero duration notes in midi sequence.
- Abc Tools auto exporter can now export into a folder-tree recursively.
- Fix for Maestro exporting to wav would ignore skip silence option.
- Separate LAME or FFmpeg downloads are no longer needed to export MP3s from Maestro or AbcPlayer.
- Fix that if the MIDI has any invalid tempo messages of zero MPQ, those messages will now be ignored.
- Fix source playback volume for GS MIDIs would change when assigning 2+ tracks to a part.
- Enable font sizes up to 36 pt which will help scale the app on 4K monitors. 
- Installer will now allow downgrading
- Fix output to abc and preview could be missing notes. This bug was introduced in 3.1.9

Version 3.1.10
- Fixed a bug in songs with more than 15 parts where the preview would sometimes play the wrong lotro instrument for a abc part.
    The bug has been there since v3.1.0, but now its fixed so that one might sometimes for a fraction of a second hear the wrong instruments,
    but then it fixes itself. 
- Added more samples for Bardic, LM Fiddle, Basic Fiddle and LM Bassoon, so that less samples have to be interpolated.
    They will sound slightly more accurate for specific notes now.
    Maestro might also use less CPU in preview now, downside is it will consume more memory and the install files are larger.

Version 3.1.9
- Tweaked histogram colors. Green is for polyphony count under 45 notes, yellow is 45 to 64, and red is 64+.
- Added horizontal lines to histogram to show where the two color thresholds are.
- Maestro won't complain about a missing midi/abc file if Maestro can find the file in the MSX directory.
- Added option for deleting notes quantized to zero duration to prevent dissonance from unintended overlapping of fast notes.
- Adjustment for how histogram count very long notes that are being broken up.
- Added option for Ignore expression messages. This is mostly useful for people that commonly use self made MIDIs output from Musescore,
    and want a 1:1 relationship between note velocities. If in doubt, leave it unchecked.
- Fix ABC triplet parsing bug, which caused some ABC files to have parsing errors.
- Add support for parsing ABC files with bar repeat symbols. Maestro/AbcPlayer will play repeating sections only once, just as Lotro does.

Version 3.1.8
- Shrink abctools icon to mitigate antivirus false positive

Version 3.1.7
- Fixed exporting to ABC would change histogram
- Fixed delays were accounted for twice when making histogram

Version 3.1.6
- Added fade-in and fade-out to tune-editor to allow fading all parts together.

Version 3.1.5
- Fix for histogram getting glitchy.
- Fix for Student's FX Fiddle stopped working.

Version 3.1.2
- Added a histogram of note count polyphony. Is more accurate than the note count indicator was.
- Removed note count indicator textlabel.

Version 3.1.0
- Switched to launching Maestro using exe files rather than bat files. No more blank icons!
- Maestro is now installed in the 64-bit programs folder (C:\Program Files\Maestro)
- Maestro and AbcPlayer can now be pinned to the Windows taskbar.
- Added a Windows context menu entry, so you can right-click ABC or MIDI files and choose "Edit with Maestro".
- Fix not playing initial ABC notes, when Maestro first starts playing a file.
    The fix only applies to parts lower than 15. Large songs will still skip initial notes at first playback.
- When you use the zoom feature, the controls are now locked to the left side.
- Made the track delay window modal, so you can use Maestro while it's open.
- Added a bar position indicator while dragging the playhead. Helpful for figuring out the bar ranges for a section or tune edit.
- Fixed a bug with the note and peak counters rendering weird on the old theme. Font is now consistent with the rest of the UI.

Version 3.0.3
- Support for exporting ABC preview MP3/WAV files from Maestro, in addition to ABC Player.
- File menu options for reloading an MSX project's MIDI file without closing Maestro (shortcut Ctrl-R), or changing the source MIDI file of a project.
- Maestro maintains a list of recently opened project files in the file menu for quick access.
- Add selection controls for selecting enabled drum hits. Select all, select none, invert selection, copy/paste selection between parts.
- Experimental change to tune/section editors to fix Linux issue.
- Add support for right-click soloing Xtra drum notes
- New bigger sliders for volume/pan control in Maestro
- Fix bug where deleting a soloed part would result in Maestro being silent
- Fix bug where releasing right-click solo on a drum track would reset the state of soloed parts

Version 3.0.1
- Support for midi expression messages. This will in some MIDIs give increased dynamic range on note volumes.
    MSX project files saved with an older Maestro will not use this new note volume system.
    Not even if you save them again, starting fresh or editing the MSX file manually is the only way.
- Channel volume and expression messages are no longer filtered in MIDI file playback.
    Known issue: When sliding the Maestro volume slider fast, the sound might pop and crackle a bit.
- Maestro now remembers where you put the panel divider between the notegraphs and the part list.

Version 3.0.0
- Fix Maestro would fail silently at loading a midi with a timesignature that was missing some data, but otherwise was correct.
- Add any missing EndOfTrack events when loading MIDI.
- Add solo/mute buttons for ABC preview to the parts list.
- Add ability to ctrl-right-click a track to solo all parts which use that track.
- Remove reverb and chorus on Gervill MIDI out device when that is selected to play source midi.
- Semi-fix for polyphony counting too high when skipping in song during playback.
- Maestro and Abc Player can now preview 24 part songs, and export them to MP3.
- Max note counter can now go up to 128.
- Fix for abc playback could pop and click, especially when skipping in songs with many parts.
    This might still be an issue on a slower PC.
- Fix when opening a MSX, and it starts auto playing, it would not skip silence.
- Made light theme the default
- Fixed that a pitchbend or panning message on one track could end up not affecting another track, even though they were on same channel.
- Allow MIDIs to adjust pitch bend range using data button increment/decrement instead of setting only absolute values.
    This is a very rarely used method, but now Maestro supports it anyway.
- Fix that bends that took effect at start of notes, might not have been in effect due to order of midi-events.
- Parts of the bent notes will no longer be quantized to zero duration and discarded, possibly leaving a gap in the bent tone.
- Pitch bends that spans less than a selected range is now treated differently: (the range can be chosen in misc settings)
    Improves the quantization by doing it later after the grid is computed.
    Notes that belonged to same bent tone will no longer be partly forced octave transposed, now they are transposed together.
    Now painted as one note. Also section-editor treats them as one note. So its not possible for example to mute half of it.
- Mix timings upgraded to version 2.
    Note endings will now have even less influence on the timing compared to note starts. Plus other small improvements.
- Added a tool that can take an folder of msx project files and auto export them all into abc songs.
- Fixed that if a tempo change was made in tune-editor and the end bar was after the end of the song, mix timings could fail.

Version 2.5.0.117
- Ability to customize instrument names for default part naming.
- Possible fix for not finding the sf2 soundfont when starting by clicking on the jar.
- Option in section-editor to exclude notes with specified pan.
    For example it can be set to not play notes that are panned left.
- Support for importing and exporting part numbering configurations.
- Re-open settings at the same page if "reset page" was used.
- Fix bug where "overwrite" confirmation for exporting drum maps would still overwrite if cancel was selected.
- The msx filename for saving Maestro projects will be built from the ABC filename export pattern if it is enabled.
- Fixed that some MIDIs could make Maestro freeze.

Version 2.5.0.116
- Added an Abc Merge Tool. It can takes multiple single-part ABC files and merge them into one multi-part ABC song.
- Added an option to section editor to reset volumes on source notes (e.g. midi notes),
    so seen from Maestro there will be no volume variations in the midi file in the specified section (beside from section changes).
- Add ability to reset setting pages. Each page has gotten a Reset Page button.
- Add part count indicator to Song Parts title text.
- Option in section editor to set all note volumes to default.
- Added a warning if you try to open a MSX project file created on a newer version of Maestro with an older version.
- Fix that when installing Maestro fresh, the drummap could be empty.
- Maestro can now be used while Section and Tune editors are open.
- Trim non-active parts from ABC export.
- The part list can now be resized.

Version 2.5.0.115
- Space can now be used to play/pause.
- It is now again possible to start multiple Maestro or AbcPlayers.
- AbcPlayer can now export MP3 on Linux and MacOS also.
- Internal refactoring and update java target to JRE 8.
- New menu option to save a midi, which will expand tracks to only have 1 instrument per track.
    After saving the new midi, it will ask if you would like to load the new expanded midi.
- Add support for using pattern to define export ABC filename. 

Version 2.5.0.114
- Fix missing file in MSI install.

Version 2.5.0.113
- Added flat dark and flat light user-interface modes.

Version 2.5.0.112
- Fixed an issue in AbcPlayer where it would think a note was shorter than 0.06s, but it was due to precision error in 17th decimal place.

Version 2.5.0.111
- Added option to convert most extended ascii and diacritical marks in ABC to simple latin letters and remove unicode chars.
    This will be enabled by default.
    It is useful for most LUA based songbooks that have trouble with many non-latin letters.

Version 2.5.0.110
- Fixed that some unicode chars in filenames could prevent an already running Maestro/Abcplayer from opening the file by double-clicking in Windows.

Version 2.5.0.109
- Fixed a regression in the auto part numbering scheme introduced in v2.5.0.102
- When changing an instrument to another that has same number scheme, for example Lute of Ages to Basic lute, the part number will now not change.
- A fix for that when switching auto part numbering scheme it would sometimes not convert all instruments to new scheme.

Version 2.5.0.108
- Ignore when receiving data that is not a filename. This fixes the odd error messages that could pop up. 

Version 2.5.0.105
- Made the bar lines inside edited sections be greenish. When the bar lines is dense it can otherwise be hard to see edited areas.
- Fixed that the exe files was not removed from ZIP distribution.

Version 2.5.0.104
- Removed the exe files, it will now use the bat files instead.
    This should solve the issue some users have experienced with running out of memory.
    The first time you run Maestro or AbcPlayer, your firewall might ask you to allow Java to connect to network. You can allow or disallow, it does not use Internet, so either should work.
      Technical explanation: It use ports locally for passing file path to already running program when opening files, hence why firewall asks.

Version 2.5.0.103
- Doubled the number of available sections in section-editor and tune-editor. Moved the help text into a tooltip, just hover mouse over 'Help'.

Version 2.5.0.102
- When manually changing a part number so that the numbering scheme no longer applies for 1 or more parts,
    those parts will not be auto assigned new numbers but will keep their numbers. 

Version 2.5.0.101
- Made sure polyphony counter gets reset when switching to original preview.
- Added option to choose MIDI playback device.
    If a device is selected but no longer connected, default will do the playback.
- Added support for ports (which is not a part of the midi standard).
    Programs such as Cakewalk and Musescore export those kind of midi.
    Maestro default midi device still wont playback the midi correct, but at least the tracks instrument names should now be correct.

Version 2.5.0.100
- Added a Tune editor that allows setting tempo changes and seminote transposing for certain bars.
    Works like Section Editor and copy/paste works from section editor too.
    The button is marked "T" and will become green when edits has been made.
    The tempo panel above the tracks will also show the edits with a dark green color. But neither
    the tempo-lines or the tempo-indicator will change, it will still show the original tempo.
    Beware of pitfalls:
      Setting too high or too low tempo, might cause a popup warning that tempo at a certain points get beyond the limit.
      If setting a high tempo, it might also pop up a warning that meter denominator is too high. If that happens, either
      reduce the tempo again, or set the denominator to a lower value. For example if it was 4/4 then you can try 2/2 or 4/2.

Version 2.5.0.99
- MIDIs with low time resolution that can not be halved many times, will now be PPQ upscaled when Maestro loads the MIDI.
    If you have in the past exported ABCs where the timing sounded off, it is recommended to re-export them from midi with this version.
    For some midi with this issue, export were not affected though. And for some only normal rhythm was affected while swing was okay. And some opposite.
- Fixed that when the midi had a track where ASCII control character(s) was used, it would save a project file that could not again be opened.
    The project file saved is now xml v1.1 instead of v1.0, which allows reading of such chars.
    If you have some old project files that you could save but not open again afterwards, just edit it and change 1.0 to 1.1 in the first line.
- Added bat files to the MSI installer also.
- Added German and French nicknames for instruments. These are used when loading up old ABCs into Maestro and AbcPlayer, plus in the Numerate button.
    Thanks to Brago and Thalvo for helping.

Version 2.5.0.98
- Fix again that drum panel was still misaligned by 4 pixels.

Version 2.5.0.97
- Fix that drum panel was misaligned
- Improved Abcplayer ability to verify FFmpeg file.

Version 2.5.0.96
- Introduced combine priorities.
    When using Mix Timings it allows to set which tracks are more important with regards to rhythm,
    when you combine multiple tracks into a part. When this is enabled and tracks are combined,
    a checkbox will appear for each combined track for increasing priority.

Version 2.5.0.95
- Fix that Maestro would output ABC timestamps at each bar that did not take tempo change into consideration.
- Set a limit to the amount that Maestro adjust long note breaks to fit on bars.
- Add .bat files to the zip release that will ensure that if you have both 32 and 64 bit Java installed, that it starts the 64 bit version.
- AbcPlayer can now use FFmpeg to make MP3 files.
    There is a 3 second timer, from clicking the menu till you get dialog up, just like for LAME.
    I used v4.4.1-release-full, but newer versions should work too, and vX.X.X-release-essentials versions also, they are less bloated.
    This is not an endorsement for FFmpeg from me. I have no idea which version your going to get,
    if you trust it, use it. If you don't, don't use it. Don't blame me, I just choose it due to its versatility and popularity.
- When starting Maestro with 32 bit Java, there will now pop up a warning.
- Added heap memory usage to Maestro about dialog.
    If you should run out of memory using 64 bit java, try use the bat files that came with the ZIP or double click the jars directly.
    The sign you are running out of memory is usually that the interface starts to get drawn weirdly and/or Maestro acts up.

Version 2.5.0.94
- Added a note button next to zoom button. The note you write will be saved in the msx project file, not the abc file.
    You can use it to keep notes of why you arranged it a certain way,
    or what still needs to be done. Or to keep track of the lyrics.
- A 99% fix for Soundblaster Audigy 5 not respecting master volume setting when skipping in certain MIDIs.
- Added a Numerate button to auto assign numbers to identical instrument part titles
    So "Lute" and "Lute 4" will become "Lute 1" and "Lute 2".
    It doesn't work when part titles is not in English or if an instrument name is not recognized.
    Basically it does not attempt to modify or count parts that does not start with an instrument name and then ends or have a number at the end. 

Version 2.5.0.93
- A more elegant solution to not allowing to delete last part.
- Disable Close Project menu item, when nothing is opened.
- Add copy/paste buttons to section-editor. Works across parts. Will only copy the bar starts and ends plus if they are enabled.
- If open a msx file and had to find the midi file in another than the original location, then the project will now be marked as modified.
- When opening a MIDI it will now do a better job of guessing the artist and title from the filename. Sometimes it might switch them around though.
- When suggesting an abc filename, it will now remove extra dots, as songbook do not like them.
- Add an export timestamp to the abc header, like this example: %%export-timestamp 2022-10-22 08:26:11

Version 2.5.0.92
- Added an Edit menu item for (de)selecting all tracks in AbcPlayer.
- Added a Recent Opened files menu item in AbcPlayer.
- Prevent clearing tracks and mess up MIDI player by not having an abc part selected (does not matter if its assigned or not),
    so often only MIDI drum tracks would play. Wouldn't mess up ABC preview playing though.
    This is the new mechanisms:
    - Prevented Delete part button from deleting the last remaining part from the list.
    - When deleting the first part in the list, the new first part will now be properly selected.
    - If CTRL-clicking the selected part, the first part will now be selected, so a part is always selected.

Version 2.5.0.91
- Support for very long MIDIs.
    Tested up to 2 hours and 11 minutes, but probably can handle much more.

Version 2.5.0.90
- Fix for missing "Delete" part button on MacOS.
    It will make the left panels wide enough to show LM Bassoon as a part. Which will then also make room for the delete button.
- LCM algorithm improvement for mix timing. Result should be the same, but slightly faster.

Version 2.5.0.89
- Attempt for fixing missing "delete part" button on MacOS. Didn't pan out.

Version 2.5.0.88
- Notes in non-sustained parts that has already been started, will now get very high priority for pruning subsequent tied notes.
    This is due to some experiments in lotro that shows that they do not count for the max 6 limit.
    
Version 2.5.0.87
- Special release that supports songs over 2 hours. This is only in this release, next release will be normal again.  

Version 2.5.0.86
- Refined the note pruning algorithm for staying under 6 simultaneous notes per instrument. Done in cooperation with Jersiel.
- Removed the "Show discarded notes in yellow" feature.
    It could get confusing when only part of a note got discarded, but the UI would show entire note as discarded.

Version 2.5.0.85
- Made extra check to be sure no duplicate drum notes are output as lotro will accept them but not double play them.

Version 2.5.0.84
- Added 8 new drum sounds that each is a combi of 2 drum sounds, their names start with "Xtra".
    The sounds are some bass, snares and a crash cymbal. Jersiel supplied the combis, except one.
    Note that because they are 2 sounds, they will sound louder, and will eat into the 6 note limit fast, plus add to the 64 full song limit also.
    Note pruning down to 6 for a part, might split them up too, prune one of its sounds but not the other.
    So be very mindful of how the track(s) and song is made when considering to use these.
    Its mostly a convenience for small bands. If you need combis and have band members enough,
    consider choosing 2 drum parts instead and make combis yourself to circumvent the 6 note limit.
    Note that the new Xtra combi sounds cannot be listened to as solo drum sound, but you can still listen solo to the whole drum part.
- Note pruning down to 6 for drum is now less arbitrary. Louder notes are prioritized and bass notes too.
    Note that pruned drum notes is not shown in yellow even if that feature is enabled.
- Made a new comment field in each part that tells which lotro instrument it was made for (%%made-for).
    Since transcribers might not use the instrument-name when they name a part, or use it in another language,
    this is added so can choose the right instrument in AbcPlayer and Maestro when loading abc files, less guessing.
    Only forward compatible. If loading old abc files, it will work the old way with guessing from %%part-name or part title (T:).
- Made sure peak counter is reset when opening new song. But if notes from previous song is still in decay phase then they might increase the counter anyway.
- Changed font of polyphony counter.
    
Version 2.5.0.83
- Fix that 64 bit Maestro and AbcPlayer would not open a different file when already running if initialized by double-clicking in Windows.

Version 2.5.0.82
- Made 64 bit the default version.
- Removed support for 32 bit.
- Included more samples for Horn and Bagpipe.
- Polyphony counter now also shows peak concurrent playing notes. Suggested by Jersiel.
    But do remember again that due to technical reasons Maestro note fadeout time is longer than in lotro,
    and since notes in fadeout phase is counted, the polyphony numbers have a tendency to be too high.
- German and French words for 'center', 'left' and 'right' in part titles will now also change stereo pan.
    The words recognized is now: left, links, gauche, right, rechts, droite, middle, center, zentrum, mitte and centre.
    Upper or lower case does not matter.
- Added support for Songbook variants based on The Badgers Chapter.
    It can output title, genre and mood.
    There is also an option to output all parts played for convenience.
    There is no way to select individual parts to be played though, to do that have to use songbrowser.exe

Version 2.5.0.81
- Maestro now accept karaoke midi files with .kar ending.
- Added Close Project to main file menu. Suggested by Paane.
- Polyphony counter now goes orange above 54 notes. Suggested by Jersiel. Will still go red at 64+.
- Fixed that rare MIDIs with very low Pulse Per Quarter Note (PPQ) resolution could change note and rest lengths in non-swing mode and in mix timings mode.
    The fix makes the output in sync and correct tempo when using non-swing timing or mix timing or both.
    It does however not solve the imprecise timings of such MIDI when converted to ABC.
    I suggest for such MIDI, edit them to a finer resolution (for example PPQ of 480) in a MIDI editor.
    Thank you to Jersiel for hearing the issue in the first place.
- Fixed that final rest in a abc part would start where the event that began last would end,
    instead of be sure to pick the event that actually ended last and use that ending.
- Fixed that the very last chord of a abc part would not be broken up and tied,
    but instead would just be output even if notes in had different lengths.
    Lotro would not complain, but for consistency it will now also be broken up and tied. 

Version 2.5.0.80
- ABC preview in Maestro will now work with 15 parts instead of just 14.
- ABC preview might work with more than 15 parts, if certain conditions are met.
    Maestro internally uses midi to play the preview.
    When you make a song with more than 15 parts, it will try to combine parts
    using 2 methods in order to share midi channels of which there is a limited number.
    If unsuccesful, then an error message will pop up when preview is in ABC mode. 
    There is an absolute max of 24 parts due to Lotro's raid maximum.
    AbcPlayer and Maestro still wont play ABC files that have more than 14 parts,
    it only works for preview when source is a midi file. Might work on this in future version.

Version 2.5.0.79
- Added option to show polyphony count while previewing abc song. See tooltip in options for more info.
- In the delay dialog the time can now be entered using comma as decimal point. Suggested by Notenzauber.
- In XG, GM2 or GS midis, drums tracks will no longer get assigned some random GM instrument voice.
    They will be named "XG Drums", "GM2 Drums" or "GS Drums", and will some times still sound wrong when playing the midi.
    But now at least they will be marked as drum track so people don't assign melodic instruments on them.
- Added support for showing GS, XG and GM2 instrument and drum kit names. If it does not know the name then it fall back to GM name.
    Number of known instrument and kit names has gone from 129 to 2446 in total.
    The name lists was compiled mostly by github.com/jazz-soft and then edited and expanded by me.

Version 2.5.0.76
- Change preview dynamics for +pppp+ to same as +ppp+ to match Lotro.
- Include x3 times as many horn samples to make it sound more like Lotro.

Version 2.5.0.75
- Fixed that a MIDI Note ON event after a Note ON for same pitch, would remove the first Note ON event if the second had non-zero velocity.

Version 2.5.0.74
- Mix Timings now no longer consider section silenced notes when determining rhythm.
- When a Lotro instrument part stop using a section edited track, also stop visualizing the section edits, even though it remembers them.
- Fix that when attempting to load a Maestro Project that has parts assigned to MIDI tracks that does no longer exist in the MIDI,
    Maestro would sometimes give the user no feedback on why it does not open. It was due to flooding of whitespaces to main Maestro window,
    which forced the important message out of view.

Version 2.5.0.73
- Mix Timings is now enabled per default for new projects.
- Will now export swing/mix timing checkbox settings to abc file as comments.

Version 2.5.0.72
- Octave doubling will now show visually in the notegraph also.
    This is helpful for evaluating out of range notes.
- Less interruptions in very long sustained notes (up to 5s).
- Improved Mix Timings algorithm near tempo-changes.
- For drum parts Mix Timings now only use note start times, not end times for evaluating rhythm.

Version 2.5.0.71
- Fixed that after loading a Maestro Project from .msx file, existing drum-parts would not instantly update the preview audio when individual drum notes were changed.

Version 2.5.0.70
- Added checkbox for Mix Timings, where it will do parts of song in swing/triplet/even timings depending on the detection of note rhythms.
    This is instrument-part independent, it might put notes on swing rhythm on the flute while at the same time keeping even rhythm on drums.
    If that is enabled then the old triplet/swing checkbox will still set the default grid for majority of the song. So remember to still
    set the triplet/swing checkbox to what most of the song conforms to.
    One drawback is that with Mix Timings enabled you can no longer reliable hand-edit the .abc file and copy-paste bars. A bar might not be 100%
    matching duration of same bar in other parts, or exports with different timing settings (this is a ABC cosmetic issue only, the notes will play at correct time). So be careful with editing.
- Fixed that if sections were not entered in order, the green background coloring of the track would not cover all sections.

Version 2.5.0.69
- Added ability for octave doubling of sections. Increased number of sections from 6 to 10.
    Note this will not be visible in the notegraph, but can be heard in abc preview. To visualize it, save to abc and load up the abc to inspect.
- Added option in section-editor to silence or octave double the parts of the track that is not covered by sections.
- Fixed regression of minimum tempo to 12bpm instead of Maestro standard 10bpm.

Version 2.5.0.68
- Enabled section editor for percussion also. (Cowbells, Drum, Student FX)
- The delays can now be heard in abc preview in Maestro.

Version 2.5.0.67
- Added student fiddle as an percussion instrument also, with only the noises. Student's FX Fiddle.

Version 2.5.0.66
- When breaking up long notes, allow them only to be 5s instead of 6s, as some samples are only around 5s long.
- When breaking up long notes, prioritize again to do it at bar boundaries if possible. Allow it to happen also at the first bar boundary it crosses though.
    Else break them close to 2.5s to still prevent rounding notes to zero duration.
- Reduced max meter denominator to 8 again to avoid divide by zero.
- Fixed that when loading up an ABC file into Maestro 64bit and not having volume at max, the volume could gradually become lower when skipping around while ABC previewing.

Version 2.5.0.65
- More tidy output of long rests while still preventing too long rests.

Version 2.5.0.64
- Better detection of when lotro determines minimum note duration by using 'equal to' vs 'greater than'.

Version 2.5.0.63
- Added a stereo slider, goes from mono to wide stereo panning for ABC preview.

Version 2.5.0.62
- Reinstated about half of the hold at end of notes for more mimic of lotro release.

Version 2.5.0.61
- Reverted back to 2.5.0 release envelope duration, as it was a better lotro approximation.
    Thanks to Pontin for good feedback and to Jersiel to spot that the sound issue in Pontin's video was in the note releases.

Version 2.5.0.60
- Built a .msi installer and 64-bit executables. 32 bit will still be the default that is run if you double click on MIDI, abc or msx files (unless you change it).

Version 2.5.0.59
- When exporting with changed tempo, the part description duration should now be correct.
- The prune algorithm now consider the highest and lowest notes in the chord as that from their pitch before they were compressed into 3 octaves.
    Thanks to Jersiel for inputting that this way makes more sense.
- Fixed an old bug that with some MIDIs could result in notes of duration zero seconds, which lotro would reject.
    The fix I did in 2.5.0.46 would actually allow this old bug to happen more often. But all good now, its fixed.
- The ZIP file now includes more files and install instructions has changes, scroll down to see them.

Version 2.5.0.58
- Readded all plucked samples to the .sf2 file to reduce CPU usage when previewing.

Version 2.5.0.57
- Enabled faster memory cleanup of map of discarded notes inside NoteEvents, and use less memory too.
- Reduced and changed which samples is shipped in the .sf2 file, to reduce memory usage.

Version 2.5.0.56
- Added possibility to add a delay up to 1 second on a part. (but only if tempo is higher or equal to 50 bpm)
    This can be used to get a chorus or echo effect by having two similar parts where one of them has a small delay.
    When you first click the Delay Part button below the instrument part list,
    the dialog will popup in upper left corner of your screen (like section editor will too).
- Section editor and Delay Part dialogs will now pop up where they were last closed.

Version 2.5.0.55
- Now the new 2.5.0.42 discard algorithm will play in preview also, instead of only happening during export to ABC.
- The discarded notes will now show as yellow when in Abc Preview if enabled in options.
- Zoom will now also zoom a little vertical.
- Fixed that ABC-Player would not detect too short or too long notes if tied. Also beautified the messages slightly.
- Section editors input lines no longer need to be in chronological order.

Version 2.5.0.53
- Fixed bug that sometimes would make Delete button greyed out after deleting an instrument part.
- Added two more tooltips.

Version 2.5.0.52
- Fixed that some rare MIDIs when changing tempo in Maestro and when in ABC Preview, would show a wrong total song duration.
    It would however always export the correct duration, only the displayed duration was affected.
- When a section has been silenced it will no longer visually show the volume of the silenced notes.

Version 2.5.0.51
- Allow to load a section from .msx file that does nothing. Sometimes its handy to have such section saved, when doing WIP.
    Bar numbers must still meet requirements though. Still only enabled sections will be saved.
- Centered the input numbers in the section editor.
- Now stores the edit line in section editor into the section data, to make it show on same line again. Also saves it in the .msx file.
- Added possibility for fade-in also. And fading is now adjustable in amount.
    A value of 0 mean no fading. 100 means full fade-out. -100 means full fade-in.
    Magnitudes larger than 100/-100, for example -150, will 'over-fade',
    meaning the fading will become so steep it takes place over less time than the section spans.
- Added show volume button in section editor. Press down and hold to see volumes in track.
- Added tooltips to section editor input fields.

Version 2.5.0.50
- Improved check for overlapping sections in section editor.

Version 2.5.0.49
- Fixed that if a drum track with drums was active while zooming in its drum notes could not be seen.
- Section editor will now disable bad input when clicking on APPLY. Plus added more info text.

Version 2.5.0.48
- Fixed that zoom button sometimes would not work.

Version 2.5.0.47
- Added a zoom button in lower right corner.
- Reverted the fix for bar maximum counter and expanded its tooltip info.
	Seems some songs it worked for, and some only for the MIDI preview and not for the ABC preview.
	Further investigation is needed for better fix.

Version 2.5.0.46
- Fix for very slow songs with long bars (for example 50bpm 2/2) could sometimes export a rest longer than 8s, which would make lotro refuse to play it.

Version 2.5.0.45
- Added option to fadeout specific sections.
- Optimized some code in section handling.
- Fixed that bar counter maximum in Maestro and ABC Player would show one too much.

Version 2.5.0.44
- Added option to change volume of specific sections of a track.
	How much the number affects the volume depends on the notes volume in the MIDI and on what the volume slider for the track is set to.
	Its basically an MIDI velocity offset. Examples: -50, 10, 151 etc etc.
    Although MIDI only goes from 0-127 you can enter a greater number offset to also offset the track volume slider.
- Adjusted some instrument volumes.
	This was done by carefully using same velocity/note and comparing them against Lute of Ages. Done by recording in Audacity and check (normalized with lute A) difference between them in lotro and in Maestro.
	LM Fiddle, Basic fiddle, traveller's was changed. Have not checked cowbells, pibgorn, theo, drum, brusque, b.bassoon, st.fiddle yet. Ongoing work.

Version 2.5.0.43
- Allow time-signature denominator up to 32 from 8.
- Added option to change octave or silence specific sections of a track by clicking button next to volume slider.
	Using this with long songs with many parts might make Maestro less resposive, especially when preview is running.
- Improved the max 6 note discard algorithm by discarding non-sustainable instrument notes that have tie to previous note and already played past the 1.1s limit.

Version 2.5.0.42
- Fix for LOTRO failing playback with Note's duration is too short
- Removed stereo and reverb in Maestro ABC preview
    This will make it much easier to accurate judge the instruments volumes compared to each other.
- Export explicit unit note length to ABC output for good measure.
- Fixed a meter division that was cast in a non helpful way
- Made ABC Player check duration limits of rests also and those check is now made against actual note durations, not converted tempo changes in ABC file comment sections.
- When loading ABC file with non-abbreviated tuplets like for example '(3:2:3' it will now process it properly instead of failing.
- MIDI instrument names now more in line with official specification.
- Preview note endings now sound more in line with lotro's way of ending notes. Was careful measured, should be fairly accurate.
- The soundfont now contains all pibgorn and clarinet notes. Reason is that they vary alot in quality and that should be heard in preview to not make songs more beautiful than lotro renders them.
    This will make the soundfont file slightly larger though and make Maestro consume more memory.
- Track panel title tooltip will now show count of bad clarinet/pibgorn notes.
    To see this, select pibgorn or clarinet, and hover mouse over a track title.
- Lotro has a max 6 notes played at the same time per instrument. The way notes are discarded is now done with a much smarter algorithm instead of often discarding the highest pitched notes.
    This feature is very very handy for solo and small bands, where multiple tracks often is combined into same instrument.
    Jersiel co-developed the algorithm for this.






Want to have multiple different Maestro versions installed?
===========================================================
Somebody asked me how to do this, so here is answer:
If you want different versions of Maestro installed at same time, just install using the zip as described at top of this document.



FAQ
===
Will the upgraded Maestro .msx files load into old 2.5.0 Maestro?

- Yes, but new features will be ignored. Combi drum sounds will be silenced. And more than 15 parts wont be previewed.
  Also note that the midi notes might be grouped into different tracks, for example drum notes can appear inside e.g. a piano track.


I get a crash, how can I find out what caused it?

- Start Maestro from commandline
  It should then output a stacktrace when crashing. If unsure on how to read it, just send to Aifel (Laurelin) or Elamond (Landroval).


After having put Windows to sleep while Maestro were running the preview starts to sound uneven, there is preview delays. How to fix?

- Save your work, close Maestro and reopen it. Its best to close Maestro before putting Windows to sleep.


Can I compile the programs myself, can I get the source-code?

- Yes its open-source, just ask and we will help.
