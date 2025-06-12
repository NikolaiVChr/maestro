package com.digero.common.view;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Insets;
import javax.swing.JLabel;

public class LeanJLabel extends JLabel {
	private static final long serialVersionUID = 4028224577557000571L;
	private int maxTextWidth = 0;

    public LeanJLabel(String text) {
        super(text);
        maxTextWidth = getFontMetrics(getFont()).stringWidth(text);
    }

    @Override
    public void setText(String text) {
        super.setText(text);
        repaint();
    }

    @Override
    public void revalidate() {}

    /** 
     * If ever want to force a layout (e.g. after a font/locale change),
     * call this manually:
     */
    public void forceRevalidate() {
        super.revalidate();
    }

    @Override
    public Dimension getPreferredSize() {
        // Always report a width equal to the widest text we’ve seen
        Insets ins = getInsets();
        FontMetrics fm = getFontMetrics(getFont());
        int h = fm.getHeight() + ins.top + ins.bottom;
        return new Dimension(maxTextWidth + ins.left + ins.right, h);
    }
}
