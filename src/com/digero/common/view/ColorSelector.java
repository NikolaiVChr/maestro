package com.digero.common.view;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.xml.transform.TransformerException;

import com.digero.common.util.Util;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.digero.common.util.ExtensionFileFilter;
import com.digero.maestro.util.XmlUtil;

/**
 * Panel for configuring colors.
 * Does not get persisted automatically.
 * But user can import/export.
 */
public class ColorSelector extends JPanel {
    private static final Logger log = Logger.getLogger("view.color");

    private final Map<ColorTable, Color> originalState = new EnumMap<>(ColorTable.class);
    private final List<ColorRow> rows = new ArrayList<>();
    
    private JComboBox<ColorTable> bgCombo;
    private JComboBox<ColorTable> fgCombo;
    private JLabel previewLabel;
    private JLabel ratioLabel;

    private File lastDir = Util.getDocumentsDir();

    public ColorSelector() {
        super(new BorderLayout());

        setPreferredSize(new Dimension(900, 700));
        setMinimumSize(new Dimension(300, 300));

        


        JScrollPane scrollPane = new JScrollPane(createListPanel());
        scrollPane.getVerticalScrollBar().setUnitIncrement(40);

        add(createContrastPanel(), BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(createButtonPanel(), BorderLayout.SOUTH);
        
        // Init contrast checker
        bgCombo.setSelectedItem(ColorTable.GRAPH_BACKGROUND_ENABLED);
        fgCombo.setSelectedItem(ColorTable.NOTE_ENABLED);
        updateContrastPreview();

        String initTheme = Preferences.userNodeForPackage(ColorTable.class).get("themeFile", "");
        if (!initTheme.isEmpty()) {
            File file = new File(initTheme);
            log.info("Attempting to load theme from " + file.getName());
            try {
                loadTheme(file);
            } catch (Exception e) {
                log.info("Failed to load init color theme. Will not attempt again next startup.");
                Preferences.userNodeForPackage(ColorTable.class).remove("themeFile");
            }
        }

        for (ColorTable ct : ColorTable.values()) {
            // remember how the colors were when the user started Maestro
            originalState.put(ct, ct.get());
        }
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        
        JButton importBtn = new JButton("Import Theme XML");
        importBtn.setToolTipText("Load colors from file. Will only change the colors contained in the file.");
        importBtn.addActionListener(e -> importTheme());
        
        JButton exportBtn = new JButton("Export Theme XML");
        exportBtn.setToolTipText("<html>Save colors to file (only saves the modified colors)." +
                "<br>Note: Color IDs 'might' change in future Maestro versions. (then they will be ignored when importing)</html>");
        exportBtn.addActionListener(e -> exportTheme());

        panel.add(importBtn);
        panel.add(exportBtn);
        panel.add(new JLabel("Export to apply permanently!"));
        return panel;
    }

    private JPanel createContrastPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Contrast & Visibility"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;

        bgCombo = new JComboBox<>(ColorTable.values());
        fgCombo = new JComboBox<>(ColorTable.values());

        bgCombo.addActionListener(e -> updateContrastPreview());
        fgCombo.addActionListener(e -> updateContrastPreview());

        previewLabel = new JLabel("Preview Text: The quick hobbit eats the whole pie", SwingConstants.CENTER) {
            @Override
            protected void paintComponent (Graphics g){
                // If the bg color is transparent, fill with white
                if (getBackground().getAlpha() < 255) {
                    g.setColor(Color.WHITE);//I'm too lazy to make a checkerboard pattern
                    g.fillRect(0, 0, getWidth(), getHeight());
                }

                // Paint the selected background color on top
                g.setColor(getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());

                // draw text
                super.paintComponent(g);
            }
        };
        previewLabel.setOpaque(false);
        previewLabel.setPreferredSize(new Dimension(300, 40));
        previewLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        
        ratioLabel = new JLabel("Ratio: ");
        ratioLabel.setFont(ratioLabel.getFont().deriveFont(Font.BOLD));

        c.gridx = 0; c.gridy = 0;
        panel.add(new JLabel("Background:"), c);
        c.gridx = 1;
        panel.add(bgCombo, c);
        
        c.gridx = 2; c.gridy = 0;
        panel.add(new JLabel("Foreground:"), c);
        c.gridx = 3;
        panel.add(fgCombo, c);

        c.gridx = 0; c.gridy = 1; c.gridwidth = 4;
        panel.add(previewLabel, c);
        
        c.gridy = 2;
        panel.add(ratioLabel, c);

        return panel;
    }

    private JPanel createListPanel() {
        JPanel list = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 5, 2, 5);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        
        int rowIdx = 0;
        for (ColorTable ct : ColorTable.values()) {
            ColorRow row = new ColorRow(ct);
            rows.add(row);

            c.gridy = rowIdx++;
            
            c.gridx = 0; c.weightx = 0d;
            list.add(row.editColorButton, c);
            
            c.gridx = 1; c.weightx = 1.0d;
            list.add(row.label, c);
            
            c.gridx = 2; c.weightx = 0d;
            list.add(row.resetBtn, c);
        }
        
