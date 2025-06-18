package com.aifel.abctools;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileFilter;
import java.lang.reflect.InvocationTargetException;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.SwingUtilities;
import com.digero.common.util.Themer;
import com.digero.common.util.Util;
import com.digero.maestro.MaestroMain;
import com.digero.maestro.view.MiscSettings;

public class AbcTools {

	private Preferences toolsPrefs = Preferences.userNodeForPackage(AbcTools.class);
	private Preferences mergePrefs;
	
	private volatile static AbcToolsView frame = null;
	
	@SuppressWarnings("unused")
	private static AbcTools abcTools;

	private AutoExporter autoInstance;
	private MergeTool mergeInstance;
	
	public static void main(String[] args) {
		
		try {
			SwingUtilities.invokeAndWait(() -> {
				try {
					MiscSettings misc = new MiscSettings(Preferences.userNodeForPackage(MaestroMain.class).node("miscSettings"), true);
					Themer.setLookAndFeel(misc.theme, misc.fontSize);
				} catch (Exception e) {
					// Reset theme to default if an error occurred setting look and feel
					//Preferences preferences = Preferences.userNodeForPackage(MaestroMain.class);
					//preferences.node("saveAndExportSettings").put("theme", "Default");
				}
				/*
				try {
					UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
				} catch (ClassNotFoundException | InstantiationException | IllegalAccessException
						| UnsupportedLookAndFeelException e) {
					e.printStackTrace();
				}
				*/
				try {
					frame = new AbcToolsView();
					frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			});
		} catch (InvocationTargetException | InterruptedException e) {
			e.printStackTrace();
		}
		
		abcTools = new AbcTools();
	}

	AbcTools() {
		// Setup folders from stored prefs if available:
		String myHome = Util.getLotroMusicPath(false).getAbsolutePath();
		mergePrefs = toolsPrefs.node("mergeTool");
		Preferences autoPrefs = toolsPrefs.node("autoExport");
		
		new MaestroMain();//used for version number
		
		SwingUtilities.invokeLater(() -> {
			frame.addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(WindowEvent e) {
					writePrefs();
				}
			});
			
			// Setup Maestro version number:	
			frame.setTitle("ABC Tools v"+MaestroMain.APP_VERSION.toString());
	
			/*
			 * try { List<Image> icons = new ArrayList<>(); icons.add(ImageIO.read(new
			 * FileInputStream("abcmergetool.ico"))); frame.setIconImages(icons); } catch (Exception ex) { // Ignore
			 * ex.printStackTrace(); }
			 */
		});
		autoInstance = new AutoExporter(frame, myHome, this, autoPrefs);
		mergeInstance = new MergeTool(frame, myHome, this, mergePrefs);
	}

	protected void writePrefs() {
		mergeInstance.flushPrefs();
		autoInstance.flushPrefs();

		try {
			toolsPrefs.flush();
		} catch (BackingStoreException e) {
			e.printStackTrace();
		}
	}

	static class AbcFileFilter implements FileFilter {

		@Override
		public boolean accept(File file) {
			String name = file.getName().toLowerCase();
			return name.endsWith(Util.ABC_FILE_EXTENSION) || name.endsWith(Util.TXT_FILE_EXTENSION);
		}
	}

	static class MsxFileFilter implements FileFilter {

		@Override
		public boolean accept(File file) {
			String name = file.getName().toLowerCase();
			return name.endsWith(Util.MSX_FILE_EXTENSION);
		}
	}
	
	/**
	 * Allow .git files/folder to be ignored
	 */
	static class FolderFileFilter implements FileFilter {

		@Override
		public boolean accept(File file) {
			return !file.getName().startsWith(".git");
		}
	}
}
