package com.digero.maestro.view;

import com.digero.common.view.ColorTable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PanVisualizerPanel extends JPanel {
    // This class was made in collaboration with Gemini 3

    // Visual Config
    private static final int BASE_RADIUS = 80;
    private static final int DOT_SIZE = 24;
    private static final int STACK_STEP = 26; // How much to move out per overlap

    // Color Palette
    private static final Color COL_ACTIVE = ColorTable.CONTROLS_EDITED.get();     // Light Green (Manual)
    private static final Color COL_USER   = ColorTable.PAN_USER.get();       // Bright Yellow (Gold)
    private static final Color COL_AUTO   = ColorTable.PAN_AUTO.get(); // Dull Grey (Auto)
    private static final Color COL_TEXT   = ColorTable.PAN_TEXT.get();
    private static final Color COL_TEXT_ACTIVE = ColorTable.PAN_TEXT_ACTIVE.get();
    private static final Color COL_STEM   = ColorTable.PAN_STEM.get(); // Connector line color

    // State
    private Integer activePan = null;
    private String activeLabel = "1";
    private List<PartInfo> otherParts;

    public PanVisualizerPanel() {
        setOpaque(false);
        setPreferredSize(new Dimension(220, 120));
    }

    public void updateState(int currentPan, String label, List<PartInfo> others) {
        this.activePan = currentPan;
        this.activeLabel = label;
        this.otherParts = others;
        repaint();
    }

    public void clearState(Integer activePan) {
        this.activePan = activePan;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int cx = getWidth() / 2;
        int cy = getHeight() - 20;

        // 1. Draw Arc Track
        g2.setColor(new Color(60, 60, 60, 100));
        g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawArc(cx - BASE_RADIUS, cy - BASE_RADIUS, BASE_RADIUS * 2, BASE_RADIUS * 2, 15, 150);

        // 2. Draw Listener Head
        g2.setColor(Color.LIGHT_GRAY);
        g2.fillOval(cx - 10, cy - 10, 20, 20);

        // 3. Prepare and Sort Background Parts
        List<DrawCmd> drawList = new ArrayList<>();
        if (otherParts != null) {
            for (PartInfo p : otherParts) {
                // Skip the active part (we draw it last, floating on top)
                if (activePan != null && p.label().equals(activeLabel)) continue;
                drawList.add(new DrawCmd(p, false));
            }
        }

        // Sort by Pan position so we can detect neighbors easily
        drawList.sort(Comparator.comparingInt(cmd -> cmd.part.pan));

        // 4. Calculate Stacking (Radius Jitter)
        // If two parts are close, move the second one further out
        for (int i = 0; i < drawList.size(); i++) {
            DrawCmd current = drawList.get(i);

            // Check previous items to find stack level
            // We check the last few items in case there is a cluster of 3 or 4
            if (i > 0) {
                DrawCmd prev = drawList.get(i - 1);

                // Threshold: If within ~8 pan units, they visually overlap
                int dist = Math.abs(current.part.pan - prev.part.pan);

                if (dist < 8) {
                    // Collision! Stack on top of the neighbor
                    current.stackLevel = prev.stackLevel + 1;
                }
            }
        }

        // 5. Draw Background Parts
        for (DrawCmd cmd : drawList) {
            Color c = cmd.part.userPanned() ? COL_USER : COL_AUTO;
            int radius = BASE_RADIUS + (cmd.stackLevel * STACK_STEP);

            drawPartCircle(g2, cx, cy, cmd.part.pan(), cmd.part.label(), c, false, radius);
        }

        // 6. Draw Active Part (Always on top, always on the main track)
        if (activePan != null) {
            drawPartCircle(g2, cx, cy, activePan, activeLabel, COL_ACTIVE, true, BASE_RADIUS);
        }
    }

    private void drawPartCircle(Graphics2D g2, int cx, int cy, int pan, String label, Color color, boolean isActive, int radius) {
        // Map Pan (0-127) to Angle (165 to 15 degrees)
        double angleDeg = 165.0 - (pan * (150.0 / 127.0));
        double angleRad = Math.toRadians(angleDeg);

        // Calculate Position
        int x = cx + (int) (Math.cos(angleRad) * radius);
        int y = cy - (int) (Math.sin(angleRad) * radius);

        // If stacked (radius > BASE), draw a connecting "Stem" line to the track
        if (radius > BASE_RADIUS) {
            int baseX = cx + (int) (Math.cos(angleRad) * BASE_RADIUS);
            int baseY = cy - (int) (Math.sin(angleRad) * BASE_RADIUS);

            g2.setColor(COL_STEM);
            g2.setStroke(new BasicStroke(1f));
            g2.drawLine(baseX, baseY, x, y);
        }

        int size = isActive ? DOT_SIZE + 6 : DOT_SIZE;
        int offset = size / 2;

        // Draw Shadow/Glow if active
        if (isActive) {
            g2.setColor(new Color(0, 0, 0, 60));
            g2.fillOval(x - offset + 3, y - offset + 3, size, size);
        }

        // Draw Circle
        g2.setColor(color);
        g2.fillOval(x - offset, y - offset, size, size);

        // Draw Border
        if (!isActive) {
            g2.setColor(new Color(0, 0, 0, 50));
            g2.setStroke(new BasicStroke(1f));
            g2.drawOval(x - offset, y - offset, size, size);
        }

        // Draw Text
        g2.setColor(isActive ? COL_TEXT_ACTIVE : COL_TEXT);
        g2.setFont(new Font("SansSerif", Font.BOLD, isActive ? 11 : 10));
        FontMetrics fm = g2.getFontMetrics();
        int tx = x - (fm.stringWidth(label) / 2);
        int ty = y + (fm.getAscent() / 2) - 1;
        g2.drawString(label, tx, ty);
    }

    // Helper class for rendering logic
    private static class DrawCmd {
        PartInfo part;
        int stackLevel = 0; // 0 = On the Track, 1 = Step Out, 2 = Step Out More...

        DrawCmd(PartInfo part, boolean active) {
            this.part = part;
        }
    }

    // Helper record
    public record PartInfo(int pan, String label, boolean userPanned) {}
}