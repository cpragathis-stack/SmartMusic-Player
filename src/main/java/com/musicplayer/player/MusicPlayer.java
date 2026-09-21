package com.musicplayer.player;

import com.musicplayer.database.DatabaseManager;
import com.musicplayer.library.Song;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.control.Alert;
import javafx.util.Duration;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MusicPlayer {

    private static MusicPlayer instance;

    // =========================================================
    // PLAYBACK STATE
    // =========================================================

    private Song currentSong;

    private ObservableList<Song> playlist =
            FXCollections.observableArrayList();

    private int currentIndex = -1;

    private boolean isPlaying = false;
    private boolean isPaused = false;
    private boolean isShuffle = false;
    private boolean isRepeat = false;

    private double currentVolume = 0.80;

    // Offline session resume support.
    private static final java.util.Properties savedState =
            PlayerStateStore.load();
    private double pendingStartSeconds = -1;
    private boolean resumeWithoutAutoPlay = false;
    private boolean quietStartup = false;

    /**
     * Last sampled playback position (seconds) used to accumulate listening
     * time without double counting. Reset when a track starts.
     */
    private int lastReportedSecond = -1;

    private int progressTick = 0;

    // =========================================================
    // JAVAFX MEDIA PLAYER
    // =========================================================

    private MediaPlayer mediaPlayer;

    // =========================================================
    // PROGRESS TIMER
    // =========================================================

    private Timer progressTimer;

    // =========================================================
    // LISTENERS
    // =========================================================

    public interface OnSongChangeListener {
        void onSongChanged(Song song);
    }

    public interface OnPlaybackStateChangeListener {
        void onStateChanged(boolean isPlaying);
    }

    public interface OnProgressUpdateListener {
        void onProgress(Duration duration);
    }

    public interface OnTrackFinishedListener {
        void onTrackFinished();
    }

    private final List<OnSongChangeListener> songChangeListeners =
            new ArrayList<>();

    private final List<OnPlaybackStateChangeListener> stateChangeListeners =
            new ArrayList<>();

    private final List<OnProgressUpdateListener> progressListeners =
            new ArrayList<>();

    private OnTrackFinishedListener trackFinishedListener;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    private MusicPlayer() {
        applySavedSettings();
        startProgressTimer();
    }

    private void applySavedSettings() {
        try {
            double vol = Double.parseDouble(savedState.getProperty("volume", "0.8"));
            currentVolume = Math.max(0.0, Math.min(1.0, vol));
        } catch (Exception ignored) {
        }
        isShuffle = Boolean.parseBoolean(savedState.getProperty("shuffle", "false"));
        isRepeat = Boolean.parseBoolean(savedState.getProperty("repeat", "false"));
        isMuted = Boolean.parseBoolean(savedState.getProperty("muted", "false"));
    }

    public static synchronized MusicPlayer getInstance() {

        if (instance == null) {
            instance = new MusicPlayer();
        }

        return instance;
    }

    // =========================================================
    // LISTENERS
    // =========================================================

    public void addOnSongChangeListener(OnSongChangeListener listener) {

        if (listener != null &&
                !songChangeListeners.contains(listener)) {

            songChangeListeners.add(listener);
        }
    }

    public void addOnPlaybackStateChangeListener(
            OnPlaybackStateChangeListener listener) {

        if (listener != null &&
                !stateChangeListeners.contains(listener)) {

            stateChangeListeners.add(listener);
        }
    }

    public void setOnProgressUpdateListener(
            OnProgressUpdateListener listener) {

        progressListeners.clear();

        if (listener != null) {
            progressListeners.add(listener);
        }
    }

    /** Appends a progress listener without replacing others (used by Now Playing). */
    public void addOnProgressUpdateListener(
            OnProgressUpdateListener listener) {

        if (listener != null &&
                !progressListeners.contains(listener)) {

            progressListeners.add(listener);
        }
    }

    public void setOnTrackFinishedListener(
            OnTrackFinishedListener listener) {

        this.trackFinishedListener = listener;
    }

    private void notifySongChange(Song song) {

        Platform.runLater(() -> {

            for (OnSongChangeListener listener :
                    songChangeListeners) {

                try {
                    listener.onSongChanged(song);
                } catch (Exception ignored) {
                }
            }
        });
    }

    private void notifyStateChange(boolean playing) {

        Platform.runLater(() -> {

            for (OnPlaybackStateChangeListener listener :
                    stateChangeListeners) {

                try {
                    listener.onStateChanged(playing);
                } catch (Exception ignored) {
                }
            }
        });
    }

    // =========================================================
    // PLAYLIST
    // =========================================================

    public void setPlaylist(List<Song> songs, int startIndex) {

        if (songs == null) {
            playlist.clear();
            currentIndex = -1;
            return;
        }

        playlist = FXCollections.observableArrayList(songs);

        if (playlist.isEmpty()) {
            currentIndex = -1;
            return;
        }

        if (startIndex >= 0 &&
                startIndex < playlist.size()) {

            currentIndex = startIndex;

        } else {

            currentIndex = 0;
        }
    }

    public ObservableList<Song> getPlaylist() {
        return playlist;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public Song getCurrentSong() {
        return currentSong;
    }

    // =========================================================
    // PLAY SONG
    // =========================================================

    /** Plays a song and starts it at the given offset in seconds (session resume). */
    public void playSong(Song song, double startSeconds) {
        if (song == null) {
            return;
        }
        pendingStartSeconds = Math.max(0, startSeconds);
        playSong(song);
    }

    // =========================================================
    // RESTORE OFFLINE SESSION
    // =========================================================

    /**
     * Restores the last playback session (last song, queue, position, volume,
     * shuffle/repeat/mute) so the player resumes where it left off.
     */
    public void restoreSession() {
        String idStr = savedState.getProperty("lastSongId");
        if (idStr == null || idStr.trim().isEmpty()) {
            return;
        }
        int songId;
        try {
            songId = Integer.parseInt(idStr.trim());
        } catch (Exception e) {
            return;
        }
        Song song = DatabaseManager.getInstance().getSongById(songId);
        if (song == null) {
            return;
        }

        List<Song> queue = new ArrayList<>();
        String queueIds = savedState.getProperty("queue");
        if (queueIds != null && !queueIds.isBlank()) {
            for (String part : queueIds.split(",")) {
                try {
                    Song s = DatabaseManager.getInstance()
                            .getSongById(Integer.parseInt(part.trim()));
                    if (s != null) {
                        queue.add(s);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        if (queue.isEmpty()) {
            queue.add(song);
        }
        if (!queue.contains(song)) {
            queue.add(0, song);
        }

        int index = queue.indexOf(song);
        if (index < 0) {
            index = 0;
        }

        double position = 0;
        try {
            position = Double.parseDouble(savedState.getProperty("position", "0"));
        } catch (Exception ignored) {
        }

        boolean autoplay = Boolean.parseBoolean(savedState.getProperty("autoplay", "true"));

        quietStartup = true;
        resumeWithoutAutoPlay = !autoplay;
        setPlaylist(queue, index);
        playSong(song, position);
    }

    // =========================================================
    // PERSIST STATE
    // =========================================================

    private void persistState(double position) {
        try {
            savedState.setProperty("volume", String.valueOf(currentVolume));
            savedState.setProperty("shuffle", String.valueOf(isShuffle));
            savedState.setProperty("repeat", String.valueOf(isRepeat));
            savedState.setProperty("muted", String.valueOf(isMuted));
            savedState.setProperty("autoplay", String.valueOf(isPlaying));

            if (currentSong != null) {
                savedState.setProperty("lastSongId", String.valueOf(currentSong.getSongId()));
                savedState.setProperty("index", String.valueOf(Math.max(0, currentIndex)));
                savedState.setProperty("position", String.format("%.1f", Math.max(0, position)));
                StringBuilder ids = new StringBuilder();
                for (Song s : playlist) {
                    if (s == null) {
                        continue;
                    }
                    if (ids.length() > 0) {
                        ids.append(",");
                    }
                    ids.append(s.getSongId());
                }
                savedState.setProperty("queue", ids.toString());
            }
        } catch (Exception ignored) {
        }
        PlayerStateStore.save(savedState);
    }

    public void playSong(Song song) {

        if (song == null) {
            System.err.println("Cannot play null song.");
            return;
        }

        currentSong = song;

        // Find this song inside playlist
        if (playlist != null && !playlist.isEmpty()) {

            for (int i = 0; i < playlist.size(); i++) {

                Song item = playlist.get(i);

                if (item != null &&
                        item.getSongId() == song.getSongId()) {

                    currentIndex = i;
                    break;
                }
            }
        }

        System.out.println();
        System.out.println("========================================");
        System.out.println("NOW PLAYING");
        System.out.println("Song ID   : " + song.getSongId());
        System.out.println("Song Name : " + song.getSongName());
        System.out.println("Artist    : " + song.getArtist());
        System.out.println("DB Path   : " + song.getFilePath());
        System.out.println("========================================");

        /*
         * Resolve the actual audio file.
         */
        File audioFile = resolveAudioFile(song);

        if (audioFile == null) {

            System.err.println(
                    "ERROR: Could not find audio for: "
                            + song.getSongName()
            );

            System.err.println(
                    "Database path was: "
                            + song.getFilePath()
            );

            notifyStateChange(false);

            // During a silent midnight-style restore, just skip the alert.
            if (quietStartup) {
                quietStartup = false;
                pendingStartSeconds = -1;
                resumeWithoutAutoPlay = false;
                return;
            }

            showFileNotFound(song.getFilePath());
            return;
        }

        System.out.println(
                "ACTUAL AUDIO FILE: "
                        + audioFile.getAbsolutePath()
        );

        play(audioFile.getAbsolutePath());

        // Save play history
        updateHistory(song.getSongId());

        // Reset per-track listening tracker so a fresh track never inherits
        // an old cursor position.
        lastReportedSecond = -1;

        notifySongChange(song);

        persistState(0);
    }

    // =========================================================
    // FIND AUDIO FILE
    // =========================================================

    private File resolveAudioFile(Song song) {

        if (song == null) {
            return null;
        }

        /*
         * STRICT RESOLUTION:
         * Only the canonical path stored in the database is ever used.
         * We never fuzzy-search the disk, so the exact file the user
         * imported is the exact file that plays.
         */
        String databasePath = song.getFilePath();

        if (databasePath == null ||
                databasePath.trim().isEmpty()) {

            System.err.println(
                    "No file path stored for: "
                            + song.getSongName()
            );

            return null;
        }

        try {

            File file = new File(databasePath.trim());

            if (file.exists() &&
                    file.isFile()) {

                String canonical =
                        file.getCanonicalPath();

                File canonicalFile =
                        new File(canonical);

                if (canonicalFile.exists() &&
                        canonicalFile.isFile()) {

                    return canonicalFile;
                }
            }

        } catch (Exception e) {

            System.err.println(
                    "Could not resolve file path: "
                            + databasePath
            );
        }

        return null;
    }

    // =========================================================
    // PLAY BY INDEX
    // =========================================================

    public void playIndex(int index) {

        if (playlist == null ||
                playlist.isEmpty()) {

            System.err.println(
                    "Playlist is empty."
            );

            return;
        }

        if (index < 0 ||
                index >= playlist.size()) {

            System.err.println(
                    "Invalid playlist index: "
                            + index
            );

            return;
        }

        currentIndex = index;

        Song song =
                playlist.get(index);

        playSong(song);
    }

    // =========================================================
    // PLAY FILE
    // =========================================================

    public void play(String filePath) {

        stopAudioEngine();

        if (filePath == null ||
                filePath.trim().isEmpty()) {

            System.err.println(
                    "ERROR: Empty audio file path."
            );

            return;
        }

        File file =
                new File(filePath);

        if (!file.exists()) {

            System.err.println(
                    "ERROR: Audio file does not exist:"
            );

            System.err.println(
                    file.getAbsolutePath()
            );

            showFileNotFound(filePath);

            return;
        }

        if (!file.isFile()) {

            System.err.println(
                    "ERROR: Path is not a file:"
            );

            System.err.println(
                    file.getAbsolutePath()
            );

            return;
        }

        try {

            String mediaUri =
                    file.toURI().toString();

            System.out.println(
                    "Loading audio:"
            );

            System.out.println(
                    mediaUri
            );

            Media media =
                    new Media(mediaUri);

            mediaPlayer =
                    new MediaPlayer(media);

            mediaPlayer.setVolume(
                    currentVolume
            );

            mediaPlayer.setOnReady(() -> {

                System.out.println(
                        "Audio loaded successfully: "
                                + file.getName()
                );

                persistRealDuration();

                if (pendingStartSeconds > 0) {
                    double total = mediaPlayer.getTotalDuration() != null
                            ? mediaPlayer.getTotalDuration().toSeconds()
                            : 0;
                    if (total > 0) {
                        mediaPlayer.seek(
                                Duration.seconds(
                                        Math.min(pendingStartSeconds, total)
                                )
                        );
                    }
                }
                pendingStartSeconds = -1;

                if (resumeWithoutAutoPlay) {
                    resumeWithoutAutoPlay = false;
                    mediaPlayer.pause();
                    isPlaying = false;
                    isPaused = true;
                    notifyStateChange(false);
                    return;
                }

                mediaPlayer.play();

                isPlaying = true;
                isPaused = false;

                notifyStateChange(true);
            });

            mediaPlayer.setOnEndOfMedia(() -> {

                isPlaying = false;
                isPaused = false;

                notifyStateChange(false);

                Platform.runLater(
                        this::onTrackFinished
                );
            });

            mediaPlayer.setOnError(() -> {

                String error =
                        mediaPlayer.getError() != null
                                ? mediaPlayer
                                .getError()
                                .getMessage()
                                : "Unknown MediaPlayer error";

                System.err.println(
                        "Audio playback error: "
                                + error
                );

                isPlaying = false;
                isPaused = false;

                notifyStateChange(false);
            });

            mediaPlayer.setOnPlaying(() -> {

                isPlaying = true;
                isPaused = false;

                notifyStateChange(true);
            });

            mediaPlayer.setOnPaused(() -> {

                isPlaying = false;
                isPaused = true;

                notifyStateChange(false);
            });

            mediaPlayer.setOnStopped(() -> {

                isPlaying = false;

                notifyStateChange(false);
            });

        } catch (Exception e) {

            System.err.println(
                    "Could not load audio file:"
            );

            System.err.println(
                    filePath
            );

            e.printStackTrace();

            isPlaying = false;
            isPaused = false;

            notifyStateChange(false);
        }
    }

    private void showFileNotFound(String filePath) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Local file not found");
            alert.setHeaderText("This song is no longer available at its original location");
            alert.setContentText("Please re-import the song or scan your music folder to restore it.\n\n"
                    + (filePath == null || filePath.isBlank()
                    ? "This song has no stored local file path."
                    : "Missing file:\n" + filePath));
            alert.show();
        });
    }

    // =========================================================
    // NEXT
    // =========================================================

    public Song playNext() {

        if (playlist == null ||
                playlist.isEmpty()) {

            return null;
        }

        if (isShuffle &&
                playlist.size() > 1) {

            int nextIndex;

            do {

                nextIndex =
                        (int) (
                                Math.random()
                                        * playlist.size()
                        );

            } while (
                    nextIndex == currentIndex
            );

            currentIndex = nextIndex;

        } else {

            currentIndex =
                    (currentIndex + 1)
                            % playlist.size();
        }

        Song nextSong =
                playlist.get(currentIndex);

        recordListeningAccumulation();

        playSong(nextSong);

        return nextSong;
    }

    // =========================================================
    // PREVIOUS
    // =========================================================

    public Song playPrevious() {

        if (playlist == null ||
                playlist.isEmpty()) {

            return null;
        }

        if (isShuffle &&
                playlist.size() > 1) {

            int previousIndex;

            do {

                previousIndex =
                        (int) (
                                Math.random()
                                        * playlist.size()
                        );

            } while (
                    previousIndex == currentIndex
            );

            currentIndex =
                    previousIndex;

        } else {

            currentIndex =
                    (currentIndex - 1
                            + playlist.size())
                            % playlist.size();
        }

        Song previousSong =
                playlist.get(currentIndex);

        recordListeningAccumulation();

        playSong(previousSong);

        return previousSong;
    }

    // =========================================================
    // PLAY / PAUSE
    // =========================================================

    public void togglePlayPause() {

        if (mediaPlayer == null) {

            if (currentSong != null) {
                playSong(currentSong);
            }

            return;
        }

        if (isPlaying) {

            pause();

        } else {

            resume();
        }
    }

    public void pause() {

        if (mediaPlayer == null) {
            return;
        }

        if (isPlaying) {

            recordListeningAccumulation();

            mediaPlayer.pause();

            isPlaying = false;
            isPaused = true;

            persistState(getCurrentTimeSeconds());

            notifyStateChange(false);
        }
    }

    public void resume() {

        if (mediaPlayer == null) {

            if (currentSong != null) {
                playSong(currentSong);
            }

            return;
        }

        if (isPaused) {

            mediaPlayer.play();

            isPlaying = true;
            isPaused = false;

            notifyStateChange(true);

        } else if (!isPlaying) {

            mediaPlayer.play();

            isPlaying = true;
            isPaused = false;

            notifyStateChange(true);
        }
    }

    // =========================================================
    // STOP
    // =========================================================

    public void stop() {

        recordListeningAccumulation();

        lastReportedSecond = -1;

        if (mediaPlayer != null) {

            try {
                mediaPlayer.stop();
            } catch (Exception ignored) {
            }
        }

        isPlaying = false;
        isPaused = false;

        persistState(0);

        notifyStateChange(false);
    }

    // =========================================================
    // STOP AUDIO ENGINE
    // =========================================================

    private void stopAudioEngine() {

        lastReportedSecond = -1;

        if (mediaPlayer != null) {

            try {
                mediaPlayer.stop();
            } catch (Exception ignored) {
            }

            try {
                mediaPlayer.dispose();
            } catch (Exception ignored) {
            }

            mediaPlayer = null;
        }

        isPlaying = false;
        isPaused = false;
    }

    // =========================================================
    // PLAYBACK STATE
    // =========================================================

    public boolean isPlaying() {
        return isPlaying;
    }

    public boolean isPaused() {
        return isPaused;
    }

    // =========================================================
    // SHUFFLE
    // =========================================================

    public boolean isShuffle() {
        return isShuffle;
    }

    public void setShuffle(boolean shuffle) {
        this.isShuffle = shuffle;
        persistState(getCurrentTimeSeconds());
    }

    // =========================================================
    // REPEAT
    // =========================================================

    public boolean isRepeat() {
        return isRepeat;
    }

    public void setRepeat(boolean repeat) {
        this.isRepeat = repeat;
        persistState(getCurrentTimeSeconds());
    }

    // =========================================================
    // SEEK
    // =========================================================

    public void seek(double seconds) {

        if (mediaPlayer == null) {
            return;
        }

        try {

            Duration total =
                    mediaPlayer.getTotalDuration();

            if (total == null ||
                    total.isUnknown()) {

                return;
            }

            double safeSeconds =
                    Math.max(
                            0,
                            Math.min(
                                    seconds,
                                    total.toSeconds()
                            )
                    );

            mediaPlayer.seek(
                    Duration.seconds(
                            safeSeconds
                    )
            );

        } catch (Exception e) {

            System.err.println(
                    "Seek error: "
                            + e.getMessage()
            );
        }
    }

    // =========================================================
    // DURATION
    // =========================================================

    public double getDurationSeconds() {

        if (mediaPlayer == null) {
            return 0;
        }

        try {

            Duration duration =
                    mediaPlayer.getTotalDuration();

            if (duration == null ||
                    duration.isUnknown()) {

                return 0;
            }

            return duration.toSeconds();

        } catch (Exception e) {

            return 0;
        }
    }

    // =========================================================
    // CURRENT TIME
    // =========================================================

    public double getCurrentTimeSeconds() {

        if (mediaPlayer == null) {
            return 0;
        }

        try {

            Duration current =
                    mediaPlayer.getCurrentTime();

            if (current == null) {
                return 0;
            }

            return current.toSeconds();

        } catch (Exception e) {

            return 0;
        }
    }

    // =========================================================
    // VOLUME
    // =========================================================

    public void setVolume(double volume) {

        currentVolume =
                Math.max(
                        0.0,
                        Math.min(
                                1.0,
                                volume
                        )
                );

        if (mediaPlayer != null) {

            mediaPlayer.setVolume(
                    currentVolume
            );
        }

        persistState(getCurrentTimeSeconds());
    }

    public double getVolume() {
        return currentVolume;
    }

    // =========================================================
    // MUTE
    // =========================================================

    private boolean isMuted = false;

    public boolean isMuted() {
        return isMuted;
    }

    public void setMuted(boolean muted) {
        this.isMuted = muted;
        if (mediaPlayer != null) {
            try {
                mediaPlayer.setMute(muted);
            } catch (Exception ignored) {
            }
        }
        persistState(getCurrentTimeSeconds());
    }

    public void toggleMute() {
        setMuted(!isMuted);
    }

    // =========================================================
    // CURRENT FILE
    // =========================================================

    public String getCurrentFilePath() {

        return currentSong != null
                ? currentSong.getFilePath()
                : null;
    }

    // =========================================================
    // TRACK FINISHED
    // =========================================================

    private void onTrackFinished() {

        if (currentSong != null) {

            recordListeningAccumulation();
        }

        if (trackFinishedListener != null) {

            trackFinishedListener.onTrackFinished();

            return;
        }

        if (isRepeat) {

            if (currentSong != null) {
                playSong(currentSong);
            }

            return;
        }

        playNext();
    }

    // =========================================================
    // PROGRESS TIMER
    // =========================================================

    private void startProgressTimer() {

        progressTimer =
                new Timer(
                        "MusicPlayerProgressTimer",
                        true
                );

        progressTimer.scheduleAtFixedRate(
                new TimerTask() {

                    @Override
                    public void run() {

                        if (!isPlaying) {
                            return;
                        }

                        double currentSeconds =
                                getCurrentTimeSeconds();

                        Duration duration =
                                Duration.seconds(
                                        currentSeconds
                                );

                        Platform.runLater(() -> {

                            for (
                                    OnProgressUpdateListener listener :
                                    progressListeners
                            ) {

                                try {

                                    listener.onProgress(
                                            duration
                                    );

                                } catch (Exception ignored) {
                                }
                            }
                        });

                        // Persist the resume position roughly every 5 seconds.
                        progressTick++;
                        if (progressTick % 20 == 0) {
                            persistState(currentSeconds);
                        }
                    }
                },
                200,
                250
        );
    }

    // =========================================================
    // HISTORY / PLAY COUNT
    // =========================================================

    private void updateHistory(int songId) {
        DatabaseManager.getInstance().recordPlay(songId);
    }

    /**
     * Tracks how much of the current track was actually listened to and, when
     * the track is abandoned before 15 seconds, also records an early skip.
     * Natural auto-advance at the end of a track never counts as a skip.
     */
    private void recordListeningAccumulation() {
        if (currentSong == null) {
            return;
        }
        double current = getCurrentTimeSeconds();
        if (current < 1) {
            return;
        }
        int position = (int) current;
        int userId = com.musicplayer.auth.UserSession.getCurrentUserId();
        if (userId <= 0) {
            userId = 1;
        }

        // Skip detection: leaving the track very early counts as a skip.
        if (position < 15) {
            DatabaseManager.getInstance().recordSkip(currentSong.getSongId());
        }

        // Listening-time accumulation: only count forward movement since the
        // last sampled position, so repeated calls never double-count.
        if (lastReportedSecond < 0) {
            lastReportedSecond = position;
            return;
        }
        int delta = position - lastReportedSecond;
        lastReportedSecond = position;
        if (delta <= 0) {
            return;
        }
        DatabaseManager.getInstance().addListeningSeconds(currentSong.getSongId(), delta);
        com.musicplayer.history.HistoryTracker.getInstance()
                .logListen(userId, currentSong.getSongId(), delta);
    }

    /** Legacy helper kept for callers; see {@link #recordListeningAccumulation()}. */
    private void recordSkipIfEarly() {
        recordListeningAccumulation();
    }

    /**
     * Writes the real length of the current audio back into the database so
     * tables and the player bar show the true "m:ss" duration.
     */
    private void persistRealDuration() {
        if (currentSong == null || mediaPlayer == null) {
            return;
        }
        try {
            Duration duration = mediaPlayer.getTotalDuration();
            if (duration == null || duration.isUnknown() || duration.isIndefinite()) {
                return;
            }
            long totalSeconds = Math.round(duration.toSeconds());
            if (totalSeconds <= 0) {
                return;
            }
            long minutes = totalSeconds / 60;
            long seconds = totalSeconds % 60;
            String formatted = minutes + ":" + String.format("%02d", seconds);
            DatabaseManager.getInstance().updateSongDuration(
                    currentSong.getSongId(), formatted);
        } catch (Exception ignored) {
        }
    }
}
