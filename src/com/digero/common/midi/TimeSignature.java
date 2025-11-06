package com.digero.common.midi;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import java.util.logging.Logger;

/**
 * Representation of a MIDI time signature.
 */
public class TimeSignature implements MidiConstants {
	private static final Logger log = Logger.getLogger("import.midi");
	public static final int MAX_DENOMINATOR = 8;
	public static final TimeSignature FOUR_FOUR = new TimeSignature(4, 4);

	public final int numerator;
	public final int denominator;
	private final int metronome;
	private final int thirtySecondNotes;

	/**
	 * Constructs a TimeSignature from a numerator and denominator.
	 * 
	 * @param numerator   The numerator, must be less than 256.
	 * @param denominator The denominator, must be a power of 2.
	 * @throws IllegalArgumentException If the numerator is not less than 256, or the denominator is not a power of 2.
	 */
	public TimeSignature(int numerator, int denominator) {
		verifyData(numerator, denominator);

		this.numerator = numerator;
		this.denominator = denominator;
		this.metronome = 24;
		this.thirtySecondNotes = 8;
	}

	public TimeSignature(MetaMessage midiMessage) throws InvalidMidiDataException {
		byte[] data = midiMessage.getData();
		if (midiMessage.getType() != META_TIME_SIGNATURE || data.length < 4) {
			throw new InvalidMidiDataException("Midi message is not a time signature event. Length:" + data.length);
		}

		if ((1 << data[1]) > MAX_DENOMINATOR) {
			this.numerator = 4;
			this.denominator = 4;
			this.metronome = 24;
			this.thirtySecondNotes = 8;

			log.fine("Orig MIDI time signature: "+(data[0] & 0xFF)+"/"+(1 << data[1])+" - ");
			log.fine((data[3] & 0xFF)+" 32nd notes per "+(data[2] & 0xFF)+" MIDI clocks.");
			log.fine("New  MIDI time signature: 4/4 - 8 32nd notes per 24 MIDI clocks.");
		} else {
            // convert the bytes to unsigned since javas byte is signed but MIDIs are unsigned.
			this.numerator = data[0] & 0xFF;
			this.denominator = 1 << data[1];
			this.metronome = data[2] & 0xFF;
			this.thirtySecondNotes = data[3] & 0xFF;

			log.fine("MIDI time signature: "+this.numerator+"/"+this.denominator+" - " +this.thirtySecondNotes+" 32nd notes per "+this.metronome+" MIDI clocks.");
		}
	}

	public TimeSignature(MetaMessage midiMessage, boolean tryHarder) throws InvalidMidiDataException {
        // This constructor gets called when the other constructor throws an exception
		byte[] data = midiMessage.getData();
		if (midiMessage.getType() != META_TIME_SIGNATURE || data.length < 2 || data.length == 3) {
			throw new InvalidMidiDataException("Midi message is not a time signature event. Length:" + data.length);
		}

		if ((1 << data[1]) > MAX_DENOMINATOR) {
			this.numerator = 4;
			this.denominator = 4;
			this.metronome = 24;
			this.thirtySecondNotes = 8;
			
			log.fine("TH Orig MIDI time signature: "+(data[0] & 0xFF)+"/"+(1 << data[1])+" - ");
			log.fine((data[3] & 0xFF)+" 32nd notes per "+(data[2] & 0xFF)+" MIDI clocks.");
			log.fine("TH New  MIDI time signature: 4/4 - 8 32nd notes per 24 MIDI clocks.");
		} else {
			this.numerator = data[0] & 0xFF;
			this.denominator = 1 << data[1];
			// This message is not legal, but since it had the meter
			// we put the default values for the remaining 2 values.
			this.metronome = 24;
			this.thirtySecondNotes = 8;
			
			log.fine("TH MIDI time signature: "+this.numerator+"/"+this.denominator+" - " +this.thirtySecondNotes+" 32nd notes per "+this.metronome+" MIDI clocks.");
		}
	}

    public TimeSignature(String str) {
        this(str, false);
    }

