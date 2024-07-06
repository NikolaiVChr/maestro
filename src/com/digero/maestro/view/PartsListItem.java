package com.digero.maestro.view;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EventObject;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;

import com.digero.common.util.IDiscardable;
import com.digero.common.util.Listener;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.AbcPartMetadataSource;

import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstants;

public class PartsListItem extends JPanel implements IDiscardable, TableLayoutConstants {
	@SuppressWarnings("serial")
	public static class PartsListItemEvent extends EventObject {
		public enum EventType {
			SELECTION, SOLO, MUTE, UNSOLO_ALL, UNMUTE_ALL;
		}

		private final EventType type;

		public PartsListItemEvent(PartsListItem item, EventType type) {
			super(item);
			this.type = type;
		}

		public EventType getType() {
			return type;
		}
	}

	private static final long serialVersionUID = -1794798972919435415L;

	static final int GUTTER_WIDTH = 4;
	static final int TITLE_WIDTH = 100;
	static final int SOLO_WIDTH = 8;
	static final int MUTE_WIDTH = 8;

	private static double[] LAYOUT_COLS = new double[] { FILL, PREFERRED, PREFERRED };
	private static double[] LAYOUT_ROWS = new double[] { PREFERRED };

	private JLabel title;
	private JButton soloButton;
	private JButton muteButton;

	private AbcPart part;

	private Color selectedFg, selectedBg, unselectedFg, unselectedBg;

	private Listener<PartsListItemEvent> itemListener = null;

	public PartsListItem(AbcPart part) {
		super(new TableLayout(LAYOUT_COLS, LAYOUT_ROWS));

		this.setPart(part);

		title = new JLabel(part.toString());
		title.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 0));

		int h = title.getPreferredSize().height + 4;

		JList<AbcPartMetadataSource> dummy = new JList<AbcPartMetadataSource>();
		selectedFg = dummy.getSelectionForeground();
		selectedBg = dummy.getSelectionBackground();
		unselectedFg = dummy.getForeground();
		unselectedBg = dummy.getBackground();

		setBackground(unselectedBg);
		setForeground(unselectedFg);

		Dimension buttonSize = new Dimension(h, h);

		String soloText = part.isSoloed() ? "<html><b>S</b></html>" : "<html>S</html>";
		Color soloColor = part.isSoloed() ? Color.decode("#7e7eff") : new JButton().getBackground();
		soloButton = new JButton(soloText);
		soloButton.setToolTipText("Solo/Unsolo Part");
		soloButton.setBackground(soloColor);
		soloButton.setPreferredSize(buttonSize);
		soloButton.setMargin(new Insets(0, 0, 0, 0));
		soloButton.setFocusable(false);
		soloButton.addActionListener(e -> {
			boolean isSolo = !part.isSoloed();
			if ((e.getModifiers() & ActionEvent.SHIFT_MASK) != 0) {
				itemListener.onEvent(new PartsListItemEvent(PartsListItem.this, PartsListItemEvent.EventType.UNSOLO_ALL));
			}
			setSolo(isSolo);
			itemListener.onEvent(new PartsListItemEvent(PartsListItem.this, PartsListItemEvent.EventType.SOLO));
		});

		String muteText = part.isMuted() ? "<html><b>M</b></html>" : "<html>M</html>";
		Color muteColor = part.isMuted() ? Color.decode("#ff7777") : new JButton().getBackground();
		muteButton = new JButton(muteText);
		muteButton.setToolTipText("Mute/Unmute Part");
		muteButton.setBackground(muteColor);
		muteButton.setPreferredSize(buttonSize);
		muteButton.setMargin(new Insets(0, 0, 0, 0));
		muteButton.setFocusable(false);
		muteButton.addActionListener(e -> {
			boolean isMute = !part.isMuted();
			if ((e.getModifiers() & ActionEvent.SHIFT_MASK) != 0) {
				itemListener.onEvent(new PartsListItemEvent(PartsListItem.this, PartsListItemEvent.EventType.UNMUTE_ALL));
			}
			setMute(isMute);
			itemListener.onEvent(new PartsListItemEvent(PartsListItem.this, PartsListItemEvent.EventType.MUTE));
		});

		int col = -1;
		add(title, ++col + ", 0");
		add(soloButton, ++col + ", 0");
		add(muteButton, ++col + ", 0");

		title.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (itemListener != null) {
					PartsListItemEvent ev = new PartsListItemEvent(PartsListItem.this,
							PartsListItemEvent.EventType.SELECTION);
					itemListener.onEvent(ev);
				}
			}

		});
	}

	private PartsListItem(String titleTxt) {
		title = new JLabel(titleTxt);
		title.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 0));

		int h = title.getPreferredSize().height + 4;
		Dimension buttonSize = new Dimension(h, h);
		soloButton = new JButton("<html><b>S</b></html>");
		soloButton.setPreferredSize(buttonSize);
		soloButton.setMargin(new Insets(0, 0, 0, 0));
		soloButton.setFocusable(false);

		muteButton = new JButton("<html><b>M</b></html>");
		muteButton.setPreferredSize(buttonSize);
		muteButton.setMargin(new Insets(0, 0, 0, 0));
		muteButton.setFocusable(false);

		int col = -1;
		add(title, ++col + ", 0");
		add(soloButton, ++col + ", 0");
		add(muteButton, ++col + ", 0");
	}
	
	public void setSolo(boolean solo) {
		part.setSoloed(solo);
		soloButton.setBackground(solo ? Color.decode("#7e7eff") : new JButton().getBackground());
		soloButton.setText(solo ? "<html><b>S</b></html>" : "<html>S</html>");
	}
	
	public void setMute(boolean mute) {
		part.setMuted(mute);
		muteButton.setBackground(mute ? Color.decode("#ff7777") : new JButton().getBackground());
		muteButton.setText(mute ? "<html><b>M</b></html>" : "<html>M</html>");
	}
	
	public boolean isSoloed() {
		return part.isSoloed();
	}
	
	public boolean isMuted() {
		return part.isMuted();
	}

	public static Dimension getProtoDimension() {
		final PartsListItem item = new PartsListItem("000. Lonely Mountain Bassoon*");
		return item.getPreferredSize();
	}

	public void setItemListener(Listener<PartsListItemEvent> l) {
		itemListener = l;
	}

	public void removeItemListener(Listener<PartsListItemEvent> l) {
		itemListener = null;
	}

	void setSelected(boolean selected) {
		setBackground(selected ? selectedBg : unselectedBg);
		setForeground(selected ? selectedFg : unselectedFg);
		title.setForeground(selected ? selectedFg : unselectedFg);
	}

	@Override
	public void discard() {

	}

	public AbcPart getPart() {
		return part;
	}

	public void setPart(AbcPart part) {
		this.part = part;
	}

}
