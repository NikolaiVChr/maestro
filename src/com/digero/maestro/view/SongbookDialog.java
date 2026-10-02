package com.digero.maestro.view;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

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
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;

import com.digero.common.abc.AbcText;
import com.digero.common.abctomidi.AbcSongbook;
import com.digero.common.i18n.UIText;
import com.digero.common.util.Util;

/**
 * Asks what to do with a songbook (a file of standard ABC with many X: tunes): open one tune, open all as parts (as
 * before), or split the book into one file per tune. Shows the tunes with number, title, type (R:), key and meter, a
 * filter over all of them (and over every title, composer and origin of the tune: a tune is often known by another
 * name), and the ABC of the selected tune (without the file header, which all tunes share).
 * <p>
 * Sizes come from the font, so the dialog follows Maestro's text size setting. Keys: type to filter, Up/Down to move in
 * the list (also from the filter), Enter to open the selected tune, Escape to cancel, double-click to open.
 */
public class SongbookDialog extends JDialog {
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
	public record Result(Choice choice, AbcSongbook.Tune tune) {
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
	private static final String ALL_AS_PARTS = "common.abctomidi.songbook.all.as.parts";
	private static final String ALL_AS_PARTS_TIP = "common.abctomidi.songbook.all.as.parts.tip";
	private static final String SPLIT = "common.abctomidi.songbook.split";
	private static final String SPLIT_TIP = "common.abctomidi.songbook.split.tip";
	private static final String SPLIT_FOLDER = "common.abctomidi.songbook.split.folder";
	private static final String CANCEL = "common.abctomidi.songbook.cancel";
	private static final String SPLIT_DONE = "common.abctomidi.songbook.split.done";
	private static final String SPLIT_FAILED = "common.abctomidi.songbook.split.failed";
	private static final String SPLIT_NOT_WRITABLE = "common.abctomidi.songbook.split.not.writable";

	private final AbcSongbook book;
	private final File bookFile;
	private final List<AbcSongbook.Tune> tunes;
	private final String[][] rows; // number, title, type, key, meter
	private final String[] searchText; // Per tune: its columns, titles, composers and origins, lower case, without accents
	private final String[] otherTitles; // Per tune: its titles after the first, or null
	private final JTable table;
	private final TableRowSorter<AbstractTableModel> sorter;
	private final JTextField filter = new JTextField();
	private final JLabel count = new JLabel();
	private final JTextArea preview = new JTextArea();
	private final JButton openButton = new JButton(UIText.get(OPEN_TUNE));
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
	public static Result show(Component owner, AbcSongbook book, File bookFile) {
		AbcSongbook.Tune lastTune = null;
		if (bookFile != null && bookFile.equals(lastFile) && lastResult != null) {
			lastTune = lastResult.tune;
		}
		SongbookDialog dialog = new SongbookDialog(owner, book, bookFile, lastTune);
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

	private SongbookDialog(Component owner, AbcSongbook book, File bookFile, AbcSongbook.Tune selectedTune) {
		super(owner == null ? null : SwingUtilities.getWindowAncestor(owner), UIText.get(TITLE, bookFile.getName()),
				ModalityType.APPLICATION_MODAL);
		this.book = book;
		this.bookFile = bookFile;
		this.tunes = book.tunes();

		rows = new String[tunes.size()][];
		searchText = new String[tunes.size()];
		otherTitles = new String[tunes.size()];
		int selectedRow = 0;
		for (int i = 0; i < tunes.size(); i++) {
			AbcSongbook.Tune tune = tunes.get(i);
			List<String> lines = book.tuneLines(tune);
			rows[i] = new String[] { tune.number(), tune.title(), field(lines, "R:"), field(lines, "K:"),
					field(lines, "M:") };
			List<String> titles = tuneFields(lines, "T:");
			if (titles.size() > 1)
				otherTitles[i] = String.join(", ", titles.subList(1, titles.size()));
			searchText[i] = simplify(String.join(" ", rows[i]) + " " + String.join(" ", titles) + " "
					+ String.join(" ", tuneFields(lines, "C:")) + " " + String.join(" ", tuneFields(lines, "O:")));
			if (selectedRow == 0 && selectedTune != null && selectedTune.equals(tune.number())) {
				log.severe("row "+i);
				selectedRow = i;
			}
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
		if (selectedTune != null) viewRow = table.convertRowIndexToView(tunes.indexOf(selectedTune));
		table.setRowSelectionInterval(viewRow, viewRow);


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
		buttons.add(openButton, b);
		b.insets = new Insets(0, 0, 0, 0);
		buttons.add(cancelButton, b);
		content.add(buttons, BorderLayout.SOUTH);
		setContentPane(content);

		// Behaviour
		openButton.addActionListener(e -> openSelected());
		allButton.addActionListener(e -> close(new Result(Choice.ALL_AS_PARTS, null)));
		cancelButton.addActionListener(e -> close(new Result(Choice.CANCEL, null)));
		splitButton.addActionListener(e -> splitAll());
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
				SongbookDialog.this.removeWindowListener(this);
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

	private AbcSongbook.Tune selectedTune() {
		int viewRow = table.getSelectedRow();
		return (viewRow < 0) ? null : tunes.get(table.convertRowIndexToModel(viewRow));
	}

	private void showSelected() {
		AbcSongbook.Tune tune = selectedTune();
		// The tune from its X: (the file header is the same for every tune)
		List<String> lines = (tune == null) ? List.of() : book.tuneLines(tune);
		int x = 0;
		while (x < lines.size() && !lines.get(x).startsWith("X:"))
			x++;
		preview.setText(String.join("\n", lines.subList(Math.min(x, lines.size()), lines.size())));
		preview.setCaretPosition(0);
		openButton.setEnabled(tune != null);
	}

	private void openSelected() {
		AbcSongbook.Tune tune = selectedTune();
		if (tune != null)
			close(new Result(Choice.TUNE, tune));
	}

	private void splitAll() {
		// Next to the book, else (a read-only folder, a CD) in the user's Documents or home folder
		String name = bookFile.getName().replaceFirst("(?i)\\.(abc|txt)$", "");
		File start = bookFile.getAbsoluteFile().getParentFile();
		if (!isWritable(start)) {
			start = Util.getDocumentsDir();
		}
		JFileChooser chooser = new JFileChooser(start);
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setSelectedFile(new File(start, UIText.get(SPLIT_FOLDER, name)));
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
}