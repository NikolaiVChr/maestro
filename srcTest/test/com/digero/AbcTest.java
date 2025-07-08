package com.digero;

import com.digero.common.abctomidi.AbcToMidi;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AbcTest {
    @Test
    public void testTuplet() {
        // Unit test for tuplets
        //
        String[][] lowTests = {{"3","(3:2:3"},{"3:2:","(3:2:3"},{"3:2","(3:2:3"},{"3::2","(3:2:2"},{"3::","(3:2:3"}};
        for (String[] test : lowTests) {
            assertTrue(AbcToMidi.testTuplet(test, false), "Tuplet failed: " + test[0]);
        }
        String[][] highTests = {{"5","(5:2:5"},{"7:2:","(7:2:7"},{"9:2","(9:2:9"},{"5::2","(5:2:2"},{"7::","(7:2:7"}};
        for (String[] test : highTests) {
            assertTrue(AbcToMidi.testTuplet(test, false), "Tuplet failed: " + test[0]);
        }
    }

    @Test
    public void testCompoundTuplet() {
        // Unit test for tuplets
        //
        String[][] lowTests = {{"3","(3:2:3"},{"3:2:","(3:2:3"},{"3:2","(3:2:3"},{"3::2","(3:2:2"},{"3::","(3:2:3"}};
        for (String[] test : lowTests) {
            assertTrue(AbcToMidi.testTuplet(test, true), "Tuplet failed: " + test[0]);
        }
        String[][] highTests = {{"5","(5:3:5"},{"7:2:","(7:2:7"},{"9:2","(9:2:9"},{"5::2","(5:3:2"},{"7::","(7:3:7"}};
        for (String[] test : highTests) {
            assertTrue(AbcToMidi.testTuplet(test, true), "Tuplet failed: " + test[0]);
        }
    }
}
