package com.digero.maestro.view;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequence;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicFileChooserUI;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;

import com.digero.common.abc.AbcText;
import com.digero.common.abctomidi.AbcToMidi;
import com.digero.common.abctomidi.AbcTunebook;
import com.digero.common.abctomidi.FileAndData;
import com.digero.common.i18n.UIText;
import com.digero.common.util.FileParseException;
import com.digero.common.util.Util;
import com.digero.common.view.ColorTable;

/**
 * Asks what to do with a tunebook (a file of standard ABC with many X: tunes): open one tune, open all as parts (as
 * before), or split the book into one file per tune. Shows the tunes with number, title, type (R:), key and meter, a
 * filter over all of them (and over every title, composer and origin of the tune: a tune is often known by another
 * name), and the ABC of the selected tune (without the file header, which all tunes share). A tune that doesn't load
 * has its error marked in red in the ABC, the message as the ABC's tooltip. Play plays the selected tune as Open tune
 * would read it, and pauses it (PreviewPlayer).
 * <p>
 * Sizes come from the font, so the dialog follows Maestro's text size setting. Keys: type to filter, Up/Down to move in
 * the list (also from the filter), Space in the list to play or pause, Enter to open the selected tune, Escape to
 * cancel, double-click to open.
 */
public class TunebookDialog extends JDialog {
	protected static final Logger log = Logger.getLogger("tunebook");
	/** What the user chose. */
	public enum Choice {
		/** Open the selected tune: see {@link Result#tune()} */
		TUNE,
		/** Open every tune as a part of one song, as before */
		ALL_AS_PARTS,
		/** Closed or cancelled */
		CANCEL
	}

	/** The user's choice, and the tune for {@link Choice#TUNE} (else null). */
	public record Result(Choice choice, AbcTunebook.Tune tune) {
	}

	// The texts' keys (UIText)
	private static final String TITLE = "common.abctomidi.songbook.title";
	private static final String INTRO = "common.abctomidi.songbook.intro";
	private static final String FILTER = "common.abctomidi.songbook.filter";
	private static final String FILTER_TIP = "common.abctomidi.songbook.filter.tip";
	private static final String ALSO_KNOWN_AS = "common.abctomidi.songbook.also.known.as";
	private static final String COUNT = "common.abctomidi.songbook.count";
	private static final String[] COLUMNS = { "common.abctomidi.songbook.column.number",
			"common.abctomidi.songbook.column.title", "common.abctomidi.songbook.column.type",
				"common.abctomidi.songbook.column.key", "common.abctomidi.songbook.column.meter" };
	private static final String OPEN_TUNE = "common.abctomidi.songbook.open.tune";
	private static final String PLAY = "common.abctomidi.songbook.play";
	private static final String PLAY_TIP = "common.abctomidi.songbook.play.tip";
	private static final String PAUSE = "common.abctomidi.songbook.pause";
	private static final String PLAY_FAILED = "common.abctomidi.songbook.play.failed";
	private static final String ALL_AS_PARTS = "common.abctomidi.songbook.all.as.parts";
	private static final String ALL_AS_PARTS_TIP = "common.abctomidi.songbook.all.as.parts.tip";
	private static final String SPLIT = "common.abctomidi.songbook.split";
	private static final String SPLIT_TIP = "common.abctomidi.songbook.split.tip";
	private static final String SPLIT_FOLDER = "common.abctomidi.songbook.split.folder";
	private static final String CANCEL = "common.abctomidi.songbook.cancel";
	private static final String SPLIT_DONE = "common.abctomidi.songbook.split.done";
	private static final String SPLIT_FAILED = "common.abctomidi.songbook.split.failed";
	private static final String SPLIT_NOT_WRITABLE = "common.abctomidi.songbook.split.not.writable";

	/** An error's line in the ABC, across the whole width, and the error's character in it (red, seen through) */
	private final Highlighter.HighlightPainter ERROR_LINE = new LinePainter(ColorTable.TUNEBOOK_ERROR_LINE.get());
	private final Highlighter.HighlightPainter ERROR_CHARACTER =
			new DefaultHighlighter.DefaultHighlightPainter(ColorTable.TUNEBOOK_ERROR_CHARACTER.get());

