package com.digero.common.midi;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.digero.common.abc.LotroInstrument;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.view.PanVisualizerPanel;

/**
 * Factors that influence the final panning position of a part:
 * - Total number of instruments, smaller bands get smaller total spread, so it don't sound disjointed.
 * - Put instruments in classes, and treat same class the same
 * - Whether instruments are sustained is factored into how wide an instrument prefers to sit
 * - Treats drums/cowbells/jaunty seperately
 * - Also uses octave range to determine how far from center it prefers to sit
 * - Keeps track of global balance of mix and zig-zag placements in inward spiral
 * - Makes sure instruments don't get panned to same value, then it will offset a slight bit.
 * - Still respects user-pan value and factors that into global balance and occupied positions. *
 *
 * TODO: Maybe there even could be a Part name variable for outputting what user/auto-pans are selected.
 */
public class PanGenerator {
    // This class was build in collaboration with Gemini 3

    public static final int CENTER = 64;
    public static final int LEFT = 0;
    public static final int RIGHT = 127;

    public static final Pattern leftRegex = Pattern.compile("\\b(left|links|gauche)\\b");
    public static final Pattern rightRegex = Pattern.compile("\\b(right|rechts|droite)\\b");
    public static final Pattern centerRegex = Pattern.compile("\\b(middle|center|zentrum|mitte|centre)\\b");

    // MAX WIDTH CONSTANTS (utilizing full MIDI 0-127 range)
    // Center +/- 63 covers virtually the entire spectrum.
    public static final int WIDTH_WIDE    = 63; // Sustained Fiddles/ Bassoons / Flutes / Pibgorn
    public static final int WIDTH_MID     = 45; // Lutes / Harps / Plucked and short Fiddles and Brusque / Jaunty
    public static final int WIDTH_NARROW  = 20; // Bass / Cowbells
    public static final int WIDTH_CENTRIC = 0; // Drums

    private final int[] count;

    // Tracks used slots to prevent mathematical stacking
    private final BitSet usedPanPositions;

    // Tracks the "Start Side" for each instrument family (1 = Right, -1 = Left)
    private final Map<LotroInstrument, Integer> instrumentStartSide;

    // Tracks the overall weight of the mix. +1 for Right, -1 for Left.
    private int globalBalance = 0;

    private int totalParts = 0;
    private List<PanVisualizerPanel.PartInfo> allPans;

    public PanGenerator() {
        count = new int[LotroInstrument.values().length];
        usedPanPositions = new BitSet(128);
        instrumentStartSide = new EnumMap<>(LotroInstrument.class);
    }

    public void reset() {
        Arrays.fill(count, 0);
        usedPanPositions.clear();
        instrumentStartSide.clear();
        globalBalance = 0;
        totalParts = 0;
    }

    /**
     * SORTING STRATEGY: "The Octave Waterfall"
     * 1. High Freq (Octave 3, 2) -> Process First -> Claim Edges
     * 2. Mid-Freq (Octave 1, 0) -> Process Next -> Fill Mid
     * 3. Low Freq (Octave -1) -> Process Last -> Fill Center
     */
    public void sortParts(List<AbcPart> parts, List<PanVisualizerPanel.PartInfo> allPans) {
        this.totalParts = parts.size();
        this.allPans = allPans;
        parts.sort(Comparator.comparingInt(p -> getPriority(p.getInstrument())));
    }

    public void sortInstruments(List<Object[]> instr) {
        this.totalParts = instr.size();
        this.allPans = null;
        instr.sort(Comparator.comparingInt(p -> getPriority((LotroInstrument) p[1])));
    }

    /**
     * PRIORITY LOGIC:
     * 1. Sustained/Pads (Process First -> Claim Widest Spots)
     * 2. High Pitch
     * 3. Plucked/Short (Process Later -> Fill Inner Gaps)
     * 4. Low Pitch
     * 5. Drums, Cowbells (Last)
     */
    private int getPriority(LotroInstrument i) {
        if (i == LotroInstrument.BASIC_DRUM || i == LotroInstrument.MOOR_COWBELL || i == LotroInstrument.BASIC_COWBELL) return 100;

        // Base Score from Octave (Lower Octave = Higher Number/Later Priority)
        // Range approx: 3 (High) to -1 (Low)
        int score = (10 - i.octaveDelta) * 2;

        // "Sustained" instruments (Pads) should be processed BEFORE "Plucked" instruments
        // of the same octave. This ensures the Pads get the outer "shell" and
        // the Plucked notes fill the "core".
        if (!isPluckedOrShort(i)) {
            score -= 1;
        }

        return score;
    }

