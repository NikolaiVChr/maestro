package com.digero.maestro.view;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED;
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.text.ParseException;
import java.util.HashMap;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.digero.common.abc.LotroInstrument;
import com.digero.common.icons.IconLoader;
import com.digero.common.midi.NoteFilterSequencerWrapper;
import com.digero.common.midi.SequencerEvent.SequencerProperty;
import com.digero.common.midi.SequencerWrapper;
import com.digero.common.util.ICompileConstants;
import com.digero.common.util.IDiscardable;
import com.digero.common.util.Listener;
import com.digero.common.util.Util;
import com.digero.common.view.ColorTable;
import com.digero.common.view.InstrumentComboBox;
import com.digero.common.view.PatchedJScrollPane;
import com.digero.common.view.WrapLayout;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.AbcPartEvent;
import com.digero.maestro.abc.AbcPartEvent.AbcPartProperty;
import com.digero.maestro.abc.PartAutoNumberer;
import com.digero.maestro.midi.TrackInfo;

import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstants;

/**
 * We should really rename this class. It has nothing to do with parts. Very confusing. ~Aifel
 * 
 * This is the panel that holds the tracks and their controls.
 * It also hold the part header at top, and tempopanel and histogram panel.
 * 
 */
@SuppressWarnings("serial")
public class PartPanel extends JPanel implements ICompileConstants, TableLayoutConstants {
	private static final int HGAP = 4;
	private static final int VGAP = 4;

	private AbcPart abcPart;// The currently selected abcPart in left part panel
	private PartAutoNumberer partAutoNumberer;
	private NoteFilterSequencerWrapper sequencer;
	private SequencerWrapper abcSequencer;
	private boolean isAbcPreviewMode = false;
	private boolean showMaxPolyphony = false;

	private JSpinner numberSpinner;
	private SpinnerNumberModel numberSpinnerModel;
	private JButton numberSettingsButton;
	private JTextField nameTextField;
	private JComboBox<LotroInstrument> instrumentComboBox;
	private JLabel messageLabel;
	
	private JSlider hZoomSlider;
	private JSlider vZoomSlider;
	
	private JPanel splitPanel;
	
	private JPanel controlPanel;
	
	private PatchedJScrollPane noteGraphScrollPane;
	private JPanel noteGraphPanel;
	
	// Note graphs
	HistogramPanel histogramPanel;
	TempoPanel tempoPanel;
	HashMap<Integer, TrackPanel> trackPanels = new HashMap<Integer, TrackPanel>();
	
	private ControlLayout controlLayout;
	private GraphLayout graphLayout;

	private boolean initialized = false;

	private float hZoom = 1.f;
	private float vZoom = 1.f;
	private boolean textnoteVisible = false;
	private JTextArea noteContent = new JTextArea();
	private JScrollPane notePanel = null;
	private boolean syncUpdate = false;
	private boolean mouseHzooming = false;
	private Point mousePointTrack = null;
	private Point mousePointView = null;
	private double sequenceProgress = 0.0d;

