package com.digero.maestro.view;

import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.digero.common.midi.SequencerWrapper;
import com.digero.common.util.IDiscardable;
import com.digero.common.util.Listener;
import com.digero.common.util.Pair;
import com.digero.common.view.ColorTable;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.AbcPartEvent;
import com.digero.maestro.abc.AbcPartMetadataSource;
import com.digero.maestro.abc.AbcSong;
import com.digero.maestro.abc.AbcSongEvent;
import info.clearthought.layout.TableLayoutConstants;

public class SongPartsListPanel extends JPanel implements IDiscardable, TableLayoutConstants {
    protected static final Logger log = Logger.getLogger("view.PartsList");

	protected DefaultListModel<AbcPart> model;

	protected List<PartsListItem> parts = new ArrayList<>();
	protected AbcPart selectedPart = null;
	protected int selectedIndex = -1;
	protected MiscSettings miscSettings;

	private final SequencerWrapper abcSequencer;

	protected final Dimension rowDimension;
	private int dropInsertIndex = -1;
	private final PanelTransferHandler handler;

	private int hoveredTrack = -1;

	public SongPartsListPanel(SequencerWrapper abcSequencer, MiscSettings miscSettings) {
		this.abcSequencer = abcSequencer;
		this.miscSettings = miscSettings;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(new JList<AbcPartMetadataSource>().getBackground());

		rowDimension = PartsListItem.getProtoDimension();
		rowDimension.height = 8 * rowDimension.height; // min size should fit 8 rows
		this.setMinimumSize(rowDimension);

		/*
		if this is ran here it will update with stale track numbers from last preview
		instead we rely on PREVIEW_TRACK_NUMBER to set the soloMute state
		if (this.abcSequencer != null) {
			this.abcSequencer.addChangeListener(e -> {
				if (e.getProperty() == SequencerProperty.SEQUENCE) {
					updateTrackNumbers();
				}
			});
		}
		*/
		
		model = new DefaultListModel<>();
			
		handler = new PanelTransferHandler(this, true, false);
		setTransferHandler(handler);		
		new DropTarget(this, DnDConstants.ACTION_MOVE, new DropTargetAdapter() {
					    
		    @Override
		    public void dragEnter(DropTargetDragEvent dtde) {
		        if (dtde.isDataFlavorSupported(PANEL_FLAVOR)) {
		            dtde.acceptDrag(DnDConstants.ACTION_MOVE);
					if (PanelTransferHandler.isDragInProgress)
						getRootPane().setCursor(DragSource.DefaultMoveDrop);
		        }
		        else {
		            dtde.rejectDrag();
		        }
		    }

		    @Override
		    public void dragOver(DropTargetDragEvent dtde) {
		        if (dtde.isDataFlavorSupported(PANEL_FLAVOR)) {
		            dtde.acceptDrag(DnDConstants.ACTION_MOVE);

		            Point p = dtde.getLocation();
		            int newDropInsertIndex = handler.getDropIndex(dtde.getDropTargetContext().getComponent(), p);
					if (dropInsertIndex != newDropInsertIndex) {
						// condition avoids calling the repaint method too often
						dropInsertIndex = newDropInsertIndex;
						repaint();
					}
		        }
		        else {
		            dtde.rejectDrag();
		        }
		    }

		    @Override
		    public void dragExit(DropTargetEvent dte) {
		        dropInsertIndex = -1;
		        repaint();
				if (PanelTransferHandler.isDragInProgress)
					getRootPane().setCursor(DragSource.DefaultMoveNoDrop);
		    }

		    @Override
		    public void drop(DropTargetDropEvent dtde) {
		        dropInsertIndex = -1;
		        repaint();
		        try {
		        	if (handler != null && dtde.getTransferable().getTransferData(PANEL_FLAVOR) instanceof String) {
		        	dtde.acceptDrop(DnDConstants.ACTION_MOVE);
		        			                
		                handler.handleDrop(SongPartsListPanel.this, (String)dtde.getTransferable().getTransferData(PANEL_FLAVOR),dtde.getLocation());
		                dtde.dropComplete(true);
		        	} else {
		        		dtde.dropComplete(false);
		        		return;
		        	}
		        } catch (Exception ex) {
	                dtde.dropComplete(false);
	                log.log(Level.WARNING, "Error dropping part in parts list", ex);
	            }
		    }
		}, true);
		
		
		setPreferredSize(new Dimension(250,24*20));
	}
	
