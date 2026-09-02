package com.digero.maestro.view;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.EventObject;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;

import com.digero.common.util.IDiscardable;
import com.digero.common.util.Listener;
import com.digero.common.view.ColorTable;
import com.digero.common.view.UIText;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.AbcPartMetadataSource;

import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstants;

public class PartsListItem extends JPanel implements IDiscardable, TableLayoutConstants {
	
	private static final long serialVersionUID = -1794798972919435415L;
	
	public static class PartsListItemEvent extends EventObject {
		private static final long serialVersionUID = 8572619183993666151L;

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
	
	static final int GUTTER_WIDTH = 4;
	static final int TITLE_WIDTH = 100;
	static final int SOLO_WIDTH = 8;
	static final int MUTE_WIDTH = 8;

	protected double[] LAYOUT_COLS = new double[] { FILL, PREFERRED, PREFERRED };
	protected double[] LAYOUT_COLS_BADGER = new double[] { FILL, PREFERRED, PREFERRED, PREFERRED };
	protected double[] LAYOUT_ROWS = new double[] { PREFERRED };

	protected JLabel title;
	protected JButton soloButton;
	protected JButton muteButton;
	protected JButton badgerButton;

	protected AbcPart part;

	protected Color selectedFg;

	protected Color selectedBg;

	protected Color unselectedFg;

	protected Color unselectedBg;

	private boolean selected = false;
	private boolean trackHighlight = false;

	protected Listener<PartsListItemEvent> itemListener = null;
	
	private SongPartsListPanel parent = null;

	public PartsListItem(AbcPart part, boolean showBadger, SongPartsListPanel parent) {
		super();
		
		this.parent = parent;

		this.setPart(part);

		initStart(part);

		initFinish(showBadger);

		initPost();
	}
	
	protected double[] getColumns(boolean showBadger) {
		return showBadger?LAYOUT_COLS_BADGER:LAYOUT_COLS;
	}
	
	protected LayoutManager getLayouts(boolean showBadger) {
		return new TableLayout(getColumns(showBadger), LAYOUT_ROWS);
	}
	
	protected int getBuffer() {
		return 4;
	}

	protected void initStart(AbcPart part) {
		title = new JLabel(part.toString());
		title.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 0));

		int h = title.getPreferredSize().height + getBuffer();

		JList<AbcPartMetadataSource> dummy = new JList<>();
		selectedFg = dummy.getSelectionForeground();
		selectedBg = dummy.getSelectionBackground();
		unselectedFg = dummy.getForeground();
		unselectedBg = dummy.getBackground();

		setBackground(unselectedBg);
		setForeground(unselectedFg);

		Dimension buttonSize = new Dimension(h, h);

		String badgerText = "<html>"+part.getBadgerPrio()+"</html>";
		Color badgerColor = new JButton().getBackground();
		badgerButton = new JButton(badgerText);
		badgerButton.setToolTipText(UIText.get("maestro.songbook.setup.priority"));
		badgerButton.setBackground(badgerColor);
		badgerButton.setPreferredSize(buttonSize);
		badgerButton.setMargin(new Insets(0, 0, 0, 0));
		badgerButton.setFocusable(false);
		badgerButton.addActionListener(e -> {
			int prio = part.getBadgerPrio();
			prio += AbcPart.badgerPrioStep;
			if (prio > AbcPart.badgerPrioLowest) prio = AbcPart.badgerPrioHighest;
			part.setBadgerPrio(prio);
			String text = "<html>"+prio+"</html>";
			badgerButton.setText(text);
		});
		
		String soloText = part.isSoloed() ? UIText.get("maestro.html.b.solo.b.html") : UIText.get("maestro.html.solo.html");
		Color soloColor = part.isSoloed() ? ColorTable.PARTS_LIST_SOLO.get() : new JButton().getBackground();
		soloButton = new JButton(soloText);
		soloButton.setToolTipText(UIText.get("maestro.solo.unsolo.part"));
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