    /**
     * Main calculation method.
     * @param instrument The instrument to pan.
     * @param panModifier User slider 0-100 (100 = Full Wide, 0 = Mono).
     * @param requestedPan User-defined override (0-127). If null, auto-positioning is used.
     * @return A unique MIDI pan value (0-127).
     */
    public int get(LotroInstrument instrument, int panModifier, Integer requestedPan, int partNumber) {
        int targetPan;
        boolean userPanned;

        if (requestedPan != null) {
            // --- MANUAL MODE ---
            // Respect user position, but apply Global Width Slider
            userPanned = true;

            targetPan = requestedPan;

            // Update Global Balance so AUTO instruments can compensate
            // If the user forces Left, balance goes negative, pushing the next auto instrument Right.
            if (targetPan > CENTER) globalBalance++;
            else if (targetPan < CENTER) globalBalance--;

        } else {
            // --- AUTO MODE ---
            targetPan = computeAutoPan(instrument);
            userPanned = false;
        }

        if (allPans != null) {
            allPans.add(new PanVisualizerPanel.PartInfo(targetPan, Integer.toString(partNumber), userPanned));
        }

        targetPan = applyModifier(targetPan, panModifier);

        // If requestedPan causes a collision with another requestedPan, we allow it (user intent).
        if (requestedPan == null) {
            targetPan = findClosestAvailable(targetPan);
        }

        targetPan = Math.clamp(targetPan, 0, 127);

        // Mark this spot as taken (Collision Avoidance)
        // Even for manual pans, we mark it so auto-instruments don't sit on top of it.
        usedPanPositions.set(targetPan);

        return targetPan;
    }

    private float getStageSizeScalar() {
        if (totalParts <= 1) return 0.0f; // Mono
        if (totalParts == 2) return 0.40f; // Duet (Intimate)
        if (totalParts == 3) return 0.60f; // Trio
        if (totalParts < 6)  return 0.85f; // Small Band
        return 1.0f; // Full Ensemble
    }

    private int applyModifier(int pan, int modifier) {
        if (modifier == 100) return pan;
        int offset = pan - CENTER;
        return CENTER + (int) (offset * (modifier / 100.0f));
    }

    private int computeAutoPan(LotroInstrument instrument) {
        // 0. Calculate the "Small Band Correction" to prevent holes in the stereo spread
        float stageScalar = getStageSizeScalar();

        // 1. Map to Base "Voice"
        // Distinct voices (Sustained vs. Plucked) get their own counters.
        LotroInstrument mapped = mapToBase(instrument);
        int index = count[mapped.ordinal()]++;

        // 1. Determine Direction
        int direction;

        if (index == 0) {
            // First of its kind: Check Global Balance
            // If balance > 0 (Right Heavy), go Left (-1). Otherwise Right (1).
            direction = (globalBalance > 0) ? -1 : 1;

            // Save this decision so the next one of this type goes opposite
            instrumentStartSide.put(mapped, direction);
        } else {
            // Second of its kind: Look up how the first one started
            int startSide = instrumentStartSide.getOrDefault(mapped, 1);

            // If index is even (0, 2, 4), match start side.
            // If index is odd (1, 3, 5), go opposite.
            direction = (index % 2 == 0) ? startSide : -startSide;
        }

        // Update Global Balance for the next instrument to see
        globalBalance += direction;

        // 2. Ideal Width
        int idealWidth = getIdealWidth(instrument);

        // Decay: 10% per pair.
        float decay = 1.0f - ((float)(index / 2) * 0.10f);//cast like that on purpose
        if (decay < 0.2f) decay = 0.2f;

        // 4. Calculate
        float rawOffset = idealWidth * decay * direction * stageScalar;

        return CENTER + (int) rawOffset;
    }

