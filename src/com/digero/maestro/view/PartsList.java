package com.digero.maestro.view;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.digero.common.midi.SequencerWrapper;
import com.digero.common.midi.SequencerEvent.SequencerProperty;
import com.digero.common.util.IDiscardable;
import com.digero.common.util.Listener;
import com.digero.common.util.Pair;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.AbcPartEvent;
import com.digero.maestro.abc.AbcPartMetadataSource;
import com.digero.maestro.abc.AbcSong;
import com.digero.maestro.abc.AbcSongEvent;
import info.clearthought.layout.TableLayoutConstants;

@SuppressWarnings("serial")
public class PartsList extends JPanel implements IDiscardable, TableLayoutConstants {
	protected DefaultListModel<AbcPart> model;
	private BoxLayout layout;

	protected List<PartsListItem> parts = new ArrayList<PartsListItem>();
	protected AbcPart selectedPart = null;
	protected int selectedIndex = -1;
	protected MiscSettings miscSettings;

	private SequencerWrapper abcSequencer;

	protected final Dimension rowDimension;

	public PartsList(SequencerWrapper abcSequencer, MiscSettings miscSettings) {
		this.abcSequencer = abcSequencer;
		this.miscSettings = miscSettings;
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
			
		setTransferHandler(new PanelTransferHandler(this, true, false));
		//DropTarget dt = new DropTarget(PartsList.this, DnDConstants.ACTION_MOVE, new PartsListDropHandler(), true);
		
		//setDropTarget(dt);
		
		
		setPreferredSize(new Dimension(250,24*20));
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

	protected void addPart(int idx) {
		AbcPart part = model.elementAt(idx);
		PartsListItem item = new PartsListItem(part, false, this);

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
		updatePartSoloMute(item.getPart());
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
	
	public List<Pair<Boolean, Boolean>> getSoloMuteStates() {
		List<Pair<Boolean, Boolean>> partSoloMuteList = new ArrayList<Pair<Boolean, Boolean>>(parts.size());
		for (PartsListItem item : parts) {
			Pair<Boolean, Boolean> soloMute = new Pair<Boolean, Boolean>(item.isSoloed(), item.isMuted());
			partSoloMuteList.add(soloMute);
		}
		return partSoloMuteList;
	}
	
	public void restoreSoloMuteState(List<Pair<Boolean, Boolean>> soloMuteState) {
		int len = soloMuteState.size() < parts.size()? soloMuteState.size() : parts.size();
		for (int i = 0; i < len; i++) {
			Pair<Boolean, Boolean> soloMute = soloMuteState.get(i);
			PartsListItem item = parts.get(i);
			item.setSolo(soloMute.first);
			item.setMute(soloMute.second);
			updateSequencerState(item);
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
	
	public static class PanelTransferHandler extends TransferHandler {
		
		PartsList main;
		private boolean canImport;
		private boolean export;
		
		PanelTransferHandler(PartsList main, boolean canImport, boolean export) {
			super();
			this.canImport = canImport;
			this.export = export;
			this.main = main;
		}
		
		@Override
		protected Transferable createTransferable(JComponent c) {
		    //System.out.println("Starting drag for: " + c);
			
		    if (!export) return null;

		    int panelIndex = main.model.indexOf(((PartsListItem) c.getParent()).getPart()); 
		    if (panelIndex == -1) {
		        System.out.println("Warning: Item not found in model!");
		        return null;
		    }

		    //System.out.println("Panel Index: " + panelIndex);
		    return new CustomTransferable(String.valueOf(panelIndex)); 
		}
		
		@Override
        public int getSourceActions(JComponent c) {
			if (!export) return TransferHandler.NONE;
            return TransferHandler.MOVE;
        }
		
		@Override
	    public boolean importData(TransferSupport support) {
	        if (!canImport(support)) return false;
	        try {
	            String partId = (String)
	                support.getTransferable()
	                       .getTransferData(PANEL_FLAVOR);
	            JComponent target = (JComponent) support.getComponent();
	            Point dropPt =
	                support.getDropLocation()
	                       .getDropPoint();
	            handleDrop(target, partId, dropPt);
	            return true;
	        } catch (Exception e) {
	            e.printStackTrace();
	            return false;
	        }
	    }
              
        public void handleDrop(JComponent target, String partId, Point dropPoint) {
            //System.out.println("Processing drop inside PanelTransferHandler...");
            
            int originalIndex = Integer.parseInt(partId);
        	DefaultListModel<AbcPart> modl = main.model; 
        	if (originalIndex != -1 && originalIndex < modl.getSize()) {
	        	
	        	AbcPart part = modl.getElementAt(originalIndex);
	        	int newIndex = getDropIndex(target, dropPoint);
	        	
	            //System.out.println("\n"+part.getTitle()+": originalIndex="+originalIndex);
	            //System.out.println("newIndex1="+newIndex);
	            
	            if (newIndex < originalIndex || newIndex > originalIndex+1) {
		            modl.removeElement(part);
		            if (newIndex > originalIndex) newIndex--;
		            if (newIndex > modl.size()-1) {
		            	modl.addElement(part);
		            } else {
		            	modl.insertElementAt(part, newIndex);
		            }
		            //System.out.println("newIndex2="+modl.indexOf(part)+" model size="+modl.size());
		            
		            //main.updateParts(); done by listener instead
		            part.getAbcSong().rearrangedParts();
	            }
            } else {
                System.out.println("Item not found in model!");
            }
        }
        
        private int getDropIndex(Component target, Point dropPoint) {
            int index = 0;
            for (Component comp : main.getComponents()) {
            	Point relativeDropPoint = SwingUtilities.convertPoint(target, dropPoint.getLocation(), main);
                Rectangle bounds = comp.getBounds();
                if (relativeDropPoint.y < bounds.y + bounds.height / 2) {
                    return index;
                }
                index++;
            }
            return main.getComponentCount(); // Drop at the end
        }

        @Override
        public boolean canImport(TransferSupport support) {
        	boolean result = canImport && support.isDataFlavorSupported(PANEL_FLAVOR);
            return result;
        }
        
        @Override
        public void exportDone(JComponent c, Transferable t, int action) {
            if (action == TransferHandler.MOVE) {
                //cleanup
            }
        }
    }
	
	static final DataFlavor PANEL_FLAVOR = new DataFlavor("application/x-custom-string", "Part index");
	
	public static class CustomTransferable implements Transferable {
	    private final String data;

	    public CustomTransferable(String data) {
	        this.data = data;
	    }

	    @Override
	    public DataFlavor[] getTransferDataFlavors() {
	        return new DataFlavor[]{PANEL_FLAVOR}; // Only custom flavor
	    }

	    @Override
	    public boolean isDataFlavorSupported(DataFlavor flavor) {
	        return flavor.equals(PANEL_FLAVOR);
	    }

	    @Override
	    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
	        if (!flavor.equals(PANEL_FLAVOR)) throw new UnsupportedFlavorException(flavor);
	        return data;
	    }
	}
	
	JScrollPane scrollPane = null;
	public void setScroll(JScrollPane partsListScrollPane) {
		scrollPane = partsListScrollPane;
	}
	
	@Override
	public Dimension getPreferredSize() {
		if (scrollPane == null) {
			return super.getPreferredSize();
		}
		int h = 0;
		int w = 0;
		//revalidate();
		for (Component c : getComponents()) {
			h += c.getPreferredSize().height;
			//w = Math.max(w, c.getPreferredSize().width);
		}
		//System.out.println("Viewport size: " + scrollPane.getViewport().getSize());
		//System.out.println("Inner component size: " + getSize());
		return new Dimension(Math.max(w, scrollPane.getViewport().getWidth()), Math.max(h, scrollPane.getViewport().getHeight()));
	}
}
