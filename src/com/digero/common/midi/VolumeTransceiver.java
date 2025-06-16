package com.digero.common.midi;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;

import com.digero.maestro.midi.SequenceInfo;

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

	public VolumeTransceiver()
	{
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
		if (message instanceof ShortMessage)
		{
			ShortMessage m = (ShortMessage) message;
			if (m.getCommand() == ShortMessage.SYSTEM_RESET)
			{
				systemReset = true;
			}
		} else if (message instanceof SysexMessage) {
			SysexMessage m = (SysexMessage) message;
			
			byte[] sysex = m.getMessage();
					    
			if (sysex.length > 4 && sysex[1] == SYSEX_UNIVERSAL_REALTIME && (sysex[3] & 0xFF) == 0x04 && (sysex[4] & 0xFF) == 0x01) {
				//System.out.println("Ignored SysEx device volume command:\n"+MidiUtils.formatBytes(sysex));
				return;
			} else if (SequenceInfo.isResetGS(sysex)) {
				//System.out.println("GS reset (will mess with MIDI playback volume, so we set also volume again)");
				systemReset = true;
				//return;
			} else if (SequenceInfo.isResetXG(sysex)) {
				//System.out.println("XG reset (will mess with MIDI playback volume, so we set also volume again)");
				systemReset = true;
			} else if (SequenceInfo.isResetGM2(sysex)) {
				//System.out.println("GM2 reset (will mess with MIDI playback volume, so we set also volume again)");
				systemReset = true;
			} else {
				//System.out.println("SysEx command: "+MidiUtils.formatBytes(sysex));			
			}
		}

		passOn(message, timeStamp);
		if (systemReset) {
			//System.out.println("systemReset");
			sendDeviceVolume();
		}
	}
}