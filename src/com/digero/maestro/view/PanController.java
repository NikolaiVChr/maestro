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

        slider.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {

                if (popupWindow == null) {
                    Window owner = SwingUtilities.getWindowAncestor(slider);
                    popupWindow = new JWindow(owner);
                    popupWindow.add(visualizer);
                    /*
                        parts = 25 diam
                        track = 80 radius
                        bottom = 20 buffer
                        Vertical: room for 4 parts stacked means 3.5 at top + buffer + track_radius
                        Horiz:   room for 4 parts stacked on each side means 7 parts + track_diam
                        7 x 25 + 80 x 2 = 335 wide. 80 + 3.5 x 25 + 20 = 188 high.
                     */
                    popupWindow.setSize(335, 200);
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