    private int getIdealWidth(LotroInstrument i) {
        if (i == LotroInstrument.BASIC_DRUM) return WIDTH_CENTRIC;
        if (i == LotroInstrument.MOOR_COWBELL || i == LotroInstrument.BASIC_COWBELL) return WIDTH_NARROW;

        // Special case for the "Bell-like" Jaunty (hand-lyre)
        // it needs space to shimmer.
        if (i == LotroInstrument.JAUNTY_HAND_KNELLS) return WIDTH_MID;

        return switch (i.octaveDelta) {
            case 3 -> WIDTH_WIDE;
            case 2 -> WIDTH_WIDE;
            // If it is Octave 1 (Fiddles) but Plucked, restrict width slightly
            case 1 -> isPluckedOrShort(i) ? WIDTH_MID : WIDTH_WIDE;
            case 0 -> WIDTH_MID;
            case -1 -> WIDTH_NARROW;
            default -> WIDTH_MID;
        };
    }

    /**
     * Identifies instruments that have short decay or plucked attacks.
     * These should generally sit "inside" the sustained instruments in the stereo field.
     */
    private boolean isPluckedOrShort(LotroInstrument i) {
        return !i.sustainable;
    }

    private int findClosestAvailable(int target) {
        target = Math.clamp(target, 0, 127);

        // Try the ideal spot first (check neighbors for air)
        if (isSpotClean(target)) return target;

        // Spiral out 1 by 1, but strictly demand "Air" around the candidate
        for (int dist = 1; dist < 64; dist++) {
            int right = target + dist;
            if (right <= 127 && isSpotClean(right)) return right;

            int left = target - dist;
            if (left >= 0 && isSpotClean(left)) return left;
        }

        // Fallback: If the stage is somehow packed,
        // just find any empty seat (ignore an air gap)
        for (int dist = 0; dist < 64; dist++) {
            if (target + dist <= 127 && !usedPanPositions.get(target + dist)) return target + dist;
            if (target - dist >= 0 && !usedPanPositions.get(target - dist)) return target - dist;
        }

        return target;
    }

    /**
     * Returns true only if 'pos' is empty, AND its immediate neighbors are empty.
     * This guarantees at least 1 unit of silence between every instrument.
     */
    private boolean isSpotClean(int pos) {
        if (usedPanPositions.get(pos)) return false; // Seat taken

        // Check Right Neighbor (if exists)
        if (pos < 127 && usedPanPositions.get(pos + 1)) return false;

        // Check Left Neighbor (if exists)
        if (pos > 0 && usedPanPositions.get(pos - 1)) return false;

        return true;
    }

    /**
     * Maps instruments to their "Counter Group".
     * Crucially, we separate Plucked Fiddles from Sustained Fiddles
     * so they don't force each other to decay.
     */
    private LotroInstrument mapToBase(LotroInstrument i) {
        return switch (i) {
            // Group 1: Sustained Fiddles
            case BASIC_FIDDLE, STUDENT_FIDDLE, LONELY_MOUNTAIN_FIDDLE, BARDIC_FIDDLE
                    -> LotroInstrument.BASIC_FIDDLE;

            // Group 2: Plucked/Short Fiddles (New Group!)
            // Mapping them to Trusty ensures they have a separate index counter
            // from the Basic Fiddles.
            case TRAVELLERS_TRUSTY_FIDDLE, SPRIGHTLY_FIDDLE
                    -> LotroInstrument.TRAVELLERS_TRUSTY_FIDDLE;

            // Group 3: Harps
            case BASIC_HARP, MISTY_MOUNTAIN_HARP -> LotroInstrument.BASIC_HARP;

            // Group 4: Lutes
            case LUTE_OF_AGES, BASIC_LUTE -> LotroInstrument.LUTE_OF_AGES;

            // Group 5: Sustained Bassoons
            case BASIC_BASSOON, LONELY_MOUNTAIN_BASSOON -> LotroInstrument.BASIC_BASSOON;

            // Group 6: Non-sustained Bassoon
            case BRUSQUE_BASSOON -> LotroInstrument.BRUSQUE_BASSOON;

            default -> i; // Bagpipes, Horns, etc. map to themselves
        };
    }
}
