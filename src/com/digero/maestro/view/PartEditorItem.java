package com.digero.maestro.view;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EventObject;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import com.digero.common.util.IDiscardable;
import com.digero.common.util.Listener;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.AbcPartMetadataSource;

import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstants;

public class PartEditorItem extends PartsListItem implements IDiscardable, TableLayoutConstants {
	private static final long serialVersionUID = -1794798972919435416L;

	private JTextField delayField;
	private JTextField conclusionFermataField;
	private JTextField maxField;

	protected static Dimension horizGapi = new Dimension(15,15);
	

	public PartEditorItem(AbcPart part, boolean showBadger) {
		super(part, showBadger);
	}
		
	@Override
	protected int getBuffer() {
		return 6;
	}
	
	@Override
	protected LayoutManager getLayouts(boolean showBadger) {
		return new TableLayout(getColumns(showBadger), LAYOUT_ROWS);
	}
	
	@Override
	protected double[] getColumns(boolean showBadger) {
		double[] LAYOUT_COLS_EDITOR = new double[] { FILL, PREFERRED, PREFERRED, PREFERRED, PREFERRED, PREFERRED, PREFERRED, PREFERRED, PREFERRED, PREFERRED };
		double[] LAYOUT_COLS_BADGER_EDITOR = new double[] { FILL, PREFERRED, PREFERRED, PREFERRED, PREFERRED, PREFERRED, PREFERRED, PREFERRED, PREFERRED, PREFERRED, PREFERRED };
		
		return showBadger?LAYOUT_COLS_BADGER_EDITOR:LAYOUT_COLS_EDITOR;
	}

