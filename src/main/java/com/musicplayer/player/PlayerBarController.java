package com.musicplayer.player;

import com.musicplayer.library.Song;
import com.musicplayer.utility.CoverArt;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/** Persistent JavaFX player bar for local tracks. */
public class PlayerBarController {

    /** Set by the main shell so clicking the track area opens Now Playing. */
    public static Runnable openNowPlayingHook;

    public PlayerBarController() {
    }

    @FXML private StackPane coverPane;
    @FXML private Label songTitleLabel;
    @FXML private Label artistLabel;
    @FXML private Label elapsedLabel;
    @FXML private Label durationLabel;
    @FXML private Button playPauseButton;
    @FXML private Button shuffleButton;
    @FXML private Button repeatButton;
    @FXML private Button muteButton;
    @FXML private Slider progressSlider;
    @FXML private Slider volumeSlider;

    private final MusicPlayer player = MusicPlayer.getInstance();
    private boolean seeking;
    private ImageView coverView;

    private static final String INACTIVE_ICON = "-fx-text-fill: #94A3B8;";
    private static final String ACTIVE_ICON = "-fx-text-fill: #38BDF8;";

    @FXML
    private void initialize() {
        player.addOnSongChangeListener(this::showSong);
        player.addOnPlaybackStateChangeListener(playing ->
                playPauseButton.setText(playing ? "Pause" : "Play"));
        player.setOnProgressUpdateListener(this::updateProgress);
        volumeSlider.valueProperty().addListener((observable, oldValue, newValue) ->
                player.setVolume(newValue.doubleValue() / 100.0));
        progressSlider.valueChangingProperty().addListener((observable, wasChanging, isChanging) -> {
            seeking = isChanging;
            if (!isChanging) player.seek(progressSlider.getValue());
        });
        progressSlider.setOnMouseReleased(event -> player.seek(progressSlider.getValue()));
        refreshStateButtons();
        wireOpenNowPlaying();
        if (player.getCurrentSong() != null) showSong(player.getCurrentSong());
    }

    private void wireOpenNowPlaying() {
        javafx.event.EventHandler<javafx.scene.input.MouseEvent> open = event -> {
            if (openNowPlayingHook != null) {
                openNowPlayingHook.run();
            }
        };
        if (coverPane != null) {
            coverPane.setOnMouseClicked(open);
            coverPane.setStyle(coverPane.getStyle() + "-fx-cursor: hand;");
        }
        if (songTitleLabel != null) {
            songTitleLabel.setOnMouseClicked(open);
            songTitleLabel.setStyle(songTitleLabel.getStyle() + "-fx-cursor: hand;");
        }
        if (artistLabel != null) {
            artistLabel.setOnMouseClicked(open);
            artistLabel.setStyle(artistLabel.getStyle() + "-fx-cursor: hand;");
        }
    }

    private void refreshStateButtons() {
        if (shuffleButton != null) {
            shuffleButton.setStyle(player.isShuffle() ? ACTIVE_ICON : INACTIVE_ICON);
        }
        if (repeatButton != null) {
            repeatButton.setStyle(player.isRepeat() ? ACTIVE_ICON : INACTIVE_ICON);
        }
        if (muteButton != null) {
            muteButton.setText(player.isMuted() ? "Muted" : "Mute");
            muteButton.setStyle(player.isMuted() ? ACTIVE_ICON : INACTIVE_ICON);
        }
    }

    private void showSong(Song song) {
        if (song == null) return;
        songTitleLabel.setText(song.getSongName());
        artistLabel.setText(song.getArtist());
        durationLabel.setText(song.getDuration());
        if (coverView == null) {
            coverPane.getChildren().clear();
            coverView = new ImageView();
            coverView.setFitWidth(56);
            coverView.setFitHeight(56);
            coverView.setPreserveRatio(false);
            coverView.setStyle("-fx-background-radius: 8;");
            coverPane.getChildren().add(coverView);
        }
        javafx.scene.image.Image cover = CoverArt.getCover(song);
        if (cover != null) {
            coverView.setImage(cover);
        }
    }

    private void updateProgress(Duration position) {
        Platform.runLater(() -> {
            if (seeking) return;
            double total = player.getDurationSeconds();
            double seconds = position.toSeconds();
            progressSlider.setMax(Math.max(1, total));
            progressSlider.setValue(seconds);
            elapsedLabel.setText(format(seconds));
            if (total > 0) durationLabel.setText(format(total));
        });
    }

    @FXML private void togglePlayPause() { player.togglePlayPause(); }
    @FXML private void next() { player.playNext(); }
    @FXML private void previous() { player.playPrevious(); }
    @FXML private void toggleShuffle() {
        player.setShuffle(!player.isShuffle());
        shuffleButton.setStyle(player.isShuffle() ? ACTIVE_ICON : INACTIVE_ICON);
    }
    @FXML private void toggleRepeat() {
        player.setRepeat(!player.isRepeat());
        repeatButton.setStyle(player.isRepeat() ? ACTIVE_ICON : INACTIVE_ICON);
    }
    @FXML private void toggleMute() {
        player.toggleMute();
        muteButton.setText(player.isMuted() ? "Muted" : "Mute");
        muteButton.setStyle(player.isMuted() ? ACTIVE_ICON : INACTIVE_ICON);
    }

    private String format(double seconds) {
        int value = (int) Math.max(0, seconds);
        return value / 60 + ":" + String.format("%02d", value % 60);
    }
}
