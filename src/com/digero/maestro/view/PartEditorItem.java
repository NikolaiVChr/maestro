package com.digero.maestro.view;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
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
	
	protected static double horizGap = 15;
	protected static double[] LAYOUT_COLS = new double[] { FILL, horizGap, PREFERRED, PREFERRED, horizGap, PREFERRED, PREFERRED, horizGap, PREFERRED, PREFERRED };
	protected static double[] LAYOUT_COLS_BADGER = new double[] { FILL, PREFERRED, horizGap, PREFERRED, PREFERRED, horizGap, PREFERRED, PREFERRED, horizGap, PREFERRED, PREFERRED };

	public PartEditorItem(AbcPart part, boolean showBadger) {
		super(part, showBadger);
		this.setLayout(new TableLayout(showBadger?LAYOUT_COLS_BADGER:LAYOUT_COLS, LAYOUT_ROWS));
		
		title = new JLabel(part.toString());
		title.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 0));

		int h = title.getPreferredSize().height + 6;

		//Dimension buttonSize = new Dimension(h, h);

		delayField = new JTextField(String.format("%.3f", part.delay * 0.001f));
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
		
		conclusionFermataField = new JTextField(String.format("%.3f", part.conclusionFermata * 0.001f));
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

		removeAll();
		
		int col = -1;
		add(title, ++col + ", 0");
		if (showBadger) add(badgerButton, ++col + ", 0");
		//add(soloButton, ++col + ", 0");
		//add(muteButton, ++col + ", 0");
		col++;
		add(new JLabel("Delay"), ++col + ", 0");
		add(delayField, ++col + ", 0");
		col++;
		add(new JLabel("Fermata"), ++col + ", 0");
		add(conclusionFermataField, ++col + ", 0");
		col++;
		add(new JLabel("Max notes"), ++col + ", 0");
		add(maxField, ++col + ", 0");
	}

	protected PartEditorItem(String titleTxt) {
		super(titleTxt);
		removeAll();
		title = new JLabel(titleTxt);
		title.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 0));

		int h = title.getPreferredSize().height + 6;//added 2 due to using fields
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
		col++;
		add(new JLabel("Delay"), ++col + ", 0");
		add(delayField, ++col + ", 0");
		col++;
		add(new JLabel("Fermata"), ++col + ", 0");
		add(conclusionFermataField, ++col + ", 0");
		col++;
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
				part.delay = (int) (delay * 1000);
				part.delayEdited();
			}
		} catch (NumberFormatException nfe) {

		}
		delayField.setText(String.format("%.3f", part.delay * 0.001f));
	}
	
	private void validateFermataInput() {
		try {
			float conclusionFermata = Float.parseFloat(conclusionFermataField.getText().replace(',', '.'));
			if (conclusionFermata >= 0.000f && conclusionFermata <= 5.00f) {
				part.conclusionFermata = (int) (conclusionFermata * 1000);
				part.conclusionFermataEdited();
			}
		} catch (NumberFormatException nfe) {
	
		}
		conclusionFermataField.setText(String.format("%.3f", part.conclusionFermata * 0.001f));
	}
	
	private void validateMaxInput() {
		try {
			int max = Integer.parseInt(maxField.getText());
			if (max >= 1 && max <= 6) {
				part.setNoteMax(max);
				part.maxEdited();
			}
		} catch (NumberFormatException nfe) {

		}
		maxField.setText(String.format("%d", part.getNoteMax()));
	}
}