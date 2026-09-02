package com.digero.maestro.view;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.digero.common.abc.AbcConstants;
import com.digero.common.abc.LotroInstrument;
import com.digero.common.icons.IconLoader;
import com.digero.common.midi.NoteFilterSequencerWrapper;
import com.digero.common.midi.PanGenerator;
import com.digero.common.midi.SequencerEvent.SequencerProperty;
import com.digero.common.midi.SequencerWrapper;
import com.digero.common.util.ICompileConstants;
import com.digero.common.util.IDiscardable;
import com.digero.common.util.Listener;
import com.digero.common.util.LyricLine;
import com.digero.common.util.Util;
import com.digero.common.view.ColorTable;
import com.digero.common.view.InstrumentComboBox;
import com.digero.common.view.PatchedJScrollPane;
import com.digero.common.view.UIText;
import com.digero.common.view.WrapLayout;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.AbcPartEvent;
import com.digero.maestro.abc.AbcPartEvent.AbcPartProperty;
import com.digero.maestro.abc.DissonanceDetector;
import com.digero.maestro.abc.PartAutoNumberer;
import com.digero.maestro.abc.PolyphonyHistogram;
import com.digero.maestro.midi.TrackInfo;

import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstants;

import static javax.swing.ScrollPaneConstants.*;

/**
 * This is the panel that holds the tracks and their controls.
 * It also hold the part header at top, and tempopanel and histogram panel.
 * Plus the user note/lyrics
 */
public class ArrangementView extends JPanel implements ICompileConstants, TableLayoutConstants {
    protected static final Logger log = Logger.getLogger("view.ArrangementView");

	private static final int HGAP = 4;
	private static final int VGAP = 4;

    private final JSlider panSlider;
    private final PanVisualizerPanel panPanel;
	private final ProjectFrame projectFrame;
	private boolean suppressPanEvents = false;

    private AbcPart abcPart;// The currently selected abcPart in left PartsList
	private final PartAutoNumberer partAutoNumberer;
	private final NoteFilterSequencerWrapper sequencer;
	private final SequencerWrapper abcSequencer;
	private boolean isAbcPreviewMode = false;
	private boolean showMaxPolyphony = false;
    private boolean showDissonance = false;

	private JSpinner numberSpinner;
	private final SpinnerNumberModel numberSpinnerModel;
    private JCheckBox numberLockedCheckBox;
	private final JButton numberSettingsButton;
	private final JTextField nameTextField;
	private final JComboBox<LotroInstrument> instrumentComboBox;
	private final JLabel messageLabel;

    private static final int ZOOM_SLIDER_MAX = 1000;
    private static final int ZOOM_SLIDER_MOUSE_WHEEL_STEP = 15;
	private final JSlider hZoomSlider;
	private final JSlider vZoomSlider;
	
	private final JPanel splitPanel;
	
    private final PatchedJScrollPane controlPanelScrollPane;
	private final JPanel controlPanel;
	
	private final PatchedJScrollPane noteGraphScrollPane;
	private final JPanel noteGraphPanel;
	
	// Note graphs
	HistogramPanel histogramPanel;
    DissonancePanel dissonancePanel;
	TempoPanel tempoPanel;
	HashMap<Integer, TrackPanel> trackPanels = new HashMap<>();
	
	private final ControlLayout controlLayout;
	private final GraphLayout graphLayout;

	private boolean initialized = false;

	private float hZoom = 1.f;
	private float vZoom = 1.f;
	private boolean textnoteVisible = false;
    private JTabbedPane sidePanel;
    private JScrollPane lyricsPanel = null;
    private JScrollPane statsPanel = null;
	private JScrollPane notePanel = null;
    private final JTextArea noteContent = new JTextArea();
    private final JTextArea statsContent = new JTextArea();
    private final JTextArea lyricsContent = new JTextArea();
	private final LyricEditorPanel lyricLinesContent = new LyricEditorPanel();
	private boolean countUp = true;
	private boolean allTimestamps = false;
    private boolean userEdit = true;

	private boolean syncUpdate = false;
	private boolean mouseHzooming = false;
	private Point mousePointTrack = null;
	private Point mousePointView = null;
	private double sequenceProgress = 0.0d;

    private boolean firePanListener = true;

