package com.musicplayer.utility;

import com.musicplayer.library.Song;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Generates real album-art style cover images for offline tracks.
 *
 * A square PNG is rendered per song with Java2D (gradient + highlight ring +
 * bold initial watermark) and cached under {@code ./covers/cover_<songId>.png}.
 * When writing is not possible the image is still produced in memory, so the
 * UI shows a cover everywhere and never falls back to an empty flat chip.
 */
public final class CoverArt {

    public static final int SIZE = 600;

    private static final Map<Integer, Image> IMAGE_CACHE = new HashMap<>();
    private static File coverDir;

    private CoverArt() {
    }

    private static File getCoverDir() {
        if (coverDir == null) {
            try {
                File projectDir = new File(System.getProperty("user.dir"));
                File dir = new File(projectDir, "covers");
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                coverDir = dir;
            } catch (Exception e) {
                coverDir = new File(".");
            }
        }
        return coverDir;
    }

    /** Returns the cover image for a song, generating and caching it if needed. */
    public static Image getCover(Song song) {
        if (song == null) {
            return null;
        }
        return IMAGE_CACHE.computeIfAbsent(song.getSongId(), id -> render(song));
    }

    /** File on disk for a song, or null when rendering/writing failed. */
    public static File getCoverFile(Song song) {
        if (song == null) {
            return null;
        }
        return new File(getCoverDir(), "cover_" + song.getSongId() + ".png");
    }

    private static Image render(Song song) {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            Color base = parseColor(song.getCoverColor(), "#38BDF8");
            Color dark = darken(base, 0.45f);
            Color light = lighten(base, 0.25f);

            boolean diagonal = (song.getSongId() % 2) == 0;
            GradientPaint gradient = diagonal
                    ? new GradientPaint(0, 0, dark, SIZE, SIZE, light)
                    : new GradientPaint(SIZE, 0, dark, 0, SIZE, light);
            g.setPaint(gradient);
            g.fillRect(0, 0, SIZE, SIZE);

            g.setPaint(new Color(255, 255, 255, 24));
            for (int i = 0; i < 3 + (song.getSongId() % 3); i++) {
                int size = 220 + (song.getSongId() % 5) * 40;
                g.fillOval(-60 - i * 80, 160 + i * 70, size, size);
            }

            g.setColor(new Color(255, 255, 255, 70));
            g.setStroke(new java.awt.BasicStroke(6f));
            g.draw(new Ellipse2D.Double(26, 26, SIZE - 52, SIZE - 52));

            String initial = initial(song.getSongName());
            g.setFont(new Font("Segoe UI", Font.BOLD, 270));
            FontMetrics fm = g.getFontMetrics();
            int x = (SIZE - fm.stringWidth(initial)) / 2;
            int y = (SIZE - fm.getHeight()) / 2 + fm.getAscent();
            g.setColor(new Color(255, 255, 255, 46));
            g.drawString(initial, x + 6, y + 6);
            g.setColor(new Color(255, 255, 255, 210));
            g.drawString(initial, x, y);
        } finally {
            g.dispose();
        }

        try {
            File file = getCoverFile(song);
            if (file != null) {
                ImageIO.write(image, "png", file);
            }
        } catch (Exception ignored) {
            // Rendering is still returned; writing is best-effort.
        }

        return fromBufferedImage(image);
    }

    private static Image fromBufferedImage(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int[] pixels = image.getRGB(0, 0, w, h, new int[w * h], 0, w);
        WritableImage target = new WritableImage(w, h);
        target.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
        return target;
    }

    private static String initial(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "\u266A";
        }
        String trimmed = name.trim();
        char c = trimmed.charAt(0);
        return Character.isLetterOrDigit(c) ? String.valueOf(Character.toUpperCase(c)) : "\u266A";
    }

    private static Color parseColor(String value, String fallback) {
        try {
            return Color.decode(value == null || value.isBlank() ? fallback : value);
        } catch (NumberFormatException e) {
            return Color.decode(fallback);
        }
    }

    private static Color darken(Color c, float amount) {
        return new Color(
                Math.max(0, (int) (c.getRed() * (1 - amount))),
                Math.max(0, (int) (c.getGreen() * (1 - amount))),
                Math.max(0, (int) (c.getBlue() * (1 - amount))));
    }

    private static Color lighten(Color c, float amount) {
        float sum = 1 - amount;
        return new Color(
                Math.min(255, (int) (c.getRed() * sum + 255 * amount)),
                Math.min(255, (int) (c.getGreen() * sum + 255 * amount)),
                Math.min(255, (int) (c.getBlue() * sum + 255 * amount)));
    }
}