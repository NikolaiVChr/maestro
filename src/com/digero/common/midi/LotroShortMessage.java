package com.digero.common.midi;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.ShortMessage;

/**
 * LotroShortMessage is a wrapper around ShortMessage
 * that allows the channel number to be greater than 15.
 */
public class LotroShortMessage extends ShortMessage {
	private int channel2 = -1;

	@Override
	public void setMessage(int command, int channel, int data1, int data2) throws InvalidMidiDataException {
		// check for valid value
		if (channel < 0 || channel >= MidiConstants.CHANNEL_COUNT_ABC) {
			throw new InvalidMidiDataException("channel out of range: " + channel);
		}
		channel2 = channel;
		super.setMessage(command, channel & 0xF, data1, data2);
	}

	@Override
	public int getChannel() {
		return channel2;
	}

	@SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
	public Object clone() {
		LotroShortMessage clone = new LotroShortMessage();
		try {
			clone.setMessage(getCommand(), getChannel(), getData1(), getData2());
		} catch (InvalidMidiDataException e) {
			e.printStackTrace();
			throw new IllegalArgumentException(e.getMessage());
		}
		return clone;
	}
}