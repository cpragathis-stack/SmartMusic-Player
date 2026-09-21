package com.musicplayer.utility;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal offline reader for ID3 tags embedded in MP3 files.
 *
 * <p>Supports ID3v2.2, v2.3 and v2.4 tag frames (title/artist/album/genre/year).
 * This is intentionally small and dependency-free: it never throws for
 * malformed files and simply returns empty metadata when tags are absent.
 */
public final class Mp3TagReader {

    private Mp3TagReader() {
        // Utility class - no instantiation
    }

    /** Reads metadata from the given audio file, or empty metadata when none can be read. */
    public static MusicMetadata read(File file) {
        MusicMetadata meta = new MusicMetadata();
        if (file == null || !file.isFile()) {
            return meta;
        }

        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] header = readBytes(in, 10);
            if (header == null || header.length < 3
                    || header[0] != 'I' || header[1] != 'D' || header[2] != '3') {
                return meta;
            }

            int majorVersion = header[3] & 0xFF;
            boolean hasExtendedHeader = (header[5] & 0x40) != 0;
            int tagSize = readSyncSafeInt(header, 6);
            if (tagSize < 0 || tagSize > 16 * 1024 * 1024) {
                return meta;
            }

            // The tag body follows the 10-byte header.
            byte[] body = readBytes(in, tagSize);
            if (body == null || body.length < 2) {
                return meta;
            }

            int offset = 0;
            if (hasExtendedHeader && majorVersion >= 3) {
                int extendedSize;
                if (majorVersion == 4) {
                    extendedSize = body.length >= 4 ? readSyncSafeInt(body, 0) : 0;
                } else {
                    extendedSize = body.length >= 4 ? readInt(body, 0) : 0;
                }
                if (extendedSize > 0 && extendedSize + 4 <= body.length) {
                    offset = extendedSize + 4;
                }
            }

