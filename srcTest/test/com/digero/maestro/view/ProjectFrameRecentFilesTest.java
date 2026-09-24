package com.digero.maestro.view;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import org.junit.jupiter.api.Test;

public class ProjectFrameRecentFilesTest {

    @Test
    void failedMsxOpenIsNotAddedToRecentProjects() {
        File file = new File("broken.msx");

        assertFalse(ProjectFrame.shouldAddToRecentProjects(file, true, false));
    }

    @Test
    void successfulMsxOpenIsAddedToRecentProjects() {
        File file = new File("project.msx");

        assertTrue(ProjectFrame.shouldAddToRecentProjects(file, true, true));
    }

    @Test
    void temporaryMsxOpenDoesNotUpdateRecentProjects() {
        File file = new File("project.msx");

        assertFalse(ProjectFrame.shouldAddToRecentProjects(file, false, true));
    }

    @Test
    void nonMsxFileIsNotAddedToRecentProjects() {
        File file = new File("source.mid");

        assertFalse(ProjectFrame.shouldAddToRecentProjects(file, true, true));
    }
}
