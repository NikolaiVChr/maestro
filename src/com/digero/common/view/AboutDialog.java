package com.digero.common.view;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import com.digero.common.icons.IconLoader;
import com.digero.common.util.Util;
import com.digero.common.util.Version;

public final class AboutDialog {
    private static final Logger log = Logger.getLogger("view");

	public static void show(JFrame parent, final String appName, final Version appVersion, final String appUrl,
			final String iconName) {
		ImageIcon aboutIcon;
		try {
			aboutIcon = new ImageIcon(IconLoader.getImage(iconName));
		} catch (IOException e1) {
			log.log(Level.WARNING, "Error loading icon", e1);
			aboutIcon = null;
		}

		String heapInUse = "unknown.";
		MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
		if (memoryMXBean != null) {
			MemoryUsage usage = memoryMXBean.getHeapMemoryUsage();
			if (usage != null) {
				heapInUse = (usage.getUsed() / (1024 * 1024)) + "MB/" + (usage.getMax() / (1024 * 1024)) + "MB";
			}
		}

		JLabel aboutMessage = new JLabel("<html>" //
				+ appName + "<br>" //
				+ "Version " + appVersion + "<br>" //
				+ "Created by Digero of Landroval.<br>" //
				+ "Copyright &copy; 2015 Ben Howell.<br>" //
				+ "Upgraded by Aifel of Meriadoc,<br>" //
				+ "Elamond of Peregrin and Karloman.<br>" //
				+ "<br>" //
				+ "<a href='" + appUrl + "'>" + "wiki link" + "</a><br>" //
				+ "<br>" //
				+ "Heap in use is " + heapInUse + "</html>");
		aboutMessage.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		aboutMessage.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getButton() == MouseEvent.BUTTON1) {
					Util.openURL(appUrl, null);
				}
			}
		});
		
		JLabel javaMessage = new JLabel(System.getProperty("java.vendor")+" Java v" + System.getProperty("java.version"));

		JPanel aboutPanel = new JPanel(new BorderLayout(0, 8));
		aboutPanel.add(aboutMessage, BorderLayout.CENTER);
		aboutPanel.add(javaMessage, BorderLayout.SOUTH);

		String aboutTitle = "About " + appName;
		JOptionPane.showMessageDialog(parent, aboutPanel, aboutTitle, JOptionPane.INFORMATION_MESSAGE, aboutIcon);
	}

	/** Static-only class */
	private AboutDialog() {
	}
}
