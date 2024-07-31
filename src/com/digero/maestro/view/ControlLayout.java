package com.digero.maestro.view;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;

public class ControlLayout implements LayoutManager {
	private List<Component> components = new ArrayList<>();
	private int minimumSize;
	private float zoomV = 1.0f;
	private JPanel graphsPanel;
	private int prefH = 0;

	/**
	 * Make sure Graphs Panel and this layout have same number of components.
	 * They should also be added on correct order so they match up to each other.
	 * 
	 * @param minimumSize pixels
	 * @param graphsPanel Panel with the notegraphs. It must use GraphLayout as layout.
	 */
	ControlLayout(int minimumSize, JPanel graphsPanel) {
		if (graphsPanel == null) {
			throw new IllegalArgumentException("GraphsPanel must be non null");
		}
		this.minimumSize = minimumSize;
		this.graphsPanel = graphsPanel;
	}

	@Override
	public void addLayoutComponent(String name, Component comp) {
		if (name != null) {
			throw new IllegalArgumentException("Cannot add to layout: Unknown constraint: " + name);
		}
		components.add(comp);
	}

	@Override
	public void removeLayoutComponent(Component comp) {
		components.remove(comp);
	}

	@Override
	public Dimension minimumLayoutSize(Container parent) {
		Dimension dim = new Dimension(minimumSize + parent.getInsets().left + parent.getInsets().right, minimumSize * components.size() + parent.getInsets().top + parent.getInsets().bottom);
		return dim;
	}
	
	@Override
	public Dimension preferredLayoutSize(Container parent) {
		Dimension dim = new Dimension(0, 0);

		int width = 0;
		int height = 0;

		for (Component c : components) {
			Dimension cDim = c.getPreferredSize();
			height += cDim.height;
			width += cDim.width;
		}

		dim.width = width;
		dim.height = height;

		Insets insets = parent.getInsets();
		dim.width += insets.left + insets.right;
		dim.height += insets.top + insets.bottom;

		return dim;
	}

	@Override
	public void layoutContainer(Container target) {
		Insets insets = target.getInsets();
		int north = insets.top;
		int south = target.getSize().height - insets.bottom;
		int west = insets.left;
		int east = target.getSize().width - insets.right;

		int widestWidth = 0;

		for (Component c : components) {
			Dimension cDim = c.getPreferredSize();
			if (cDim.width > widestWidth) {
				widestWidth = cDim.width;
			}
		}

		int y = north;

		for (Component c : components) {
			if (c.isVisible()) {
				Dimension cDim = c.getPreferredSize();
				if (cDim.width > widestWidth) {
					widestWidth = cDim.width;
				}
				int height = (int) (cDim.height * zoomV);
				c.setSize(widestWidth, height);
				c.setBounds(west, y, widestWidth, height);
				y += height;
			}
		}
		prefH  = y + insets.bottom; 
		graphsPanel.invalidate();
		graphsPanel.repaint();
	}
	
	/**
	 * 
	 * @param zoomV Must be equal to or larger than 1.0
	 */
	public void setZoomVertical(float zoomV) {
		if (zoomV < 1.0f) return;
		this.zoomV = zoomV;
	}
	
	public float getZoomVertical() {
		return zoomV;
	}
	
	/**
	 * Used by GraphLayout.
	 */
	public int getPreferredHeight() {
		return prefH;
	}
	
	/**
	 * Used by GraphLayout.
	 */
	public int getSize(int componentIndex) {
		if (componentIndex >= components.size()) {
			return 0;
		}
		return components.get(componentIndex).getSize().height;
	}
	
	/**
	 * Used by GraphLayout.
	 */
	public int getPos(int componentIndex) {
		if (componentIndex >= components.size()) {
			return 0;
		}
		return components.get(componentIndex).getBounds().y;
	}
}