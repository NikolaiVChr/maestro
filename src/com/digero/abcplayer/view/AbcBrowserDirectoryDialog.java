package com.digero.abcplayer.view;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.digero.common.util.Util;
import com.digero.common.view.UIText;

@SuppressWarnings("serial")
public class AbcBrowserDirectoryDialog extends JDialog {
	
	private boolean success = false;
	private DefaultListModel<String> model;
	
	public AbcBrowserDirectoryDialog(JFrame abcPlayer, List<File> directories) {
		super(abcPlayer, UIText.get("abcplayer.abc.directories"), true);
		
		JPanel panel = new JPanel(new BorderLayout());
//		panel.setPreferredSize(new Dimension(500, 200));
		panel.setMinimumSize(new Dimension(500, 200));
		
		JButton remove = new JButton(UIText.get("abcplayer.remove"));
		remove.setEnabled(false);
		
		model = new DefaultListModel<String>();
		for (File f : directories) {
			model.addElement(f.getAbsolutePath());
		}
		JList<String> list = new JList<String>();
		list.setModel(model);
		list.addListSelectionListener(e -> {
			remove.setEnabled(list.getSelectedIndex() != -1);
		});
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(list.getBackground());
		wrapper.add(list, BorderLayout.NORTH);
		wrapper.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				list.clearSelection();
			}
		});
		JScrollPane listPane = new JScrollPane(
				JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		listPane.setViewportView(wrapper);
		panel.add(listPane, BorderLayout.CENTER);
		
		JPanel controlsPanel = new JPanel(new FlowLayout());
		JPanel cancelOkPanel = new JPanel(new FlowLayout());
		
		JButton add = new JButton(UIText.get("abcplayer.add"));
		add.addActionListener(e -> {
			JFileChooser chooseDir = new JFileChooser(Util.getLotroMusicPath(true));
			chooseDir.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			int res = chooseDir.showDialog(this, UIText.get("abcplayer.add"));
			if (res == JFileChooser.APPROVE_OPTION) {
				model.addElement(chooseDir.getSelectedFile().getAbsolutePath());
			}
		});
		
		remove.addActionListener(e -> {
			for (String s : list.getSelectedValuesList()) {
				model.removeElement(s);
			}
		});
		
		JButton cancel = new JButton(UIText.get("common.cancel"));
		cancel.addActionListener(e -> {
			success = false;
			AbcBrowserDirectoryDialog.this.setVisible(false);
		});
		JButton ok = new JButton("OK");
		ok.addActionListener(e -> {
			success = true;
			AbcBrowserDirectoryDialog.this.setVisible(false);
		});
		getRootPane().setDefaultButton(ok);
		controlsPanel.add(add);
		controlsPanel.add(remove);
		cancelOkPanel.add(cancel);
		cancelOkPanel.add(ok);
		
		panel.add(controlsPanel, BorderLayout.NORTH);
		panel.add(cancelOkPanel, BorderLayout.SOUTH);
		
		getContentPane().add(panel);
		// Center
		setLocationRelativeTo(null);
		pack();
		setVisible(true);
	}
	
	public boolean isSuccess() {
		return success;
	}
	
	public List<String> getDirectories() {
		List<String> dirs = new ArrayList<String>();
		for (int i = 0; i < model.getSize(); i++) {
			dirs.add(model.get(i));
		}
		return dirs;
	}
}
