package com.digero.maestro.view;
//import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED;
//import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS;
//import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
//import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS;
//import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;
//import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED;
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.ParseException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.digero.common.abc.LotroInstrument;
import com.digero.common.icons.IconLoader;
import com.digero.common.midi.NoteFilterSequencerWrapper;
import com.digero.common.midi.SequencerWrapper;
import com.digero.common.util.ICompileConstants;
import com.digero.common.util.IDiscardable;
import com.digero.common.util.Listener;
import com.digero.common.view.ColorTable;
import com.digero.common.view.InstrumentComboBox;
import com.digero.common.view.PatchedJScrollPane;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.AbcPartEvent;
import com.digero.maestro.abc.AbcPartEvent.AbcPartProperty;
import com.digero.maestro.abc.PartAutoNumberer;
import com.digero.maestro.midi.TrackInfo;
import com.digero.maestro.view.TrackPanel.TrackDimensions;

import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstants;
import net.miginfocom.swing.MigLayout;

/**
 * We should really rename this class. It has nothing to do with parts. Very confusing. ~Aifel
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

//	private JScrollPane trackScrollPane;
	
	private JPanel splitPanel;
	
	private PatchedJScrollPane controlScrollPane;
	private JPanel controlPanel;
	
	private PatchedJScrollPane noteGraphScrollPane;
	private JPanel noteGraphPanel;

//	private JPanel trackListPanel;
//	private GroupLayout trackListLayout;
//	private GroupLayout.Group trackListVGroup;
//	private GroupLayout.Group trackListHGroup;

	private boolean initialized = false;

	private boolean zoomed = false;
	private boolean noteVisible = false;
	private JTextArea noteContent = new JTextArea();
	private JScrollPane notePanel = null;
	private boolean syncUpdate = false;

	public PartPanel(NoteFilterSequencerWrapper sequencer, PartAutoNumberer partAutoNumberer,
			SequencerWrapper abcSequencer, boolean showMaxPolyphony) {
		super(new TableLayout(//
				new double[] { FILL, PREFERRED }, //
				new double[] { PREFERRED, FILL }));

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
			if (abcPart != null)
				PartPanel.this.partAutoNumberer.setPartNumber(abcPart, (Integer) numberSpinner.getValue());
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

		JPanel dataPanel = new JPanel(new BorderLayout(0, VGAP));
		JPanel dataPanel2 = new JPanel(new FlowLayout(FlowLayout.LEFT, HGAP, 0));
		dataPanel2.add(new JLabel("X:"));
		dataPanel2.add(numberSpinner);
		dataPanel2.add(numberSettingsButton);
		dataPanel2.add(new JLabel(" I:"));
		dataPanel2.add(instrumentComboBox);
		dataPanel2.add(new JLabel(" Part name:"));
		dataPanel.add(dataPanel2, BorderLayout.WEST);
		dataPanel.add(nameTextField, BorderLayout.CENTER);

//		trackListPanel = new JPanel();
//		trackListLayout = new GroupLayout(trackListPanel);
//		trackListLayout.setVerticalGroup(trackListVGroup = trackListLayout.createSequentialGroup());
//		trackListLayout.setHorizontalGroup(trackListHGroup = trackListLayout.createParallelGroup());
//		trackListLayout.setHonorsVisibility(true);
//		trackListPanel.setLayout(trackListLayout);
//		trackListPanel.setBackground(ColorTable.PANEL_BACKGROUND_DISABLED.get());
		
		boolean dbg = false;
//		dbg = true;
//		splitPanel = new JPanel(new MigLayout((dbg? "debug, " : "") + "wrap 2, gap 0, ins 0, novisualpadding, filly", "[]0[grow]"));
		splitPanel = new JPanel(new TableLayout(new double[] { PREFERRED, FILL }, //
				new double[] { FILL }));
		splitPanel.setBorder(BorderFactory.createEmptyBorder());
		splitPanel.setBackground(ColorTable.PANEL_BACKGROUND_DISABLED.get());
		
		controlPanel = new JPanel(new MigLayout((dbg? "debug, " : "") + "wrap 1, gap 0, novisualpadding, ins 0"));
		controlPanel.setBackground(ColorTable.PANEL_BACKGROUND_DISABLED.get());
		
		controlScrollPane = new PatchedJScrollPane(controlPanel, VERTICAL_SCROLLBAR_NEVER, HORIZONTAL_SCROLLBAR_NEVER);
		controlScrollPane.setBorder(BorderFactory.createEmptyBorder());
		// Remove focus from text boxes if area under midi tracks is clicked
		controlScrollPane.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				getRootPane().requestFocus();
			}
		});
		
		controlScrollPane.setWheelScrollingEnabled(false);
		controlScrollPane.addMouseWheelListener(e -> noteGraphScrollPane.dispatchEvent(e));
		
		noteGraphPanel = new JPanel(new MigLayout((dbg? "debug, " : "") + "wrap 1, gap 0, ins 0, novisualpadding, fillx"));
		noteGraphPanel.setBackground(ColorTable.PANEL_BACKGROUND_DISABLED.get());
		
		noteGraphScrollPane = new PatchedJScrollPane(noteGraphPanel, VERTICAL_SCROLLBAR_ALWAYS, HORIZONTAL_SCROLLBAR_AS_NEEDED);
		noteGraphScrollPane.setBorder(BorderFactory.createEmptyBorder());
		noteGraphScrollPane.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				getRootPane().requestFocus();
			}
		});
		
		// Link control scroll bar model to note graph scroll bar
		// so they're both controlled by note graph scroll bar
		JScrollBar controlBar = controlScrollPane.getVerticalScrollBar();
		JScrollBar noteGraphBar = noteGraphScrollPane.getVerticalScrollBar();
		controlBar.setModel(noteGraphBar.getModel());
		
		noteGraphBar.setUnitIncrement(TrackPanel.calculateTrackDims().rowHeight / 2);

		
		splitPanel.add(controlScrollPane, "0, 0");
		splitPanel.add(noteGraphScrollPane, "1, 0");
		
//		splitPanel.add(controlScrollPane, "top");
//		splitPanel.add(noteGraphScrollPane, "top");
		
//		horizInnerScrollPane.add()
		
		

//		trackScrollPane = new JScrollPane(trackListPanel, VERTICAL_SCROLLBAR_ALWAYS, HORIZONTAL_SCROLLBAR_AS_NEEDED);
//		// Remove focus from text boxes if area under midi tracks is clicked
//		trackScrollPane.addMouseListener(new MouseAdapter() {
//			@Override
//			public void mouseClicked(MouseEvent e) {
//				getRootPane().requestFocus();
//			}
//		});
		
		messageLabel = new JLabel();
		messageLabel.setHorizontalAlignment(SwingConstants.CENTER);
		messageLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 20));
		messageLabel.setForeground(ColorTable.PANEL_TEXT_DISABLED.get());
		messageLabel.setVisible(false);

		notePanel = new JScrollPane(noteContent, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
		notePanel.setPreferredSize(new Dimension(225, 200));
		noteContent.setLineWrap(true);
		noteContent.setWrapStyleWord(true);
		noteContent.setTabSize(4);

		add(dataPanel, "0, 0");
		add(messageLabel, "0, 1, C, C");
		add(splitPanel, "0, 1");

		// Remove focus if any empty space in the window is clicked
		MouseAdapter listenForFocus = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				getRootPane().requestFocus();
			}
		};
		addMouseListener(listenForFocus);

		setAbcPart(null, false);
		initialized = true;
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
			numberSpinner.setValue(abcPart.getPartNumber());
		}
	};

	public void settingsChanged() {
		numberSpinnerModel.setStepSize(partAutoNumberer.getIncrement());
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

			numberSpinner.setValue(0);
			nameTextField.setText("");
			instrumentComboBox.setSelectedItem(LotroInstrument.DEFAULT_INSTRUMENT);

			clearTrackListPanel();
		} else {
			numberSpinner.setEnabled(true);
			nameTextField.setEnabled(true);
			instrumentComboBox.setEnabled(true);

			numberSpinner.setValue(abcPart.getPartNumber());
			nameTextField.setText(abcPart.getTitle());
			instrumentComboBox.setSelectedItem(abcPart.getInstrument());

			clearTrackListPanel();

			// Add the tempo panel if this song contains tempo changes
			if (abcPart.getSequenceInfo().hasTempoChanges() || abcPart.getAbcSong().tuneBarsModified != null) {
				TempoPanel tempoPanel = new TempoPanel(abcPart.getSequenceInfo(), sequencer, abcSequencer,
						abcPart.getAbcSong());
				tempoPanel.setAbcPreviewMode(isAbcPreviewMode);
				tempoPanel.revalidate();
				controlPanel.add(tempoPanel);
				noteGraphPanel.add(tempoPanel.getNoteGraph(), "growx");
			}
			
			// Add the histogram panel
			HistogramPanel histogramPanel = new HistogramPanel(abcPart.getSequenceInfo(), sequencer, abcSequencer,
						abcPart.getAbcSong());
			histogramPanel.setAbcPreviewMode(isAbcPreviewMode, showMaxPolyphony);
			histogramPanel.revalidate();
			controlPanel.add(histogramPanel);
			noteGraphPanel.add(histogramPanel.getNoteGraph(), "growx");

			// Add the tracks and note graphs
			for (TrackInfo track : abcPart.getSequenceInfo().getTrackList()) {
				int trackNumber = track.getTrackNumber();
				if (track.hasEvents()) {
					TrackPanel trackPanel = new TrackPanel(track, sequencer, abcPart, abcSequencer);
					trackPanel.setAbcPreviewMode(isAbcPreviewMode);
					controlPanel.add(trackPanel);
					noteGraphPanel.add(trackPanel.getNoteGraph(), "growx");
					
//					if (trackPanel.hasDrumPanels()) {
//						ArrayList<DrumPanel> drums = trackPanel.getDrumPanels();
//						for (DrumPanel dp : drums) {
//							noteGraphPanel.add(dp.getNoteGraph(), "growx");
//						}
//					}

					if (MUTE_DISABLED_TRACKS)
						sequencer.setTrackMute(trackNumber, !abcPart.isTrackEnabled(trackNumber));
				}

				if (!MUTE_DISABLED_TRACKS)
					sequencer.setTrackMute(trackNumber, false);

				sequencer.setTrackSolo(trackNumber, false);
			}
			
			// add dummy space at the end to fix scroll bar calcuation swing bug
			
			int scrollbarHeight = new JScrollPane().getHorizontalScrollBar().getPreferredSize().height;
			JPanel dummy1 = new JPanel();
			dummy1.setPreferredSize(new Dimension(200, scrollbarHeight * 2));
			dummy1.setOpaque(true);
			dummy1.setBackground(ColorTable.PANEL_BACKGROUND_DISABLED.get());
			controlPanel.add(dummy1);
			
			JPanel dummy2 = new JPanel();
			dummy2.setPreferredSize(new Dimension(200, scrollbarHeight * 2));
			dummy2.setOpaque(true);
			dummy2.setBackground(ColorTable.PANEL_BACKGROUND_DISABLED.get());
			noteGraphPanel.add(dummy2, "growx");
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

	/**
	 * This method confuses me. Why set drumpanel invisible when only disabling trackpanel??
	 * And why not run it when abcPart is null??
	 * 
	 * Disabled calling it for now, as it would anyways always set visible and drumpanels never exist as child of controlPanel
	 * 
	 * ~Aifel
	 */
	private void updateTracksVisible() {
		if (abcPart == null)
			return;

		boolean percussion = abcPart.getInstrument().isPercussion;
//		boolean setHeight = false;

		for (Component child : controlPanel.getComponents()) {
			if (child instanceof TrackPanel) {
				TrackPanel trackPanel = (TrackPanel) child;
				trackPanel.setEnabled(percussion || trackPanel.getTrackInfo().hasEvents());
//				if (!setHeight && !percussion) {
//					controlScrollPane.getVerticalScrollBar().setUnitIncrement(child.getPreferredSize().height);
//					setHeight = true;
//				}
			} else if (child instanceof DrumPanel) {
				child.setVisible(percussion);
//				if (!setHeight && percussion) {
//					controlScrollPane.getVerticalScrollBar().setUnitIncrement(child.getPreferredSize().height);
//					setHeight = true;
//				}
			} else if (child instanceof TempoPanel) {
				
			} else if (child instanceof HistogramPanel) {
				
			}
		}
	}

	private void clearTrackListPanel() {
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
		controlPanel.removeAll();
		noteGraphPanel.removeAll();
		zoomed = false;
	}

	public void setSequencer(NoteFilterSequencerWrapper sequencer) {
		AbcPart abcPartTmp = this.abcPart;
		setAbcPart(null, false);
		this.sequencer = sequencer;
		setAbcPart(abcPartTmp, false);
	}

	public void commitAllFields() {
		try {
			numberSpinner.commitEdit();
		} catch (java.text.ParseException e) {
			// Ignore
		}
	}

	public void zoom() {
		// Notice that when instrument track selection is changed clearTrackListPanel()
		// will be called and view will be unzoomed.
		int horiz = 1920 * 3;
		try {
			int width = Toolkit.getDefaultToolkit().getScreenSize().width;
			if (width > 1920) {
				horiz = Math.max(horiz, width * 2);
			}
		} catch (java.awt.HeadlessException e) {

		}

		TrackDimensions dims = TrackPanel.calculateTrackDims();

		int scaledHeight = (int) (dims.rowHeight * 1.25);
		
		boolean didWeUnZoom = false;

		for (Component child : controlPanel.getComponents()) {
			if (child instanceof TrackPanel) {
				if (!zoomed && child.getHeight() == dims.rowHeight + 1) {
//					((TrackPanel)child).getNoteGraph().setPreferredSize(new Dimension(horiz, scaledHeight+1));
					((TrackPanel)child).setRowHeight(scaledHeight);
					((TrackPanel)child).setNoteGraphWidth(horiz);
				} else {
//					((TrackPanel)child).getNoteGraph().setPreferredSize(new Dimension(200, dims.rowHeight + 1));
					((TrackPanel)child).setRowHeight(dims.rowHeight);
					((TrackPanel)child).setNoteGraphWidth(200);
					didWeUnZoom = true;
				}
				child.revalidate();
			}
		}
		noteGraphPanel.revalidate();
//		noteGraphPanel.setPreferredSize(!zoomed? new Dimension(horiz, getPreferredSize().height) : null);
//		noteGraphPanel.revalidate();
		
		revalidate();
		repaint();
		
		zoomed = !zoomed;
		if (didWeUnZoom) {
			zoomed = false;
		}
	}

	public void noteToggle() {
		noteVisible(!noteVisible);
	}

	public void noteVisible(boolean vis) {
		noteVisible = vis;
		if (noteVisible) {
			add(notePanel, "1, 0, 1, 1, F, F");
		} else {
			remove(notePanel);
		}
		validate();
		repaint();
	}

	public String getNote() {
		return noteContent.getText();
	}

	public void setNote(String note) {
		noteContent.setText(note);
	}

	public void setPolyphony(boolean showMaxPolyphony) {
		this.showMaxPolyphony = showMaxPolyphony;
		setAbcPreviewMode(isAbcPreviewMode());
	}
}
