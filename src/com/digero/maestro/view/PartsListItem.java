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

	protected Listener<PartsListItemEvent> itemListener = null;

	public PartsListItem(AbcPart part, boolean showBadger) {
		super();

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

		JList<AbcPartMetadataSource> dummy = new JList<AbcPartMetadataSource>();
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
		badgerButton.setToolTipText("Songbook setup priority, 1 = must play, 6 = least important");
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
					JComponent jc = (JComponent) e.getSource();
                    JPanel parentPanel = (JPanel) jc.getParent(); // Get enclosing panel
                    parentPanel.getTransferHandler().exportAsDrag(parentPanel, e, TransferHandler.MOVE);
				}
			}
		});
		this.addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseDragged(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) {
                	JComponent jc = (JComponent) e.getSource();
                    jc.getTransferHandler().exportAsDrag(jc, e, TransferHandler.MOVE);
				}
			}
		});
	}

	protected PartsListItem(String titleTxt) {
		setLayout(getLayouts(false));
		title = new JLabel(titleTxt);
		title.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 0));

		int h = title.getPreferredSize().height + getBuffer();
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
