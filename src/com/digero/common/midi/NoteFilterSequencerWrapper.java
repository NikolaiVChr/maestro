package com.digero.common.midi;

import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.sound.midi.*;
import javax.sound.midi.MidiDevice.Info;

import com.digero.common.midi.SequencerEvent.SequencerProperty;
import com.digero.common.util.AppInfo;
import com.digero.common.view.UIText;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.DrumNoteMap;
import com.digero.maestro.abc.LotroCombiDrumInfo;
import com.digero.maestro.view.ProjectFrame;

public class NoteFilterSequencerWrapper extends SequencerWrapper {
	private static final Logger log = Logger.getLogger("playback.midi");
	public static final String prefMIDIHeader = "MIDI out devices";
	public static final String prefMIDISelect = "Preferred MIDI out device";
	public static Preferences prefs = Preferences.userNodeForPackage(NoteFilterSequencerWrapper.class);
	private Preferences prefsNode = null;
	private final NoteFilterTransceiver filter;
	private MidiDevice device = null;
	private int listNumber = 0;
	public static String deviceInUse = null;
	private boolean feedActive = false;
	

	public NoteFilterSequencerWrapper() throws MidiUnavailableException {
		super();
		filter = new NoteFilterTransceiver();
		addTransceiver(filter);
		feedActive = AppInfo.maestro;
	}

	public NoteFilterTransceiver getFilter() {
		return filter;
	}

	public void setNoteSolo(int track, int noteId, boolean solo, AbcPart part) {
		if (solo != filter.getNoteSolo(noteId)) {
			//boolean midi = !(this instanceof LotroSequencerWrapper);
			//System.out.println((midi?"MIDI":"ABC")+" Setting track " + track + " solo to " + solo+" for note "+noteId);
			sequencer.setTrackSolo(track, solo);
			if (part == null) {
				filter.setNoteSolo(noteId, solo);
			} else {
				DrumNoteMap map = part.getDrumMap(track);
				LotroCombiDrumInfo.CombiDrumHit c = map.resolveCombi(noteId);
				if (c != null) filter.setNoteSolo(noteId, c. firstNote().id, c.secondNote().id, solo);
				else           filter.setNoteSolo(noteId, solo);
			}
			fireChangeEvent(SequencerProperty.TRACK_ACTIVE);
		}
	}

	@Override
	public void clearAllSoloMute() {
		super.clearAllSoloMute();
		filter.clearSolos();
	}

	public void clearNoteSolo(int noteId, int track, AbcPart part) {
		if (filter.getNoteSolo(noteId)) {
			if (part == null) {
				filter.setNoteSolo(noteId, false);
			} else {
				DrumNoteMap map = part.getDrumMap(track);
				LotroCombiDrumInfo.CombiDrumHit c = map.resolveCombi(noteId);
				if (c != null) filter.setNoteSolo(noteId, c.firstNote().id, c.secondNote().id, false);
				else filter.setNoteSolo(noteId, false);
			}

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
			boolean needWanted = !Objects.equals(wanted, deviceInUse);

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
				if (info.getName() != null && !info.getName().isEmpty() && info.getName().equals(preferred)) {
					myInfo = info;
					if (info.getDescription() != null) description = info.getDescription();
					if (!"Unknown vendor".equals(info.getVendor())) vendor = info.getVendor();
				}
			}
		}
		boolean customAvailable = false;
		String customKey = SynthesizerFactory.customMidiSoundfontFilename;
		if (SynthesizerFactory.getCustomMIDIAudioSynthesizer() != null) {
			prefsNode.putLong(customKey, new Date().getTime());
			customAvailable = true;
		} else {
			prefsNode.remove(customKey);
		}
		try {
			prefsNode.flush();
		} catch (BackingStoreException e) {
			log.warning(e.getMessage());
		}

		closeDevice();

        // TODO: AbcPlayer uses a class that inherit from this and this method
        //       is overridden. However its still not smart to have this method
        //       use a Maestro specific class like ProjectFrame.
		if (preferred == null) {
			log.fine("Default MIDI out selected");
			deviceInUse = null;
			if (feedActive && nonDefault) ProjectFrame.feed(UIText.get("common.default.midi.out"), null);
			return MidiSystem.getReceiver();
		}
		if (customAvailable && customKey.equals(preferred)) {
			Synthesizer s = SynthesizerFactory.getCustomMIDIAudioSynthesizer();
			if (s != null) {
				deviceInUse = customKey;
				myInfo = s.getDeviceInfo();
				// we don't assign 'device' as we don't want to close it. Might not be able to get it back.
				// therefore we set it to null
				device = null;
				if (feedActive && nonDefault) ProjectFrame.feed(UIText.get("maestro.midi.out.on.0",customKey), null);
				log.info("Non-default MIDI out selected: " + myInfo.getName()+" ("+description+") "+vendor+" with "+customKey+" soundfont");
				return s.getReceiver();
			} else {
				prefsNode.remove(customKey);
			}
		}
		if (myInfo == null) {
			log.info(UIText.get("common.default.midi.out.selected.0.not.available", preferred));
			deviceInUse = null;
			if (feedActive && nonDefault) ProjectFrame.feed(UIText.get("common.default.midi.out.0.not.available", preferred), null);
			return MidiSystem.getReceiver();
		}

		Receiver myReceiver = null;
		boolean okay = true;
		try {
			device = MidiSystem.getMidiDevice(myInfo);
			if (device instanceof com.sun.media.sound.SoftSynthesizer) {
				Map<String, Object> synthInfo = SynthesizerFactory.setupExternalSynthesizerPropertyInfo();
				((com.sun.media.sound.SoftSynthesizer) device).open(null, synthInfo);
			} else {
				device.open();
			}
			myReceiver = device.getReceiver();
		} catch (MidiUnavailableException e) {
			okay = false;
			closeDevice();
		}
		if (!okay || myReceiver == null) {
			log.info("Default MIDI out selected (" + preferred + " not connected)");
			deviceInUse = null;
			if (feedActive && nonDefault) ProjectFrame.feed(UIText.get("common.default.midi.out.0.not.connected", preferred), null);
			return MidiSystem.getReceiver();
		}

		// System.out.println("\nmaxTransmitters="+myDevice.getMaxTransmitters());
		// System.out.println("maxReceivers="+myDevice.getMaxReceivers());

		
		if (feedActive && (deviceInUse == null || !deviceInUse.equals(preferred))) ProjectFrame.feed(UIText.get("maestro.midi.out.on.0", myInfo.getName()), null);
		deviceInUse = preferred;
		
		log.info("Non-default MIDI out selected: " + myInfo.getName()+" ("+description+") "+vendor);
		return myReceiver;
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

	public boolean isDefault() {
		return deviceInUse == null;
	}
}
