package com.digero.maestro.view;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.util.ArrayList;
import java.util.List;

public class GraphLayout implements LayoutManager {
	private List<Component> components = new ArrayList<>();
	private int minimumSize;
	private float zoomH = 1.0f;
	private ControlLayout controlLayout;
	
	/**
	 * Make sure controlLayout and this layout have same number of components.
	 * They should also be added on correct order so they match up to each other.
	 * The top and bottom insets should match those from controlLayout.
	 * 
	 * @param minimumSize pixels
	 * @param controlLayout
	 */
	GraphLayout(int minimumSize, ControlLayout controlLayout) {
		if (controlLayout == null) {
			throw new IllegalArgumentException("ControlLayout must be non null");
		}
		this.minimumSize = minimumSize;
		this.controlLayout = controlLayout;
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
		Dimension pDim = parent.getSize();

		dim.width = pDim.width;
		dim.height = controlLayout.getPreferredHeight();

		return dim;
	}

	@Override
	public void layoutContainer(Container target) {
		Insets insets = target.getInsets();
		int north = insets.top;
		int south = target.getSize().height - insets.bottom;
		int west = insets.left;
		int east = target.getSize().width - insets.right;

		int width = (int) (target.getSize().width * zoomH);

		for (int i = 0; i < components.size(); i++) {
			Component c = components.get(i);
			int y = controlLayout.getPos(i);
			int height = controlLayout.getSize(i);
			c.setSize(width, height);
			c.setBounds(west, y, width, height);
		}
	}
	
	/**
	 * 
	 * @param zoomH Must be equal to or larger than 1.0
	 */
	public void setZoomHorizontal(float zoomH) {
		if (zoomH < 1.0f) return;
		this.zoomH = zoomH;
	}
	
	public float getZoomHorizontal() {
		return zoomH;
	}
}