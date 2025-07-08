package com.digero;

import com.digero.common.abctomidi.AbcToMidi;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AbcTest {
    @Test
    public void testTuplet() {
        // Unit test for tuplets
        // TODO: Include compound time boolean in tests
        //
        String[][] tests = {{"3","(3:2:3"},{"3:2:","(3:2:3"},{"3:2","(3:2:3"},{"3::2","(3:2:2"},{"3::","(3:2:3"}};
        for (String[] test : tests) {
            assertTrue(AbcToMidi.testTuplet(test, false), "Tuplet failed: " + test[0]);
        }
    }
}
