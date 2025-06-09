package com.digero.abcplayer.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EventObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.ToolTipManager;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.tree.TreePath;

import com.digero.abcplayer.AbcPlaylistXmlCoder;
import com.digero.abcplayer.view.AbcPlaylistPanel.PlaylistEvent.PlaylistEventType;
import com.digero.common.abctomidi.AbcInfo;
import com.digero.common.abctomidi.AbcToMidi;
import com.digero.common.abctomidi.FileAndData;
import com.digero.common.icons.IconLoader;
import com.digero.common.util.AbcFileTreeModel;
import com.digero.common.util.AbcFileTreeModel.AbcSongFileNode;
import com.digero.common.util.ExtensionFileFilter;
import com.digero.common.util.Listener;
import com.digero.common.util.ParseException;
import com.digero.common.util.Util;
import com.digero.common.view.AbcPlaylistTreeCellRenderer;
import com.digero.common.view.HintTextField;
import com.digero.maestro.util.XmlUtil;

import net.miginfocom.swing.MigLayout;

public class AbcPlaylistPanel extends JPanel {
	
	public static class PlaylistEvent extends EventObject {

		private static final long serialVersionUID = 4331827283417203704L;
		private boolean showSongView = false;
		
		public enum PlaylistEventType {
			PLAY_FROM_ABCINFO, PLAY_FROM_FILE, CLOSE_SONG, PLAYLIST_OPENED
		}
		
		private final PlaylistEventType type;

		public PlaylistEvent(Object source, PlaylistEventType t) {
			super(source);
			type = t;
		}
		
		public PlaylistEvent setShowSongView(boolean showSongView) {
			this.showSongView = showSongView;
			return this;
		}
		
		public boolean getShowSongView() {
			return showSongView;
		}
		
		public PlaylistEventType getType() {
			return type;
		}
	}
	
	private static final long serialVersionUID = -2515263857830606770L;
	private JSplitPane browserSplitPane;
	private JPanel leftPanel;
	private JPanel rightPanel;
	
	// Left
	private JTree abcFileTree;
	// Set with row location of menu right click in mouse listener
	// for menu item action listeners
	private int menuRowIdx = -1;
	private AbcFileTreeModel abcFileTreeModel;
	private HashSet<TreePath> expandedAbcTrees;
	private JScrollPane fileTreeScrollPane;
	private JButton addToPlaylistButton;
	private HintTextField searchTextField;
	
	// Right
	private JTable playlistTable;
	private AbcInfoTableModel tableModel;
	private JScrollPane playlistScrollPane;
	private JPopupMenu playlistContentPopupMenu;
	private JPopupMenu playlistHeaderPopupMenu;
	private JCheckBoxMenuItem columnEnablers[];
	private JLabel abcPlaylistLabel;
	
	// Bottom
	private JPanel bottomControls;
	private JButton nextSongButton;
	private JButton prevSongButton;
	private JTextField delayField;
	
	// Playlist menu
	private JMenu playlistMenu;
	private JMenuItem saveMenuItem;
	private JCheckBoxMenuItem autoplayMenuItem;
	private JCheckBoxMenuItem playbackDelayMenuItem;
	private JCheckBoxMenuItem expandSearchMenuItem;
	private JMenuItem exportSetMenuItem;
	
	private JFileChooser openPlaylistChooser = null;
	private JFileChooser savePlaylistChooser = null;
	
	private AbcInfo nowPlayingInfo = null;
	private boolean playlistDirtyFlag = false;
	private File playlistFile = null;
	
	private AbcFileTreeModel.SortType sortType;
	
	private Listener<PlaylistEvent> parentListener;
	private List<File> topLevelDirs = new ArrayList<File>();
	
	private Preferences playlistPrefs;
	
	public AbcPlaylistPanel(Preferences prefs) {
		super (new BorderLayout());
		
		playlistPrefs = prefs;
		
		browserSplitPane = new JSplitPane();
		browserSplitPane.setResizeWeight(0.5);
		
		leftPanel = new JPanel(new BorderLayout());
		rightPanel = new JPanel(new BorderLayout());
		
		browserSplitPane.setLeftComponent(leftPanel);
		browserSplitPane.setRightComponent(rightPanel);
		int pos = prefs.getInt("splitPanePos", -1);
		if (pos != -1) {
			browserSplitPane.setDividerLocation(pos);
		}
		browserSplitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> {
			prefs.putInt("splitPanePos", (Integer)e.getNewValue());
		});
		add(browserSplitPane, BorderLayout.CENTER);
		
		{
			String directoryStr = prefs.get("directories", Util.getLotroMusicPath(true).getAbsolutePath());
			String[] dirs = directoryStr.split(File.pathSeparator);
			for (String dirStr : dirs) {
				topLevelDirs.add(new File(dirStr));
			}
		}
		
		// =================================
		// Left panel
		// =================================

		sortType = AbcFileTreeModel.SortType.valueOf(prefs.get("sortType", "NAME_ASC"));
		
		abcFileTreeModel = new AbcFileTreeModel(topLevelDirs);
		abcFileTreeModel.refresh(sortType);
		abcFileTreeModel.filter("");
		
		expandedAbcTrees = new HashSet<TreePath>();
		
