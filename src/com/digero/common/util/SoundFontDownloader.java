package com.digero.common.util;

import com.digero.common.midi.SynthesizerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SoundFontDownloader {
    protected static final Logger log = Logger.getLogger("misc.SoundFontDownloader");

    // Update URL and SHA256 if you release a new version of the soundfont
    private static final String SF2_URL = "https://github.com/NikolaiVChr/mver/releases/download/v4.5.24/LotroInstruments.sf2";
    private static final String EXPECTED_SHA256 = "3b2ef0407e3219f92a379dc8c60ec4aa1d91e532e9646a59f01b1c79e54678af";

    // We append the first 8 chars of the hash to the filename to support multiple versions side-by-side
    // so users of multiple zip versions also don't have to download for each release.
    private static final String SF2_FILENAME = "LotroInstruments_" + EXPECTED_SHA256.substring(0, 8) + ".sf2";

    /**
     * Ensures the SoundFont exists in the central app data folder.
     * @return The File object pointing to the ready-to-use SoundFont, or null if failed/cancelled.
     */
    public static File ensureSoundFontExists() {
        if (SynthesizerFactory.userOwnSoundFontExist()) {
            // user has put their own sound font into the app folder, use that instead of downloading
            return null;
        }

        File dataDir = getCommonDataDirectory();
        if (dataDir == null) return null;
        File sf2File = new File(dataDir, SF2_FILENAME);

        // Check if the file exists and has the correct size (fast check)
        if (sf2File.exists() && sf2File.length() > 0) {
            // relying on the filename (which contains the hash) + file existence should be enough.
            return sf2File;
        }

        if (showDownloadDialog(sf2File)) {
            // Not found. Show UI and download
            return sf2File;
        }
        return null;
    }

    /**
     * Determines the cross-platform central storage directory.
     * Windows: %LocalAppData%/MaestroCommon
     * Linux:   ~/.local/share/digero-common
     * Mac:     ~/Library/Application Support/MaestroCommon
     */
    public static File getCommonDataDirectory() {
        String os = System.getProperty("os.name").toLowerCase();
        Path path;

        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData == null) localAppData = System.getProperty("user.home") + "\\AppData\\Local";
            path = Paths.get(localAppData, "MaestroCommon");
        } else if (os.contains("mac")) {
            path = Paths.get(System.getProperty("user.home"), "Library", "Application Support", "MaestroCommon");
        } else {
            // Linux / Unix - Use XDG standard
            String xdgData = System.getenv("XDG_DATA_HOME");
            if (xdgData == null || xdgData.isEmpty()) {
                path = Paths.get(System.getProperty("user.home"), ".local", "share", "maestro-common");
            } else {
                path = Paths.get(xdgData, "maestro-common");
            }
        }

        File dir = path.toFile();
        if (!dir.exists()) {
            boolean madeDirs = dir.mkdirs();
            if (!madeDirs) log.warning("Failed to create directory: " + dir);
            return null;
        }
        return dir;
    }

    private static boolean showDownloadDialog(File targetFile) {
        JDialog dialog = new JDialog((Frame) null, "Downloading soundbank", true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setLocationRelativeTo(null); // Center on screen

        JLabel statusLabel = new JLabel("Downloading SoundFont (200+ MB)... please wait.");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setPreferredSize(new Dimension(300, 40));
        progressBar.setStringPainted(true);
        progressBar.setIndeterminate(false);
        progressBar.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel btnPanel = new JPanel();
        JButton cancelButton = new JButton("Cancel");
        btnPanel.add(cancelButton);

        dialog.add(statusLabel, BorderLayout.NORTH);
        dialog.add(progressBar, BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        // State object to hold the result
        var result = new Object() { boolean success = false; };

        // Use temporary file to prevent corruption of an existing file on partial download
        File tempFile = new File(targetFile.getAbsolutePath() + ".tmp");

        Thread[] downloadThread = new Thread[1];

        Runnable onCancel = () -> {
            if (downloadThread[0] != null && downloadThread[0].isAlive()) {
                downloadThread[0].interrupt();
            }
            dialog.dispose();
        };

        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onCancel.run();
            }
        });

        cancelButton.addActionListener(e -> {
            onCancel.run();
        });

        // Background worker thread
        downloadThread[0] = new Thread(() -> {
            boolean keepTrying = true;
            while (keepTrying) {
                try {

                    downloadFile(SF2_URL, tempFile, progressBar, statusLabel);
                    
                    // Verify hash immediately after download
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Verifying integrity...");
                        progressBar.setString("Verifying...");
                        progressBar.setIndeterminate(true);
                    });
                    if (verifyChecksum(tempFile.toPath(), EXPECTED_SHA256)) {
                        // Rename tmp to actual
                        Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        result.success = true;
                        keepTrying = false;
                        SwingUtilities.invokeLater(dialog::dispose);
                    } else {
                        Files.deleteIfExists(tempFile.toPath());
                        throw new IOException("Downloaded file corrupted (Checksum mismatch)");
                    }
                } catch (Exception e) {
                    if (e instanceof InterruptedIOException || e instanceof InterruptedException || Thread.currentThread().isInterrupted()) {
                        keepTrying = false; // Exit the loop silently
                        try {
                            Files.deleteIfExists(tempFile.toPath());
                        } catch (IOException ignored) {}
                        return;
                    }
                    log.log(Level.WARNING, "Download failed", e);
                    try {
                        Files.deleteIfExists(tempFile.toPath());
                    } catch (IOException ignored) {
                    }
                    Object[] options = {"Retry", "Quit", "Continue"};
                    int option = JOptionPane.showOptionDialog(dialog,
                            "Download failed:\n" + e.getMessage() + "\n\n" +
                                    "You can retry, quit the app, or continue without lotro sounds.",
                            "Download Error",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.ERROR_MESSAGE,
                            null,
                            options,
                            options[0]);

                    if (option == 2) {
                        // continue
                        keepTrying = false;
                        SwingUtilities.invokeLater(dialog::dispose);
                    } else if (option == 1 || option == JOptionPane.CLOSED_OPTION) {
                        // quit
                        keepTrying = false;
                        System.exit(0);
                    } else {
                        // retry
                        SwingUtilities.invokeLater(() -> {
                            progressBar.setIndeterminate(false);
                            progressBar.setValue(0);
                            statusLabel.setText("Retrying...");
                        });
                    }
                }
            }
        });
        downloadThread[0].setDaemon(true);//tell the OS that this thread should be closed with the app.
        downloadThread[0].start();
        dialog.pack();
        dialog.setVisible(true); // blocks until dispose()

        return result.success;
    }

    private static void downloadFile(String url, File tempFile, JProgressBar progressBar, JLabel statusLabel) throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();) {

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            // Send a request header first to get size
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                throw new IOException("Server returned HTTP " + response.statusCode());
            }

            // Captive Portal (public Wifi) returning 200 OK but sending HTML login page
            String contentType = response.headers().firstValue("content-type").orElse("").toLowerCase();
            if (contentType.contains("text/html")) {
                throw new IOException("Invalid Content-Type (HTML). You may be behind a web portal.");
            }

            long totalSize = response.headers().firstValueAsLong("content-length").orElse(-1L);
            Files.createDirectories(tempFile.getParentFile().toPath());

            try (InputStream in = response.body();
                 OutputStream out = new FileOutputStream(tempFile)) {

                byte[] buffer = new byte[8192];
                long downloaded = 0;
                int bytesRead;
                long lastUpdate = 0;

                while ((bytesRead = in.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("User cancelled");

                    out.write(buffer, 0, bytesRead);
                    downloaded += bytesRead;

                    if (downloaded > 500 * 1024 * 1024) { // Limit to 500 MB for security reasons
                        throw new IOException("File too large (exceeded 500 MB limit)");
                    }

                    // Throttle UI updates to every 100ms to avoid freezing Swing
                    long now = System.currentTimeMillis();
                    if (now - lastUpdate > 100 || downloaded == totalSize) {
                        final long current = downloaded;
                        final long total = totalSize;
                        SwingUtilities.invokeLater(() -> {
                            if (total > 0) {
                                int percent = (int) ((current * 100) / total);
                                progressBar.setValue(percent);
                                progressBar.setString(percent + "% (" + (current / 1024 / 1024) + " MB)");
                            } else {
                                progressBar.setIndeterminate(true);
                            }
                        });
                        lastUpdate = now;
                    }
                }
            }
        }
    }

    private static boolean verifyChecksum(Path file, String expectedHex) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            try (InputStream fis = Files.newInputStream(file);
                 DigestInputStream dis = new DigestInputStream(fis, digest)) {

                // Transfer all bytes to the null-output
                // This triggers the DigestInputStream to process every byte automatically.
                dis.transferTo(OutputStream.nullOutputStream());
            }

            byte[] hashBytes = digest.digest();
            String actualHex = HexFormat.of().formatHex(hashBytes);
            return actualHex.equalsIgnoreCase(expectedHex);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}