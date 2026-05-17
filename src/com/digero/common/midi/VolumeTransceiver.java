package com.digero.common.midi;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;

/**
 * This class has only 2 functions:
 * 1: Intercept any sysex device master volume messages from midi files and don't pass them on to the Synthesizer.
 * 2: Inject sysex device master volume messages into synthesizer whenever Maestro master volume slider changes.
 * 
 *  Be careful, any exceptions thrown from here, wont stop execution and won't be printed in console. It will just fail.
 */
public class VolumeTransceiver implements Transceiver, MidiConstants
{
	private Receiver receiver;
	private int volume = MAX_VOLUME;
	private boolean lsbBlock = false;

	public VolumeTransceiver()
	{
	}

	public void setLSBBlock (boolean block) {
		lsbBlock = block;
	}

	public void setVolume(int volume)
	{
		if (volume < 0 || volume > MAX_VOLUME)
			throw new IllegalArgumentException();

		this.volume = volume;
		sendDeviceVolume();
	}

	public int getVolume()
	{
		return volume;
	}

	@Override public void close()
	{
	}

	@Override public Receiver getReceiver()
	{
		return receiver;
	}

	@Override public void setReceiver(Receiver receiver)
	{
		this.receiver = receiver;
		sendDeviceVolume();
	}

	private void sendDeviceVolume()
	{
		//System.out.println("sendDeviceVolume "+volume);
		passOn(MidiFactory.createDeviceVolumeMessage(volume), -1);
	}
	
	private void passOn(MidiMessage message, long timeStamp)
	{
		if (receiver != null)
		{
			receiver.send(message, timeStamp);
		}
	}
	
	@Override public void send(MidiMessage message, long timeStamp)
	{
		boolean systemReset = false;
		if (message instanceof ShortMessage m)
		{
            if (m.getCommand() == ShortMessage.SYSTEM_RESET)
			{
				//System.out.println("System reset");
				systemReset = true;
			} else if (m.getCommand() == ShortMessage.CONTROL_CHANGE && m.getData1() == BANK_SELECT_LSB && m.getData2() != 0) {
				if (lsbBlock) {
					// It's a GS midi, some of them sadly have lsb changes, we don't allow that.
					return;
				}
				if (NoteFilterSequencerWrapper.deviceInUse == null) {
					// We are using windows MIDI mapper
				}
			} else if (m.getCommand() == ShortMessage.CONTROL_CHANGE && m.getData1() == BANK_SELECT_MSB && m.getData2() != 0) {
				if (NoteFilterSequencerWrapper.deviceInUse == null) {
					// We are using windows MIDI mapper
				}
			} else if (m.getCommand() == ShortMessage.PROGRAM_CHANGE && m.getChannel() == DRUM_CHANNEL) {
				if (NoteFilterSequencerWrapper.deviceInUse == null) {
					// We are using windows MIDI mapper
				}
			}
		} else if (message instanceof SysexMessage m) {

			byte[] sysex = m.getMessage();

			if (sysex.length > 4 && sysex[1] == SYSEX_UNIVERSAL_REALTIME && (sysex[3] & 0xFF) == 0x04 && (sysex[4] & 0xFF) == 0x01) {
				//System.out.println("Ignored SysEx device volume command:\n"+MidiUtils.formatBytes(sysex));
				return;
			} else if (sysex.length >= 8 && (sysex[1] & 0xFF) == 0x43 && (sysex[4] & 0xFF) == 0x00 && (sysex[5] & 0xFF) == 0x00 && (sysex[6] & 0xFF) == 0x04) {
				// XG Master Volume (F0 43 10 4C 00 00 04 vv F7)
				// System.out.println("Ignored XG Master Volume");
				return;
			} else if (sysex.length >= 9 && (sysex[1] & 0xFF) == 0x41 && (sysex[5] & 0xFF) == 0x40 && (sysex[6] & 0xFF) == 0x00 && (sysex[7] & 0xFF) == 0x04) {
				// GS Master Volume (F0 41 10 42 12 40 00 04 vv ss F7)
				// System.out.println("Ignored GS Master Volume");
				return;
			} else if (MidiUtils.isResetGS(sysex, true)) {
				//System.out.println("GS reset (will mess with MIDI playback volume, so we set also volume again)");
				//systemReset = true;
				return;
			} else if (MidiUtils.isResetXG(sysex)) {
				//System.out.println("XG reset (will mess with MIDI playback volume, so we set also volume again)");
				//systemReset = true;
				return;
			} else if (MidiUtils.isResetGM2(sysex)) {
				//System.out.println("GM2 reset (will mess with MIDI playback volume, so we set also volume again)");
				//systemReset = true;
				return;
			} else if (MidiUtils.isResetGM(sysex)) {
				//System.out.println("GM reset");
				return;
			} else {
				//System.out.println("SysEx command: "+MidiUtils.formatBytes(sysex));			
			}
		}
		//System.out.println("Passing on: "+MidiUtils.midiMessageToString(message));
		passOn(message, timeStamp);
		if (systemReset) {
			//System.out.println("systemReset");
			sendDeviceVolume();
		}
	}
}