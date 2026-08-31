package com.digero.common.util;

import com.digero.common.midi.SynthesizerFactory;
import com.digero.common.view.UIText;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
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
    // Do not download sha256 dynamically for security reasons, keep it hardcoded.
    private static final String SF2_URL = "https://github.com/NikolaiVChr/mver/releases/download/v4.6.24/LotroInstruments.sf2";
    private static final String EXPECTED_SHA256 = "00de443b4e2e80c973a458a7e35c6271bd57b639e07c25fd52fdce13909c3e80";

    // We append the first 8 chars of the hash to the filename to support multiple versions side-by-side
    // so users of multiple zip versions also don't have to download for each release.
    private static final String SF2_FILENAME = "LotroInstruments_" + EXPECTED_SHA256.substring(0, 8) + ".sf2";

    private static class ResultObject {
        public volatile boolean success = false;
    }

    /**
     * Ensures the SoundFont exists in the central app data folder.
     * @return The File object pointing to the ready-to-use SoundFont, or null if failed/cancelled.
     */
    public static File ensureSoundFontExists() {
        if (SynthesizerFactory.userOwnSoundFontExist()) {
            // user has put their own sound font into the app folder, use that instead of downloading
            log.info("SoundFont found in app folder.");
            return null;
        }

        File dataDir = getCommonDataDirectory();
        if (dataDir == null) {
            log.info("Common Maestro folder not found.");
            return null;
        }
        File sf2File = new File(dataDir, SF2_FILENAME);

        // Check if the file exists and has the correct size (fast check)
        if (sf2File.exists() && sf2File.length() > 0) {
            // relying on the filename (which contains the hash) + file existence should be enough.
            return sf2File;
        }

        // Not found. Show UI and download
        if (showDownloadDialog(sf2File)) {
            return sf2File;
        }
        return null;
    }

    /**
     * Determines the cross-platform central storage directory.
     * Windows: %LocalAppData%/MaestroCommon
     * Linux:   ~/.local/share/maestro-common
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
            if (!madeDirs) {
                log.severe("Failed to create common Maestro folder");
                return null;
            }
        }
        return dir;
    }

    private static boolean showDownloadDialog(File targetFile) {
        final JDialog[] dialog = new JDialog[1];
        final JLabel[] statusLabel = new JLabel[1];
        final JProgressBar[] progressBar = new JProgressBar[1];
        final ResultObject result = new ResultObject();
        final Thread[] downloadThread = new Thread[1];
        final File[] tempFile = {null};
        try {
            SwingUtilities.invokeAndWait(() -> {
                dialog[0] = new JDialog((Frame) null, UIText.get("common.soundfont.lotro.soundbank"), true);
                dialog[0].setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                dialog[0].setLayout(new BorderLayout(10, 10));

                statusLabel[0] = new JLabel(UIText.get("common.soundfont.downloading.soundfont.200.mb.please.wait"));
                statusLabel[0].setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
                statusLabel[0].setHorizontalAlignment(SwingConstants.CENTER);

                progressBar[0] = new JProgressBar(0, 100);
                progressBar[0].setMinimumSize(new Dimension(300, 40));
                progressBar[0].setStringPainted(true);
                progressBar[0].setIndeterminate(false);
                progressBar[0].setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

                JPanel btnPanel = new JPanel();
                JButton cancelButton = new JButton(UIText.get("common.soundfont.cancel"));
                btnPanel.add(cancelButton);

                dialog[0].add(statusLabel[0], BorderLayout.NORTH);
                dialog[0].add(progressBar[0], BorderLayout.CENTER);
                dialog[0].add(btnPanel, BorderLayout.SOUTH);

                // Use temporary file to prevent corruption of an existing file on partial download
                tempFile[0] = new File(targetFile.getAbsolutePath() + ".tmp");

                Runnable onCancel = () -> {
                    if (downloadThread[0] != null && downloadThread[0].isAlive()) {
                        downloadThread[0].interrupt();
                    }
                    dialog[0].dispose();
                };

                dialog[0].addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        onCancel.run();
                    }
                    @Override
                    public void windowOpened(WindowEvent e) {
                        if (downloadThread[0] != null) {
                            downloadThread[0].start();
                        }
                    }
                });

                cancelButton.addActionListener(e -> {
                    log.info("Cancelling downloading of soundfont");
                    onCancel.run();
                });
            });
        } catch (InterruptedException | InvocationTargetException e3) {
            log.info("Downloading soundfont window building failed.");
            return false;
        }

        if (dialog[0] == null || tempFile[0] == null) return false;

        log.info("Downloading soundfont to shared location");

        // Background worker thread
        downloadThread[0] = new Thread(() -> {
            boolean keepTrying = true;
            try {
                while (keepTrying) {
                    try {

                        downloadFile(SF2_URL, tempFile[0], progressBar[0], statusLabel[0]);

                        // Verify hash immediately after download
                        SwingUtilities.invokeLater(() -> {
                            statusLabel[0].setText(UIText.get("common.soundfont.verifying.integrity"));
                            progressBar[0].setString(UIText.get("common.soundfont.verifying"));
                            progressBar[0].setIndeterminate(true);
                        });
                        if (verifyChecksum(tempFile[0].toPath(), EXPECTED_SHA256)) {
                            // Rename tmp to actual
                            Files.move(tempFile[0].toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            result.success = true;
                            keepTrying = false;
                            SwingUtilities.invokeLater(dialog[0]::dispose);
                        } else {
                            Files.deleteIfExists(tempFile[0].toPath());
                            throw new IOException("Downloaded file corrupted (Checksum mismatch)");
                        }
                    } catch (Exception e) {
                        if (e instanceof InterruptedIOException || e instanceof InterruptedException || Thread.currentThread().isInterrupted()) {
                            log.info("Download soundfont interrupted");
                            keepTrying = false; // Exit the loop silently
                            try {
                                Files.deleteIfExists(tempFile[0].toPath());
                            } catch (IOException ignored) {}
                            return;
                        }
                        log.info("Downloading soundfont failed");
                        log.log(Level.WARNING, "Download failed", e);
                        try {
                            Files.deleteIfExists(tempFile[0].toPath());
                        } catch (IOException ignored) {
                        }
                        Object[] options = {UIText.get("common.soundfont.retry"), UIText.get("common.soundfont.quit"), UIText.get("common.soundfont.continue")};
                        int[] option = {JOptionPane.CLOSED_OPTION};
                        try {
                            SwingUtilities.invokeAndWait(() -> {
                                option[0] = JOptionPane.showOptionDialog(dialog[0],
                                        UIText.get("common.soundfont.download.failed.0", e.getMessage()),
                                        UIText.get("common.soundfont.download.error"),
                                        JOptionPane.YES_NO_OPTION,
                                        JOptionPane.ERROR_MESSAGE,
                                        null,
                                        options,
                                        options[0]);
                            });
                        } catch (InterruptedException | InvocationTargetException ex) {
                            option[0] = JOptionPane.CLOSED_OPTION;
                        }
                        if (option[0] == 2) {
                            // continue
                            log.info("Continue without soundfont");
                            keepTrying = false;
                            SwingUtilities.invokeLater(dialog[0]::dispose);
                        } else if (option[0] == 1 || option[0] == JOptionPane.CLOSED_OPTION) {
                            // quit
                            log.info("Quitting");
                            keepTrying = false;
                            System.exit(0);
                        } else {
                            // retry
                            log.info(UIText.get("common.soundfont.retrying.downloading.soundfont.to.shared.location"));
                            SwingUtilities.invokeLater(() -> {
                                progressBar[0].setIndeterminate(false);
                                progressBar[0].setValue(0);
                                statusLabel[0].setText(UIText.get("common.soundfont.retrying"));
                            });
                        }
                    }
                }
            } finally {
                // Ensure dialog is always closed if thread dies
                if (dialog[0] != null) SwingUtilities.invokeLater(dialog[0]::dispose);
            }
        });
        downloadThread[0].setDaemon(true);//tell the OS that this thread should be closed with the app.
        try {
            SwingUtilities.invokeAndWait(() ->{
                dialog[0].pack();
                dialog[0].setLocationRelativeTo(null);//should be after pack to center properly
                dialog[0].setVisible(true); // blocks until dispose(), but 2nd EDT thread takes over rendering of the dialog while this waits.
            });
        } catch (InterruptedException | InvocationTargetException e) {
            result.success = false;
            log.info("Downloading soundfont window display interrupted");
            downloadThread[0].interrupt();
            try {
                Files.deleteIfExists(tempFile[0].toPath());
            } catch (IOException ignored) {}
        }

        log.info("Downloading soundfont success: " + result.success);

        return result.success;
    }

    private static void downloadFile(String url, File tempFile, JProgressBar progressBar, JLabel statusLabel) throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();) {

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30)) // Add read timeout
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
            throw new RuntimeException("SHA-256 algorithm not found, downloaded soundfont cannot be verified.", e);
        }
    }
}