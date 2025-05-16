package com.digero.common.midi;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.MidiDevice.Info;

import com.digero.common.midi.SequencerEvent.SequencerProperty;
import com.digero.maestro.view.ProjectFrame;

public class NoteFilterSequencerWrapper extends SequencerWrapper {
	public static final String prefMIDIHeader = "MIDI out devices";
	public static final String prefMIDISelect = "Preferred MIDI out device";
	public static Preferences prefs = Preferences.userNodeForPackage(NoteFilterSequencerWrapper.class);
	private Preferences prefsNode = null;
	private NoteFilterTransceiver filter;
	private MidiDevice device = null;
	private int listNumber = 0;
	public static String deviceInUse = null;
	private boolean feedActive = false;
	

	public NoteFilterSequencerWrapper() throws MidiUnavailableException {
		super();
		filter = new NoteFilterTransceiver();
		addTransceiver(filter);
		feedActive = true;
	}

	public NoteFilterTransceiver getFilter() {
		return filter;
	}

	public void setNoteSolo(int track, int noteId, boolean solo) {
		if (solo != getNoteSolo(track, noteId)) {
			sequencer.setTrackSolo(track, solo);
			filter.setNoteSolo(noteId, solo);
			fireChangeEvent(SequencerProperty.TRACK_ACTIVE);
		}
	}

	public boolean getNoteSolo(int track, int noteId) {
		return filter.getNoteSolo(noteId) && sequencer.getTrackSolo(track);
	}

	@Override
	public boolean isNoteActive(int noteId) {
		return filter.isNoteActive(noteId);
	}
	
	@Override
	protected void resetHardIfGone() {
		Info[] infos = MidiSystem.getMidiDeviceInfo();
		if (listNumber != infos.length) {
			listNumber = infos.length;
			String wanted = prefs.get(prefMIDISelect, null);
			boolean foundInUse = false;
			boolean foundWanted = false;
			boolean needWanted = wanted != deviceInUse;

			for (Info d : infos) {
				if (d != null && d.getName() != null) {
					if(d.getName().equals(deviceInUse)) {
						foundInUse = true;
					}
					if(needWanted && d.getName().equals(wanted)) {
						foundWanted = true;
					}
				}
			}
			if ((!foundInUse && deviceInUse != null) || (needWanted && foundWanted)) {
				long tick = getTickPosition();
				boolean running = isRunning();
				reset(true);
				setTickPosition(tick);
				if (running) start();
			}
		}
	}

	@Override
	public Receiver createReceiver() throws MidiUnavailableException {
		boolean nonDefault = deviceInUse != null;
		if (prefsNode == null) {
			prefsNode = prefs.node(prefMIDIHeader);
		}
		String preferred = prefs.get(prefMIDISelect, null);
		Info[] infos = MidiSystem.getMidiDeviceInfo();
		listNumber = infos.length;
		Info myInfo = null;
		String description = "No description";
		String vendor = "";
		for (Info info : infos) {
			boolean midiOut = false;
			try {
				MidiDevice dv = MidiSystem.getMidiDevice(info);
				midiOut = dv.getMaxReceivers() != 0;
			} catch (MidiUnavailableException e) {
			}
			if (midiOut) {
				prefsNode.putLong(info.getName(), new Date().getTime());
				//System.out.println(infoToString(info));
				if (info.getName() != null && info.getName().length() > 0 && info.getName().equals(preferred)) {
					myInfo = info;
					if (info.getDescription() != null) description = info.getDescription();
					if (!"Unknown vendor".equals(info.getVendor())) vendor = info.getVendor();
				}
			}
		}
		try {
			prefsNode.flush();
		} catch (BackingStoreException e) {
			// e.printStackTrace();
		}

		closeDevice();

		if (preferred == null) {
			// System.out.println("Default MIDI out selected");
			deviceInUse = null;
			if (nonDefault && feedActive) ProjectFrame.feed("Default MIDI out", null);
			return MidiSystem.getReceiver();
		}
		if (myInfo == null) {
			System.out.println("Default MIDI out selected (" + preferred + " not available)");
			deviceInUse = null;
			if (nonDefault) ProjectFrame.feed("Default MIDI out (" + preferred + " not available)", null);
			return MidiSystem.getReceiver();
		}

		Receiver myReciever = null;
		boolean okay = true;
		try {
			device = MidiSystem.getMidiDevice(myInfo);
			if (device instanceof com.sun.media.sound.SoftSynthesizer) {
				Map<String, Object> synthInfo = new HashMap<>();
				synthInfo.put("reverb", false);// default is true
				synthInfo.put("chorus", false);// default is true
				((com.sun.media.sound.SoftSynthesizer) device).open(null, synthInfo);
			} else {
				device.open();
			}
			myReciever = device.getReceiver();
		} catch (MidiUnavailableException e) {
			okay = false;
			closeDevice();
		}
		if (!okay || myReciever == null) {
			System.out.println("Default MIDI out selected (" + preferred + " not connected)");
			deviceInUse = null;
			if (nonDefault) ProjectFrame.feed("Default MIDI out (" + preferred + " not connected)", null);
			return MidiSystem.getReceiver();
		}

		// System.out.println("\nmaxTransmitters="+myDevice.getMaxTransmitters());
		// System.out.println("maxReceivers="+myDevice.getMaxReceivers());

		
		if (feedActive && (deviceInUse == null || !deviceInUse.equals(preferred))) ProjectFrame.feed("MIDI out on "+myInfo.getName(), null);
		deviceInUse = preferred;
		
		System.out.println("Non-default MIDI out selected: " + myInfo.getName()+" ("+description+") "+vendor);
		return myReciever;
	}

	@Override
	public void discard() {
		closeDevice();
		super.discard();
	}

	private void closeDevice() {
		if (device != null) {
			// System.out.println("CLOSING "+ device.getDeviceInfo().getName());
			device.close();
			device = null;
		}
	}

	private String infoToString(Info info) {
		String str = "";
		str += "\nName: " + info.getName();
		str += "\nVendor: " + info.getVendor();
		str += "\nDescription: " + info.getDescription();
		str += "\nVersion: " + info.getVersion();
		try {
			MidiDevice dv = MidiSystem.getMidiDevice(info);
			str += "\nMax Transmitters: " + dv.getMaxTransmitters();
			str += "\nMax Receivers: " + dv.getMaxReceivers();
		} catch (MidiUnavailableException e) {
		}
		return str;
	}

	public void setRealDura(long realDuraTicks) {
		this.realDuraTicks = realDuraTicks; 	
	}
}