    public ArrangementView(NoteFilterSequencerWrapper sequencer, PartAutoNumberer partAutoNumberer,
                           SequencerWrapper abcSequencer, boolean showMaxPolyphony, boolean showDissonance,
						   ProjectFrame projectFrame) {
		super();// y  part-header, zoom, tracks
        TableLayout mainLayout = new TableLayout(//layout
                new double[]{FILL, PREFERRED},  // x  tracks, note
                new double[]{PREFERRED, FILL});

		this.projectFrame = projectFrame;

        mainLayout.setHGap(HGAP);
        mainLayout.setVGap(VGAP);
        setLayout(mainLayout);


		setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ColorTable.PANEL_BORDER_HORIZ.get()));
        setOpaque(true);
		
		this.showMaxPolyphony = showMaxPolyphony;
        this.showDissonance = showDissonance;

		this.sequencer = sequencer;
		this.abcSequencer = abcSequencer;
		this.partAutoNumberer = partAutoNumberer;

		numberSpinnerModel = new SpinnerNumberModel(1, 1, 999,
                partAutoNumberer.getIncrement());
		numberSpinner = new JSpinner(numberSpinnerModel);
		numberSpinner.addChangeListener(e -> {
			if (abcPart != null && !abcPart.suppressSpinnerUpdate) {
				ArrangementView.this.partAutoNumberer.setPartNumber(abcPart,
                        (Integer) numberSpinner.getValue(), abcPart.getAbcSong().getParts());
			}
		});

        numberLockedCheckBox = new JCheckBox(UIText.get("maestro.lock"));
        numberLockedCheckBox.setHorizontalTextPosition(SwingConstants.RIGHT);
        numberLockedCheckBox.setFocusable(false);
        numberLockedCheckBox.setToolTipText(UIText.get("maestro.tip.html.fix.the.part.number.to.the.current.value.html"));
        numberLockedCheckBox.addActionListener(e -> {
            if (abcPart != null) {
                abcPart.setPartNumberManuallyAssigned(numberLockedCheckBox.isSelected(), true);
            }
        });
		numberLockedCheckBox.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				showMenu(e);
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				showMenu(e);
			}

			private void showMenu(MouseEvent e) {
				if (e.isPopupTrigger() && abcPart != null) {
					JPopupMenu menu = new JPopupMenu();

					// Lock All Option
					JMenuItem lockAll = new JMenuItem(UIText.get("maestro.menu.lock.all"));
					lockAll.addActionListener(al -> setAllLocks(true));

					// Unlock All Option
					JMenuItem unlockAll = new JMenuItem(UIText.get("maestro.menu.unlock.all"));
					unlockAll.addActionListener(al -> setAllLocks(false));

					menu.add(lockAll);
					menu.add(unlockAll);
					menu.show(e.getComponent(), e.getX(), e.getY());
				}
			}

			private void setAllLocks(boolean locked) {
				if (abcPart == null) return;
				for (AbcPart party : abcPart.getAbcSong().getParts()) {
					party.setPartNumberManuallyAssigned(locked, false);
				}
				// I 'think' that we can get away with only notifying listeners of current part:
				abcPart.notifyPartNumberManuallyAssigned();
			}
		});

		numberSettingsButton = new JButton(IconLoader.getImageIcon("gear_16.png"));
		numberSettingsButton.setMargin(new Insets(0, 0, 0, 0));
		numberSettingsButton.setToolTipText(UIText.get("maestro.tip.automatic.part.numbering.options"));
		numberSettingsButton.setVisible(false);

		nameTextField = new JTextField(24);
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
				ArrangementView.this.partAutoNumberer.setInstrument(abcPart, newInstrument, abcPart.getAbcSong().getParts());
				abcPart.replaceTitleInstrument(newInstrument, oldInstrument);
				nameTextField.setText(abcPart.getTitle());
				//updateTracksVisible();
			}
		});
		
		JPanel partSettingsPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, HGAP, 0));
        partSettingsPanel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                Component panel = e.getComponent();
                Dimension newPreferredSize = panel.getPreferredSize();
                Dimension currentSize = panel.getSize();

                if (newPreferredSize.height != currentSize.height)
                {
                    panel.getParent().revalidate();
                }
            }
        });
        add(partSettingsPanel, "0, 0, 1, 0, f, f");//layout

		// We never support a zoom max of less than 6x
		final float maxHZoomBase = 6.f;
		// For songs longer than 1 minute, we divide song length in seconds
		// by 8 to get adjusted zoom
		// so that approx. 8 seconds of music is on screen for max zoom
		final float zoomSecondDivider = 8f;
		JLabel hZoomLabel = new JLabel(UIText.get("maestro.horiz.zoom"));
		hZoomSlider = new JSlider(0, ZOOM_SLIDER_MAX, 0);
		hZoomSlider.setFocusable(false);
		hZoomSlider.addChangeListener(e -> {
			float secs = (sequencer.getLength() / ((float) AbcConstants.ONE_SECOND_MICROS));
			float adjustedZoom = Math.max(maxHZoomBase, secs / zoomSecondDivider);
			float oldHZoom = hZoom;
            double normalizedSliderValue = hZoomSlider.getValue() / (double) ZOOM_SLIDER_MAX;
			hZoom = Util.map((float)Math.pow(normalizedSliderValue,4d), 0.0f, 1.0f, 1.f, adjustedZoom);
			//System.out.println("hZoomSlider - value: " + hZoomSlider.getValue());
			if (hZoom != oldHZoom) {
				calcZoomTarget();
				updateZoom();
				if (mousePointTrack != null) mousePointTrack.x = (int) (mousePointTrack.x * hZoom / oldHZoom);
				scrollToPosition(mouseHzooming);
				repaintAfterZoom();
				//System.out.println("hZoomSlider - hZoom: " + hZoom);
			}
		});
		
		final float maxVZoom = 10.f;
		JLabel vZoomLabel = new JLabel(UIText.get("maestro.vert.zoom"));
		vZoomSlider = new JSlider(0, ZOOM_SLIDER_MAX, 0);
		vZoomSlider.setFocusable(false);
		vZoomSlider.addChangeListener(e -> {
			vZoom = Util.map(vZoomSlider.getValue(), 0, ZOOM_SLIDER_MAX, 1, maxVZoom);
			updateZoom();
			repaintAfterZoom();
//			System.out.println("vz: " + vZoom);
		});
		
		JCheckBox followCheckBox = new JCheckBox(UIText.get("maestro.follow"));
		followCheckBox.setHorizontalTextPosition(SwingConstants.LEFT);
		followCheckBox.setFocusable(false);
		followCheckBox.setToolTipText(UIText.get("maestro.tip.follow"));
		
		JPanel xPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, HGAP, 0));
		xPanel.add(new JLabel("X:"));
		xPanel.add(numberSpinner);
		partSettingsPanel.add(xPanel);
		partSettingsPanel.add(numberLockedCheckBox);
		partSettingsPanel.add(numberSettingsButton);
		
		JPanel iPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, HGAP, 0));
		iPanel.add(new JLabel("I:"));
		iPanel.add(instrumentComboBox);
		partSettingsPanel.add(iPanel);
		
		JPanel partNamePanel = new JPanel(new WrapLayout(FlowLayout.LEFT, HGAP, 0));
		partNamePanel.add(new JLabel(UIText.get("maestro.part.name")));
		partNamePanel.add(nameTextField);
		partSettingsPanel.add(partNamePanel);

        JPanel partPanPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, HGAP, 0));
        partPanPanel.add(new JLabel(UIText.get("maestro.pan.part")));
        panSlider = new JSlider(PanGenerator.LEFT, PanGenerator.RIGHT+1, 0);
        panSlider.setFocusable(false);
        panSlider.setMajorTickSpacing(PanGenerator.CENTER);
        panSlider.setPaintLabels(false);
        panSlider.setPaintTicks(true);
        panSlider.setSnapToTicks(false);
        panSlider.setToolTipText(UIText.get("maestro.tip.slider.pan"));
        panPanel = new PanVisualizerPanel();
        PanController panWindow = new PanController(panSlider, panPanel);//also adds mouse-listener
        panSlider.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON3) {
                    if(abcPart != null) {
                        //System.out.println("Resetting pan to automatic 1");
                        if ((e.getModifiersEx() & InputEvent.BUTTON1_DOWN_MASK) != 0) {
                            // right mouse clicked while mouse left is held down
                            suppressPanEvents = true;
                        }
                        abcPart.setUserPan(null);
                        panPanel.setOthers(abcPart.getAbcSong().allPans);

                        // Slider to center
                        // temporarily disable firePanListener to move the knob without triggering logic
                        firePanListener = false;
                        panSlider.setValue(64);
                        firePanListener = true;

                        setPanSliderColor();
                        panPanel.repaint();
                        //System.out.println("Resetting pan to automatic 2");
                    }
                } else if (e.getButton() == MouseEvent.BUTTON2) {
                    //System.out.println("Resetting pan to center 1");
                    setPan(PanGenerator.CENTER);
                    //System.out.println("Resetting pan to center 2");
                } else if (e.getButton() == MouseEvent.BUTTON1) {
                    if (abcPart != null) {
                        // This is for when initially pressing mouse before dragging slider,
                        // that should already update the panPanel.
                        //System.out.println("Pressed mouse 1");
                        panPanel.updateState(panSlider.getValue(), Integer.toString(abcPart.getPartNumber()), abcPart.getAbcSong().allPans);
                        //System.out.println("Pressed mouse 2");
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    suppressPanEvents = false;
                }
                panPanel.repaint();
            }
        });
        panSlider.addChangeListener(e -> {
            if (firePanListener && !suppressPanEvents) {
                //System.out.println("Pan action 1");
                setPan(panSlider.getValue());
                setPanSliderColor();
                //System.out.println("Pan action 2");
            } else {
                //System.out.println("No pan action");
            }
        });
        partPanPanel.add(panSlider);
        partSettingsPanel.add(partPanPanel);

		JPanel zoomPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, HGAP, 0));
        zoomPanel.setBorder(BorderFactory.createTitledBorder(""));
		zoomPanel.add(hZoomLabel);
		zoomPanel.add(hZoomSlider);
		zoomPanel.add(vZoomLabel);
		zoomPanel.add(vZoomSlider);
		zoomPanel.add(followCheckBox);
		partSettingsPanel.add(zoomPanel);

		splitPanel = new JPanel(new TableLayout(new double[] { PREFERRED, FILL }, //
				new double[] { FILL }));
		splitPanel.setBorder(BorderFactory.createEmptyBorder());
		splitPanel.setBackground(ColorTable.CENTER_BACKGROUND.get());
		
		noteGraphPanel = new JPanel();
		noteGraphScrollPane = new PatchedJScrollPane(noteGraphPanel, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED);


		controlLayout = new ControlLayout(32+1, noteGraphPanel);
		controlPanel = new JPanel(controlLayout) {
            @Override
            public boolean isValidateRoot() {
                return true;
            }
        };

        controlPanelScrollPane = new PatchedJScrollPane(controlPanel, VERTICAL_SCROLLBAR_NEVER, HORIZONTAL_SCROLLBAR_NEVER);
        controlPanelScrollPane.setBorder(BorderFactory.createEmptyBorder());

        // Link control scroll bar model to note graph scroll bar
        // so they're both controlled by note graph scroll bar
        int unit = TrackPanel.calculateTrackDims().rowHeight / 2;
        JScrollBar noteGraphBar = noteGraphScrollPane.getVerticalScrollBar();
        JScrollBar controlBar = controlPanelScrollPane.getVerticalScrollBar();
        noteGraphBar.setUnitIncrement(unit);
        controlBar.setUnitIncrement(unit);
        controlPanelScrollPane.getVerticalScrollBar().setModel(
                noteGraphScrollPane.getVerticalScrollBar().getModel()
        );

        // This will make the play-head update smooth when on auto-follow:
        controlPanelScrollPane.getViewport().setScrollMode(JViewport.BACKINGSTORE_SCROLL_MODE);
        noteGraphScrollPane.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);

        MouseAdapter listenForControlFocus = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                getRootPane().requestFocus();
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (!handleMouseWheelZoom(e)) {
                    noteGraphScrollPane.dispatchEvent(SwingUtilities.convertMouseEvent(controlPanel, e, noteGraphScrollPane));
                }
            }
        };
        controlPanel.addMouseListener(listenForControlFocus);
        controlPanel.addMouseWheelListener(listenForControlFocus);

		controlPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0)); // top, left, bottom, right
		controlPanel.setBackground(ColorTable.CENTER_BACKGROUND.get());
		
		graphLayout = new GraphLayout(TrackPanel.calculateTrackDims().rowHeight + 1, controlLayout);
		graphLayout.setViewport(noteGraphScrollPane.getViewport(), this);
		noteGraphPanel.setLayout(graphLayout);
		noteGraphPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0)); // top, left, bottom, right

		noteGraphPanel.setBackground(ColorTable.CENTER_BACKGROUND.get());
		
		
		noteGraphScrollPane.setBorder(BorderFactory.createEmptyBorder());
		noteGraphScrollPane.setCorner(ScrollPaneConstants.LOWER_RIGHT_CORNER, new JPanel());
		noteGraphScrollPane.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				getRootPane().requestFocus();
			}
		});

		splitPanel.add(controlPanelScrollPane, "0, 0");
		splitPanel.add(noteGraphScrollPane, "1, 0, f, f");
		
		messageLabel = new JLabel();
		messageLabel.setHorizontalAlignment(SwingConstants.CENTER);
		messageLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 20));
		messageLabel.setForeground(ColorTable.PANEL_TEXT_DISABLED.get());
		messageLabel.setVisible(false);

        createSidePanel();


		add(messageLabel, "0, 1, C, C");
		add(splitPanel, "0, 1");

		// For follow support
		sequencer.addChangeListener(e -> {
			lyricLinesContent.setTick(sequencer.getTickPosition());
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

            // If a PartsList Drag'n'Drop operation is active, pause follow.
            // That fixes the d'n'd cursor flicker.
            if (SongPartsListPanel.PanelTransferHandler.isDragInProgress) {
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

    private void createSidePanel() {
        sidePanel = new JTabbedPane(JTabbedPane.TOP);
        sidePanel.setPreferredSize(new Dimension(250, 20000));
        //sidePanel.setMinimumSize(new Dimension(225, 200));

        // lyricsPanel is the textfield with project lyrics
        lyricsPanel = new JScrollPane(lyricsContent, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
        lyricsContent.setLineWrap(true);
        lyricsContent.setWrapStyleWord(true);
        lyricsContent.setTabSize(4);
        lyricsContent.setEditable(false);

		JPanel lyricsTabContainer = new JPanel(new BorderLayout());
		lyricsTabContainer.add(lyricLinesContent, BorderLayout.CENTER);
		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));

		JButton copyButton = new JButton(UIText.get("maestro.sidepanel.copy"));
		copyButton.setToolTipText(UIText.get("maestro.sidepanel.copy.lyrics.to.clipboard.in.poetical.friendly.format"));
		copyButton.addActionListener(e -> {
			if (abcPart == null) return;
			String text = lyricLinesContent.getPoeticalLyrics(abcPart.getAbcSong().getQTM(), abcPart.getAbcSong().isOrganic(), abcPart, countUp, allTimestamps);

			if (text != null) {
				StringSelection selection = new StringSelection(text);
				Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
			}
		});

		JButton revertButton = new JButton(UIText.get("maestro.sidepanel.revert"));
		revertButton.setToolTipText(UIText.get("maestro.sidepanel.reload.lyrics.from.midi.source.discards.edits"));
		revertButton.addActionListener(e -> {
			if (abcPart != null) {
				// Fetch original MIDI/project text
				List<LyricLine> lines = abcPart.getSequenceInfo().getDataCache().getLyricLines();

				int result = JOptionPane.showConfirmDialog(
						SwingUtilities.getWindowAncestor(revertButton),
						lines.isEmpty()
								? UIText.get("maestro.sidepanel.source.contains.no.lyrics.do.you.want.to.delete.all.lyrics")
								: UIText.get("maestro.sidepanel.are.you.sure.you.want.to.revert.lyrics.to.midi.source"),
						UIText.get("maestro.sidepanel.delete.and.revert.lyrics"),
						JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

				if (result == JOptionPane.YES_OPTION) {
					lyricLinesContent.setFromLyricLines(lines);
					abcPart.getAbcSong().notifyLyricLinesModified();
					lyricLinesContent.modified = false;
				}
			}
		});

		buttonPanel.add(copyButton);
		buttonPanel.add(revertButton);


		// Add buttons to the bottom
		lyricsTabContainer.add(buttonPanel, BorderLayout.SOUTH);

        // notePanel is the text field with project notes
        notePanel = new JScrollPane(noteContent, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
        noteContent.setLineWrap(true);
        noteContent.setWrapStyleWord(true);
        noteContent.setTabSize(4);
        noteContent.setEditable(true);
        noteContent.getDocument().addDocumentListener(new DocumentListener() {
            private void handleUserEdit(DocumentEvent e) {
                if (abcPart == null) return;
                if (userEdit) {
                    abcPart.getAbcSong().setNote(noteContent.getText(), true);
                }
            }

            @Override
            public void insertUpdate(DocumentEvent e) { handleUserEdit(e); }

            @Override
            public void removeUpdate(DocumentEvent e) { handleUserEdit(e); }

            @Override
            public void changedUpdate(DocumentEvent e) { handleUserEdit(e); }
        });

        // statsPanel is the textfield with project stats
        statsPanel = new JScrollPane(statsContent, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
        statsContent.setLineWrap(true);
        statsContent.setWrapStyleWord(true);
        statsContent.setTabSize(4);
        statsContent.setEditable(false);

        //sidePanel.addTab("Lyrics", lyricsPanel);
		sidePanel.addTab(UIText.get("maestro.sidepanel.lyrics"), lyricsTabContainer);
        sidePanel.addTab(UIText.get("maestro.sidepanel.notes"), notePanel);
        sidePanel.addTab(UIText.get("maestro.sidepanel.stats"), statsPanel);
    }

    public void scrollToTop() {
        noteGraphScrollPane.getVerticalScrollBar().setValue(0);
    }
	
	private boolean calcZoomTarget() {
		sequenceProgress = sequencer.getThumbPosition() / (double)(sequencer.getLength());
				
		int trackHeadGraphPos = (int) (graphLayout.getTrackWidth() * sequenceProgress);
		
		int minimum = noteGraphScrollPane.getViewport().getViewPosition().x;
		int maximum = noteGraphScrollPane.getViewport().getViewPosition().x + noteGraphScrollPane.getViewport().getExtentSize().width;
		
		boolean inView = trackHeadGraphPos >= minimum && trackHeadGraphPos <= maximum;
		
		if (!inView) sequenceProgress = ((minimum+maximum)/2.0d)/(double)graphLayout.getTrackWidth();
		
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
		if (!newView.equals(oldView)) noteGraphScrollPane.getViewport().setViewPosition(newView);
	}
	
	// Returns false if we should forward the event to the scroll pane
	// since it's just a normal scroll (no shift or control held)
	boolean handleMouseWheelZoom(MouseWheelEvent e) {
		if (e.isControlDown()) {
				int val = hZoomSlider.getValue() - ZOOM_SLIDER_MOUSE_WHEEL_STEP * e.getWheelRotation();
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
				hZoomSlider.setValue(Util.clamp(val, 0, ZOOM_SLIDER_MAX));
				mouseHzooming  = false;
				return true;
		} else if (e.isShiftDown()) {
			int val = vZoomSlider.getValue() - ZOOM_SLIDER_MOUSE_WHEEL_STEP * e.getWheelRotation();
			vZoomSlider.setValue(Util.clamp(val, 0, ZOOM_SLIDER_MAX));
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

	@Override
	public void addMouseListener(MouseListener ml) {
		super.addMouseListener(ml);
		controlPanel.addMouseListener(ml);
		noteGraphPanel.addMouseListener(ml);
	}

	private final Listener<AbcPartEvent> abcPartListener = e -> {
        //log.warning(this.getClass().getTypeName()+" AbcPartEvent: "+e.getProperty());
		if (e.getProperty() == AbcPartProperty.PART_NUMBER) {
			abcPart.suppressSpinnerUpdate = true;
			numberSpinner.setValue(abcPart.getPartNumber());
			abcPart.suppressSpinnerUpdate = false;
		} else if (e.getProperty() == AbcPartProperty.INSTRUMENT) {
			setAbcPart(abcPart, true); // Revalidate layout
        } else if (e.getProperty() == AbcPartProperty.PART_NUMBER_MANUAL) {
            numberLockedCheckBox.setSelected(abcPart.isPartNumberManuallyAssigned());
        } else if (e.getProperty() == AbcPartProperty.USER_PAN) {
            setPanSlider();
            setPanSliderColor();
        }
        if(e.isAbcPreviewRelated()) {
            //not needed, we update panel from ProjectFrame, when setting new histogram
            //histogramPanel.updateCountLabel();
        }
	};

    /**
     * Set userPan on abcPart
     * This will disable auto-pan.
     */
    private void setPan(int value) {
        if (abcPart != null) {
            //System.out.println("setPan on abcPart: " + value);
            value = Math.clamp(value, PanGenerator.LEFT, PanGenerator.RIGHT);//important as slider goes to 128
            abcPart.setUserPan(value);
            panPanel.updateState(value, Integer.toString(abcPart.getPartNumber()), abcPart.getAbcSong().allPans);
            //System.out.println("setPan updateState done");
        }
    }

    private void setPanSlider() {
        // called from abcPartListener and when changing part
        int value = (abcPart == null)?64:(abcPart.getUserPan() == null?64:abcPart.getUserPan());
        firePanListener = false;
        //System.out.println("setPanSlider 1: " + value);
        panSlider.setValue(value);
        //System.out.println("setPanSlider 2");
        firePanListener = true;
    }

    private void setPanSliderColor() {
        if (abcPart != null) {
            if (abcPart.getUserPan() == null) {
                panSlider.setBackground(UIManager.getColor("Slider.highlight"));
            } else {
                panSlider.setBackground(ColorTable.CONTROLS_EDITED.get());
            }
        } else {
            panSlider.setBackground(UIManager.getColor("Slider.highlight"));
        }
    }

	public void settingsChanged() {
		numberSpinnerModel.setStepSize(partAutoNumberer.getIncrement());
	}
	
	public void closeAbcSong() {
		clearTrackListPanel(true);
		histogramPanel = null;
        dissonancePanel = null;
		tempoPanel = null;
		trackPanels.clear();
		abcPart = null;
	}

	/**
	 * Call this when any colors have changed
	 */
	public void rebuildPanels() {
		AbcPart oldPart = abcPart;
		PolyphonyHistogram oldHistogram = histogramPanel == null?null:histogramPanel.getHistogram();
		DissonanceDetector oldDissonance = dissonancePanel == null?null:dissonancePanel.getDissonance();
		clearTrackListPanel(true);
		histogramPanel = null;
		dissonancePanel = null;
		tempoPanel = null;
		trackPanels.clear();
		abcPart = null;
		setAbcPart(oldPart, true);//this call will remake the panels
		if (histogramPanel != null) histogramPanel.setHistogram(oldHistogram);
		if (dissonancePanel != null) dissonancePanel.setDissonance(oldDissonance);
		splitPanel.setBackground(ColorTable.CENTER_BACKGROUND.get());
		controlPanel.setBackground(ColorTable.CENTER_BACKGROUND.get());
		noteGraphPanel.setBackground(ColorTable.CENTER_BACKGROUND.get());
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
            numberLockedCheckBox.setEnabled(false);
			nameTextField.setEnabled(false);
			instrumentComboBox.setEnabled(false);
			
			hZoomSlider.setEnabled(false);
			vZoomSlider.setEnabled(false);

			numberSpinner.setValue(1);
            numberLockedCheckBox.setSelected(false);
			nameTextField.setText("");
			instrumentComboBox.setSelectedItem(LotroInstrument.DEFAULT_INSTRUMENT);
            panSlider.setEnabled(false);
			clearTrackListPanel(true);
		} else {
			lyricLinesContent.abcSong = abcPart.getAbcSong();
			numberSpinner.setEnabled(true);
            numberLockedCheckBox.setEnabled(true);
			nameTextField.setEnabled(true);
			instrumentComboBox.setEnabled(true);
			
			hZoomSlider.setEnabled(true);
			vZoomSlider.setEnabled(true);

			numberSpinner.setValue(abcPart.getPartNumber());
            numberLockedCheckBox.setSelected(abcPart.isPartNumberManuallyAssigned());
			nameTextField.setText(abcPart.getTitle());
			instrumentComboBox.setSelectedItem(abcPart.getInstrument());
            panSlider.setEnabled(true);

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
			histogramPanel.setAbcPreviewMode(isAbcPreviewMode);
            histogramPanel.setShowPanel(showMaxPolyphony);
			histogramPanel.revalidate();
			
			controlPanel.add(histogramPanel,"x");
			noteGraphPanel.add(histogramPanel.getNoteGraph(),"x");

            // Add the dissonance panel
            if (dissonancePanel == null) {
                dissonancePanel = new DissonancePanel(abcPart.getSequenceInfo(), sequencer, abcSequencer,
                        abcPart.getAbcSong());
            }
            dissonancePanel.setAbcPreviewMode(isAbcPreviewMode);
            dissonancePanel.setShowPanel(showDissonance);
            dissonancePanel.revalidate();
            controlPanel.add(dissonancePanel,"x");
            noteGraphPanel.add(dissonancePanel.getNoteGraph(),"x");


			

			// Add the tracks and note graphs
			for (TrackInfo track : abcPart.getSequenceInfo().getTrackList()) {
				int trackNumber = track.getTrackNumber();
				if (track.hasEvents()) {
					if (!trackPanels.containsKey(trackNumber)) {
						TrackPanel tp = new TrackPanel(track, sequencer, abcPart, abcSequencer, controlLayout);
						trackPanels.put(trackNumber, tp);
						tp.projectFrame = projectFrame;
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
			
			class Dummy extends JPanel implements ArrangementViewItem {
				@Override
				public JPanel getNoteGraph() {
					return null;
				}

				@Override
				public boolean isVerticalZoomForbidden() {
					return true;
				}

                @Override
                public boolean isAbcPreviewMode() {
                    return false;
                }

                @Override
                public void setAbcPreviewMode(boolean abcPreviewMode) {

                }
            }
			Dummy dummy1 = new Dummy();
			dummy1.setPreferredSize(new Dimension(100, scrollbarHeight * 2));
			dummy1.setOpaque(true);
			dummy1.setBackground(ColorTable.CENTER_BACKGROUND.get());
			controlPanel.add(dummy1, "x");
			
			JPanel dummy2 = new JPanel();
			dummy2.setPreferredSize(new Dimension(100, scrollbarHeight * 2));
			dummy2.setOpaque(true);
			dummy2.setBackground(ColorTable.CENTER_BACKGROUND.get());
			noteGraphPanel.add(dummy2,"x");
		}

		this.abcPart = abcPart;
		if (this.abcPart != null) {
			this.abcPart.addAbcListener(abcPartListener);
		}
        setPanSlider();
        setPanSliderColor();
		//updateTracksVisible();
        if (this.abcPart != null) {
            Integer userPan = this.abcPart.getUserPan();
            panPanel.updateState(
                    userPan,
                    Integer.toString(this.abcPart.getPartNumber()),
                    this.abcPart.getAbcSong().allPans
            );
        }

		revalidate();
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
					((HistogramPanel) child).setAbcPreviewMode(isAbcPreviewMode);
                    ((HistogramPanel) child).setShowPanel(showMaxPolyphony);
				} else if (child instanceof DissonancePanel) {
                    ((DissonancePanel) child).setAbcPreviewMode(isAbcPreviewMode);
                    ((DissonancePanel) child).setShowPanel(showDissonance);
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

	void repaintAfterZoom() {
		//Note: Invalidate does not invalidate subcomponents, hence why it's called on the panels directly
		noteGraphPanel.invalidate();
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

	public void sidepanelToggle() {
		sidepanelVisible(!textnoteVisible);
	}

	public void sidepanelVisible(boolean vis) {
		textnoteVisible = vis;
		if (textnoteVisible) {
			add(sidePanel, "1, 1");//layout
		} else {
			remove(sidePanel);
		}
		revalidate();
        repaint();
	}

    public void sidepanelTab(String tabName) {
        sidePanel.setSelectedIndex(sidePanel.indexOfTab(tabName));
    }

	public String getTextnote() {
		return noteContent.getText();
	}

	public void setTextnote(String note) {
        userEdit = false;
		noteContent.setText(note);
		noteContent.setCaretPosition(0);
        userEdit = true;
	}

    public void setLyrics(String lyrics) {
        lyricsContent.setText(lyrics);
        lyricsContent.setCaretPosition(0);
    }

	public void setLyricLines(List<LyricLine> lyrics, boolean modified) {
		lyricLinesContent.setFromLyricLines(lyrics);
		lyricLinesContent.modified = modified;
		//lyricLinesContent.setCaretPosition(0);
	}

	public void stopEditingLyrics() {
		lyricLinesContent.stopEditing();
	}

	public boolean isLyricsModified() {
		return lyricLinesContent.modified;
	}

	public List<LyricLine> getLyricLines() {
		return lyricLinesContent.getLyricLines();
	}

	/**
	 * If true, the copied lyrics timestamp will count up.
	 * If false, they will count down.
	 */
	public void setPoeticalLyricsAdvancement(boolean up) {
		countUp = up;
	}

	public void setPoeticalLyricsTimestampEveryLine(boolean lyricsTimestampEveryLine) {
		allTimestamps = lyricsTimestampEveryLine;
	}

    public void setStats(String stats) {
        statsContent.setText(stats);
        statsContent.setCaretPosition(0);
    }

	public void setPolyphony(boolean showMaxPolyphony) {
		this.showMaxPolyphony = showMaxPolyphony;
		setAbcPreviewMode(isAbcPreviewMode());
	}

    public void setDissonanceEnabled(boolean enabled) {
        this.showDissonance = enabled;
        setAbcPreviewMode(isAbcPreviewMode());
    }

    @Override
    public boolean isValidateRoot() {
        return true;
    }

    public void setHistogram(PolyphonyHistogram histogram) {
        if (histogramPanel != null) histogramPanel.setHistogram(histogram);
    }

    public void setDissonance(DissonanceDetector dissonanceDetector) {
        if (dissonancePanel != null) dissonancePanel.setDissonance(dissonanceDetector);
    }
}