		abcFileTree = new JTree();
		abcFileTree.setShowsRootHandles(true);
		abcFileTree.setFocusable(true);
		abcFileTree.setRootVisible(false);
		abcFileTree.setModel(abcFileTreeModel);
		abcFileTree.setCellRenderer(new AbcPlaylistTreeCellRenderer());
		abcFileTree.collapseRow(0);
		abcFileTree.setDragEnabled(true);
		abcFileTree.setToggleClickCount(0);
		abcFileTree.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, Collections.emptySet());
		abcFileTree.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, Collections.emptySet());
		abcFileTree.setTransferHandler(new TransferHandler() {
			private static final long serialVersionUID = -3378747637795103415L;

			@Override
			protected Transferable createTransferable(JComponent c) {
				JTree tree = (JTree) c;
		        TreePath[] selectedPaths = tree.getSelectionPaths();
		        if (selectedPaths == null) {
		        	return null;
		        }

				List<File> fileList = new ArrayList<>();
				for (TreePath path : selectedPaths) {
					AbcSongFileNode node = (AbcSongFileNode) path.getLastPathComponent();
					fileList.add(node.getFile());
		        }

				// Use DataHandler to create a Transferable with the javaFileListFlavor
				return new FileListTransferable(fileList);
		    }

			@Override
			public int getSourceActions(JComponent c) {
	    		return COPY_OR_MOVE;
			}
			
			// Necessary since for some reason there is no exposed Transferable class for DataFlavor.javaFileListFlavor
			class FileListTransferable implements Transferable {
				
				private final List<File> files;
				
				public FileListTransferable(List<File> files) {
					this.files = files;
				}

				@Override
				public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
					if (!isDataFlavorSupported(flavor)) {
						throw new UnsupportedFlavorException(flavor);
					}
					return files;
				}

				@Override
				public DataFlavor[] getTransferDataFlavors() {
					return new DataFlavor[] {DataFlavor.javaFileListFlavor};
				}

				@Override
				public boolean isDataFlavorSupported(DataFlavor flavor) {
					return DataFlavor.javaFileListFlavor.equals(flavor);
				}
				
			}
		});
		abcFileTree.addTreeSelectionListener(e -> {
			// Only allow adding files if all the selected ones are abc files (no folders)
			boolean validSelection = true;
			TreePath[] paths = abcFileTree.getSelectionPaths();
			if (paths != null) {
				for (TreePath path : paths) {
					AbcSongFileNode f = (AbcSongFileNode)path.getLastPathComponent();
					if (f.getFile().isDirectory()) {
						validSelection = false;
						break;
					}
				}
			} else {
				validSelection = false; // Nothing selected
			}
			addToPlaylistButton.setEnabled(validSelection);
		});
		abcFileTree.addTreeExpansionListener(new TreeExpansionListener() {
			@Override
			public void treeCollapsed(TreeExpansionEvent e) {
				expandedAbcTrees.remove(e.getPath());
				expandedAbcTrees.removeIf(path -> e.getPath().isDescendant(path));
			}

			@Override
			public void treeExpanded(TreeExpansionEvent e) {
				expandedAbcTrees.add(e.getPath());
			}
		});
		JPopupMenu fileTreePopup = new JPopupMenu();
		
		JMenuItem fileTreeAddToPlaylist = new JMenuItem("Add to playlist");
		fileTreeAddToPlaylist.addActionListener(e -> {
//			addTreePathsToPlaylist(abcFileTree.getSelectionPaths());
			addFilesToPlaylist(treePathsToFileList(abcFileTree.getSelectionPaths()), -1);
		});
		fileTreePopup.add(fileTreeAddToPlaylist);
		
		JMenuItem fileTreePlay = new JMenuItem("Play");
		fileTreePlay.addActionListener(e -> {
			AbcSongFileNode f = (AbcSongFileNode)(abcFileTree.getPathForRow(menuRowIdx).getLastPathComponent());
			firePlaylistEvent(f.getFile(), PlaylistEventType.PLAY_FROM_FILE, true);
		});
		fileTreePopup.add(fileTreePlay);
		
		abcFileTree.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				doPopupCheck(e);
			}
			
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
					int idx = abcFileTree.getClosestRowForLocation(e.getX(), e.getY());
					if (idx == -1) {
						return;
					}
					AbcSongFileNode f = (AbcSongFileNode)(abcFileTree.getPathForRow(idx).getLastPathComponent());
					if (f.getFile().isDirectory()) {
						if (abcFileTree.isExpanded(idx))
							abcFileTree.collapsePath(abcFileTree.getPathForRow(idx));
						else
							abcFileTree.expandPath(abcFileTree.getPathForRow(idx));
						return;
					}
					firePlaylistEvent(f.getFile(), PlaylistEventType.PLAY_FROM_FILE);
				}
			}
			
			@Override
			public void mouseReleased(MouseEvent e) {
				doPopupCheck(e);
			}
			
			public void doPopupCheck(MouseEvent e) {
				if (e.isPopupTrigger()) {
					menuRowIdx = abcFileTree.getClosestRowForLocation(e.getX(), e.getY());
					if (menuRowIdx != -1) {
						abcFileTree.getSelectionRows();
						if (!IntStream.of(abcFileTree.getSelectionRows()).anyMatch(x -> x == menuRowIdx)) {
							abcFileTree.setSelectionRows(new int[] {menuRowIdx});
						}
						TreePath path = abcFileTree.getPathForRow(menuRowIdx);
						AbcSongFileNode f = (AbcSongFileNode)path.getLastPathComponent();
						fileTreePlay.setEnabled(!f.getFile().isDirectory());
						fileTreePopup.show(e.getComponent(), e.getX(), e.getY());	
					}
				}
			}
		});
		ToolTipManager.sharedInstance().registerComponent(abcFileTree);
		fileTreeScrollPane = new JScrollPane(abcFileTree, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		fileTreeScrollPane.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "none");
		
		JLabel abcBrowserLabel = new JLabel("ABC Browser");
		abcBrowserLabel.setToolTipText("<html>Browser for your ABC files.<br>Double-click a song to play it, or drag selected songs to the playlist panel.</html>");
		Font font = abcBrowserLabel.getFont();
		abcBrowserLabel.setFont(font.deriveFont(Font.BOLD, font.getSize2D()));
		abcBrowserLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		
		leftPanel.add(fileTreeScrollPane, BorderLayout.CENTER);
		leftPanel.add(abcBrowserLabel, BorderLayout.NORTH);
		
		
		// =================================
		// Right panel
		// =================================
		
		abcPlaylistLabel = new JLabel("Untitled Playlist");
		font = abcPlaylistLabel.getFont();
		abcPlaylistLabel.setFont(font.deriveFont(Font.BOLD, font.getSize2D()));
		abcPlaylistLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		
		tableModel = new AbcInfoTableModel();
		tableModel.addTableModelListener(e -> {
			playlistDirtyFlag = true;
			if (playlistFile == null && tableModel.getRowCount() == 0) {
				playlistDirtyFlag = false;
			}
			updatePlaylistLabel();
			
			if (tableModel.getRowCount() == 0) {
				nextSongButton.setEnabled(false);
				prevSongButton.setEnabled(false);
			} else if (nowPlayingInfo != null) {
				int idx = tableModel.getIdxForAbcInfo(nowPlayingInfo);
				prevSongButton.setEnabled(idx > 0);
				nextSongButton.setEnabled(idx >= 0 && idx < tableModel.getRowCount() -1);
			}
		});
		
		playlistTable = new JTable(tableModel) {
			private static final long serialVersionUID = 7474807902173697106L;
			
			@Override
			public Component prepareRenderer(TableCellRenderer render, int row, int col) {
				Component c = super.prepareRenderer(render, row, col);
				JComponent jc = (JComponent)c;
				AbcInfo inf = (row != -1) ? tableModel.getAbcInfoAt(row) : null;
				
				if (nowPlayingInfo != null && inf == nowPlayingInfo) {
					Color foreground = UIManager.getColor("Label.foreground");
					Border border = BorderFactory.createCompoundBorder(
							BorderFactory.createMatteBorder(2, 0, 2, 0, foreground),
							BorderFactory.createEmptyBorder(0, 3, 0, 3));
					jc.setBorder(border);
					Font f = new JLabel().getFont();
					f = f.deriveFont(Font.ITALIC | Font.BOLD);
					jc.setFont(f);
				}
				
				return jc;
			}
			
			@Override
			public String getToolTipText(MouseEvent e) {
				String txt = null;
				Point p = e.getPoint();
				int row = rowAtPoint(p);
				
				if (row != -1) {
					txt = "";
					AbcInfo inf = tableModel.getAbcInfoAt(row);
					for (File file : inf.getSourceFiles()) {
						txt += file.getName();
						txt += "\n";
					}
					for (int i = 0; i < inf.getPartCount(); i++) {
						if (i != 0) {
							txt += "\n";
						}
						txt = txt + inf.getPartFullName(i + 1);
					}
				}
				return txt;
			}
		};
		
		playlistTable.setFocusable(true);
		playlistTable.setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);
		playlistTable.setFillsViewportHeight(true);
		playlistTable.setDragEnabled(true);
		playlistTable.setDropMode(DropMode.INSERT_ROWS);
		playlistTable.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "none");
		playlistTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "none");
		playlistTable.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "none");
		playlistTable.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, Collections.emptySet());
		playlistTable.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, Collections.emptySet());
		playlistTable.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_DELETE) {
					while (playlistTable.getSelectedRow() != -1) {
						tableModel.removeRow(playlistTable.getSelectedRow());
					}
				}
			}
		});
		playlistTable.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
					if (playlistTable.getSelectedRow() == -1) {
						return;
					}
					AbcInfo info = tableModel.getAbcInfoAt(playlistTable.getSelectedRow());
					setNowPlayingInfo(info);
					firePlaylistEvent(info, PlaylistEventType.PLAY_FROM_ABCINFO);
				} else if (e.getButton() == MouseEvent.BUTTON2) {
					
				}
			}
			
			@Override
			public void mousePressed(MouseEvent e) {
				Point p = e.getPoint();
				int row = playlistTable.rowAtPoint(p);
				if (row != -1 && !IntStream.of(playlistTable.getSelectedRows()).anyMatch(x -> x == row)) {
					playlistTable.setRowSelectionInterval(row, row);
				}
			}
		});
		
		PlaylistTransferHandler transferHandler = new PlaylistTransferHandler(playlistTable);
		transferHandler.setPlaylistLoadCallback(f -> {
			if (promptSavePlaylist()) {
				loadPlaylist(f, false);
			}
		});
		transferHandler.setAbcFileLoadCallback((f, i) -> {
			addFilesToPlaylist(f, i);
		});
		playlistTable.setTransferHandler(transferHandler);
		playlistHeaderPopupMenu = new JPopupMenu();
		JMenuItem resizeToFitMenuItem = playlistHeaderPopupMenu.add(new JMenuItem("Resize Columns to Fit Content"));
		resizeToFitMenuItem.addActionListener(e -> {
			TableColumnModel columns = playlistTable.getColumnModel();
			int columnCount = columns.getColumnCount();
			if (columnCount == 0) return;
			
			int availableWidth = playlistTable.getWidth() - columns.getColumnMargin() * (columnCount - 1);
			int perColumnWidth = availableWidth / columnCount;
			
			int[] optimalWidths = new int[columnCount];
			int totalOptimalWidth = 0;
			
			TableCellRenderer headerRenderer = playlistTable.getTableHeader().getDefaultRenderer();
			
			// Get max sizes needed for each column
			for (int i = 0; i < columnCount; i++) {
				int maxSize = -1;
				TableColumn col = columns.getColumn(i);
				Component headerComp = headerRenderer.getTableCellRendererComponent(playlistTable, col.getHeaderValue(), false, false, 0, i);
				maxSize = Math.max(maxSize, headerComp.getPreferredSize().width);
				
				int rowCount = playlistTable.getRowCount();
				TableCellRenderer cellRenderer = playlistTable.getCellRenderer(0, i);
				for (int j = 0; j < rowCount; j++) {
					Object value = playlistTable.getValueAt(j, i);
					Component comp = cellRenderer.getTableCellRendererComponent(playlistTable, value, false, false, j, i);
					maxSize = Math.max(maxSize, comp.getPreferredSize().width);
				}
				
				optimalWidths[i] = maxSize;
				totalOptimalWidth += maxSize;
			}
			
			if (totalOptimalWidth > availableWidth) {
				Integer[] columnIndexes = IntStream.range(0, columnCount).boxed().toArray(Integer[]::new);

				Arrays.sort(columnIndexes, (i1, i2) -> Integer.compare(optimalWidths[i2], optimalWidths[i1]));
				int excessWidth = totalOptimalWidth - availableWidth;
				int remainingColumns = columnCount;
				
				// Shrink largest columns to size of equal-sized columns first
				for (int i = 0; i < columnCount && excessWidth > 0; i++) {
					int colIndex = columnIndexes[i];
					int optimalWidth = optimalWidths[colIndex];
					int minWidth = Math.min(optimalWidth, perColumnWidth); // min size = (#cols / total w)
					
					int reduce = Math.min(excessWidth / remainingColumns, optimalWidth - minWidth);
					optimalWidths[colIndex] -= reduce;
					excessWidth =- reduce;
					remainingColumns--;
				}
				
				// Distribute rest across all columns
				if (excessWidth > 0) {
					int reductionPerColumn = excessWidth / columnCount;
					for (int i = 0; i < columnCount && excessWidth > 0; i++) {
						int reduce = Math.min(reductionPerColumn, optimalWidths[i] - 10); // min size = 10
						optimalWidths[i] -= reduce;
						excessWidth -= reduce;
					}
				}
			}
			
			for (int i = 0; i < columnCount; i++) {
				TableColumn col = columns.getColumn(i);
				col.setPreferredWidth(optimalWidths[i]);
			}
			
			playlistTable.doLayout();
		});
		JMenuItem resizeToDefaultMenuItem = playlistHeaderPopupMenu.add(new JMenuItem("Resize Columns to Equal Size"));
		resizeToDefaultMenuItem.addActionListener(e -> {
			TableColumnModel columns = playlistTable.getColumnModel();
			int columnCount = columns.getColumnCount();
			if (columnCount == 0) return;
			
			int availableWidth = playlistTable.getWidth() - columns.getColumnMargin() * (columnCount - 1);
			int perColumnWidth = availableWidth / columnCount;
			
			for (int i = 0; i < columnCount; i++) {
				TableColumn column = columns.getColumn(i);
				column.setPreferredWidth(perColumnWidth);
			}
			playlistTable.doLayout();
		});
		playlistHeaderPopupMenu.addSeparator();
		playlistTable.getTableHeader().setComponentPopupMenu(playlistHeaderPopupMenu);
		playlistTable.getTableHeader().setReorderingAllowed(false);
		
		initTableHeaderSort();
		
		playlistHeaderPopupMenu.addSeparator();
		
		initTableHeaderColumns();
		
		Preferences colSizes = playlistPrefs.node("colSizes");
		
		TableColumnModel columnModel = playlistTable.getColumnModel();
		for (int i = 0; i < columnModel.getColumnCount(); i++) {
			TableColumn column = columnModel.getColumn(i);
			int savedWidth = colSizes.getInt("column_" + i, -1);
			if (savedWidth != -1) {
				column.setPreferredWidth(savedWidth);	
			}
		}
		
		playlistTable.getColumnModel().addColumnModelListener(new TableColumnModelListener() {
			@Override
			public void columnSelectionChanged(ListSelectionEvent e) {
			}
			
			@Override
			public void columnRemoved(TableColumnModelEvent e) {
			}
			
			@Override
			public void columnMoved(TableColumnModelEvent e) {
			}
			
			@Override
			public void columnMarginChanged(ChangeEvent e) {
				TableColumnModel columnModel = (TableColumnModel) e.getSource();
				for (int i = 0; i < columnModel.getColumnCount(); i++) {
					TableColumn column = columnModel.getColumn(i);
					int width = column.getWidth();
					colSizes.putInt("column_" + i, width);
				}
			}
			
			@Override
			public void columnAdded(TableColumnModelEvent e) {
			}
		});
		
		playlistContentPopupMenu = new JPopupMenu();
		JMenuItem playItem = new JMenuItem("Play");
		playItem.addActionListener(e -> {
			AbcInfo info = tableModel.getAbcInfoAt(playlistTable.getSelectedRow());
			setNowPlayingInfo(info);
			firePlaylistEvent(info, PlaylistEventType.PLAY_FROM_ABCINFO);
		});
		playlistContentPopupMenu.add(playItem);
		JMenuItem removeItem = new JMenuItem("Remove Selected");
		removeItem.addActionListener(e -> {
			while (playlistTable.getSelectedRow() != -1) {
				tableModel.removeRow(playlistTable.getSelectedRow());
			}
		});
		playlistContentPopupMenu.add(removeItem);
		playlistContentPopupMenu.addPopupMenuListener(new PopupMenuListener() {

			@Override
			public void popupMenuCanceled(PopupMenuEvent e) {
			}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
			}

			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
				boolean enabled = playlistTable.getSelectedRow() != -1;
				if (enabled) {
					AbcInfo info = tableModel.getAbcInfoAt(playlistTable.getSelectedRow());
					playItem.setText("Play '" + info.getTitle() + "'");
				} else {
					playItem.setText("Play");
				}
				playItem.setEnabled(enabled);
				removeItem.setEnabled(enabled);
			}
			
		});
		playlistTable.setComponentPopupMenu(playlistContentPopupMenu);
		
        DefaultTableCellRenderer centered = new DefaultTableCellRenderer();  
        centered.setHorizontalAlignment(SwingConstants.CENTER);
		
		playlistScrollPane = new JScrollPane(playlistTable,JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		playlistScrollPane.setViewportView(playlistTable);
		
		// =================================
		// Bottom Controls
		// =================================

		addToPlaylistButton = new JButton("Add Selected");
		addToPlaylistButton.setToolTipText("<html>Add the selected songs in the ABC Browser to the playlist.<br> Control-click or shift-click to select multiple songs.</html>");
		addToPlaylistButton.setEnabled(false);
		addToPlaylistButton.addActionListener(e -> {
//			addTreePathsToPlaylist(abcFileTree.getSelectionPaths());
			if (abcFileTree.getSelectionPaths() == null) {
				return;
			}
			addFilesToPlaylist(treePathsToFileList(abcFileTree.getSelectionPaths()), -1);
		});
		
		searchTextField = new HintTextField("Search...", 15);
		searchTextField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void changedUpdate(DocumentEvent e) { update(e); }
			@Override
			public void insertUpdate(DocumentEvent e) { update(e); }
			@Override
			public void removeUpdate(DocumentEvent e) { update(e); }
			
			public void update(DocumentEvent e) {
				abcFileTreeModel.filter(searchTextField.getText());
				if (!searchTextField.getText().isEmpty() && expandSearchMenuItem.isSelected()) {
					expandMatchedPaths();
				}
				reExpandPaths();
			}
		});
		
		JButton playPlaylistButton = new JButton("Play");
		playPlaylistButton.setToolTipText("Play playlist starting from the first song.");
		playPlaylistButton.setFocusable(false);
		playPlaylistButton.addActionListener(e -> {
			if (tableModel.getRowCount() == 0) {
				return;
			}
			AbcInfo info = tableModel.getAbcInfoAt(0);
			setNowPlayingInfo(info);
			firePlaylistEvent(info, PlaylistEventType.PLAY_FROM_ABCINFO);
		});
		
		nextSongButton = new JButton("Next Song");
		nextSongButton.setEnabled(false);
		nextSongButton.addActionListener(e -> {
			int newIdx = tableModel.getIdxForAbcInfo(nowPlayingInfo) + 1;
			if (newIdx >= tableModel.getRowCount()) {
				return;
			}

			AbcInfo info = tableModel.getAbcInfoAt(newIdx);
			setNowPlayingInfo(info);
			firePlaylistEvent(info, PlaylistEventType.PLAY_FROM_ABCINFO);
		});
		
		prevSongButton = new JButton("Prev Song");
		prevSongButton.setEnabled(false);
		prevSongButton.addActionListener(e -> {
			int newIdx = tableModel.getIdxForAbcInfo(nowPlayingInfo) - 1;
			if (newIdx < 0) {
				return;
			}

			AbcInfo info = tableModel.getAbcInfoAt(newIdx);
			setNowPlayingInfo(info);
			firePlaylistEvent(info, PlaylistEventType.PLAY_FROM_ABCINFO);
		});
		
		String delayToolTipText = "<html>Configure song switch delay.<br>"+
				"Used to simulate the total setlist time, including the time it takes to switch parts between each song.<br>"+
				"Set it to the average number of seconds it takes your band to switch songs, and the total time of the set<br>"+
				"including song switches will be calculated and displayed next to the playlist title.</html>";
		JLabel delayLabel = new JLabel("Song switch delay (seconds):");
		delayLabel.setToolTipText(delayToolTipText);
		delayField = new JTextField(playlistPrefs.get("delayInSecs", ""));
		delayField.setToolTipText(delayToolTipText);
		delayField.addKeyListener(new KeyAdapter() {
			@Override
			public void keyTyped(KeyEvent e) {
				char c = e.getKeyChar();
				if ((c < '0' || c > '9') && c != KeyEvent.VK_BACK_SPACE && c != KeyEvent.VK_DELETE) {
					getToolkit().beep();
					e.consume();
				}
			}
		});
		delayField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void removeUpdate(DocumentEvent e) { changed(e); }
			@Override
			public void insertUpdate(DocumentEvent e) { changed(e); }
			@Override
			public void changedUpdate(DocumentEvent e) { changed(e); }
			
			public void changed(DocumentEvent e) {
				updatePlaylistLabel();
				playlistPrefs.put("delayInSecs", delayField.getText());
			}
		});
		delayField.setColumns(4);
		
		bottomControls = new JPanel(new MigLayout("fillx"));
		bottomControls.add(addToPlaylistButton);
		bottomControls.add(searchTextField);
		
		bottomControls.add(new JPanel(), "pushx 200");
		
		bottomControls.add(delayLabel, "align right");
		bottomControls.add(delayField, "align center");
		bottomControls.add(prevSongButton);
		bottomControls.add(playPlaylistButton);
		bottomControls.add(nextSongButton, "sg play");
		
		add(bottomControls, BorderLayout.SOUTH);
		
		rightPanel.add(playlistScrollPane, BorderLayout.CENTER);
		rightPanel.add(abcPlaylistLabel, BorderLayout.NORTH);
		
		// =================================
		// Playlist menu
		// =================================
		
		playlistMenu = new JMenu("Playlist");
		JMenuItem loadMenuItem = playlistMenu.add(new JMenuItem("Open Playlist..."));
		loadMenuItem.addActionListener(e -> {
			if (promptSavePlaylist()) {
				promptOpenPlaylist();	
			}
		});
		JMenuItem appendMenuItem = playlistMenu.add(new JMenuItem("Append Playlist..."));
		appendMenuItem.addActionListener(e -> {
			promptOpenPlaylist(true /* append */);
		});
		JMenuItem saveAsMenuItem = playlistMenu.add(new JMenuItem("Save Playlist As..."));
		saveAsMenuItem.addActionListener(e -> {
			savePlaylistAs();
		});
		saveMenuItem = playlistMenu.add(new JMenuItem("Save Playlist"));
		saveMenuItem.setEnabled(false);
		saveMenuItem.addActionListener(e-> {
			if (playlistFile == null) {
				return;
			}
			
			savePlaylist();
		});
		JMenuItem clearMenuItem = playlistMenu.add(new JMenuItem("Clear Playlist"));
		clearMenuItem.addActionListener(e -> {
			if (promptSavePlaylist()) {
				playlistFile = null;
				saveMenuItem.setEnabled(false);
				tableModel.clearRows();
				
				if (nowPlayingInfo != null) {
					firePlaylistEvent(this, PlaylistEventType.CLOSE_SONG);
				}
			}
		});
		exportSetMenuItem = playlistMenu.add(new JMenuItem("Export Playlist as Set..."));
		exportSetMenuItem.addActionListener(e -> {
			TableColumnModel cm = playlistTable.getColumnModel();
			ArrayList<String> visibleColumns = new ArrayList<>(cm.getColumnCount());
			for (int i = 0; i < cm.getColumnCount(); i++) {
				visibleColumns.add((String)cm.getColumn(i).getHeaderValue());
			}
			PlaylistSetExportWizard wiz = new PlaylistSetExportWizard(
					(JFrame)SwingUtilities.getWindowAncestor(this),
					prefs.node("setExport"),
					playlistFile,
					tableModel.getTableData(),
					visibleColumns);
			wiz.setVisible(true);
		});
		playlistMenu.addSeparator();
		autoplayMenuItem = (JCheckBoxMenuItem) playlistMenu.add(new JCheckBoxMenuItem("Enable Autoplay"));
		autoplayMenuItem.setSelected(playlistPrefs.getBoolean("autoplay", true));
		autoplayMenuItem.addActionListener(e -> {
			playlistPrefs.putBoolean("autoplay", autoplayMenuItem.isSelected());
		});
		playbackDelayMenuItem = (JCheckBoxMenuItem) playlistMenu.add(new JCheckBoxMenuItem("Enable Delay between Playback"));
		playbackDelayMenuItem.setSelected(playlistPrefs.getBoolean("playbackDelay", false));
		playbackDelayMenuItem.addActionListener(e -> {
			playlistPrefs.putBoolean("playbackDelay", playbackDelayMenuItem.isSelected());
		});
		expandSearchMenuItem = (JCheckBoxMenuItem) playlistMenu.add(new JCheckBoxMenuItem("Enable Auto-Expanding Searched Folders"));
		expandSearchMenuItem.setSelected(playlistPrefs.getBoolean("autoExpandSearch", false));
		expandSearchMenuItem.addActionListener(e -> {
			playlistPrefs.putBoolean("autoExpandSearch", expandSearchMenuItem.isSelected());
		});
		playlistMenu.addSeparator();
		JMenu sortBy = new JMenu("Sort browser by...");
		playlistMenu.add(sortBy);
		ButtonGroup group = new ButtonGroup();
		for (AbcFileTreeModel.SortType type : AbcFileTreeModel.SortType.values()) {
			JRadioButtonMenuItem item = new JRadioButtonMenuItem(type.toString());
			item.addActionListener(e -> {
				sortType = type;
				prefs.put("sortType", sortType.name());
				abcFileTreeModel.sort(sortType);
				abcFileTreeModel.filter(searchTextField.getText());
				reExpandPaths();
			});
			group.add(item);
			sortBy.add(item);
			
			if (type == sortType) {
				item.setSelected(true);
			}
		}
		JMenuItem directoryMenuItem = playlistMenu.add(new JMenuItem("Browser Directories..."));
		directoryMenuItem.addActionListener(e -> {
			JFrame f = (JFrame)SwingUtilities.getWindowAncestor(AbcPlaylistPanel.this);
			AbcBrowserDirectoryDialog d = new AbcBrowserDirectoryDialog(f, topLevelDirs);
			if (d.isSuccess()) {
				List<String> dirs = d.getDirectories();
				String newPrefString = String.join(File.pathSeparator, dirs);
				prefs.put("directories", newPrefString);
				topLevelDirs = dirs.stream().map(File::new).collect(Collectors.toList());
				abcFileTreeModel.setDirectories(topLevelDirs);
				abcFileTreeModel.refresh(sortType);
				abcFileTreeModel.filter(searchTextField.getText());
				reExpandPaths();
			}
		});
		JMenuItem refreshMenuItem = playlistMenu.add(new JMenuItem("Refresh Browser"));
		refreshMenuItem.addActionListener(e -> {
			abcFileTreeModel.refresh(sortType);
			abcFileTreeModel.filter(searchTextField.getText());
			reExpandPaths();
		});
	}
	
	private void expandMatchedPaths() {
		HashSet<TreePath> validExpandedTrees = new HashSet<TreePath>();
		AbcSongFileNode root = (AbcSongFileNode)abcFileTreeModel.getRoot();
		traverseAndExpand(validExpandedTrees, root, new TreePath(root));
		expandedAbcTrees = validExpandedTrees;
	}
	
	private void traverseAndExpand(HashSet<TreePath> newExpanded, AbcSongFileNode node, TreePath nodePath) {
		if (!node.isLeaf()) {
			boolean hasChildFolder = false;
			for (int i = 0; i < node.getChildrenCount(); i++) {
				AbcSongFileNode child = node.getChildAt(i);
				TreePath childPath = nodePath.pathByAddingChild(child);
				if (child.getChildrenCount() != 0) {
					traverseAndExpand(newExpanded, child, childPath);
					hasChildFolder = true;
				}
			}
			
			if (!hasChildFolder) {
				newExpanded.add(nodePath);
			}
		}
	}
	
	private void reExpandPaths() {
		HashSet<TreePath> validExpandedTrees = new HashSet<TreePath>();
		HashSet<TreePath> snapshot = new HashSet<>(expandedAbcTrees);
		for (TreePath path : snapshot) {
			Object[] nodes = path.getPath();
			Object walk = abcFileTreeModel.getRoot();
			boolean found = true;
			for (Object node : nodes) {
				if (node == abcFileTreeModel.getRoot()) {
					continue;
				}
				
				int idx = abcFileTreeModel.getIndexOfChild(walk, node);
				if (idx < 0) {
					found = false;
				} else {
					walk = abcFileTreeModel.getChild(walk, idx);
				}
			}
			if (found) {
				abcFileTree.expandPath(path);
				validExpandedTrees.add(path);
			}
		}
		expandedAbcTrees = validExpandedTrees;
	}
	
	private void initTableHeaderColumns() {
		columnEnablers = new JCheckBoxMenuItem[AbcInfoTableModel.COL_COUNT];
		Preferences columnPrefs = playlistPrefs.node("columns");
		List<String> colNames = tableModel.getColumnNames();
		for (int i = 0; i < tableModel.getColumnCount(); i++) {
			int idx = i;
			String name = tableModel.getColumnName(i);
			boolean enabled = columnPrefs.getBoolean(name, tableModel.getColumnDefaultEnabled(name));
			TableColumn col = playlistTable.getColumn(name);
			JCheckBoxMenuItem item = new JCheckBoxMenuItem((String)name);
			item.setSelected(enabled);
			item.addActionListener(e -> {
				if (item.isSelected()) {
					playlistTable.addColumn(col); 
					int from = playlistTable.getColumnCount() - 1;
					int to = -1;
					for (int j = 0; j <= from; j++) {
						String n = playlistTable.getColumnName(j);
						if (colNames.indexOf(n) > idx) {
							to = j;
							break;
						}
					}
					if (to != -1) {
						playlistTable.moveColumn(from, to);	
					}
				} else {
					playlistTable.removeColumn(col);
				}
				columnPrefs.putBoolean(name, item.isSelected());
			});
			if (!enabled) {
				playlistTable.removeColumn(col);
			}
			playlistHeaderPopupMenu.add(item);
			columnEnablers[i] = item;
		}
	}
	
	private void initTableHeaderSort() {
		List<String> colNames = tableModel.getColumnNames();
		JMenu sortMenu = new JMenu("Sort Playlist By...");
		JMenu ascending = new JMenu("Ascending");
		JMenu descending = new JMenu("Descending");
		sortMenu.add(ascending);
		sortMenu.add(descending);
		playlistHeaderPopupMenu.add(sortMenu);
		
		for (String col : colNames) {
			JMenuItem asc = ascending.add(new JMenuItem(col));
			JMenuItem desc = descending.add(new JMenuItem(col));
			ActionListener listen = e -> {
				tableModel.sortBy(col, e.getSource() == asc);
			};
			asc.addActionListener(listen);
			desc.addActionListener(listen);
		}
	}
	
	private void firePlaylistEvent(Object obj, PlaylistEventType type) {
		if (parentListener != null) {
 			parentListener.onEvent(new PlaylistEvent(obj, type));
		}
	}
	
	private void firePlaylistEvent(Object obj, PlaylistEventType type, boolean showSongView) {
		if (parentListener != null) {
 			parentListener.onEvent(new PlaylistEvent(obj, type).setShowSongView(showSongView));
		}
	}
	
	public JMenu getPlaylistMenu() {
		return playlistMenu;
	}
	
	private boolean savePlaylist() {
		if (playlistFile == null) {
			return false;
		}
		try {
			XmlUtil.saveDocument(AbcPlaylistXmlCoder.savePlaylistToXml(tableModel.getTableData()), playlistFile);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this, "Failed to save playlist", "Failed to save playlist", JOptionPane.ERROR_MESSAGE);
			return false;
		}
		
		saveMenuItem.setEnabled(true);
		playlistDirtyFlag = false;
		playlistPrefs.put("playlistDirectory", playlistFile.getParentFile().getAbsolutePath());
		updatePlaylistLabel();
		return true;
	}
	
	private boolean savePlaylistAs() {
		if (savePlaylistChooser == null) {
			savePlaylistChooser = new JFileChooser();
			savePlaylistChooser.setDialogTitle("Save ABC Playlist");
			savePlaylistChooser.setFileFilter(new ExtensionFileFilter("ABC Playlist (.abcp)", "abcp"));
		}
		
		if (playlistFile != null) {
			savePlaylistChooser.setSelectedFile(playlistFile);
		} else {
			String folder = playlistPrefs.get("playlistDirectory", Util.getLotroMusicPath(true).getAbsolutePath());
			savePlaylistChooser.setCurrentDirectory(new File(folder));
		}
		
		File file = null;
		
		while (true) {
		
			int result = savePlaylistChooser.showSaveDialog(this);
			
			if (result != JFileChooser.APPROVE_OPTION) {
				return false;
			}
		
			file = savePlaylistChooser.getSelectedFile();
			String fileName = file.getName();
			int dot = fileName.lastIndexOf('.');
			if (dot <= 0 || !fileName.substring(dot).equalsIgnoreCase(".abcp")) {
				fileName += ".abcp";
				file = new File(file.getParent(), fileName);
			}
			
			if (file.exists()) {
				int res = JOptionPane.showConfirmDialog(this,
						"File \"" + fileName + "\" already exists.\n" + "Do you want to replace it?",
						"Confirm Replace File", JOptionPane.YES_NO_CANCEL_OPTION);
				if (res == JOptionPane.CANCEL_OPTION)
					return false;
				else if (res == JOptionPane.NO_OPTION)
					continue;
			}
			
			break;
		}
		
		try {
			XmlUtil.saveDocument(AbcPlaylistXmlCoder.savePlaylistToXml(tableModel.getTableData()), file);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this, "Failed to save playlist", "Failed to save playlist", JOptionPane.ERROR_MESSAGE);
			return false;
		}
		
		playlistFile = file;
		saveMenuItem.setEnabled(true);
		playlistDirtyFlag = false;
		playlistPrefs.put("playlistDirectory", savePlaylistChooser.getCurrentDirectory().getAbsolutePath());
		updatePlaylistLabel();
		
		return true;
	}
	
	public void loadPlaylist(File file, boolean append) {
		boolean markDirty = false;
		
		List<List<File>> songs = null;
		try {
			songs = AbcPlaylistXmlCoder.loadPlaylist(file);
		} catch (ParseException e) {
			JOptionPane.showMessageDialog(this, e.getMessage(), "Failed to load playlist", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		if (songs == null) {
			return;
		}
		
		List<AbcInfo> data = new ArrayList<AbcInfo>();
		List<Integer> nonExistentFiles = new ArrayList<Integer>();
		
		for (List<File> files : songs) {
			List<FileAndData> fad = new ArrayList<FileAndData>();
			for (File f : files) {
				if (!f.exists()) {
					
					nonExistentFiles.add(data.size());
					AbcInfo inf = new AbcInfo();
					inf.addSourceFile(f);
					data.add(inf);
					continue;
				}
				try {
					fad.add(new FileAndData(f, AbcToMidi.readLines(f)));
					data.add(AbcToMidi.parseAbcMetadata(fad));
				} catch (Exception e) {
					String err = "Failed to parse abc:\n" + f.getAbsolutePath();
					JOptionPane.showMessageDialog(this, err, "Failed to load song", JOptionPane.ERROR_MESSAGE);
					markDirty = true;
				}
			}
		}
		
		if (!nonExistentFiles.isEmpty()) {
			handleMissingPlaylistFiles(nonExistentFiles, data);
			markDirty = true;
		}
		
		if (data.size() == 0 && !nonExistentFiles.isEmpty() && !append) {
			JOptionPane.showMessageDialog(this, "Failed to load any of the songs in this playlist!", "Failed to Load", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		if (!append) {
			tableModel.clearRows();
		} else if (playlistFile != null || tableModel.getRowCount() != 0) {
			markDirty = true;
		}
		
		for (AbcInfo inf : data) {
			tableModel.addRow(inf);
		}
		
		playlistDirtyFlag = markDirty;
		
		if (!append || playlistFile == null) {
			playlistFile = file;
		}
		saveMenuItem.setEnabled(true);
		updatePlaylistLabel();
	}
	
	public void handleMissingPlaylistFiles(List<Integer> missingSongs, List<AbcInfo> songs) {
		String confirmDialogStr = "Some abc files couldn't be found:\n";
		for (int i = 0; i < Math.max(5, missingSongs.size()); i++) {
			AbcInfo inf = songs.get(missingSongs.get(i));
			confirmDialogStr += inf.getSourceFiles().get(0).getName() + "\n";
		}
		if (missingSongs.size() > 5) {
			confirmDialogStr += (missingSongs.size() - 5) + " more...\n";
		}
		confirmDialogStr += "Would you like to recursively search for them?";
		int result = JOptionPane.showConfirmDialog(
				this,
				confirmDialogStr,
				"Missing Songs",
				JOptionPane.YES_NO_OPTION);
		
		if (result == JOptionPane.NO_OPTION) {
			for (int i = missingSongs.size() - 1; i >= 0; i--) {
				int idx = missingSongs.get(i);
				songs.remove(idx);
			}
			return;
		}
		
		String folder = playlistPrefs.get("playlistDirectory", Util.getLotroMusicPath(true).getAbsolutePath());
		
		JFileChooser folderChooser = new JFileChooser();
		folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		folderChooser.setCurrentDirectory(new File(folder));
		folderChooser.setMultiSelectionEnabled(false);
		folderChooser.setDialogTitle("Choose Search Folder");
		folderChooser.showOpenDialog(this);
		if (result != JFileChooser.APPROVE_OPTION) {
			for (int i = missingSongs.size() - 1; i >= 0; i--) {
				int idx = missingSongs.get(i);
				songs.remove(idx);
			}
			return;
		}
		
		HashMap<String, Integer> nameToIdx = new HashMap<>();
		List<String> foundFiles = new ArrayList<String>();
		
		for (Integer songIdx : missingSongs) {
			String missingFile = songs.get((int)songIdx).getSourceFiles().get(0).getName();
			nameToIdx.put(missingFile, songIdx);
		}
		
		try {
			Files.walkFileTree(folderChooser.getSelectedFile().toPath(), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs){
					String filename = file.getFileName().toString();
					
					if (nameToIdx.containsKey(filename)) {
						Integer idx = nameToIdx.get(filename);
						
						File f = file.toFile();
						
						List<FileAndData> fad = new ArrayList<FileAndData>();
						AbcInfo inf = null;
						try {
							fad.add(new FileAndData(f, AbcToMidi.readLines(f)));
							inf = AbcToMidi.parseAbcMetadata(fad);
						} catch (IOException | ParseException e) {
							e.printStackTrace();
							return FileVisitResult.CONTINUE;
						}
						
						if (inf != null) {
							songs.set(idx, inf);
							nameToIdx.remove(filename);
							foundFiles.add(f.getAbsolutePath());
						}
						
						if (nameToIdx.isEmpty()) {
							return FileVisitResult.TERMINATE;
						}
					}
					
					return FileVisitResult.CONTINUE;
				}
				
				@Override
				public FileVisitResult visitFileFailed(Path file, IOException e) {
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		if (nameToIdx.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Found all missing files!", "Search Complete", JOptionPane.INFORMATION_MESSAGE);
		} else if (nameToIdx.size() == missingSongs.size()) {
			JOptionPane.showMessageDialog(this, "No missing files were found in the selected folder.", "Search Complete", JOptionPane.INFORMATION_MESSAGE);
		} else {
			String fileReportStr = "Found files:\n";
			for (int i = 0; i < foundFiles.size(); i++ ) {
				fileReportStr += foundFiles.get(i) + "\n";
			}
			fileReportStr += "Still missing " + nameToIdx.size() + " files.";
			JOptionPane.showMessageDialog(this, fileReportStr, "Search Complete", JOptionPane.INFORMATION_MESSAGE);
		}
		
		for (int i = missingSongs.size() - 1; i >= 0; i--) {
			int idx = missingSongs.get(i);
			if (nameToIdx.containsValue((Integer)idx)) {
				songs.remove(idx);
			}
		}
	}
	
	public boolean isPlayingFromPlaylist() {
		return nowPlayingInfo != null;
	}
	
	public void promptOpenPlaylist() {
		promptOpenPlaylist(false /* append */);
	}
	
	public void promptOpenPlaylist(boolean append) {
		if (openPlaylistChooser == null) {
			openPlaylistChooser = new JFileChooser();
			openPlaylistChooser.setDialogTitle("Open ABC Playlist");
			openPlaylistChooser.setMultiSelectionEnabled(false);
			openPlaylistChooser.setFileFilter(new ExtensionFileFilter("ABC Playlist (.abcp)", "abcp"));
		}
		
		String folder = playlistPrefs.get("playlistDirectory", Util.getLotroMusicPath(true).getAbsolutePath());
		openPlaylistChooser.setCurrentDirectory(new File(folder));
		
		int result = openPlaylistChooser.showOpenDialog(this);
		File file = null;
		if (result != JFileChooser.APPROVE_OPTION) {
			return;
		}
		
		file = openPlaylistChooser.getSelectedFile();
		
		if (nowPlayingInfo != null && !append) {
			firePlaylistEvent(this, PlaylistEventType.CLOSE_SONG);
		}
		
		loadPlaylist(file, append);
		playlistPrefs.put("playlistDirectory", openPlaylistChooser.getCurrentDirectory().getAbsolutePath());
		
		firePlaylistEvent(this, PlaylistEventType.PLAYLIST_OPENED);
	}
	
	public boolean promptSavePlaylist() {
		if (!isPlaylistModified()) {
			return true;
		}
		
		String message;
		if (playlistFile == null) {
			message = "Do you want to save this untitled playlist?";
		} else {
			message = "Do you want to save changes to " + playlistFile.getName() + "?";
		}
		
		int result = JOptionPane.showConfirmDialog(this, message, "Save Changes", JOptionPane.YES_NO_CANCEL_OPTION,
				JOptionPane.QUESTION_MESSAGE, IconLoader.getImageIcon("abcplayer_32.png"));
		if (result == JOptionPane.CANCEL_OPTION)
			return false;
		if (result == JOptionPane.YES_OPTION) {
			return playlistFile == null? savePlaylistAs() : savePlaylist();
		}
		return true;
	}
	
	public void advanceToNextSongIfNeeded() {
		if (!autoplayMenuItem.isSelected()) {
			return;
		}
		
		if (tableModel.getRowCount() == 0) {
			return;
		}
		
		int newIdx = (tableModel.getIdxForAbcInfo(nowPlayingInfo) + 1) % tableModel.getRowCount();
		
		if (newIdx > 0) {
			int delayTime = getSongDelayTimeInSeconds(); 
			if (delayTime > 0 && playbackDelayMenuItem.isSelected()) {
				new SwingWorker<Boolean, Boolean>() {
					int delayRemaining = delayTime;
					@Override
					protected Boolean doInBackground() throws Exception {
						while (delayRemaining > 0) {
							SwingUtilities.invokeLater(() -> {
								updatePlaylistLabel(delayRemaining);
							});
							Thread.sleep(1000);
							delayRemaining -= 1;
						}
						return true;
					}
					
					@Override
					protected void done() {
						int idx = (tableModel.getIdxForAbcInfo(nowPlayingInfo) + 1) % tableModel.getRowCount();
						if (idx > 0) {
							AbcInfo info = tableModel.getAbcInfoAt(idx);
							setNowPlayingInfo(info);
							firePlaylistEvent(info, PlaylistEventType.PLAY_FROM_ABCINFO);
						} else {
							setNowPlayingInfo(null);
						}
						updatePlaylistLabel();
					}
				}.execute();
			} else {
				AbcInfo info = tableModel.getAbcInfoAt(newIdx);
				setNowPlayingInfo(info);
				firePlaylistEvent(info, PlaylistEventType.PLAY_FROM_ABCINFO);
			}
		}
		else {
			setNowPlayingInfo(null);
		}
	}
	
	public boolean isPlaylistModified() {
		return playlistDirtyFlag;
	}
	
	private void setNowPlayingInfo(AbcInfo nowPlaying) {
		nowPlayingInfo = nowPlaying;
		if (nowPlayingInfo == null) {
			nextSongButton.setEnabled(false);
			prevSongButton.setEnabled(false);
		} else {
			int idx = tableModel.getIdxForAbcInfo(nowPlayingInfo);
			prevSongButton.setEnabled(idx > 0);
			nextSongButton.setEnabled(idx < tableModel.getRowCount() - 1);
		}
		playlistTable.repaint();
	}
	
	private void updatePlaylistLabel() {
		updatePlaylistLabel(-1);
	}
	
	private void updatePlaylistLabel(int nextSongIn) {
		long totalTimeMicroSec = 0;
		int numSongs = tableModel.getRowCount();
		for (AbcInfo inf : tableModel.getTableData()) {
			String dur = inf.getSongDurationStr();
			if (dur == null || dur.isEmpty() || !dur.contains(":")) {
				continue;
			}
			String[] split = dur.split(":");
			int m = Integer.parseInt(split[0]);
			int s = Integer.parseInt(split[1]);
			totalTimeMicroSec += 60000000L * m;
			totalTimeMicroSec += 1000000L * s;
		}
		
		String labelStr = "";
		
		if (playlistFile != null) {
			labelStr = playlistFile.getName();
			if (labelStr.contains(".")) {
				labelStr = labelStr.substring(0, labelStr.lastIndexOf('.'));
			}
			abcPlaylistLabel.setToolTipText(playlistFile.getAbsolutePath());
		} else {
			labelStr = "Untitled Playlist";
			abcPlaylistLabel.setToolTipText("");
		}
		
		if (playlistDirtyFlag) {
			labelStr += "*";
		}
		
		long totalTimeWithDelayMicroSec = totalTimeMicroSec;
		int delaySec = getSongDelayTimeInSeconds();
		
		if (delaySec > 0 && numSongs > 0) {
			totalTimeWithDelayMicroSec = totalTimeWithDelayMicroSec + (numSongs - 1) * (delaySec * 1000000l);
		}
		
		if (totalTimeMicroSec != 0) {
			String songs = " Song" + (numSongs > 1? "s" : "");
			labelStr += " (" + numSongs + songs +  ", Time: [" + Util.formatDuration(totalTimeMicroSec) + "]";
			if (totalTimeWithDelayMicroSec != totalTimeMicroSec) {
				labelStr += ", With Switches: [" + Util.formatDuration(totalTimeWithDelayMicroSec) + "]";
			}
			labelStr += ")";
		}
		
		if (nextSongIn >= 0) {
			labelStr = labelStr + " [Next song playing in: " + nextSongIn + "]";
		}
		
		abcPlaylistLabel.setText(labelStr);
	}
	
	private int getSongDelayTimeInSeconds() {
		if (!delayField.getText().isEmpty()) {
			try {
				int delaySec = Integer.parseInt(delayField.getText());
				return delaySec;
			} catch (NumberFormatException e) {
				return -1;
			}
		}
		return 0;
	}
	
	public void resetPlaylistPosition() {
		setNowPlayingInfo(null);
	}
	
	private void addFilesToPlaylist(List<File> files, int insertPos) {
		String filterText = searchTextField.getText();
		new SwingWorker<Boolean, Boolean>() {
			boolean loadPlaylist = false;
			List<AbcInfo> data = new ArrayList<>();
			@Override
			protected Boolean doInBackground() {
				if (files.size() == 1 && files.get(0).getName().endsWith(".abcp")) {
					loadPlaylist = true;
					return true;
				}

				boolean onlyFolders = true;

				// Pre scan for folders
				for (File file : files) {
					if (!file.isDirectory()) onlyFolders = false;
				}

				List<File> toLoad = files;
				// Expand folders recursively
				if (onlyFolders) {
					toLoad = new ArrayList<File>();

					try {
						toLoad = files.stream()
								.filter(File::exists)
								.map(File::toPath) // Convert File to Path
								.flatMap(path -> getAbcFilesInFolder(path)) // Process each directory
								.filter(theFile -> theFile.getName().toLowerCase().contains(filterText)) // Filter based on search textbox
								.sorted(AbcFileTreeModel.getFileComparator(sortType))
								.collect(Collectors.toList());
					} catch (Exception e) {
						e.printStackTrace();
						return false;
					}
				}

				for (File file : toLoad) {
					List<FileAndData> fad = new ArrayList<FileAndData>();
					try {
						fad.add(new FileAndData(file, AbcToMidi.readLines(file)));
						data.add(AbcToMidi.parseAbcMetadata(fad));
					} catch (Exception e) {
						e.printStackTrace();
						continue;
					}
				}
				return true;
			}

			private Stream<File> getAbcFilesInFolder(Path directory) {
				try {
					return Files.walk(directory)
							.filter(Files::isRegularFile)
							.filter(path -> path.toString().endsWith(Util.ABC_FILE_EXTENSION) || path.toString().endsWith(Util.TXT_FILE_EXTENSION))
							.map(Path::toFile);
				} catch (IOException e) {
					e.printStackTrace();
					return Stream.empty();
				}
			}

			@Override
			protected void done() {
				if (loadPlaylist && promptSavePlaylist()) {
					loadPlaylist(files.get(0), false);
				} else {
					if (insertPos == -1) { // Append to table
						for (AbcInfo info : data) {
							tableModel.addRow(info);
						}
						playlistTable.revalidate();
						Rectangle rect = playlistTable.getCellRect(playlistTable.getRowCount() - 1, 0, true);
						playlistTable.scrollRectToVisible(rect);
					} else { // Drag and drop to a specific position
						int idx = insertPos;
						for (AbcInfo info : data) {
							tableModel.insertRow(info, idx++);
						}
					}
				}
			}
		}.execute();
	}
	
	private List<File> treePathsToFileList(TreePath[] paths) {
		List<File> ret = new ArrayList<File>(paths.length);
		for (TreePath path : paths) {
			AbcSongFileNode node = (AbcSongFileNode)path.getLastPathComponent();
			ret.add(node.getFile());
		}
		
		return ret;
	}
	
	public void setPlaylistListener(Listener<PlaylistEvent> l) {
		parentListener = l;
	}
	
	public void removePlaylistListener() {
		parentListener = null;
	}
}