	public PartPanel(NoteFilterSequencerWrapper sequencer, PartAutoNumberer partAutoNumberer,
			SequencerWrapper abcSequencer, boolean showMaxPolyphony) {
		super(new TableLayout(//
				new double[] { FILL, PREFERRED },  // x  tracks, note
				new double[] { PREFERRED, PREFERRED, FILL }));// y  part-header, zoom, tracks

		TableLayout layout = (TableLayout) getLayout();
		layout.setHGap(HGAP);
		layout.setVGap(VGAP);

		setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ColorTable.PANEL_BORDER.get()));
		
		this.showMaxPolyphony = showMaxPolyphony;

		this.sequencer = sequencer;
		this.abcSequencer = abcSequencer;
		this.partAutoNumberer = partAutoNumberer;

		numberSpinnerModel = new SpinnerNumberModel(0, 0, 999, partAutoNumberer.getIncrement());
		numberSpinner = new JSpinner(numberSpinnerModel);
		numberSpinner.addChangeListener(e -> {
			if (abcPart != null && !abcPart.suppressSpinnerUpdate) {
				PartPanel.this.partAutoNumberer.setPartNumber(abcPart, (Integer) numberSpinner.getValue());
			}
		});

		numberSettingsButton = new JButton(IconLoader.getImageIcon("gear_16.png"));
		numberSettingsButton.setMargin(new Insets(0, 0, 0, 0));
		numberSettingsButton.setToolTipText("Automatic part numbering options");
		numberSettingsButton.setVisible(false);

		nameTextField = new JTextField(32);
		nameTextField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void removeUpdate(DocumentEvent e) {
				if (abcPart != null && !syncUpdate)
					abcPart.setTitle(nameTextField.getText());
			}

			@Override
			public void insertUpdate(DocumentEvent e) {
				if (abcPart != null && !syncUpdate)
					abcPart.setTitle(nameTextField.getText());
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				if (abcPart != null && !syncUpdate)
					abcPart.setTitle(nameTextField.getText());
			}
		});

		instrumentComboBox = new InstrumentComboBox();
		instrumentComboBox.addActionListener(e -> {
			if (abcPart != null) {
				LotroInstrument newInstrument = (LotroInstrument) instrumentComboBox.getSelectedItem();
				LotroInstrument oldInstrument = abcPart.getInstrument();
				PartPanel.this.partAutoNumberer.setInstrument(abcPart, newInstrument);
				abcPart.replaceTitleInstrument(newInstrument, oldInstrument);
				nameTextField.setText(abcPart.getTitle());
				//updateTracksVisible();
			}
		});
		
		JPanel partSettingsPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, HGAP, 0));

		// We never support a zoom max of less than 6x
		final float maxHZoomBase = 6.f;
		// For songs longer than 1 minute, we divide song length in seconds
		// by 10 to get adjusted zoom
		// so that approx. 8 seconds of music is on screen for max zoom
		final float zoomSecondDivider = 8f;
		JLabel hZoomLabel = new JLabel("HZoom:");
		hZoomSlider = new JSlider(0, 1000, 0);
		hZoomSlider.setFocusable(false);
		hZoomSlider.addChangeListener(e -> {
			float secs = (sequencer.getLength() / (1000 * 1000.f));
			float adjustedZoom = Math.max(maxHZoomBase, secs / zoomSecondDivider);
			float oldHZoom = hZoom; 
			hZoom = Util.map((float)Math.pow(hZoomSlider.getValue(),4d), 0.0f, (float)Math.pow(1000.0d,4d), 1.f, adjustedZoom);
			if (hZoom != oldHZoom) {
				calcZoomTarget();
				updateZoom();
				if (mousePointTrack != null) mousePointTrack.x *= hZoom / oldHZoom;
				scrollToPosition(mouseHzooming);
				repaintAfterZoom();
	//			System.out.println("hz: " + hZoom);
			}
		});
		
		final float maxVZoom = 6.f;
		JLabel vZoomLabel = new JLabel("VZoom:");
		vZoomSlider = new JSlider(0, 1000, 0);
		vZoomSlider.setFocusable(false);
		vZoomSlider.addChangeListener(e -> {
			vZoom = Util.map(vZoomSlider.getValue(), 0, 1000, 1, maxVZoom);
			updateZoom();
			repaintAfterZoom();
//			System.out.println("vz: " + vZoom);
		});
		
		JCheckBox followCheckBox = new JCheckBox("Follow");
		followCheckBox.setHorizontalTextPosition(SwingConstants.LEFT);
		followCheckBox.setFocusable(false);
		followCheckBox.setToolTipText("<html>Auto follow track position while song is playing.</html>");
		
		JPanel xPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, HGAP, 0));
		xPanel.add(new JLabel("X:"));
		xPanel.add(numberSpinner);
		partSettingsPanel.add(xPanel);
		
		partSettingsPanel.add(numberSettingsButton);
		
		JPanel iPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, HGAP, 0));
		iPanel.add(new JLabel("I:"));
		iPanel.add(instrumentComboBox);
		partSettingsPanel.add(iPanel);
		
		JPanel partNamePanel = new JPanel(new WrapLayout(FlowLayout.LEFT, HGAP, 0));
		partNamePanel.add(new JLabel("Part name:"));
		partNamePanel.add(nameTextField);
		partSettingsPanel.add(partNamePanel);
		
		JPanel zoomPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, HGAP, 0));
		zoomPanel.add(hZoomLabel);
		zoomPanel.add(hZoomSlider);
		zoomPanel.add(vZoomLabel);
		zoomPanel.add(vZoomSlider);
		zoomPanel.add(followCheckBox);
		partSettingsPanel.add(zoomPanel);

		splitPanel = new JPanel(new TableLayout(new double[] { PREFERRED, FILL }, //
				new double[] { FILL }));
		splitPanel.setBorder(BorderFactory.createEmptyBorder());
		splitPanel.setBackground(ColorTable.PANEL_BACKGROUND_DISABLED.get());
		
		noteGraphPanel = new JPanel() {
			@Override
			public Dimension getPreferredSize() {
				int widestWidth = (int) (noteGraphScrollPane.getViewport().getExtentSize().width * graphLayout.getZoomHorizontal());
				int height = controlLayout.getPreferredHeight();
				return new Dimension(widestWidth, height);
			}
		};
		noteGraphScrollPane = new PatchedJScrollPane(noteGraphPanel, VERTICAL_SCROLLBAR_ALWAYS, HORIZONTAL_SCROLLBAR_AS_NEEDED);

		controlLayout = new ControlLayout(TrackPanel.calculateTrackDims().rowHeight + 1, noteGraphPanel, noteGraphScrollPane);
		controlPanel = new JPanel(controlLayout) {
			@Override
			public Dimension getPreferredSize() {
				int widestWidth = 0;
				for (Component c : getComponents()) {
					if (c.isVisible()) {
						Dimension cDim = c.getPreferredSize();
						if (cDim.width > widestWidth) {
							widestWidth = cDim.width;
						}
					}
				}
				return new Dimension(widestWidth, controlLayout.getPreferredHeight());
			}
		};
		MouseAdapter listenForControlFocus = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				getRootPane().requestFocus();
			}
		};
		controlPanel.addMouseListener(listenForControlFocus);
		controlPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0)); // top, left, bottom, right
		controlPanel.setBackground(ColorTable.PANEL_BACKGROUND_DISABLED.get());
		
		graphLayout = new GraphLayout(TrackPanel.calculateTrackDims().rowHeight + 1, controlLayout);
		graphLayout.setViewport(noteGraphScrollPane.getViewport());
		noteGraphPanel.setLayout(graphLayout);
		noteGraphPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0)); // top, left, bottom, right

		noteGraphPanel.setBackground(ColorTable.PANEL_BACKGROUND_DISABLED.get());
		
		
		noteGraphScrollPane.setBorder(BorderFactory.createEmptyBorder());
		noteGraphScrollPane.setCorner(ScrollPaneConstants.LOWER_RIGHT_CORNER, new JPanel());
		noteGraphScrollPane.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				getRootPane().requestFocus();
			}
		});
		noteGraphScrollPane.getViewport().addChangeListener(ch -> {
			controlPanel.invalidate();
			validate();
			repaint();
		});
		
		// Link control scroll bar model to note graph scroll bar
		// so they're both controlled by note graph scroll bar
		JScrollBar noteGraphBar = noteGraphScrollPane.getVerticalScrollBar();		
		noteGraphBar.setUnitIncrement(TrackPanel.calculateTrackDims().rowHeight / 2);

		splitPanel.add(controlPanel, "0, 0");
		splitPanel.add(noteGraphScrollPane, "1, 0, f, f");
		
		messageLabel = new JLabel();
		messageLabel.setHorizontalAlignment(SwingConstants.CENTER);
		messageLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 20));
		messageLabel.setForeground(ColorTable.PANEL_TEXT_DISABLED.get());
		messageLabel.setVisible(false);

		// notePanel is the textfield with project notes
		notePanel = new JScrollPane(noteContent, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
		notePanel.setPreferredSize(new Dimension(225, 200));
		noteContent.setLineWrap(true);
		noteContent.setWrapStyleWord(true);
		noteContent.setTabSize(4);

		add(partSettingsPanel, "0, 0");
		add(messageLabel, "0, 2, C, C");
		add(splitPanel, "0, 2");
		
		// For follow support
		sequencer.addChangeListener(e -> {
			if (!followCheckBox.isSelected()) {
				return;
			}
			
			if (Math.abs(hZoom - 1.f) < 0.001) {
				return;
			}
			
			if (e.getProperty() != SequencerProperty.POSITION || sequencer.isDragging() ||
					!(sequencer.isRunning() || abcSequencer.isRunning())) {
				return;
			}
			
			if (noteGraphScrollPane.getHorizontalScrollBar().getValueIsAdjusting()) {
				return;
			}
			sequenceProgress = sequencer.getThumbPosition() / (double)(sequencer.getLength());
			scrollToPosition(false);
		});
		
		noteGraphScrollPane.getViewport().getView().addMouseWheelListener(e -> {
			if (!handleMouseWheelZoom(e)) {
				noteGraphScrollPane.dispatchEvent(SwingUtilities.convertMouseEvent(this, e, noteGraphScrollPane));
			}
		});

		JPanel t = this; 
		// Remove focus if any empty space in the window is clicked
		MouseAdapter listenForFocus = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				getRootPane().requestFocus();
			}
			
			@Override
			public void mouseWheelMoved(MouseWheelEvent arg0) {
				if (!handleMouseWheelZoom(arg0)) {
					noteGraphScrollPane.dispatchEvent(SwingUtilities.convertMouseEvent(t, arg0, noteGraphScrollPane));
				}
			}
		};
		addMouseListener(listenForFocus);
		addMouseWheelListener(listenForFocus);

		setAbcPart(null, false);
		initialized = true;
	}
	
	private boolean calcZoomTarget() {
		sequenceProgress = sequencer.getThumbPosition() / (double)(sequencer.getLength());
				
		int trackHeadGraphPos = (int) (graphLayout.getTrackWidth() * sequenceProgress);
		
		int minimum = noteGraphScrollPane.getViewport().getViewPosition().x;
		int maximum = noteGraphScrollPane.getViewport().getViewPosition().x + noteGraphScrollPane.getViewport().getExtentSize().width;
		
		boolean inView = trackHeadGraphPos >= minimum && trackHeadGraphPos <= maximum;
		
		if (!inView) sequenceProgress = ((minimum+maximum)/2)/(double)graphLayout.getTrackWidth();
		
		return inView;
	}

	private void scrollToPosition(boolean mouseZooming) {
		int graphWidth = graphLayout.getTrackWidth();
		int trackHeadGraphPos = (int) (graphWidth * sequenceProgress);
		int viewWidth = noteGraphScrollPane.getViewport().getExtentSize().width;
		int value = trackHeadGraphPos - viewWidth / 2;
		if (mouseZooming && mousePointView != null && mousePointTrack != null) {
			value = mousePointTrack.x - mousePointView.x;
		}		
		Point oldView = noteGraphScrollPane.getViewport().getViewPosition();
		Point newView = new Point(Math.min(graphWidth - viewWidth, Math.max(0,value)), oldView.y);
		noteGraphScrollPane.getViewport().setViewPosition(newView);
	}
	
	// Returns false if we should forward the event to the scroll pane
	// since it's just a normal scroll (no shift or control held)
	boolean handleMouseWheelZoom(MouseWheelEvent e) {
		if (e.isControlDown()) {
				int val = hZoomSlider.getValue() - 15 * e.getWheelRotation();
				mouseHzooming = true;
				PointerInfo info = MouseInfo.getPointerInfo();
				if (info != null) {
					Point p = info.getLocation();
					SwingUtilities.convertPointFromScreen(p, noteGraphPanel);				
					mousePointTrack = p;
					info = MouseInfo.getPointerInfo();
					Point p2 = info.getLocation();
					SwingUtilities.convertPointFromScreen(p2, noteGraphScrollPane);				
					mousePointView = p2;
				} else {
					mousePointView = null;
					mousePointTrack = null;
				}
				hZoomSlider.setValue(Util.clamp(val, 0, 1000));
				mouseHzooming  = false;
				return true;
		}
		else if (e.isShiftDown()) {
			int val = vZoomSlider.getValue() - 15 * e.getWheelRotation();
			vZoomSlider.setValue(Util.clamp(val, 0, 1000));
			return true;
		}
		
		return false;
	}

	public void setNewTitle(AbcPart thePart) {
		if (thePart != abcPart || nameTextField.getText().equals(thePart.getTitle()))
			return;
		syncUpdate = true;
		nameTextField.setText(thePart.getTitle());
		syncUpdate = false;
	}

	public void addSettingsActionListener(ActionListener listener) {
		numberSettingsButton.addActionListener(listener);
		numberSettingsButton.setVisible(true);
	}

	private Listener<AbcPartEvent> abcPartListener = e -> {
		if (e.getProperty() == AbcPartProperty.PART_NUMBER) {
			abcPart.suppressSpinnerUpdate = true;
			numberSpinner.setValue(abcPart.getPartNumber());
			abcPart.suppressSpinnerUpdate = false;
		} else if (e.getProperty() == AbcPartProperty.INSTRUMENT) {
			setAbcPart(abcPart, true); // Revalidate layout
		}
	};

	public void settingsChanged() {
		numberSpinnerModel.setStepSize(partAutoNumberer.getIncrement());
	}
	
	public void closeAbcSong() {
		clearTrackListPanel(true);
		histogramPanel = null;
		tempoPanel = null;
		trackPanels.clear();
		abcPart = null;
	}

	public void setAbcPart(AbcPart abcPart, boolean force) {
		messageLabel.setVisible(false);

		if (this.abcPart == abcPart && initialized && !force)
			return;

		if (this.abcPart != null) {
			try {
				numberSpinner.commitEdit();
			} catch (ParseException e) {
			}
			this.abcPart.removeAbcListener(abcPartListener);
			this.abcPart = null;
		}

		if (abcPart == null) {
			numberSpinner.setEnabled(false);
			nameTextField.setEnabled(false);
			instrumentComboBox.setEnabled(false);
			
			hZoomSlider.setEnabled(false);
			vZoomSlider.setEnabled(false);

			numberSpinner.setValue(0);
			nameTextField.setText("");
			instrumentComboBox.setSelectedItem(LotroInstrument.DEFAULT_INSTRUMENT);

			clearTrackListPanel(true);
		} else {
			numberSpinner.setEnabled(true);
			nameTextField.setEnabled(true);
			instrumentComboBox.setEnabled(true);
			
			hZoomSlider.setEnabled(true);
			vZoomSlider.setEnabled(true);

			numberSpinner.setValue(abcPart.getPartNumber());
			nameTextField.setText(abcPart.getTitle());
			instrumentComboBox.setSelectedItem(abcPart.getInstrument());

			clearTrackListPanel(false);

			// Add the tempo panel if this song contains tempo changes
			if (abcPart.getSequenceInfo().hasTempoChanges() || abcPart.getAbcSong().tuneBarsModified != null) {
				if (tempoPanel == null) {
					tempoPanel = new TempoPanel(abcPart.getSequenceInfo(), sequencer, abcSequencer,
							abcPart.getAbcSong());
				}
				tempoPanel.setAbcPreviewMode(isAbcPreviewMode);
				tempoPanel.revalidate();
				controlPanel.add(tempoPanel,"x");
				noteGraphPanel.add(tempoPanel.getNoteGraph(),"x");
			}
			
			// Add the histogram panel
			if (histogramPanel == null) {
				histogramPanel = new HistogramPanel(abcPart.getSequenceInfo(), sequencer, abcSequencer,
						abcPart.getAbcSong());
			}
			histogramPanel.setAbcPreviewMode(isAbcPreviewMode, showMaxPolyphony);
			histogramPanel.revalidate();
			
			controlPanel.add(histogramPanel,"x");
			noteGraphPanel.add(histogramPanel.getNoteGraph(),"x");
			

			// Add the tracks and note graphs
			for (TrackInfo track : abcPart.getSequenceInfo().getTrackList()) {
				int trackNumber = track.getTrackNumber();
				if (track.hasEvents()) {
					if (!trackPanels.containsKey(trackNumber)) {
						trackPanels.put(trackNumber, new TrackPanel(track, sequencer, abcPart, abcSequencer, controlLayout));
					}
					TrackPanel trackPanel = trackPanels.get(trackNumber);
					trackPanel.setAbcPart(abcPart);
					trackPanel.setAbcPreviewMode(isAbcPreviewMode);
					controlPanel.add(trackPanel,"x");
					noteGraphPanel.add(trackPanel.getNoteGraph(),"x");

					if (MUTE_DISABLED_TRACKS)
						sequencer.setTrackMute(trackNumber, !abcPart.isTrackEnabled(trackNumber));
				}

				if (!MUTE_DISABLED_TRACKS)
					sequencer.setTrackMute(trackNumber, false);

				sequencer.setTrackSolo(trackNumber, false);
			}
			
			// add dummy space at the end to fix scroll bar calcuation swing bug
			
			int scrollbarHeight = new JScrollPane().getHorizontalScrollBar().getPreferredSize().height;
			
			class Dummy extends JPanel implements PartPanelItem {
				@Override
				public JPanel getNoteGraph() {
					return null;
				}

				@Override
				public boolean isVerticalZoomForbidden() {
					return true;
				}
			}			
			Dummy dummy1 = new Dummy();
			dummy1.setPreferredSize(new Dimension(100, scrollbarHeight * 2));
			dummy1.setOpaque(true);
			dummy1.setBackground(ColorTable.PANEL_BACKGROUND_DISABLED.get());
			controlPanel.add(dummy1, "x");
			
			JPanel dummy2 = new JPanel();
			dummy2.setPreferredSize(new Dimension(100, scrollbarHeight * 2));
			dummy2.setOpaque(true);
			dummy2.setBackground(ColorTable.PANEL_BACKGROUND_DISABLED.get());
			noteGraphPanel.add(dummy2,"x");
		}

		this.abcPart = abcPart;
		if (this.abcPart != null) {
			this.abcPart.addAbcListener(abcPartListener);
		}

		//updateTracksVisible();
		validate();
		repaint();
	}

	public AbcPart getAbcPart() {
		return abcPart;
	}

	public void setAbcPreviewMode(boolean isAbcPreviewMode) {
		//if (this.isAbcPreviewMode != isAbcPreviewMode) {
			this.isAbcPreviewMode = isAbcPreviewMode;
			for (Component child : controlPanel.getComponents()) {
				if (child instanceof TrackPanel) {
					((TrackPanel) child).setAbcPreviewMode(isAbcPreviewMode);
				} else if (child instanceof DrumPanel) {
					((DrumPanel) child).setAbcPreviewMode(isAbcPreviewMode);
				} else if (child instanceof TempoPanel) {
					((TempoPanel) child).setAbcPreviewMode(isAbcPreviewMode);
				} else if (child instanceof HistogramPanel) {
					((HistogramPanel) child).setAbcPreviewMode(isAbcPreviewMode, showMaxPolyphony);
				}
			}
		//}
	}

	public boolean isAbcPreviewMode() {
		return isAbcPreviewMode;
	}

	public void showInfoMessage(String message) {
		setAbcPart(null, false);

		messageLabel.setText(message);
		messageLabel.setVisible(true);
	}

	private void clearTrackListPanel(boolean discard) {
		if (discard) {
			for (Component child : controlPanel.getComponents()) {
				if (child instanceof IDiscardable) {
					((IDiscardable) child).discard();
				}
			}
			for (Component child : noteGraphPanel.getComponents()) {
				if (child instanceof IDiscardable) {
					((IDiscardable) child).discard();
				}
			}
		}
		controlPanel.removeAll();
		noteGraphPanel.removeAll();
	}

	public void commitAllFields() {
		try {
			numberSpinner.commitEdit();
		} catch (java.text.ParseException e) {
			// Ignore
		}
	}

	public void updateZoom() {
		graphLayout.setZoomHorizontal(hZoom);
		controlLayout.setZoomVertical(vZoom);
	}

	private void repaintAfterZoom() {
		//Note invalidate does not invalidate sub components, hence why its called on the panels directly
		noteGraphPanel.invalidate();
		noteGraphScrollPane.invalidate();
		controlPanel.invalidate();
		revalidate();
		repaint();
	}
	
	public void unZoom() {
		// Called from ProjectFrame when song closes
		hZoomSlider.setValue(0);
		vZoomSlider.setValue(0);
		repaintAfterZoom();
	}

	public void textnoteToggle() {
		textnoteVisible(!textnoteVisible);
	}

	public void textnoteVisible(boolean vis) {
		textnoteVisible = vis;
		if (textnoteVisible) {
			add(notePanel, "1, 0, 1, 2, F, F");
		} else {
			remove(notePanel);
		}
		validate();
		repaint();
	}

	public String getTextnote() {
		return noteContent.getText();
	}

	public void setTextnote(String note) {
		noteContent.setText(note);
		noteContent.setCaretPosition(0);
	}

	public void setPolyphony(boolean showMaxPolyphony) {
		this.showMaxPolyphony = showMaxPolyphony;
		setAbcPreviewMode(isAbcPreviewMode());
	}
}
