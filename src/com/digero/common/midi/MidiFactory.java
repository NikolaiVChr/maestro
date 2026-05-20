package com.digero.common.midi;

import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;

/**
 * Provides static methods to create MidiEvents.
 */
public class MidiFactory implements MidiConstants {

	private static final Logger log = Logger.getLogger("midi");

	/**
	 * @param mpqn Microseconds per quarter note
	 */
	public static MidiEvent createTempoEvent(int mpqn, long ticks) {
		try {
			byte[] data = new byte[3];
			data[0] = (byte) ((mpqn >>> 16) & 0xFF);
			data[1] = (byte) ((mpqn >>> 8) & 0xFF);
			data[2] = (byte) (mpqn & 0xFF);

			MetaMessage msg = new MetaMessage();
			msg.setMessage(META_TEMPO, data, data.length);
			return new MidiEvent(msg, ticks);
		} catch (InvalidMidiDataException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Expects UTF-8 string
	 */
	public static MidiEvent createTrackNameEvent(String name) {
		try {
			byte[] data = name.getBytes(StandardCharsets.UTF_8);
			MetaMessage msg = new MetaMessage();
			msg.setMessage(META_TRACK_NAME, data, data.length);
			return new MidiEvent(msg, 0);
		} catch (InvalidMidiDataException e) {
			throw new RuntimeException(e);
		}
	}

	public static MidiEvent createProgramChangeEvent(int patch, int channel, long ticks) {
		try {
			ShortMessage msg = new ShortMessage();
			msg.setMessage(ShortMessage.PROGRAM_CHANGE, channel, patch, 0);
			return new MidiEvent(msg, ticks);
		} catch (InvalidMidiDataException e) {
			throw new RuntimeException(e);
		}
	}

	public static MidiMessage createAllNotesOff(int channel) {
		try {
			LotroShortMessage msg = new LotroShortMessage();
			msg.setMessage(ShortMessage.CONTROL_CHANGE, channel, MidiConstants.ALL_NOTES_OFF, 0);
			return msg;
		} catch (InvalidMidiDataException e) {
			throw new RuntimeException(e);
		}
	}

	public static MidiEvent createLotroChangeEvent(int patch, int channel, long ticks) {
		try {
			LotroShortMessage msg = new LotroShortMessage();
			msg.setMessage(ShortMessage.PROGRAM_CHANGE, channel, patch, 0);
			return new MidiEvent(msg, ticks);
		} catch (InvalidMidiDataException e) {
			throw new RuntimeException(e);
		}
	}

	public static void modifyProgramChangeMessage(ShortMessage msg, int patch) {
		try {
			msg.setMessage(ShortMessage.PROGRAM_CHANGE, msg.getChannel(), patch, 0);
		} catch (InvalidMidiDataException e) {
			throw new RuntimeException(e);
		}
	}

	public static MidiEvent createNoteOnEvent(int id, int channel, long ticks) {
		return createNoteOnEventEx(id, channel, 112, ticks);
	}

	public static MidiEvent createNoteOnEventEx(int id, int channel, int velocity, long ticks) {
		try {
			LotroShortMessage msg = new LotroShortMessage();
			msg.setMessage(ShortMessage.NOTE_ON, channel, id, velocity);
			return new MidiEvent(msg, ticks);
		} catch (InvalidMidiDataException e) {
			throw new RuntimeException(e);
		}
	}

	public static MidiEvent createNoteOffEvent(int id, int channel, long ticks) {
		return createNoteOffEventEx(id, channel, 112, ticks);
	}

	public static MidiEvent createNoteOffEventEx(int id, int channel, int velocity, long ticks) {
		try {
			LotroShortMessage msg = new LotroShortMessage();
			msg.setMessage(ShortMessage.NOTE_OFF, channel, id, velocity);
			return new MidiEvent(msg, ticks);
		} catch (InvalidMidiDataException e) {
			throw new RuntimeException(e);
		}
	}

	public static MidiEvent createPanEvent(int value, int channel) {
		return createPanEvent(value, channel, 0L);
	}

	public static MidiEvent createPanEvent(int value, int channel, long ticks) {
		return createControllerEvent(PAN_CONTROL, value, channel, ticks);
	}

	public static MidiEvent createReverbControlEvent(int value, int channel, long ticks) {
		return createControllerEvent(REVERB_CONTROL, value, channel, ticks);
	}

	public static MidiEvent createChorusControlEvent(int value, int channel, long ticks) {
		return createControllerEvent(CHORUS_CONTROL, value, channel, ticks);
	}

	public static MidiEvent createChannelVolumeEvent(int volume, int channel, long ticks) {
		if (volume < 0 || volume > Byte.MAX_VALUE)
			throw new IllegalArgumentException();

		return createControllerEvent(CHANNEL_VOLUME_CONTROLLER_COARSE, volume, channel, ticks);
	}

    public static MidiEvent createExpressionEvent(int volume, int channel, int tick) {
        if (volume < 0 || volume > Byte.MAX_VALUE)
            throw new IllegalArgumentException();

        return createControllerEvent(CHANNEL_EXPRESSION_CONTROLLER, volume, channel, tick);
    }

	public static MidiEvent createControllerEvent(byte controller, int value, int channel, long ticks) {
		try {
			LotroShortMessage msg = new LotroShortMessage();
			msg.setMessage(ShortMessage.CONTROL_CHANGE, channel, controller, value);
			return new MidiEvent(msg, ticks);
		} catch (InvalidMidiDataException e) {
			throw new RuntimeException(e);
		}
	}

	public static MidiEvent createTimeSignatureEvent(TimeSignature meter, long ticks) {
		return new MidiEvent(meter.toMidiMessage(), ticks);
	}

	public static boolean isSupportedMidiKeyMode(KeyMode mode) {
		return mode == KeyMode.MAJOR || mode == KeyMode.MINOR;
	}

	public static MidiEvent createKeySignatureEvent(KeySignature key, long ticks) {
		return new MidiEvent(key.toMidiMessage(), ticks);
	}

	public static MidiEvent createPortEvent(int port) {
		if (port < 0 || port > 255) {
			log.severe("Invalid MIDI Port: " + port + ". Must be 0-255.");
			return null;
		}
		try {
			byte[] data = new byte[1];
			data[0] = (byte) port;
			MetaMessage msg = new MetaMessage();
			msg.setMessage(META_PORT_CHANGE, data, 1);
			return new MidiEvent(msg, 0);
		} catch (InvalidMidiDataException e) {
			return null;
		}
	}

	public static MidiEvent createEndOfTrackEvent(long tick) {
		try {
			MetaMessage msg = new MetaMessage();
			msg.setMessage(META_END_OF_TRACK, new byte[0], 0);
			return new MidiEvent(msg, tick);
		} catch (InvalidMidiDataException e) {
			return null;
		}
	}

	public static MidiMessage createSustainOff(Integer channel) {
		try {
			LotroShortMessage msg = new LotroShortMessage();
			msg.setMessage(ShortMessage.CONTROL_CHANGE, channel, MidiConstants.SUSTAIN_OFF, 0);
			return msg;
		} catch (InvalidMidiDataException e) {
			throw new RuntimeException(e);
		}
	}

	public static MidiMessage createAllControllersOff(Integer channel) {
		try {
			LotroShortMessage msg = new LotroShortMessage();
			msg.setMessage(ShortMessage.CONTROL_CHANGE, channel, MidiConstants.RESET_ALL_CONTROLLERS, 0);
			return msg;
		} catch (InvalidMidiDataException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static MidiMessage createDeviceVolumeMessage(int MSB_VOL)
	{
		if (MSB_VOL < 0 || MSB_VOL > Byte.MAX_VALUE)
			throw new IllegalArgumentException();

		byte EOX = (byte) 247;
		byte LSB_VOL_MIN = (byte) 0;
		byte DEVICE_CONTROL = (byte) 4;
		byte MASTER_VOLUME = (byte) 1;
		byte ALL_DEVICES = (byte) 127;
		byte[] data = {(byte) SysexMessage.SYSTEM_EXCLUSIVE, SYSEX_UNIVERSAL_REALTIME, ALL_DEVICES, DEVICE_CONTROL, MASTER_VOLUME,
				LSB_VOL_MIN, (byte) MSB_VOL, EOX};
		MidiMessage msg = null;
		try {
			msg = new SysexMessage(data, data.length);
		} catch (InvalidMidiDataException e) {
			e.printStackTrace();
		}
		return msg;
	}

    public static MidiMessage createGMReset() {
		MidiMessage reset = new SysexMessage();
		byte EOX = (byte) 0xF7;
		byte[] data = {(byte) SysexMessage.SYSTEM_EXCLUSIVE, SYSEX_UNIVERSAL_NON_REALTIME, DEVICE_ID_BROADCAST, (byte) 0x09, (byte) 0x01, EOX};
		try {
			((SysexMessage) reset).setMessage(data, 6);
			/*
			byte[] message = reset.getMessage();
			if (message.length == 6 && (message[0] & 0xFF) == SysexMessage.SYSTEM_EXCLUSIVE && (message[1] & 0xFF) == SYSEX_UNIVERSAL_NON_REALTIME
					&& (message[3] & 0xFF) == 0x09 && (message[4] & 0xFF) == 0x01 && (message[5] & 0xFF) == 0xF7) {
				System.out.println("Okay");
			} else {
				System.out.println("Fail "+MidiUtils.midiMessageToString(reset));
			}
			*/
		} catch (InvalidMidiDataException e) {
			e.printStackTrace();
		}
		return reset;
    }

	public static MidiMessage createGM2Reset() {
		MidiMessage reset = new SysexMessage();
		byte EOX = (byte) 0xF7;
		byte[] data = {(byte) SysexMessage.SYSTEM_EXCLUSIVE, SYSEX_UNIVERSAL_NON_REALTIME, DEVICE_ID_BROADCAST, (byte) 0x09, (byte) 0x03, EOX};
		try {
			((SysexMessage) reset).setMessage(data, 6);
		} catch (InvalidMidiDataException e) {
			e.printStackTrace();
		}
		return reset;
	}

	public static MidiMessage createGSReset() {
		MidiMessage reset = new SysexMessage();
		byte EOX = (byte) 0xF7;

		byte addrH = (byte) 0x40;
		byte addrM = (byte) 0x00;
		byte addrL = (byte) 0x7F;
		byte dataByte  = (byte) 0x00;

		byte[] data = {(byte) SysexMessage.SYSTEM_EXCLUSIVE,
				(byte) 0x41, DEVICE_ID_ROLAND, (byte) 0x42, (byte) 0x12, addrH, addrM, addrL, dataByte, calculateRolandChecksum(addrH,addrM,addrL,dataByte), EOX};
		try {
			((SysexMessage) reset).setMessage(data, 11);
		} catch (InvalidMidiDataException e) {
			e.printStackTrace();
		}
		return reset;
	}

	public static MidiMessage createXGReset() {
		MidiMessage reset = new SysexMessage();
		byte EOX = (byte) 0xF7;
		byte[] data = {(byte) SysexMessage.SYSTEM_EXCLUSIVE, (byte) 0x43, DEVICE_ID_YAMAHA, DEVICE_ID_XG, (byte) 0x00, (byte) 0x00, (byte) 0x7E, (byte) 0x00, EOX};
		try {
			((SysexMessage) reset).setMessage(data, 9);
		} catch (InvalidMidiDataException e) {
			e.printStackTrace();
		}
		return reset;
	}

	/**
	 * Roland SysEx Checksum.
	 *
	 * @param addressAndData An array containing only the address and data bytes.
	 * @return The calculated checksum byte.
	 */
	public static byte calculateRolandChecksum(byte... addressAndData) {
		int sum = 0;

		// Add up the decimal values of all address and data bytes
		for (byte b : addressAndData) {
			// Use & 0xFF to prevent negative values from signed Java bytes
			sum += (b & 0xFF);
		}

		// Find the remainder when divided by 128
		int remainder = sum % 128;

		// Subtract the remainder from 128.
		// The & 0x7F ensures that if the remainder is 0, the checksum is 0 (not 128).
		int checksum = (128 - remainder) & 0x7F;

		return (byte) checksum;
	}
}