	public void updateParts() {
		parts = new ArrayList<>();
		removeAll();

		if (model.getSize() == 0) {
			selectedIndex = -1;
			selectedPart = null;
			hoveredTrack = -1;
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

		if (hoveredTrack != -1) {
			item.setTrackHighlight(part.isTrackEnabled(hoveredTrack));
		} else {
			item.setTrackHighlight(false);
		}
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
        if (selectedIndex == -1) {
            //System.out.println("Warning: PartsList selectedIndex is -1");
            //System.out.println("model.size()=" + model.size());
        } else {
            //System.out.println("PartsList selectedIndex=" + selectedIndex);
        }
		return selectedIndex;
	}

	AbcPart getSelectedPart() {
		if (model == null || selectedIndex == -1 || selectedIndex >= model.size())
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
		if (part == null || abcSequencer == null) {
			return;
		}

		int trackNo = part.getPreviewSequenceTrackNumber();

		if (trackNo >= 0) {
            //System.out.println(part.getTitle()+": updatePartSoloMute trackNo="+trackNo+" part.isSoloed()="+part.isSoloed()+" part.isMuted()="+part.isMuted()+" seq="+abcSequencer);
			abcSequencer.setTrackMute(trackNo, part.isMuted());
			abcSequencer.setTrackSolo(trackNo, part.isSoloed());
		}
	}
	
	public List<Pair<Boolean, Boolean>> getSoloMuteStates() {
		List<Pair<Boolean, Boolean>> partSoloMuteList = new ArrayList<>(parts.size());
		for (PartsListItem item : parts) {
			Pair<Boolean, Boolean> soloMute = new Pair<>(item.isSoloed(), item.isMuted());
			partSoloMuteList.add(soloMute);
		}
		return partSoloMuteList;
	}
	
	public void restoreSoloMuteState(List<Pair<Boolean, Boolean>> soloMuteState) {
		int len = Math.min(soloMuteState.size(), parts.size());
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
        //log.warning(this.getClass().getTypeName()+" AbcPartEvent: "+e.getProperty());
		switch (e.getProperty()) {
			case TRACK_ENABLED:
			case INSTRUMENT:
			case TITLE:
				updateParts();
				break;
			case PREVIEW_TRACK_NUMBER:
				// The new preview track number gets set after new sequence,
				// so we listen to it and react as needed.
				updatePartSoloMute(e.getSource());
				break;
			default:
				break;
		}
	};

	public Listener<AbcSongEvent> songListener = e -> {
		AbcSong song = e.getSource();
		if (song == null)
			return;
        //log.warning(this.getClass().getTypeName()+" AbcSongEvent: "+e.getProperty());
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
            // we do this in Projectframe instead, so
            // that we are sure updateParts runs before
            // project parts calls setselecteditem
			//updateParts();
			break;
		default:
			break;
		}
	};
	
	public static class PanelTransferHandler extends TransferHandler {
        /** This global flag is true when any DnD is in progress. */
        public static volatile boolean isDragInProgress = false;

		SongPartsListPanel main;
		private final boolean canImport;
		private final boolean export;
		
		PanelTransferHandler(SongPartsListPanel main, boolean canImport, boolean export) {
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
		        log.warning("Warning: Item not found in model!");
		        return null;
		    }

			isDragInProgress = true;
			JRootPane root = main.getRootPane();
			if (root != null)
				root.setCursor(DragSource.DefaultMoveDrop);

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
	            log.log(Level.WARNING, "Error importing DnD data", e);
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
        
        public int getDropIndex(Component target, Point dropPoint) {
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
            isDragInProgress = false;
			main.getRootPane().setCursor(null);
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
	
	@Override
	protected void paintChildren(Graphics g) {
	    super.paintChildren(g);
	    paintLine(g);
	}
	
	protected void paintLine(Graphics g) {
	    if (dropInsertIndex >= 0) {
	        Graphics2D g2 = (Graphics2D) g.create();

	        float[] dash = {4f, 4f};
	        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, dash, 0f));
	        g2.setColor(ColorTable.PARTS_LIST_DND_LINE.get());

	        int y = 0;
	        if (dropInsertIndex < getComponentCount()) {
	            Component c = getComponent(dropInsertIndex);
	            y = c.getY();
	        } else if (getComponentCount() > 0) {
	        	Component comp = getComponents()[getComponentCount()-1];
	            y = comp.getY()+comp.getHeight();
	        }
	        g2.drawLine(0, y, getWidth(), y);
	        g2.dispose();
	    }
	}

	public void highlightPartsForTrack(int trackNumber) {
		for (PartsListItem item : parts) {
			boolean uses = item.getPart().isTrackEnabled(trackNumber);
			item.setTrackHighlight(uses);
		}
		hoveredTrack = trackNumber;
	}

	public void clearTrackHighlight(final int trackNumber) {
		if (hoveredTrack == trackNumber) {
			hoveredTrack = -1;
			for (PartsListItem item : parts) item.setTrackHighlight(false);
		}
	}
}
