package com.digero.maestro.view;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.digero.common.midi.SequencerWrapper;
import com.digero.common.midi.SequencerEvent.SequencerProperty;
import com.digero.common.util.IDiscardable;
import com.digero.common.util.Listener;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.AbcPartEvent;
import com.digero.maestro.abc.AbcPartMetadataSource;
import com.digero.maestro.abc.AbcSong;
import com.digero.maestro.abc.AbcSongEvent;

import info.clearthought.layout.TableLayoutConstants;

@SuppressWarnings("serial")
public class PartsList extends JPanel implements IDiscardable, TableLayoutConstants {
	private DefaultListModel<AbcPart> model;
	private BoxLayout layout;

	private List<PartsListItem> parts = new ArrayList<PartsListItem>();
	private AbcPart selectedPart = null;
	private int selectedIndex = -1;

	private SequencerWrapper abcSequencer;

	private final Dimension rowDimension;

	public PartsList(SequencerWrapper abcSequencer) {
		this.abcSequencer = abcSequencer;
		layout = new BoxLayout(this, BoxLayout.Y_AXIS);
		setLayout(layout);
		setBackground(new JList<AbcPartMetadataSource>().getBackground());

		rowDimension = PartsListItem.getProtoDimension();
		rowDimension.height = 8 * rowDimension.height; // min size should fit 8 rows
		this.setMinimumSize(rowDimension);

		this.abcSequencer.addChangeListener(e -> {
			if (e.getProperty() == SequencerProperty.SEQUENCE) {
				updateTrackNumbers();
			}
		});

		model = new DefaultListModel<AbcPart>();
	}

	public void updateParts() {
		parts = new ArrayList<PartsListItem>();
		removeAll();

		if (model.getSize() == 0) {
			selectedIndex = -1;
			selectedPart = null;
		}

		for (int i = 0; i < model.getSize(); i++) {
			addPart(i);
		}

		this.revalidate();
		this.repaint();
	}

	private void addPart(int idx) {
		AbcPart part = model.elementAt(idx);
		PartsListItem item = new PartsListItem(part);

		item.setItemListener(itemListener);

		if (part == selectedPart) {
			selectedIndex = idx;
			item.setSelected(true);
		}

		parts.add(idx, item);
		add(item);
	}

	private void updateTrackNumbers() {
		for (PartsListItem item : parts) {
			updatePartSoloMute(item.getPart());
		}
	}

	public void selectPart(int idx) {
		if (idx < 0) {
			selectedIndex = idx;
			selectedPart = null;
			return;
		}

		for (int i = 0; i < parts.size(); i++) {
			PartsListItem item = parts.get(i);
			item.setSelected(i == idx);
		}
		selectedIndex = idx;
		selectedPart = parts.get(idx).getPart();

		for (ListSelectionListener listener : listenerList.getListeners(ListSelectionListener.class)) {
			ListSelectionEvent event = new ListSelectionEvent(parts.get(idx).getPart(), idx, idx, false);
			listener.valueChanged(event);
		}

		revalidate();
		repaint();
	}

	public void init() {
		updateParts();
	}

	@Override
	public void discard() {
		removeAll();
		selectedIndex = -1;
		selectedPart = null;
	}

	int getSelectedIndex() {
		return selectedIndex;
	}

	AbcPart getSelectedPart() {
		if (model == null)
			return null;
		return model.elementAt(getSelectedIndex());
	}

	private int getIndexOfPart(AbcPart part) {
		for (int i = 0; i < model.size(); i++) {
			if (part.equals(model.get(i)))
				return i;
		}
		return -1;
	}

	DefaultListModel<AbcPart> getModel() {
		return model;
	}

	public void setModel(DefaultListModel<AbcPart> model) {
		this.model = model;
		init();
	}

	public void addListSelectionListener(ListSelectionListener listener) {
		listenerList.add(ListSelectionListener.class, listener);
	}

	public void removeListSelectionListener(ListSelectionListener listener) {
		listenerList.remove(ListSelectionListener.class, listener);
	}

	void ensureIndexIsVisible(int index) {

	}
	
	private void updateSequencerState(PartsListItem item) {
		int trackNo = item.getPart().getPreviewSequenceTrackNumber();

		if (trackNo >= 0) {
			abcSequencer.setTrackMute(trackNo, item.isMuted());
			abcSequencer.setTrackSolo(trackNo, item.isSoloed());
		}
	}

	private void updatePartSoloMute(AbcPart part) {
		if (part == null) {
			return;
		}

		int trackNo = part.getPreviewSequenceTrackNumber();

		if (trackNo >= 0) {
			abcSequencer.setTrackMute(trackNo, part.isMuted());
			abcSequencer.setTrackSolo(trackNo, part.isSoloed());
		}
	}
	
	private void unsoloAll() {
		for (PartsListItem item : parts) {
			if (item.isSoloed()) {
				item.setSolo(false);
				updateSequencerState(item);
			}
		}
	}
	
	private void unmuteAll() {
		for (PartsListItem item : parts) {
			if (item.isMuted()) {
				item.setMute(false);
				updateSequencerState(item);	
			}
		}
	}

	// Listens to the PartsListItems for selection and solo/mute events
	public Listener<PartsListItem.PartsListItemEvent> itemListener = e -> {
		PartsListItem item = (PartsListItem) e.getSource();
		AbcPart part = item.getPart();
		switch (e.getType()) {
		case SELECTION:
			selectPart(getIndexOfPart(part));
			break;
		case SOLO:
		case MUTE:
			updatePartSoloMute(part);
			break;
		case UNSOLO_ALL:
			unsoloAll();
			break;
		case UNMUTE_ALL:
			unmuteAll();
			break;
		default:
			break;
		}
	};

	public Listener<AbcPartEvent> partListener = e -> {
		switch (e.getProperty()) {
		case TRACK_ENABLED:
		case INSTRUMENT:
		case TITLE:
			updateParts();
			break;
		default:
			break;
		}
	};

	public Listener<AbcSongEvent> songListener = e -> {
		AbcSong song = e.getSource();
		if (song == null)
			return;

		switch (e.getProperty()) {
		case PART_ADDED:
			e.getPart().addAbcListener(partListener);
			updateParts();
			break;
		case BEFORE_PART_REMOVED:
			AbcPart part = e.getPart();
			part.removeAbcListener(partListener);
			part.setSoloed(false);
			updatePartSoloMute(part);
			break;
		case PART_LIST_ORDER:
			updateParts();
			break;
		default:
			break;
		}
	};
}
