package com.digero.maestro.abc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.digero.common.i18n.LocaleManager;
import com.digero.common.i18n.UIText;
import com.digero.common.util.FileParseException;
import com.digero.common.util.WarningHandler;
import com.digero.maestro.util.FileResolver;
import com.digero.maestro.view.MiscSettings;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.prefs.Preferences;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AbcSongFileVersionTest {

    @TempDir
    Path tempDir;

    /**
     * Warning handler that always proceeds on warnings.
     */
    private static final WarningHandler PROCEED_ON_WARNING = (warningId, title, message) ->
        WarningHandler.WarningAction.PROCEED;

    /**
     * Test preferences used in the tests.
     */
    private Preferences testPreferences;

    /**
     * Part auto-numberer used in the tests.
     */
    private PartAutoNumberer partAutoNumberer;

    /**
     * File resolver that always throws an assertion error, indicating that file resolution should not be needed.
     */
    private static final FileResolver NO_FILE_RESOLUTION = new FileResolver() {
        @Override
        public File locateFile(File original, String message) {
            throw new AssertionError("File resolution should not be needed: " + message);
        }

        @Override
        public File resolveFile(File original, String message) {
            throw new AssertionError("File resolution should not be needed: " + message);
        }
    };

    @BeforeEach
    void createSourceMidi() throws Exception {
        LocaleManager.init();
        UIText.init();

        // Initialize test preferences.
        testPreferences = Preferences.userRoot().node("maestro-tests/" + UUID.randomUUID());

        // Initialize the part auto-numberer.
        partAutoNumberer = new PartAutoNumberer(testPreferences.node("partAutoNumberer"));

        // Create a simple MIDI sequence for testing.
        Sequence sequence = new Sequence(Sequence.PPQ, 480);

        // Create a track and add some MIDI events.
        Track track = sequence.createTrack();
        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100), 0));
        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 480));

        // Save the MIDI sequence to the temporary directory.
        MidiSystem.write(sequence, 1, tempDir.resolve("test.mid").toFile());
    }

    @AfterEach
    void cleanUpPreferences() throws Exception {
        if (testPreferences != null) {
            testPreferences.removeNode();
            testPreferences = null;
        }
    }

    /**
     * Tests that the old file version uses legacy semantics.
     */
    @Test
    void oldFileVersionUsesLegacySemantics() throws Exception {
        AbcSong song = loadProject("3.3.3.300");

        try {
            // The first key of tuneBars should reflect the legacy semantics for old file versions.
            assertEquals(1.0f, song.tuneBars.firstKey());
        } finally {
            song.discard();
        }
    }

    /**
     * Tests that the current file version uses the current semantics.
     */
    @Test
    void currentFileVersionUsesCurrentSemantics() throws Exception {
        AbcSong song = loadProject("4.6.26.300");

        try {
            // The first key of tuneBars should reflect the current semantics for the current file version.
            assertEquals(2.0f, song.tuneBars.firstKey());
        } finally {
            song.discard();
        }
    }

    /**
     * Tests that a missing file version defaults to Maestro 2.5.0 compatibility.
     */
    @Test
    void missingFileVersionUsesMaestro250Compatibility() throws Exception {
        AbcSong song = loadProject(null);

        try {
            // The first key of tuneBars should reflect the Maestro 2.5.0 compatibility for missing file versions.
            assertEquals(1.0f, song.tuneBars.firstKey());
        } finally {
            song.discard();
        }
    }

    /**
     * Tests that malformed file versions are rejected.
     */
    @ParameterizedTest
    @ValueSource(strings = { "", "invalid", "3.foo" })
    void malformedFileVersionRejectsProject(String fileVersion) {
        // Attempting to load a project with a malformed file version should throw a FileParseException.
        assertThrows(FileParseException.class, () -> loadProject(fileVersion));
    }

    /**
     * Creates a project file with the specified file version and loads it as an AbcSong instance.
     */
    private AbcSong loadProject(String fileVersion) throws Exception {
        String versionAttribute = fileVersion == null ? "" : " fileVersion=\"" + fileVersion + "\"";

        Path projectFile = tempDir.resolve("test.msx");

        Files.writeString(
            projectFile,
            """
            <?xml version="1.1" encoding="UTF-8" standalone="no"?>
            <song%s>
                <sourceFile>test.mid</sourceFile>
                <autoSortedParts>false</autoSortedParts>
                <tuneSection>
                    <startBar>2</startBar>
                    <endBar>4</endBar>
                </tuneSection>
            </song>
            """.formatted(versionAttribute)
        );

        // Load the project file as an AbcSong instance.
        return new AbcSong(
            projectFile.toFile(),
            partAutoNumberer,
            null,
            null,
            null,
            NO_FILE_RESOLUTION,
            new MiscSettings(null, true),
            false,
            null,
            true,
            PROCEED_ON_WARNING
        );
    }
}