	private final AbcTunebook book;
	private final File bookFile;
	private final List<AbcTunebook.Tune> tunes;
	private final String[][] rows; // number, title, type, key, meter
	private final String[] searchText; // Per tune: its columns, titles, composers and origins, lower case, without accents
	private final String[] otherTitles; // Per tune: its titles after the first, or null
	private final JTable table;
	private final TableRowSorter<AbstractTableModel> sorter;
	private final JTextField filter = new JTextField();
	private final JLabel count = new JLabel();
	private final JTextArea preview = new JTextArea();
	private final JButton openButton = new JButton(UIText.get(OPEN_TUNE));
	private final JButton playButton = new JButton(UIText.get(PLAY));
	private final PreviewPlayer player = new PreviewPlayer(PreviewPlayer::openDefault);
	private AbcTunebook.Tune shown; // The tune in the ABC pane and for Play
	private boolean shownOnce;
	private Result result = new Result(Choice.CANCEL, null);
	private static Result lastResult = null;
	private static File lastFile = null;

	/**
	 * Shows the dialog (modal) and returns the user's choice.
	 *
	 * @param owner    The window it belongs to, or null
	 * @param book     The songbook
	 * @param bookFile Its file: for the title and the folder offered for splitting
	 */
	public static Result show(Component owner, AbcTunebook book, File bookFile) {
		AbcTunebook.Tune lastTune = null;
		if (bookFile != null && bookFile.equals(lastFile) && lastResult != null) {
			lastTune = lastResult.tune;
		}
		TunebookDialog dialog = new TunebookDialog(owner, book, bookFile, lastTune);
		dialog.setVisible(true);
		if (dialog.result != null && (dialog.result.choice == Choice.TUNE || dialog.result.choice == Choice.CANCEL)) {
			lastResult = dialog.result;
			lastFile = dialog.bookFile;
		} else {
			lastResult = null;
			lastFile = null;
		}
		return dialog.result;
	}

