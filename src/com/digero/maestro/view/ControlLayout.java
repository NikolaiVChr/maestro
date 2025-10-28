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
	private final List<Component> components = new ArrayList<>();
	private final List<Integer> componentPos = new ArrayList<>();
	private final int minimumSize;
	private float zoomV = 1.0f;
	private final JPanel graphsPanel;
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
		if (name == null) {
			throw new IllegalArgumentException("Cannot add to layout: Unknown null constraint");
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
        int widestWidth = 0;
        int totalHeight = 0;

        for (Component c : components) {
            if (c.isVisible()) {
                Dimension cDim = c.getPreferredSize();

                // in layoutContainer
                if (cDim.width > widestWidth) {
                    widestWidth = cDim.width;
                }

                // as in layoutContainer
                int height = (int) (Math.max(minimumSize, cDim.height) * zoomV);
                if (((PartPanelItem)c).isVerticalZoomForbidden()) {
                    height = cDim.height;
                }
                totalHeight += height;
            }
        }

        Insets insets = parent.getInsets();
        widestWidth += insets.left + insets.right;
        totalHeight += insets.top + insets.bottom;

        // like layoutContainer
        prefH = totalHeight;

        return new Dimension(widestWidth, totalHeight);
    }

	@Override
	public void layoutContainer(Container target) {
		//System.out.println("Layout control "+components.size());
		Insets insets = target.getInsets();
		int north = insets.top;
		int south = target.getSize().height - insets.bottom;
		int west = insets.left;
		int east = target.getSize().width - insets.right;

		int widestWidth = 0;
		
		for (Component c : components) {
			if (c.isVisible()) {
				Dimension cDim = c.getPreferredSize();
				if (cDim.width > widestWidth) {
					widestWidth = cDim.width;
				}
			}
		}
		
		int y = north;
		componentPos.clear();
		for (Component c : components) {
			if (c.isVisible()) {
				Dimension cDim = c.getPreferredSize();
				int height = (int) (Math.max(minimumSize,cDim.height) * zoomV);
				if (((PartPanelItem)c).isVerticalZoomForbidden()) {
					height = cDim.height;
				}
				c.setSize(widestWidth, height);
				componentPos.add(y);

                c.setBounds(west, y, widestWidth, height);
				y += height;
			} else {
				// Histogram will come in here when midi preview is selected.
				componentPos.add(y);
			}
		}
		prefH  = y + insets.bottom; 
		graphsPanel.revalidate();
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
	public int getCount() {
		return components.size();
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
		if (componentIndex >= componentPos.size()) {
			return 0;
		}
		return componentPos.get(componentIndex);
	}
}