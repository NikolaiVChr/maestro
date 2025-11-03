package com.digero.common.icons;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.logging.Logger;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

public class IconLoader {
    private static final Logger log = Logger.getLogger("file");

	public static ImageIcon getImageIcon(String name) {
        URL icon = IconLoader.class.getResource(name);
        if (icon == null) return null;
		return new ImageIcon(icon);
	}

    public static BufferedImage getImage(String name) throws IOException {
        InputStream url = IconLoader.class.getResourceAsStream(name);
        if (url == null) {
            throw new IOException("Resource not found: " + name);
        }
        return ImageIO.read(url);
    }

	public static URL getUrl(String name) {
		return IconLoader.class.getResource(name);
	}

	public static ImageIcon getDisabledIcon(String name) {
		try {
            URL icon = IconLoader.class.getResource(name);
            if (icon == null) return null;
			BufferedImage img = ImageIO.read(icon);
			int width = img.getWidth();
			int height = img.getHeight();
			int[] argbArray = new int[width];
			float[] hsb = null;
			final int H = 0;
			final int S = 1;
			final int B = 2;
			for (int y = 0; y < height; y++) {
				img.getRGB(0, y, width, 1, argbArray, 0, width);
				for (int x = 0; x < width; x++) {
					int argb = argbArray[x];
					int r = (argb >>> 16) & 0xFF;
					int g = (argb >>> 8) & 0xFF;
					int b = (argb >>> 0) & 0xFF;

					hsb = Color.RGBtoHSB(r, g, b, hsb);
					hsb[S] = 0.0f;
					final float c = 0.5f;
					final float d = 0.1f;
					hsb[B] = (c - d) + (1 - c) * hsb[B];

					argbArray[x] = (argb & 0xFF000000) | (Color.HSBtoRGB(hsb[H], hsb[S], hsb[B]) & 0x00FFFFFF);
				}
				img.setRGB(0, y, width, 1, argbArray, 0, width);
			}
			return new ImageIcon(img);
		} catch (IOException e) {
			assert false;
			log.warning("Failed to load disabled icon: " + name);
			return getImageIcon(name);
		}
	}
}
