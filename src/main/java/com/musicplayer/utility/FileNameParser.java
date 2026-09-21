package com.musicplayer.utility;

import java.io.File;

/**
 * Derives song title / artist / album / genre from a file name when tag
 * metadata is not available. Handles common conventions such as
 * {@code "Artist - Title"}, {@code "01 - Title"}, leading track numbers and
 * {@code [feat ...]} markers without ever failing.
 */
public final class FileNameParser {

    private FileNameParser() {
        // Utility class - no instantiation
    }

    /** Returns metadata derived purely from the file name. */
    public static MusicMetadata fromFile(File file) {
        MusicMetadata meta = new MusicMetadata();
        if (file == null) {
            return meta;
        }

        String base = baseName(file.getName());
        String title = base;
        String artist = "Unknown Artist";
        String album = file.getParentFile() != null
                ? file.getParentFile().getName()
                : "Local Music";

        int separator = findSeparator(base);
        if (separator > 0) {
            artist = cleanName(base.substring(0, separator));
            title = base.substring(separator + 2);
        }

        // Strip leading track numbers such as "01 - ", "01. ".
        title = title.replaceFirst("^\\s*\\d{1,3}\\s*[.-]\\s*", "").trim();
        title = cleanName(title);
        if (title.isBlank()) {
            title = cleanName(base);
        }
        if (title.isBlank()) {
            title = "Untitled";
        }

        // Strip "([feat. X])", "[feat. X]" and similar markers from the title.
        title = stripFeatMarkers(title);

        meta.setTitle(title);
        meta.setArtist(cleanName(artist));
        meta.setAlbum(cleanName(album));
        meta.setGenre(guessGenre(file.getName()));
        return meta;
    }

    private static int findSeparator(String base) {
        int index = base.indexOf(" - ");
        return index > 0 ? index : -1;
    }

    private static String baseName(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static String cleanName(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('_', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String stripFeatMarkers(String title) {
        String cleaned = title.replaceAll("\\s*[\\(\\[]\\s*(feat|ft|featuring)\\..*?[\\)\\]]", "")
                .replaceAll("\\s*[\\(\\[]\\s*(feat|ft|featuring)\\s.*?[\\)\\]]", "")
                .trim();
        return cleaned.isBlank() ? title : cleaned;
    }

    /** Very light genre guess based on keywords appearing in the file name or path. */
    public static String guessGenre(String fileName) {
        if (fileName == null) {
            return "Pop";
        }
        String lower = fileName.toLowerCase();
        if (lower.contains("lofi") || lower.contains("lo-fi")) return "Lofi";
        if (lower.contains("beat")) return "Beat";
        if (lower.contains("electronic") || lower.contains("techno") || lower.contains("edm")) return "Electronic";
        if (lower.contains("acoustic")) return "Acoustic";
        if (lower.contains("ambient")) return "Ambient";
        if (lower.contains("rock")) return "Rock";
        if (lower.contains("hiphop") || lower.contains("hip-hop") || lower.contains("rap")) return "Hip-Hop";
        if (lower.contains("classical")) return "Classical";
        if (lower.contains("jazz")) return "Jazz";
        if (lower.contains("indie")) return "Indie";
        if (lower.contains("pop")) return "Pop";
        if (lower.contains("instrumental")) return "Instrumental";
        return "Pop";
    }
}