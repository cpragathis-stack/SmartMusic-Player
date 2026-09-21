package com.musicplayer.utility;

import java.io.File;
import java.io.IOException;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * Computes the REAL length of a local audio file at import time.
 *
 * <p>No fake or invented values are ever returned. Every format reader
 * below measures actual frame/codec data from the file on disk:
 * <ul>
 *   <li>MP3 â€” walked with JLayer (pure-Java, offline) from the actual
 *       frame headers, summing each frame's sample length.</li>
 *   <li>WAV â€” read through JavaSound's real frame length / frame rate.</li>
 * </ul>
 *
 * <p>On any failure or unreadable file this returns {@code null}, which
 * callers must treat as "no reliable duration available" â€” they keep the
 * stored fallback instead of substituting a made-up number.
 */
public final class AudioDurationReader {

    private AudioDurationReader() {
    }

    /**
     * @return real duration as a formatted {@code "m:ss"} string, or
     *         {@code null} if the duration could not be measured reliably.
     */
    public static String readDurationString(File file) {

        long millis = readDurationMillis(file);

        if (millis < 0) {
            return null;
        }

        long totalSeconds = Math.max(1, Math.round(millis / 1000.0));

        String minutes = String.valueOf(totalSeconds / 60);
        String seconds = String.format("%02d", totalSeconds % 60);

        return minutes + ":" + seconds;
    }

    /**
     * @return real duration in milliseconds, or -1 when not measurable.
     */
    public static long readDurationMillis(File file) {

        if (file == null || !file.isFile()) {
            return -1;
        }

        String name = file.getName()
                .toLowerCase();

        try {

            if (name.endsWith(".mp3")) {

                return readMp3Millis(file);

            } else if (name.endsWith(".wav")) {

                return readWavMillis(file);
            }

        } catch (Exception e) {

            System.err.println(
                    "Duration reader error for "
                            + file.getName()
                            + ": "
                            + e.getMessage()
            );
        }

        return -1;
    }

    /** Real MP3 length from frame headers (JLayer walk). */
    private static long readMp3Millis(File file)
            throws IOException,
                   javazoom.jl.decoder.BitstreamException {

        javazoom.jl.decoder.Bitstream bitstream =
                new javazoom.jl.decoder.Bitstream(
                        new java.io.BufferedInputStream(
                                new java.io.FileInputStream(file)
                        )
                );

        long totalMillis = 0;

        try {

            javazoom.jl.decoder.Header header;

            int guard = 0;

            while ((header = bitstream.readFrame()) != null) {

                try {

                    totalMillis += header.ms_per_frame();

                } finally {

                    bitstream.closeFrame();
                }

                // Hard safety guard against a corrupt/looping stream.
                if (++guard > 4_000_000) {
                    System.err.println(
                            "MP3 frame guard hit: "
                                    + file.getName()
                    );
                    break;
                }
            }

        } finally {

            try {
                bitstream.close();
            } catch (Exception ignored) {
            }
        }

        return totalMillis > 0 ? totalMillis : -1;
    }

    /** Real WAV length from JavaSound frame length / frame rate. */
    private static long readWavMillis(File file)
            throws IOException,
                   UnsupportedAudioFileException {

        AudioFileFormat format =
                AudioSystem.getAudioFileFormat(file);

        int frameLength =
                format.getFrameLength();

        float frameRate =
                format.getFormat()
                        .getFrameRate();

        if (frameLength <= 0 ||
                frameRate <= 0) {

            return -1;
        }

        return Math.round(
                (frameLength / (double) frameRate)
                        * 1000.0
        );
    }
}
