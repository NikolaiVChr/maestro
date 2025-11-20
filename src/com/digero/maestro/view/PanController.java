package com.digero.maestro.view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class PanController {
    private JWindow popupWindow;
    private final PanVisualizerPanel visualizer;
    private final Timer fadeTimer;
    private float currentOpacity = 1.0f;

    public PanController(JSlider slider, PanVisualizerPanel visualizer) {
        this.visualizer = visualizer;

        fadeTimer = new Timer(50, e -> {
            currentOpacity -= 0.1f;
            if (currentOpacity <= 0.0f) {
                popupWindow.setVisible(false);
                visualizer.clearState(null);

                ((Timer) e.getSource()).stop();
            } else {
                popupWindow.setOpacity(currentOpacity);
            }
        });

        // Attach Listeners
        slider.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {

                if (popupWindow == null) {
                    Window owner = SwingUtilities.getWindowAncestor(slider);
                    popupWindow = new JWindow(owner);
                    popupWindow.add(visualizer);
                    popupWindow.setSize(290, 150);
                    popupWindow.setOpacity(1.0f);
                }

                if (fadeTimer.isRunning()) {
                    fadeTimer.stop();
                }

                currentOpacity = 1.0f;
                popupWindow.setOpacity(1.0f);

                updatePopupPosition(slider);
                visualizer.clearState(null);
                popupWindow.setVisible(true);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                // start the fade out
                fadeTimer.setInitialDelay(500);
                fadeTimer.restart();
            }
        });

        slider.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                updatePopupPosition(slider); // Keep it attached if slider moves
            }
        });
    }
    
    private void updatePopupPosition(JSlider slider) {
         java.awt.Point p = slider.getLocationOnScreen();
         popupWindow.setLocation(p.x - (popupWindow.getWidth()/2) + (slider.getWidth()/2), 
                                 p.y + slider.getHeight());
    }
}