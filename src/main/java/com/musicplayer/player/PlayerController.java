package com.musicplayer.player;

import com.musicplayer.database.DatabaseManager;
import com.musicplayer.library.Song;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.io.IOException;

public class PlayerController {

    public PlayerController() {
    }

    @FXML
    private Label songNameLabel;

    @FXML
    private Label artistLabel;

    @FXML
    private Label genreLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Button playPauseBtn;

    @FXML
    private Button likeBtn;

    @FXML
    private Button prevBtn;

    @FXML
    private Button nextBtn;

    @FXML
    private Button stopBtn;

    private final MusicPlayer musicPlayer =
            MusicPlayer.getInstance();


    // =========================================================
    // INITIALIZE
    // =========================================================

    @FXML
    public void initialize() {

        updatePlayerScreen();
    }


    // =========================================================
    // UPDATE SCREEN
    // =========================================================

    private void updatePlayerScreen() {

        Song song = musicPlayer.getCurrentSong();

        if (song == null) {

            songNameLabel.setText("Select a Song");
            artistLabel.setText("Artist");
            genreLabel.setText("Genre");
            statusLabel.setText("Ready to play...");

            playPauseBtn.setText("▶ Play");
            likeBtn.setText("♡ Like");

            return;
        }

        songNameLabel.setText(
                song.getSongName()
        );

        artistLabel.setText(
                song.getArtist()
        );

        genreLabel.setText(
                song.getGenre()
        );

        if (musicPlayer.isPlaying()) {

            playPauseBtn.setText("⏸ Pause");
            statusLabel.setText("▶ Now Playing");

        } else {

            playPauseBtn.setText("▶ Play");
            statusLabel.setText("Paused");
        }
    }


    // =========================================================
    // PLAY / PAUSE
    // =========================================================

    @FXML
    public void togglePlay() {

        Song song =
                musicPlayer.getCurrentSong();

        if (song == null) {

            statusLabel.setText(
                    "Please select a song first."
            );

            return;
        }

        if (musicPlayer.isPlaying()) {

            musicPlayer.pause();

            playPauseBtn.setText("▶ Play");
            statusLabel.setText("Paused");

        } else {

            musicPlayer.resume();

            playPauseBtn.setText("⏸ Pause");
            statusLabel.setText("▶ Now Playing");
        }
    }


    // =========================================================
    // STOP
    // =========================================================

    @FXML
    public void stopSong() {

        musicPlayer.stop();

        playPauseBtn.setText("▶ Play");
        statusLabel.setText("Stopped");
    }


    // =========================================================
    // NEXT
    // =========================================================

    @FXML
    public void nextSong() {

        Song next =
                musicPlayer.playNext();

        if (next != null) {

            updatePlayerScreen();

            statusLabel.setText(
                    "▶ Now Playing"
            );

        } else {

            statusLabel.setText(
                    "No next song available."
            );
        }
    }


    // =========================================================
    // PREVIOUS
    // =========================================================

    @FXML
    public void prevSong() {

        Song previous =
                musicPlayer.playPrevious();

        if (previous != null) {

            updatePlayerScreen();

            statusLabel.setText(
                    "▶ Now Playing"
            );

        } else {

            statusLabel.setText(
                    "No previous song available."
            );
        }
    }


    // =========================================================
    // LIKE BUTTON
    // =========================================================
    /*
     * Your current MusicPlayer.java does not contain
     * toggleLike() or isLiked().
     *
     * Therefore this button is kept safe for now.
     *
     * We will connect it to Favorites database later.
     */

    @FXML
    public void toggleLike() {

        Song song =
                musicPlayer.getCurrentSong();

        if (song == null) {

            statusLabel.setText(
                    "Select a song first."
            );

            return;
        }

        DatabaseManager db =
                DatabaseManager.getInstance();

        if (db.isFavorite(song.getSongId())) {

            db.removeFromFavorites(song.getSongId());

            likeBtn.setText("♡ Like");

            statusLabel.setText(
                    "\"" + song.getSongName() + "\" removed from favorites"
            );

        } else {

            db.addToFavorites(song.getSongId());

            likeBtn.setText("♥ Liked");

            statusLabel.setText(
                    "\"" + song.getSongName() + "\" added to favorites"
            );
        }
    }


    // =========================================================
    // SHUFFLE
    // =========================================================

    @FXML
    public void toggleShuffle() {

        boolean newValue =
                !musicPlayer.isShuffle();

        musicPlayer.setShuffle(newValue);

        if (newValue) {

            statusLabel.setText(
                    "Shuffle ON"
            );

        } else {

            statusLabel.setText(
                    "Shuffle OFF"
            );
        }
    }


    // =========================================================
    // GO TO LIBRARY
    // =========================================================

    @FXML
    public void goToLibrary() {

        loadScreen(
                "/fxml/Library.fxml",
                "Music Player - Library"
        );
    }


    // =========================================================
    // GO HOME
    // =========================================================

    @FXML
    public void goHome() {

        loadScreen(
                "/fxml/Home.fxml",
                "Music Player - Home"
        );
    }


    // =========================================================
    // LOAD SCREEN
    // =========================================================

    private void loadScreen(
            String fxml,
            String title) {

        try {

            FXMLLoader loader =
                    new FXMLLoader(
                            getClass().getResource(fxml)
                    );

            Parent root =
                    loader.load();

            if (songNameLabel == null) {
                return;
            }

            if (songNameLabel.getScene() == null) {
                return;
            }

            Stage stage =
                    (Stage) songNameLabel
                            .getScene()
                            .getWindow();

            stage.setScene(
                    new Scene(
                            root,
                            1100,
                            700
                    )
            );

            stage.setTitle(title);

            stage.centerOnScreen();

        } catch (IOException e) {

            System.err.println(
                    "Could not load screen: "
                            + fxml
            );

            e.printStackTrace();
        }
    }
}