    /**
     * Parses a time signature string.
     * If it's invalid, it throws an IllegalArgumentException if strict is true.
     * If strict is false, it might make a 4/4 signature.
     */
	public TimeSignature(String str, boolean strict) {
        str = str.trim();
        if (str.equals("C")) {
            this.numerator = 4;
            this.denominator = 4;
        } else if (str.equals("C|")) {
            this.numerator = 2;
            this.denominator = 2;
        } else {
            String[] parts = str.split("[/:| ]");
            if (parts.length != 2) {
                throw new IllegalArgumentException(
                        "The string: \"" + str + "\" is not a valid time signature (expected format: 4/4)");
            }
            if (!strict && Integer.parseInt(parts[1]) > MAX_DENOMINATOR) {
                this.numerator = 4;
                this.denominator = 4;
            } else {
                this.numerator = Integer.parseInt(parts[0]);
                this.denominator = Integer.parseInt(parts[1]);
            }
        }

        verifyData(this.numerator, this.denominator);

        this.metronome = 24;
        this.thirtySecondNotes = 8;
	}

	/**
	 * A best-guess as to whether this time signature represents compound meter.
	 */
	public boolean isCompound() {
		return (numerator % 3) == 0;
	}

	private static void verifyData(int numerator, int denominator) {
		if (denominator == 0 || denominator != (1 << floorLog2(denominator))) {
			throw new IllegalArgumentException("The denominator of the time signature must be a power of 2");
		}
		if (denominator > MAX_DENOMINATOR) {
			throw new IllegalArgumentException("The denominator must be less than or equal to " + MAX_DENOMINATOR);
		}
		if (numerator > 255) {
			throw new IllegalArgumentException("The numerator of the time signature must be less than 256");
		}
	}

	@SuppressWarnings("unused")
	private static boolean verifyDenom(int numerator, int denominator) {
		// This will produce a divide by zero in TimingInfo if allowed, so return false.
		return ((numerator / (double) denominator < 0.75) ? 16 : 8) * 4 / denominator >= 4;
	}

	public MetaMessage toMidiMessage() {
		MetaMessage midiMessage = new MetaMessage();
		byte[] data = new byte[4];
		data[0] = (byte) numerator;
		data[1] = floorLog2(denominator);
		data[2] = (byte) metronome;
		data[3] = (byte) thirtySecondNotes;

		try {
			midiMessage.setMessage(META_TIME_SIGNATURE, data, data.length);
		} catch (InvalidMidiDataException e) {
			throw new RuntimeException(e);
		}
		return midiMessage;
	}

	@Override
	public String toString() {
		return numerator + "/" + denominator;
	}

	@Override
	public int hashCode() {
        // the & 0xFF ensures that the bits don't overflow into the other parts of the ints bits.
        int d = (denominator       & 0xFF) << 24;
        int n = (numerator         & 0xFF) << 16;
        int m = (metronome         & 0xFF) << 8;
        int t = (thirtySecondNotes & 0xFF);

        return d | n | m | t;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof TimeSignature that) {
            return this.numerator == that.numerator && this.denominator == that.denominator
					&& this.metronome == that.metronome && this.thirtySecondNotes == that.thirtySecondNotes;
		}
		return false;
	}

	/**
	 * @return The floor of the binary logarithm for a 32 bit integer. -1 is returned if n is 0.
	 */
	public static byte floorLog2(int n) {
		byte pos = 0; // Position of the most significant bit
		if (n >= (1 << 16)) {
			n >>>= 16;
			pos += (byte) 16;
		}
		if (n >= (1 << 8)) {
			n >>>= 8;
			pos += (byte) 8;
		}
		if (n >= (1 << 4)) {
			n >>>= 4;
			pos += (byte) 4;
		}
		if (n >= (1 << 2)) {
			n >>>= 2;
			pos += (byte) 2;
		}
		if (n >= (1 << 1)) {
			pos += (byte) 1;
		}
		return ((n == 0) ? (-1) : pos);
	}

}