		String muteText = part.isMuted() ? UIText.get("maestro.html.b.mute.b.html") : UIText.get("maestro.html.mute.html");
		Color muteColor = part.isMuted() ? ColorTable.PARTS_LIST_MUTE.get() : new JButton().getBackground();
		muteButton = new JButton(muteText);
		muteButton.setToolTipText(UIText.get("maestro.mute.unmute.part"));
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
	}

	protected void initFinish(boolean showBadger) {
		setLayout(getLayouts(showBadger));
		int col = -1;
		add(title, ++col + ", 0");
		if (showBadger) add(badgerButton, ++col + ", 0");
		add(soloButton, ++col + ", 0");
		add(muteButton, ++col + ", 0");
	}

	protected void initPost() {
		
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
		
		title.addMouseMotionListener(new MouseMotionAdapter() {
			
			@Override
			public void mouseDragged(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) {
					JComponent source = (JComponent) e.getSource();
					source.getTransferHandler().exportAsDrag(source, e, TransferHandler.MOVE);
				}
			}
			
			@Override
			public void mouseMoved(MouseEvent e) {
			}
		});
		this.addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseDragged(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) {
                	title.getTransferHandler().exportAsDrag(title, e, TransferHandler.MOVE);
				}
			}
		});
		if (parent != null) {
			title.setTransferHandler(new SongPartsListPanel.PanelTransferHandler(parent, false, true));
			title.setDropTarget(null);
			setDropTarget(null);
		}
	}

	protected PartsListItem(String titleTxt) {
		setLayout(getLayouts(false));
		title = new JLabel(titleTxt);
		title.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 0));

		int h = title.getPreferredSize().height + getBuffer();
		Dimension buttonSize = new Dimension(h, h);
		soloButton = new JButton(UIText.get("maestro.html.b.solo.b.html"));
		soloButton.setPreferredSize(buttonSize);
		soloButton.setMargin(new Insets(0, 0, 0, 0));
		soloButton.setFocusable(false);

		muteButton = new JButton(UIText.get("maestro.html.b.mute.b.html"));
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
		soloButton.setBackground(solo ? ColorTable.PARTS_LIST_SOLO.get() : new JButton().getBackground());
		soloButton.setText(solo ? UIText.get("maestro.html.b.solo.b.html") : UIText.get("maestro.html.solo.html"));
	}
	
	public void setMute(boolean mute) {
		part.setMuted(mute);
		muteButton.setBackground(mute ? ColorTable.PARTS_LIST_MUTE.get() : new JButton().getBackground());
		muteButton.setText(mute ? UIText.get("maestro.html.b.mute.b.html") : UIText.get("maestro.html.mute.html"));
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
	
	@Override
	public Dimension getMaximumSize() {
		return new Dimension(super.getMaximumSize().width, getPreferredSize().height);
	}

	public void setItemListener(Listener<PartsListItemEvent> l) {
		itemListener = l;
	}

	public void removeItemListener(Listener<PartsListItemEvent> l) {
		itemListener = null;
	}

	void setSelected(boolean selected) {
		if (this.selected != selected) {
			this.selected = selected;
			updateColors();
		}
	}

	@Override
	public void discard() {

	}

	void setTrackHighlight(boolean on) {
		if (trackHighlight != on) {
			trackHighlight = on;
			updateColors();
		}
	}

	private void updateColors() {
		Color bg;
		Color fg;
		if (selected && trackHighlight) {
			bg = blend(selectedBg, ColorTable.HOVER_ACCENT.get(), 0.55f);   // selected orange, but stronger base
			fg = selectedFg;
		} else if (selected) {
			bg = selectedBg;  fg = selectedFg;
		} else if (trackHighlight) {
			bg = blend(unselectedBg, ColorTable.HOVER_ACCENT.get(), 0.35f); // unselected orange, lighter mix
			fg = unselectedFg;
		} else {
			bg = unselectedBg;  fg = unselectedFg;
		}
		setBackground(bg);
		setForeground(fg);
		title.setForeground(fg);
	}

	/** Linear blend: ratio=0 -> a, ratio=1 -> b. */
	private static Color blend(Color a, Color b, float ratio) {
		float inv = 1f - ratio;
		return new Color(
				Math.round(a.getRed()   * inv + b.getRed()   * ratio),
				Math.round(a.getGreen() * inv + b.getGreen() * ratio),
				Math.round(a.getBlue()  * inv + b.getBlue()  * ratio));
	}

	public AbcPart getPart() {
		return part;
	}

	public void setPart(AbcPart part) {
		this.part = part;
	}
}
