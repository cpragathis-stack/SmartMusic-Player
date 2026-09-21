package com.musicplayer.player;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Tiny offline persistence for the player: remembers the last song, the queue,
 * the playback position, volume, shuffle/repeat and mute state so the app can
 * resume like a real desktop music player across restarts.
 */
public final class PlayerStateStore {

    private static final String FILE = "player_state.properties";

    private PlayerStateStore() {
    }

    public static Properties load() {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(FILE)) {
            props.load(in);
        } catch (IOException ignored) {
            // First run or missing file is fine - defaults apply.
        }
        return props;
    }

    public static boolean save(Properties props) {
        try (FileOutputStream out = new FileOutputStream(FILE)) {
            props.store(out, "SmartBeats offline playback state");
            return true;
        } catch (IOException e) {
            System.err.println("Could not save player state: " + e.getMessage());
            return false;
        }
    }
}