        // Push everything up
        GridBagConstraints filler = new GridBagConstraints();
        filler.gridy = rowIdx;
        filler.weighty = 1.0d;
        list.add(new JPanel(), filler);
        
        return list;
    }

    private void updateContrastPreview() {
        ColorTable bgEnum = (ColorTable) bgCombo.getSelectedItem();
        ColorTable fgEnum = (ColorTable) fgCombo.getSelectedItem();
        
        if (bgEnum == null || fgEnum == null) return;

        Color bg = bgEnum.get();
        Color fg = fgEnum.get();

        previewLabel.setBackground(bg);
        previewLabel.setForeground(fg);

        double ratio = calculateContrastRatio(fg, bg);
        //String ratioStr = String.format("%.2f", ratio);
        
        String grading = " (Poor)";
        if (ratio >= 7.0d) grading = " (High)";
        else if (ratio >= 4.5d) grading = " (Good)";
        else if (ratio >= 3.0d) grading = " (Pass)";
        
        ratioLabel.setText("Contrast: " + grading);
        
        if (ratio >= 4.5d) ratioLabel.setForeground(new Color(0, 128, 0));
        else if (ratio >= 3.0d) ratioLabel.setForeground(new Color(150, 100, 0));
        else ratioLabel.setForeground(Color.RED);
    }

    private static double getLuminance(Color color) {
        double r = color.getRed() / 255.0d;
        double g = color.getGreen() / 255.0d;
        double b = color.getBlue() / 255.0d;

        r = (r <= 0.03928d) ? r / 12.92d : Math.pow((r + 0.055d) / 1.055d, 2.4d);
        g = (g <= 0.03928d) ? g / 12.92d : Math.pow((g + 0.055d) / 1.055d, 2.4d);
        b = (b <= 0.03928d) ? b / 12.92d : Math.pow((b + 0.055d) / 1.055d, 2.4d);

        return 0.2126d * r + 0.7152d * g + 0.0722d * b;
    }

    /**
     * Apply the alpha to the foreground so that the contrast ratio considers alpha also.
     */
    private static Color blend(Color fg, Color bg) {
        double alpha = fg.getAlpha() / 255.0d;
        double invAlpha = 1.0d - alpha;

        int r = (int) ((fg.getRed() * alpha) + (bg.getRed() * invAlpha));
        int g = (int) ((fg.getGreen() * alpha) + (bg.getGreen() * invAlpha));
        int b = (int) ((fg.getBlue() * alpha) + (bg.getBlue() * invAlpha));

        return new Color(r, g, b);
    }

    private static double calculateContrastRatio(Color fg, Color bg) {
        // If the background itself is transparent, assume it sits on White
        Color solidBg = (bg.getAlpha() < 255) ? blend(bg, Color.WHITE) : bg;
        Color solidFg = blend(fg, solidBg);

        double l1 = getLuminance(solidFg);
        double l2 = getLuminance(solidBg);
        return (Math.max(l1, l2) + 0.05d) / (Math.min(l1, l2) + 0.05d);
    }

    public void cancel() {
        for (Map.Entry<ColorTable, Color> entry : originalState.entrySet()) {
            entry.getKey().set(entry.getValue());
        }
        refreshUI();
    }
    
    public void resetToDefaults() {
        for (ColorTable ct : ColorTable.values()) {
            ct.set(ct.getDefaultValue());
        }
        refreshUI();
    }

    public void refreshUI() {
        for (ColorRow row : rows) {
            row.updateFromEnum();
        }
        updateContrastPreview();
        repaint();
    }

    private void exportTheme() {
        JFileChooser jfc = new JFileChooser();
        jfc.setDialogTitle("Export Theme");
        ExtensionFileFilter filter = new ExtensionFileFilter("Maestro Theme (*"+Util.THEME_FILE_EXTENSION+")", Util.THEME_FILE_EXTENSION_NO_DOT);
        jfc.setFileFilter(filter);
        jfc.setAcceptAllFileFilterUsed(false);
        jfc.setCurrentDirectory(lastDir);
        
        if (jfc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = jfc.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(Util.THEME_FILE_EXTENSION)) {
                file = new File(file.getParent(), file.getName() + Util.THEME_FILE_EXTENSION);
            }

            if(file.exists()) {
                int result = JOptionPane.showConfirmDialog(this, "File exist. Overwrite?", "Overwrite", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (result != JOptionPane.YES_OPTION) {
                    return;
                }
            }
            
            try {
                saveTheme(file);
                lastDir = file.getParentFile();
                int result = JOptionPane.showConfirmDialog(this, "Do you want to apply this theme every time Maestro start?", "Theme exported successfully", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (result == JOptionPane.YES_OPTION) {
                    Preferences.userNodeForPackage(ColorTable.class).put("themeFile", file.getAbsolutePath());
                }
            } catch (Exception e) {
                log.log(Level.SEVERE, "Failed to export theme", e);
                JOptionPane.showMessageDialog(this, "Error saving theme: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Will overwrite existing file.
     */
    private void saveTheme(File file) throws TransformerException, IOException {
        Document doc = XmlUtil.createDocument();
        Element root = doc.createElement("MaestroTheme");
        doc.appendChild(root);

        for (ColorTable ct : ColorTable.values()) {
            if (!ct.getDefaultValue().equals(ct.get())) {
                Element colorElem = doc.createElement("Color");
                colorElem.setAttribute("id", ct.name());
                // hex ARGB
                colorElem.setAttribute("value", String.format("%08X", ct.get().getRGB()));
                root.appendChild(colorElem);
            }
        }

        XmlUtil.saveDocument(doc, file);
    }

    private void importTheme() {
        JFileChooser jfc = new JFileChooser();
        jfc.setDialogTitle("Import Theme");
        jfc.setFileFilter(new ExtensionFileFilter("Maestro Theme (*"+Util.THEME_FILE_EXTENSION+")", Util.THEME_FILE_EXTENSION_NO_DOT));
        jfc.setAcceptAllFileFilterUsed(false);
        jfc.setCurrentDirectory(lastDir);

        if (jfc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File themeFile = jfc.getSelectedFile();
                loadTheme(themeFile);
                lastDir = themeFile.getParentFile();
                JOptionPane.showMessageDialog(this, "Theme imported successfully.");
                
            } catch (Exception e) {
                log.log(Level.SEVERE, "Failed to import theme", e);
                JOptionPane.showMessageDialog(this, "Error loading theme: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void loadTheme(File themeFile) throws Exception {
        Document doc = XmlUtil.openDocument(themeFile);
        Element root = doc.getDocumentElement();

        if (!"MaestroTheme".equals(root.getTagName())) {
            throw new Exception("Not a valid Maestro color theme file.");
        }

        NodeList colorNodes = root.getElementsByTagName("Color");
        for (int i = 0; i < colorNodes.getLength(); i++) {
            Element el = (Element) colorNodes.item(i);
            String id = el.getAttribute("id");
            String val = el.getAttribute("value");
            
            try {
                ColorTable ct = ColorTable.valueOf(id);
                int argb = Integer.parseUnsignedInt(val, 16);
                ct.set(new Color(argb, true));
            } catch (Exception ignored) {
                // Skip invalid colors
            }
        }
        refreshUI();
    }

    private class ColorRow {
        final ColorTable enumVal;
        final JButton editColorButton;
        final JLabel label;
        final JButton resetBtn;
        final String text;

        ColorRow(ColorTable enumVal) {
            this.enumVal = enumVal;
            
            editColorButton = new JButton();
            editColorButton.setPreferredSize(new Dimension(40, 20));
            editColorButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            editColorButton.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
            editColorButton.addActionListener(e -> {
                Color newColor = JColorChooser.showDialog(ColorSelector.this,
                        "Edit " + enumVal.name(), enumVal.get(), true);
                if (newColor != null) {
                    enumVal.set(newColor);
                    updateFromEnum();
                    updateContrastPreview();

                    // Repaint the the dialog
                    SwingUtilities.getWindowAncestor(ColorSelector.this).repaint();

                    // Repaint maestro
                    for (Window w : Window.getWindows()) {
                        w.repaint();
                    }
                }
            });


            if (enumVal.getInfo() != null && !enumVal.getInfo().isEmpty()) {
                text = enumVal.name()+" ~ "+enumVal.getInfo();
            } else {
                text = enumVal.name();
            }
            label = new JLabel();
            label.setToolTipText(text);

            resetBtn = new JButton("Set default");
            resetBtn.setMargin(new Insets(2, 5, 2, 5));
            resetBtn.setFont(resetBtn.getFont().deriveFont(10f));
            resetBtn.addActionListener(e -> {
                enumVal.set(enumVal.getDefaultValue());
                updateFromEnum();
                updateContrastPreview();
            });
            
            updateFromEnum();
        }

        void updateFromEnum() {
            Color c = enumVal.get();
            editColorButton.setBackground(c);
            resetBtn.setVisible(!c.equals(enumVal.getDefaultValue()));

            int alpha = c.getAlpha();
            Font font;
            String labelTxt = text;
            if (alpha < 255) {
                int percent = (int)((alpha / 255.0d) * 100);

                labelTxt = String.format("%s (Alpha: %d%%)", text, percent);
                font = label.getFont().deriveFont(Font.ITALIC);
            } else {
                font = label.getFont().deriveFont(Font.PLAIN);
            }
            label.setText(labelTxt);
            label.setFont(font);
        }
    }
}