	@Override
	protected void initStart(AbcPart part) {
		title = new JLabel(part.toString());
		title.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 0));

		JList<AbcPartMetadataSource> dummy = new JList<AbcPartMetadataSource>();
		selectedFg = dummy.getSelectionForeground();
		selectedBg = dummy.getSelectionBackground();
		unselectedFg = dummy.getForeground();
		unselectedBg = dummy.getBackground();

		setBackground(unselectedBg);
		setForeground(unselectedFg);
		
		int h = title.getPreferredSize().height + getBuffer();

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

		delayField = new JTextField(String.format("%.3f", part.delay * 0.001f).replace(',', '.'));
		delayField.setHorizontalAlignment(SwingConstants.CENTER);
		Dimension fieldSize = delayField.getPreferredSize();
		fieldSize.height = h;
		delayField.setPreferredSize(fieldSize);
		delayField.setToolTipText("Put a delay from 0s to 1.00s on a part."
				+ "\nThe effect wont be shown graphically");
		delayField.addActionListener(e -> {
			validateDelayInput();
		});
		delayField.addFocusListener(new FocusAdapter() {
			@Override
			public void focusLost(FocusEvent e) {
				validateDelayInput();
			}
		});
		
		conclusionFermataField = new JTextField(String.format("%.3f", part.conclusionFermata * 0.001f).replace(',', '.'));
		conclusionFermataField.setHorizontalAlignment(SwingConstants.CENTER);
		fieldSize = conclusionFermataField.getPreferredSize();
		fieldSize.height = h;
		conclusionFermataField.setPreferredSize(fieldSize);
		conclusionFermataField.setToolTipText("Put a conclusion fermata from 0s to 5.00s on a part."
				+ "\nDoes nothing if note not sustained by chosen instrument."
				+ "\nThe effect wont be shown graphically");
		conclusionFermataField.addActionListener(e -> {
			validateFermataInput();
		});
		conclusionFermataField.addFocusListener(new FocusAdapter() {
			@Override
			public void focusLost(FocusEvent e) {
				validateFermataInput();
			}
		});
		
		maxField = new JTextField(String.format("%d", part.getNoteMax()));
		maxField.setHorizontalAlignment(SwingConstants.CENTER);
		fieldSize = maxField.getPreferredSize();
		fieldSize.height = h;
		maxField.setPreferredSize(fieldSize);
		maxField.setToolTipText("Put a max concurrent notes from 1 to 6 on a part."
				+ "\nThe effect wont be shown graphically");
		maxField.addActionListener(e -> {
			validateMaxInput();
		});
		maxField.addFocusListener(new FocusAdapter() {
			@Override
			public void focusLost(FocusEvent e) {
				validateMaxInput();
			}
		});
	}

	@Override
	protected void initFinish(boolean showBadger) {
		setLayout(getLayouts(showBadger));
		int col = -1;
		add(title, ++col + ", 0");
		if (showBadger) add(badgerButton, ++col + ", 0");
		//add(soloButton, ++col + ", 0");
		//add(muteButton, ++col + ", 0");
		JPanel a = new JPanel();
		a.setVisible(false);
		a.setPreferredSize(horizGapi);
		add(a, ++col + ", 0");
		add(new JLabel("Delay"), ++col + ", 0");
		add(delayField, ++col + ", 0");
		JPanel b = new JPanel();
		b.setVisible(false);
		b.setPreferredSize(horizGapi);
		add(b, ++col + ", 0");
		add(new JLabel("Fermata"), ++col + ", 0");
		add(conclusionFermataField, ++col + ", 0");
		JPanel c = new JPanel();
		c.setVisible(false);
		c.setPreferredSize(horizGapi);
		add(c, ++col + ", 0");
		add(new JLabel("Max notes"), ++col + ", 0");
		add(maxField, ++col + ", 0");
	}
	
	@Override
	protected void initPost() {
		
	}

	protected PartEditorItem(String titleTxt) {
		super(titleTxt);
		removeAll();
		setLayout(getLayouts(true));
		title = new JLabel(titleTxt);
		title.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 0));

		int h = title.getPreferredSize().height + getBuffer();//added 2 due to using fields
		Dimension buttonSize = new Dimension(h, h);
		
		badgerButton = new JButton("<html><b>1</b></html>");
		badgerButton.setPreferredSize(buttonSize);
		badgerButton.setMargin(new Insets(0, 0, 0, 0));
		badgerButton.setFocusable(false);
		
		delayField = new JTextField(String.format("%.3f", 1000 * 0.001f));
		delayField.setHorizontalAlignment(SwingConstants.CENTER);
		Dimension fieldSize = delayField.getPreferredSize();
		fieldSize.height = h;
		delayField.setPreferredSize(fieldSize);
		
		conclusionFermataField = new JTextField(String.format("%.3f", 2500 * 0.001f));
		conclusionFermataField.setHorizontalAlignment(SwingConstants.CENTER);
		fieldSize = conclusionFermataField.getPreferredSize();
		fieldSize.height = h;
		conclusionFermataField.setPreferredSize(fieldSize);
		
		maxField = new JTextField(String.format("%d", 6));
		maxField.setHorizontalAlignment(SwingConstants.CENTER);
		fieldSize = maxField.getPreferredSize();
		fieldSize.height = h;
		maxField.setPreferredSize(fieldSize);

		int col = -1;
		add(title, ++col + ", 0");
		add(badgerButton, ++col + ", 0");
		//add(soloButton, ++col + ", 0");
		//add(muteButton, ++col + ", 0");
		JPanel a = new JPanel();
		a.setPreferredSize(horizGapi);
		add(a, ++col + ", 0");
		add(new JLabel("Delay"), ++col + ", 0");
		add(delayField, ++col + ", 0");
		JPanel b = new JPanel();
		b.setPreferredSize(horizGapi);
		add(b, ++col + ", 0");
		add(new JLabel("Fermata"), ++col + ", 0");
		add(conclusionFermataField, ++col + ", 0");
		JPanel c = new JPanel();
		c.setPreferredSize(horizGapi);
		add(c, ++col + ", 0");
		add(new JLabel("Max notes"), ++col + ", 0");
		add(maxField, ++col + ", 0");	
	}
	
	public static Dimension getProtoDimension() {
		final PartsListItem item = new PartEditorItem("000. Lonely Mountain Bassoon*");
		Dimension dim = item.getPreferredSize();
		return dim;
	}

	private void validateDelayInput() {
		try {
			float delay = Float.parseFloat(delayField.getText().replace(',', '.'));
			if (delay >= 0.000f && delay <= 1.00f) {
				int val = (int) (delay * 1000);
				if (part.delay != val) {
					part.delay = val;
					part.delayEdited();
				}
			}
		} catch (NumberFormatException nfe) {

		}
		delayField.setText(String.format("%.3f", part.delay * 0.001f).replace(',', '.'));
	}
	
	private void validateFermataInput() {
		try {
			float conclusionFermata = Float.parseFloat(conclusionFermataField.getText().replace(',', '.'));
			if (conclusionFermata >= 0.000f && conclusionFermata <= 5.00f) {
				int val = (int) (conclusionFermata * 1000);
				if (part.conclusionFermata != val) {
					part.conclusionFermata = val;
					part.conclusionFermataEdited();
				}
			}
		} catch (NumberFormatException nfe) {
	
		}
		conclusionFermataField.setText(String.format("%.3f", part.conclusionFermata * 0.001f).replace(',', '.'));
	}
	
	private void validateMaxInput() {
		try {
			int max = Integer.parseInt(maxField.getText());
			if (max >= 1 && max <= 6) {
				if (part.getNoteMax() != max) {
					part.setNoteMax(max);
					part.maxEdited();
				}
			}
		} catch (NumberFormatException nfe) {

		}
		maxField.setText(String.format("%d", part.getNoteMax()));
	}
}