            Map<String, String> frames = parseFrames(body, offset, majorVersion);
            applyFrames(meta, frames);

        } catch (IOException | RuntimeException ignored) {
            // Malformed or unreadable file - keep default metadata.
        }
        return meta;
    }

    private static Map<String, String> parseFrames(byte[] body, int offset, int majorVersion) {
        Map<String, String> frames = new HashMap<>();
        int position = offset;
        int headerSize = majorVersion == 2 ? 6 : 10;

        while (position + headerSize <= body.length) {
            String frameId = new String(body, position, majorVersion == 2 ? 3 : 4, StandardCharsets.ISO_8859_1);
            boolean padding = majorVersion == 2
                    ? frameId.charAt(0) == 0
                    : frameId.charAt(0) == 0 || frameId.charAt(0) == ' ';

            if (padding) {
                break;
            }

            int frameSize;
            if (majorVersion == 4) {
                // v2.4 stores sizes as sync-safe integers.
                frameSize = readSyncSafeInt(body, position + 4);
            } else {
                frameSize = readInt(body, position + 4);
            }

            if (frameSize <= 0) {
                break;
            }

            int dataStart = position + headerSize;
            if (dataStart + frameSize > body.length) {
                break;
            }

            byte[] data = new byte[frameSize];
            System.arraycopy(body, dataStart, data, 0, frameSize);

            if (frameId.startsWith("T") && !frameId.equals("TXXX") && !frameId.equals("TIPL")
                    && !frameId.equals("TMCL")) {
                frames.putIfAbsent(frameId, decodeTextFrame(data));
            }

            position = dataStart + frameSize;
        }
        return frames;
    }

    private static void applyFrames(MusicMetadata meta, Map<String, String> frames) {
        setIfPresent(meta::setTitle, frames.get("TIT2"), frames.get("TT2"));
        setIfPresent(meta::setArtist, frames.get("TPE1"), frames.get("TP1"));
        setIfPresent(meta::setAlbum, frames.get("TALB"), frames.get("TAL"));
        setIfPresent(meta::setGenre, normalizeGenre(frames.get("TCON"), frames.get("TCO")));
        String year = frames.get("TYER");
        if (year == null) {
            year = frames.get("TYE");
        }
        if (year == null) {
            String record = frames.get("TDRC");
            if (record != null && record.length() >= 4) {
                year = record.substring(0, 4);
            }
        }
        setIfPresent(meta::setYear, year);
    }

    private static void setIfPresent(java.util.function.Consumer<String> setter, String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                setter.accept(candidate);
                return;
            }
        }
    }

    private static String decodeTextFrame(byte[] data) {
        if (data.length < 1) {
            return "";
        }
        int encoding = data[0] & 0xFF;
        int start = 1;
        Charset charset = StandardCharsets.ISO_8859_1;
        if (encoding == 1) {
            boolean littleEndianBom = data.length >= 3
                    && (data[1] & 0xFF) == 0xFF && (data[2] & 0xFF) == 0xFE;
            boolean bigEndianBom = data.length >= 3
                    && (data[1] & 0xFF) == 0xFE && (data[2] & 0xFF) == 0xFF;
            if (littleEndianBom) {
                start = 3;
                charset = StandardCharsets.UTF_16LE;
            } else if (bigEndianBom) {
                start = 3;
                charset = StandardCharsets.UTF_16BE;
            } else {
                start = 1;
                charset = StandardCharsets.UTF_16BE;
            }
        } else if (encoding == 2) {
            charset = StandardCharsets.UTF_16BE;
        } else if (encoding == 3) {
            charset = StandardCharsets.UTF_8;
        }
        String value = new String(data, start, data.length - start, charset).trim();
        int nullIndex = value.indexOf('\0');
        return nullIndex >= 0 ? value.substring(0, nullIndex).trim() : value;
    }

    private static String normalizeGenre(String raw, String legacy) {
        String value = raw != null ? raw : legacy;
        if (value == null || value.isBlank()) {
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.matches("^\\(?\\d+\\)?$")) {
            String digits = trimmed.replaceAll("[^0-9]", "");
            String mapped = ID3_GENRES.get(digits);
            return mapped != null ? mapped : "Pop";
        }
        if (trimmed.contains("\\(")) {
            int start = trimmed.indexOf('(');
            int end = trimmed.indexOf(')');
            if (end > start) {
                String code = trimmed.substring(start + 1, end).trim();
                if (code.matches("\\d+")) {
                    String mapped = ID3_GENRES.get(code);
                    if (mapped != null) {
                        return mapped;
                    }
                }
            }
            return trimmed;
        }
        return trimmed;
    }

    private static int readInt(byte[] data, int offset) {
        if (offset + 4 > data.length) {
            return 0;
        }
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    private static int readSyncSafeInt(byte[] data, int offset) {
        if (offset + 4 > data.length) {
            return 0;
        }
        return ((data[offset] & 0x7F) << 21)
                | ((data[offset + 1] & 0x7F) << 14)
                | ((data[offset + 2] & 0x7F) << 7)
                | (data[offset + 3] & 0x7F);
    }

    private static byte[] readBytes(java.io.InputStream in, int length) throws IOException {
        byte[] buffer = new byte[length];
        int total = 0;
        while (total < length) {
            int read = in.read(buffer, total, length - total);
            if (read < 0) {
                break;
            }
            total += read;
        }
        if (total < length) {
            return null;
        }
        return buffer;
    }

    private static final Map<String, String> ID3_GENRES = buildGenreMap();

    private static Map<String, String> buildGenreMap() {
        String[] names = {
                "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge",
                "Hip-Hop", "Jazz", "Metal", "New Age", "Oldies", "Other", "Pop", "R&B",
                "Rap", "Reggae", "Rock", "Techno", "Industrial", "Alternative", "Ska",
                "Death Metal", "Pranks", "Soundtrack", "Euro-Techno", "Ambient",
                "Trip-Hop", "Vocal", "Jazz+Funk", "Fusion", "Trance", "Classical",
                "Instrumental", "Acid", "House", "Game", "Sound Clip", "Gospel", "Noise",
                "Alternative Rock", "Bass", "Soul", "Punk", "Space", "Meditative",
                "Instrumental Pop", "Instrumental Rock", "Ethnic", "Gothic", "Darkwave",
                "Techno-Industrial", "Electronic", "Pop-Folk", "Eurodance", "Dream",
                "Southern Rock", "Comedy", "Cult", "Gangsta", "Top 40", "Christian Rap",
                "Pop/Funk", "Jungle", "Native American", "Cabaret", "New Wave", "Psychadelic",
                "Rave", "Showtunes", "Trailer", "Lo-Fi", "Tribal", "Acid Punk", "Acid Jazz",
                "Polka", "Retro", "Musical", "Rock & Roll", "Hard Rock"
        };
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < names.length; i++) {
            map.put(String.valueOf(i), names[i]);
        }
        map.put("17", "Rock");
        map.put("13", "Pop");
        map.put("10", "Electronic");
        return map;
    }
}