package com.musicplayer.history;

import com.musicplayer.database.DatabaseManager;

/**
 * Chronological, timestamped listening log.
 *
 * Unlike the aggregated {@code UserHistory} table (play/skip totals per song),
 * this tracker writes one row per listening event with the exact moment it
 * happened and how many seconds of the track were actually played.
 */
public class HistoryTracker {

    private static HistoryTracker instance;

    private HistoryTracker() {
    }

    public static synchronized HistoryTracker getInstance() {
        if (instance == null) {
            instance = new HistoryTracker();
        }
        return instance;
    }

    /**
     * Records a single listening event for a user.
     *
     * @param userId        the user who was listening
     * @param songId        the track that was played
     * @param playedSeconds whole seconds of the track that were played
     */
    public void logListen(int userId, int songId, int playedSeconds) {
        if (songId <= 0) {
            return;
        }
        int safeUserId = userId > 0 ? userId : 1;
        DatabaseManager.getInstance()
                .logListening(safeUserId, songId, playedSeconds);
    }
}