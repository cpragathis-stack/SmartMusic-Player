package com.musicplayer;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AudioSampleGenerator {

    private AudioSampleGenerator() {
        // Prevent instantiation
    }

    public static void main(String[] args) {
        generateSampleTracks(new File("sample_music"));
    }

    /** Creates short original WAV tracks locally for a first-run offline demo. */
    public static Map<String, File> generateSampleTracks(File outputDir) {
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        Map<String, File> tracks = new LinkedHashMap<>();
        addTrack(tracks, outputDir, "Tamil_Sunrise.wav", new double[]{261.63, 293.66, 329.63, 392.00, 440.00}, "Lofi");
        addTrack(tracks, outputDir, "Tamil_Rhythm.wav", new double[]{174.61, 220.00, 261.63, 329.63, 392.00}, "Beat");
        addTrack(tracks, outputDir, "Hindi_Evening.wav", new double[]{220.00, 246.94, 293.66, 329.63, 440.00}, "Acoustic");
        addTrack(tracks, outputDir, "Hindi_Groove.wav", new double[]{146.83, 174.61, 220.00, 293.66, 349.23}, "Beat");
        addTrack(tracks, outputDir, "English_Night_Drive.wav", new double[]{220.00, 261.63, 293.66, 329.63, 440.00, 554.37}, "Electronic");
        addTrack(tracks, outputDir, "English_Chill_Horizon.wav", new double[]{261.63, 293.66, 329.63, 392.00, 523.25}, "Lofi");
        addTrack(tracks, outputDir, "English_Acoustic_Sunset.wav", new double[]{196.00, 220.00, 261.63, 293.66, 392.00}, "Acoustic");
        addTrack(tracks, outputDir, "Instrumental_Ambient.wav", new double[]{220.00, 246.94, 261.63, 293.66, 329.63}, "Ambient");
        return tracks;
    }

    private static void addTrack(Map<String, File> tracks, File outputDir, String name,
                                 double[] notes, String style) {
        File file = new File(outputDir, name);
        if (!file.isFile() || file.length() == 0) generateTrack(file, notes, 8.0, style);
        if (file.isFile() && file.length() > 0) tracks.put(name, file);
    }

    private static void generateTrack(File file, double[] notes, double durationSeconds, String style) {
        float sampleRate = 44100;
        int totalSamples = (int) (sampleRate * durationSeconds);
        byte[] buffer = new byte[totalSamples * 2];

        double noteDuration = style.equals("Electronic") || style.equals("Beat") ? 0.35 : 0.65;
        for (int i = 0; i < totalSamples; i++) {
            double time = i / sampleRate;
            int noteIndex = (int) (time / noteDuration) % notes.length;
            double freq = notes[noteIndex];

            // Harmonic acoustic synthesizer synthesis
            double fundamental = Math.sin(2.0 * Math.PI * freq * time);
            double harmonic1 = 0.4 * Math.sin(2.0 * Math.PI * (freq * 1.5) * time);
            double harmonic2 = 0.25 * Math.sin(2.0 * Math.PI * (freq * 2.0) * time);
            double subBass = 0.3 * Math.sin(2.0 * Math.PI * (freq / 2.0) * time);

            double noteTime = time % noteDuration;
            double envelope = Math.exp(-2.2 * noteTime);
            double rhythmPulse = 0.2 * Math.sin(2.0 * Math.PI * 2.0 * time);

            double sample = (fundamental + harmonic1 + harmonic2 + subBass + rhythmPulse) * envelope * 0.4;
            if (sample > 1.0) sample = 1.0;
            if (sample < -1.0) sample = -1.0;

            short pcmShort = (short) (sample * 32767);
            buffer[i * 2] = (byte) (pcmShort & 0xff);
            buffer[i * 2 + 1] = (byte) ((pcmShort >> 8) & 0xff);
        }

        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        ByteArrayInputStream bais = new ByteArrayInputStream(buffer);
        AudioInputStream ais = new AudioInputStream(bais, format, totalSamples);

        try {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, file);
        } catch (IOException e) {
            System.err.println("Error generating sample: " + e.getMessage());
        }
    }
}