	private TunebookDialog(Component owner, AbcTunebook book, File bookFile, AbcTunebook.Tune selectedTune) {
		super(owner == null ? null : SwingUtilities.getWindowAncestor(owner), UIText.get(TITLE, bookFile.getName()),
				ModalityType.APPLICATION_MODAL);
		this.book = book;
		this.bookFile = bookFile;
		this.tunes = book.tunes();

		rows = new String[tunes.size()][];
		searchText = new String[tunes.size()];
		otherTitles = new String[tunes.size()];
		for (int i = 0; i < tunes.size(); i++) {
			AbcTunebook.Tune tune = tunes.get(i);
			List<String> lines = book.tuneLines(tune);
			rows[i] = new String[] { tune.number(), tune.title(), field(lines, "R:"), field(lines, "K:"),
					field(lines, "M:") };
			List<String> titles = tuneFields(lines, "T:");
			if (titles.size() > 1)
				otherTitles[i] = String.join(", ", titles.subList(1, titles.size()));
			searchText[i] = simplify(String.join(" ", rows[i]) + " " + String.join(" ", titles) + " "
					+ String.join(" ", tuneFields(lines, "C:")) + " " + String.join(" ", tuneFields(lines, "O:")));
		}

		AbstractTableModel model = new AbstractTableModel() {
			@Override
			public int getRowCount() {
				return rows.length;
			}

			@Override
			public int getColumnCount() {
				return COLUMNS.length;
			}

			@Override
			public String getColumnName(int column) {
				return UIText.get(COLUMNS[column]);
			}

			@Override
			public Object getValueAt(int row, int column) {
				return rows[row][column];
			}
		};
		table = new JTable(model) {
			// The title's tooltip: the tune's other titles, so a match on one of them is seen
			@Override
			public String getToolTipText(MouseEvent e) {
				int row = rowAtPoint(e.getPoint());
				int column = columnAtPoint(e.getPoint());
				if (row < 0 || column < 0 || convertColumnIndexToModel(column) != 1)
					return null;
				String others = otherTitles[convertRowIndexToModel(row)];
				return (others == null) ? null : UIText.get(ALSO_KNOWN_AS, others);
			}
		};
		sorter = new TableRowSorter<>(model);
		sorter.setComparator(0, (String a, String b) -> a.matches("\\d{1,9}") && b.matches("\\d{1,9}")
				? Integer.compare(Integer.parseInt(a), Integer.parseInt(b)) : a.compareToIgnoreCase(b)); // 2 before 10
		table.setRowSorter(sorter);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setFillsViewportHeight(true);

		// Sizes from the font, so they follow Maestro's text size
		FontMetrics fm = table.getFontMetrics(table.getFont());
		int em = fm.charWidth('m');
		table.setRowHeight(fm.getHeight() + fm.getHeight() / 3);
		int[] widthsInEm = { 4, 22, 7, 5, 4 };
		for (int c = 0; c < widthsInEm.length; c++)
			table.getColumnModel().getColumn(c).setPreferredWidth(widthsInEm[c] * em);
		table.setPreferredScrollableViewportSize(new Dimension(42 * em, 16 * table.getRowHeight()));

		int viewRow = 0;
		if (selectedTune != null) {
			int idx = tunes.indexOf(selectedTune);
			if (idx != -1) viewRow = table.convertRowIndexToView(idx);
		}
		if (viewRow != 0) table.setRowSelectionInterval(viewRow, viewRow);


		Font font = table.getFont();
		preview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, font.getSize()));
		preview.setEditable(false);
		preview.setRows(16);
		preview.setColumns(40);

		filter.setToolTipText(UIText.get(FILTER_TIP));
		filter.setColumns(24);

		// Layout: intro; filter and count; list | preview; buttons
		int gap = fm.getHeight() / 2;
		JPanel content = new JPanel(new BorderLayout(gap, gap));
		content.setBorder(BorderFactory.createEmptyBorder(gap, gap, gap, gap));

		JPanel top = new JPanel(new GridBagLayout());
		GridBagConstraints g = new GridBagConstraints();
		g.gridx = 0;
		g.gridy = 0;
		g.gridwidth = 3;
		g.anchor = GridBagConstraints.WEST;
		g.fill = GridBagConstraints.HORIZONTAL;
		g.weightx = 1;
		g.insets = new Insets(0, 0, gap, 0);
		top.add(new JLabel(UIText.get(INTRO, tunes.size())), g);
		g.gridy = 1;
		g.gridwidth = 1;
		g.weightx = 0;
		g.fill = GridBagConstraints.NONE;
		g.insets = new Insets(0, 0, 0, gap);
		JLabel filterLabel = new JLabel(UIText.get(FILTER));
		filterLabel.setLabelFor(filter);
		filterLabel.setDisplayedMnemonic(KeyEvent.VK_F);
		top.add(filterLabel, g);
		g.gridx = 1;
		g.weightx = 1;
		g.fill = GridBagConstraints.HORIZONTAL;
		top.add(filter, g);
		g.gridx = 2;
		g.weightx = 0;
		g.fill = GridBagConstraints.NONE;
		g.insets = new Insets(0, gap, 0, 0);
		top.add(count, g);
		content.add(top, BorderLayout.NORTH);

		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(table), new JScrollPane(preview));
		split.setResizeWeight(0.55);
		split.setContinuousLayout(true);
		content.add(split, BorderLayout.CENTER);

		JButton allButton = new JButton(UIText.get(ALL_AS_PARTS));
		allButton.setToolTipText(UIText.get(ALL_AS_PARTS_TIP));
		JButton splitButton = new JButton(UIText.get(SPLIT));
		splitButton.setToolTipText(UIText.get(SPLIT_TIP));
		JButton cancelButton = new JButton(UIText.get(CANCEL));
		JPanel buttons = new JPanel(new GridBagLayout());
		GridBagConstraints b = new GridBagConstraints();
		b.insets = new Insets(0, 0, 0, gap);
		buttons.add(splitButton, b);
		b.weightx = 1;
		b.anchor = GridBagConstraints.WEST;
		buttons.add(allButton, b); // The book's own actions left, the usual ones right
		b.weightx = 0;
		buttons.add(playButton, b);
		buttons.add(openButton, b);
		b.insets = new Insets(0, 0, 0, 0);
		buttons.add(cancelButton, b);
		content.add(buttons, BorderLayout.SOUTH);
		setContentPane(content);

		// Behaviour
		openButton.addActionListener(e -> openSelected());
		allButton.addActionListener(e -> close(new Result(Choice.ALL_AS_PARTS, null)));
		cancelButton.addActionListener(e -> close(new Result(Choice.CANCEL, selectedTune())));
		splitButton.addActionListener(e -> splitAll());
		playButton.setToolTipText(UIText.get(PLAY_TIP));
		playButton.addActionListener(e -> playOrPause());
		player.setOnEnd(() -> SwingUtilities.invokeLater(this::updatePlayButton));
		// Space in the list plays or pauses (JTable's own Space would only select the row again)
		table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "playOrPause");
		table.getActionMap().put("playOrPause", new AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				playOrPause();
			}
		});
		getRootPane().setDefaultButton(openButton);
		getRootPane().registerKeyboardAction(e -> close(new Result(Choice.CANCEL, null)),
				KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		table.getSelectionModel().addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting())
				showSelected();
		});
		table.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && table.rowAtPoint(e.getPoint()) >= 0)
					openSelected();
			}
		});
		filter.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				applyFilter();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				applyFilter();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				applyFilter();
			}
		});
		// Up and Down in the filter move in the list, so the hands stay on the keyboard
		moveFromFilter(KeyEvent.VK_DOWN, 1);
		moveFromFilter(KeyEvent.VK_UP, -1);

		applyFilter();
		pack();
		setLocationRelativeTo(owner);
		filter.requestFocusInWindow();


		addWindowListener(new WindowAdapter() {
			@Override
			public void windowOpened(WindowEvent e) {
				if (table.getSelectedRow() >= 0)
					scrollToCenter(table.getSelectedRow());
				TunebookDialog.this.removeWindowListener(this);
			}
		});
	}

	/**
	 * The first value of a field in a tune's own lines (after its X:), else the file header's, its escapes decoded
	 * (sl\"angpolska); "" if none.
	 */
	private static String field(List<String> lines, String field) {
		String value = "";
		boolean inTune = false;
		for (String line : lines) {
			if (line.startsWith("X:"))
				inTune = true;
			if (line.startsWith(field)) {
				if (inTune)
					return AbcText.decode(line.substring(2).trim());
				if (value.isEmpty())
					value = AbcText.decode(line.substring(2).trim());
			}
		}
		return value;
	}

	/** Every value of a field in a tune's own lines (after its X:), in order, its escapes decoded. */
	private static List<String> tuneFields(List<String> lines, String field) {
		List<String> values = new ArrayList<>();
		boolean inTune = false;
		for (String line : lines) {
			if (line.startsWith("X:"))
				inTune = true;
			if (inTune && line.startsWith(field)) {
				String value = AbcText.decode(line.substring(2).trim());
				if (!value.isEmpty())
					values.add(value);
			}
		}
		return values;
	}

	/** Lower case, without accents: "Polska från" and "polska fran" find each other. */
	private static String simplify(String text) {
		return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
	}

	private void applyFilter() {
		String[] words = simplify(filter.getText()).trim().split("\\s+");
		sorter.setRowFilter(new RowFilter<>() {
			@Override
			public boolean include(Entry<? extends AbstractTableModel, ? extends Integer> entry) {
				String text = searchText[entry.getIdentifier()];
				for (String word : words) {
					if (!text.contains(word))
						return false;
				}
				return true;
			}
		});
		count.setText(UIText.get(COUNT, table.getRowCount(), tunes.size()));
		if (table.getRowCount() > 0 && table.getSelectedRow() < 0)
			select(0);
		showSelected();
	}

	private void moveFromFilter(int key, int step) {
		String name = "move" + step;
		filter.getInputMap().put(KeyStroke.getKeyStroke(key, 0), name);
		filter.getActionMap().put(name, new AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				int rowsShown = table.getRowCount();
				if (rowsShown > 0)
					select(Math.max(0, Math.min(rowsShown - 1, table.getSelectedRow() + step)));
			}
		});
	}

	private void select(int viewRow) {
		table.setRowSelectionInterval(viewRow, viewRow);
		table.scrollRectToVisible(table.getCellRect(viewRow, 0, true));
	}

	/** Scrolls the list so the row is in the middle of what's shown (as near as it can be at the list's ends). */
	private void scrollToCenter(int viewRow) {
		Rectangle cell = table.getCellRect(viewRow, 0, true);
		Rectangle shown = table.getVisibleRect();
		Rectangle wanted = new Rectangle(cell.x, Math.max(0, cell.y - (shown.height - cell.height) / 2), cell.width,
				shown.height);
		table.scrollRectToVisible(wanted);
	}

	private AbcTunebook.Tune selectedTune() {
		int viewRow = table.getSelectedRow();
		return (viewRow < 0) ? null : tunes.get(table.convertRowIndexToModel(viewRow));
	}

	private void showSelected() {
		AbcTunebook.Tune tune = selectedTune();
		if (shownOnce && tune == shown)
			return; // The same tune (the filter changed): it plays on
		shownOnce = true;
		shown = tune;
		// The tune from its X: (the file header is the same for every tune)
		List<String> lines = (tune == null) ? List.of() : book.tuneLines(tune);
		int x = 0;
		while (x < lines.size() && !lines.get(x).startsWith("X:"))
			x++;
		preview.setText(String.join("\n", lines.subList(Math.min(x, lines.size()), lines.size())));
		preview.setCaretPosition(0);
		player.setTune(check(tune, x));
		updatePlayButton();
		openButton.setEnabled(tune != null);
	}

	/**
	 * Converts the tune, for Play, and marks where it fails to load in the ABC shown, which starts at the tune's line x
	 * (its X:). Returns what Play plays, or null.
	 */
	private Sequence check(AbcTunebook.Tune tune, int x) {
		Highlighter highlighter = preview.getHighlighter();
		highlighter.removeAllHighlights();
		preview.setToolTipText(null);
		if (tune == null)
			return null;
		try {
			return tuneSequence(book, tune, bookFile);
		} catch (FileParseException error) {
			markError(error, x);
		} catch (RuntimeException e) {
			log.warning("Tune " + tune.number() + " not checked: " + e);
		}
		return null;
	}

	/** Marks the error in the ABC shown, which starts at the tune's line x (its X:), with its message as tooltip. */
	private void markError(FileParseException error, int x) {
		Highlighter highlighter = preview.getHighlighter();
		preview.setToolTipText(error.getDetail());
		int[] range = errorRange(preview.getText(), x, error.getLine(), error.getColumn());
		if (range == null)
			return; // In the file header, which isn't shown: the tooltip says it
		try {
			highlighter.addHighlight(range[0], range[1], ERROR_LINE);
			if (range[2] < range[3])
				highlighter.addHighlight(range[2], range[3], ERROR_CHARACTER);
			preview.setCaretPosition(range[0]); // Scrolled to it
		} catch (BadLocationException e) {
			log.warning("Error not marked: " + e.getMessage());
		}
	}

	private void playOrPause() {
		try {
			player.playOrPause();
		} catch (MidiUnavailableException | InvalidMidiDataException e) {
			JOptionPane.showMessageDialog(this, UIText.get(PLAY_FAILED, e.getMessage()), UIText.get(PLAY),
					JOptionPane.ERROR_MESSAGE);
		}
		updatePlayButton();
	}

	private void updatePlayButton() {
		playButton.setText(UIText.get(player.isPlaying() ? PAUSE : PLAY));
		playButton.setEnabled(player.canPlay());
	}

	/**
	 * The tune as Open tune reads it, as standard ABC (AbcSong shows this dialog only for a book that isn't made for
	 * Lotro): what Play plays.
	 */
	static Sequence tuneSequence(AbcTunebook book, AbcTunebook.Tune tune, File bookFile) throws FileParseException {
		AbcToMidi.Params params = new AbcToMidi.Params(List.of(new FileAndData(bookFile, book.tuneLines(tune),
				AbcToMidi.tuneAloneName(bookFile))));
		params.useLotroInstruments = false;
		params.standardPitch = true;
		params.standard2011 = true;
		params.expandRepeats = true;
		params.chordAccompaniment = true;
		params.specTempo = true;
		return AbcToMidi.convert(params);
	}

	/** The tune's error when it's read as Open tune reads it (tuneSequence), or null if it loads. */
	static FileParseException tuneError(AbcTunebook book, AbcTunebook.Tune tune, File bookFile) {
		try {
			tuneSequence(book, tune, bookFile);
			return null;
		} catch (FileParseException e) {
			return e;
		}
	}

	/**
	 * Where an error is in the ABC shown, which starts at the tune's line firstLine (its X:, counted from 0): {line
	 * start, line end, error start, error end}, as offsets in text. The error is the character at its column (counted
	 * from 0); without a column, or after the line's end, it's empty (start = end). Null if the line isn't shown (the
	 * file header) or there is none.
	 *
	 * @param line The error's line in the tune's text, counted from 1 (FileParseException.getLine)
	 */
	static int[] errorRange(String text, int firstLine, int line, int column) {
		int shownLine = line - 1 - firstLine;
		if (line < 1 || shownLine < 0)
			return null;
		int start = 0;
		for (int i = 0; i < shownLine; i++) {
			start = text.indexOf('\n', start) + 1;
			if (start == 0)
				return null; // After the text's end
		}
		int end = text.indexOf('\n', start);
		if (end < 0)
			end = text.length();
		int errorStart = (column < 0) ? start : Math.min(start + column, end);
		int errorEnd = (column < 0) ? start : Math.min(errorStart + 1, end);
		return new int[] { start, end, errorStart, errorEnd };
	}

	/**
	 * Paints a highlight's lines across the whole width of the text, not just under their characters.
	 */
	private record LinePainter(Color color) implements Highlighter.HighlightPainter {

		@Override
		public void paint(Graphics g, int p0, int p1, Shape bounds, JTextComponent c) {
			try {
				Rectangle2D first = c.modelToView2D(p0);
				Rectangle2D last = c.modelToView2D(p1);
				int top = (int) first.getY();
				g.setColor(color);
				g.fillRect(0, top, c.getWidth(), (int) (last.getY() + last.getHeight()) - top);
			} catch (BadLocationException ignored) {
				// Out of the text: nothing to paint
			}
		}
	}

	private void openSelected() {
		AbcTunebook.Tune tune = selectedTune();
		if (tune != null)
			close(new Result(Choice.TUNE, tune));
	}

	private void splitAll() {
		// Next to the book, else (a read-only folder, a CD) in the user's Documents or home folder
		String name = bookFile.getName().replaceFirst("(?i)\\.("+ Util.ABC_FILE_EXTENSION_NO_DOT+"|"+ Util.TXT_FILE_EXTENSION_NO_DOT+")$", "");
		File start = bookFile.getAbsoluteFile().getParentFile();
		if (!isWritable(start)) {
			start = Util.getDocumentsDir();
		}
		JFileChooser chooser = folderChooser(start, UIText.get(SPLIT_FOLDER, name));
		chooser.setDialogTitle(UIText.get(SPLIT));
		File folder;
		while (true) {
			if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
				return;
			folder = chooser.getSelectedFile();
			if (isWritable(folder))
				break;
			JOptionPane.showMessageDialog(this, UIText.get(SPLIT_NOT_WRITABLE, folder.getAbsolutePath()),
					UIText.get(SPLIT), JOptionPane.WARNING_MESSAGE);
		}
		try {
			int written = book.splitAll(folder).size();
			JOptionPane.showMessageDialog(this, UIText.get(SPLIT_DONE, written, folder.getAbsolutePath()),
					UIText.get(SPLIT), JOptionPane.INFORMATION_MESSAGE);
		} catch (IOException e) {
			JOptionPane.showMessageDialog(this, UIText.get(SPLIT_FAILED, e.getMessage()),
					UIText.get(SPLIT), JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * A chooser for a folder, opened in start, suggesting a new folder there. JFileChooser shows a selected folder's
	 * name only when the folder exists; else its name field holds the start's path and Save chooses start. So the name
	 * is put in the name field (on macOS the chooser keeps start).
	 */
	static JFileChooser folderChooser(File start, String newFolderName) {
		JFileChooser chooser = new JFileChooser(start);
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setSelectedFile(new File(start, newFolderName));
		if (chooser.getUI() instanceof BasicFileChooserUI ui)
			ui.setFileName(newFolderName);
		return chooser;
	}

	/** The folder can be written to, or (if it doesn't exist yet) made in its nearest existing parent. */
	private static boolean isWritable(File folder) {
		File existing = folder;
		while (existing != null && !existing.exists())
			existing = existing.getParentFile();
		return existing != null && existing.isDirectory() && Files.isWritable(existing.toPath());
	}

	private void close(Result chosen) {
		result = chosen;
		dispose();
	}

	/** Closed in any way: the preview stops. */
	@Override
	public void dispose() {
		player.close();
		super.dispose();
	}
}