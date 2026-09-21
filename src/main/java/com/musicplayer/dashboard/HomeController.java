package com.musicplayer.dashboard;

import com.musicplayer.auth.UserSession;
import com.musicplayer.database.DatabaseManager;
import com.musicplayer.library.MusicLibrary;
import com.musicplayer.library.Song;
import com.musicplayer.player.MusicPlayer;
import com.musicplayer.playlist.PlaylistManager;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;

import javafx.scene.Parent;
import javafx.scene.Scene;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import javafx.stage.Stage;

import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.ResourceBundle;

public class HomeController implements Initializable {

    public HomeController() {
    }

    // ============================================================
    // FXML LABELS
    // ============================================================

    @FXML
    private Label welcomeLabel;

    @FXML
    private Label songCountLabel;

    @FXML
    private Label playlistCountLabel;

    @FXML
    private Label favoriteCountLabel;

    @FXML
    private Label playedCountLabel;

    @FXML
    private ListView<Song> languageSongList;

    private final MusicLibrary musicLibrary = new MusicLibrary();
    private final MusicPlayer musicPlayer = MusicPlayer.getInstance();
    private final PlaylistManager playlistManager = new PlaylistManager();


    // ============================================================
    // INITIALIZE
    // ============================================================

    @Override
    public void initialize(
            URL url,
            ResourceBundle rb) {

        loadWelcomeMessage();

        loadStats();
        loadSongsByLanguage("All");
    }

    @FXML private void showAllLanguages() { loadSongsByLanguage("All"); }
    @FXML private void showTamilSongs() { loadSongsByLanguage("Tamil"); }
    @FXML private void showHindiSongs() { loadSongsByLanguage("Hindi"); }
    @FXML private void showEnglishSongs() { loadSongsByLanguage("English"); }

    private void loadSongsByLanguage(String language) {
        if (languageSongList == null) return;
        languageSongList.setFixedCellSize(70);
        languageSongList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Song item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setContextMenu(null);
                    return;
                }

                StackPane colorBox = new StackPane();
                colorBox.setStyle("-fx-background-color: " + item.getCoverColor() + "; -fx-background-radius: 8; -fx-min-width: 48; -fx-min-height: 48; -fx-max-width: 48; -fx-max-height: 48; -fx-alignment: CENTER;");
                Label note = new Label("");
                note.setStyle("-fx-font-size: 18px;");
                colorBox.getChildren().add(note);

                String songName = (item.getSongName() == null || item.getSongName().isBlank())
                        ? "Unknown Title" : item.getSongName();
                String artistName = (item.getArtist() == null || item.getArtist().isBlank())
                        ? "Unknown Artist" : item.getArtist();

                Label title = new Label(songName);
                title.setMaxWidth(200);
                title.setTextOverrun(OverrunStyle.CLIP);
                title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: black;");
                Label artist = new Label(artistName + "  \u2022  " + (item.getLanguage() == null || item.getLanguage().isBlank() ? "Local" : item.getLanguage()));
                artist.setMaxWidth(200);
                artist.setTextOverrun(OverrunStyle.CLIP);
                artist.setStyle("-fx-font-size: 11px; -fx-text-fill: #5D6D7E;");
                VBox text = new VBox(2, title, artist);

                Tooltip tip = new Tooltip(songName + " - " + artistName);
                Tooltip.install(text, tip);

                MenuButton options = new MenuButton("\u22EE");
                options.setStyle("-fx-font-size: 18px; -fx-background-color: transparent;");
                options.setCursor(javafx.scene.Cursor.HAND);
                Tooltip.install(options, new Tooltip("More options"));

                MenuItem playItem = new MenuItem("\u25B6 Play");
                playItem.setOnAction(e -> {
                    musicPlayer.setPlaylist(languageSongList.getItems(), getIndex());
                    musicPlayer.playSong(item);
                });

                MenuItem favItem = new MenuItem("\u2764 Add to Favorites");
                favItem.setOnAction(e -> {
                    DatabaseManager.getInstance().addToFavorites(item.getSongId());
                    Alert ok = new Alert(Alert.AlertType.INFORMATION);
                    ok.setTitle("Favorites");
                    ok.setHeaderText("Added to Favorites");
                    ok.setContentText("\"" + item.getSongName() + "\" was added to your favorites.");
                    ok.showAndWait();
                });

                MenuItem addPlaylistItem = new MenuItem("\u2795 Add to Playlist");
                addPlaylistItem.setOnAction(e -> addToPlaylist(item));

                MenuItem infoItem = new MenuItem("\u2139 Song Info");
                infoItem.setOnAction(e -> showSongInfo(item));

                MenuItem deleteItem = new MenuItem("\uD83D\uDDD1 Delete");
                deleteItem.setOnAction(e -> removeSongFromView(item));

                options.getItems().addAll(playItem, favItem, addPlaylistItem, infoItem, deleteItem);

                HBox cell = new HBox(12, colorBox, text, options);
                cell.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                HBox.setHgrow(text, Priority.ALWAYS);
                setText(null);
                setGraphic(cell);

                ContextMenu ctx = new ContextMenu();
                ctx.getItems().addAll(playItem, addPlaylistItem, deleteItem);
                setContextMenu(ctx);
            }
        });
        languageSongList.setItems(musicLibrary.filterByLanguage(language));
        languageSongList.setOnMouseClicked(event -> {
            Song selectedSong = languageSongList.getSelectionModel().getSelectedItem();
            if (selectedSong == null || event.getClickCount() < 2) {
                return;
            }
            musicPlayer.setPlaylist(languageSongList.getItems(), languageSongList.getSelectionModel().getSelectedIndex());
            musicPlayer.playSong(selectedSong);
        });
    }


    private void addToPlaylist(Song item) {
        ObservableList<String> playlists = playlistManager.getAllPlaylists();
        if (playlists.isEmpty()) {
            TextInputDialog createDlg = new TextInputDialog();
            createDlg.setTitle("Create Playlist");
            createDlg.setHeaderText("No playlists yet. Create one first:");
            createDlg.setContentText("Name:");
            createDlg.showAndWait().ifPresent(name -> {
                if (!name.trim().isEmpty()) {
                    playlistManager.createPlaylist(name.trim());
                    loadSongsByLanguage("All");
                }
            });
            return;
        }
        ChoiceDialog<String> dialog = new ChoiceDialog<>(playlists.get(0), playlists);
        dialog.setTitle("Add to Playlist");
        dialog.setHeaderText("Select a playlist:");
        dialog.setContentText("Playlist:");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(selected -> {
            int playlistId = Integer.parseInt(selected.split("\\.")[0].trim());
            playlistManager.addSongToPlaylist(playlistId, item.getSongId());
        });
    }

    private void showSongInfo(Song item) {
        Alert info = new Alert(Alert.AlertType.INFORMATION);
        info.setTitle("Song Info");
        info.setHeaderText(item.getSongName() + " - " + item.getArtist());
        StringBuilder body = new StringBuilder();
        body.append("Genre: ").append(valueOrDash(item.getGenre())).append("\n");
        body.append("Album: ").append(valueOrDash(item.getAlbum())).append("\n");
        body.append("Language: ").append(valueOrDash(item.getLanguage())).append("\n");
        body.append("Duration: ").append(valueOrDash(item.getDuration())).append("\n");
        body.append("Year: ").append(valueOrDash(item.getYear())).append("\n");
        body.append("Play Count: ").append(item.getPlayCount()).append("\n");
        info.setContentText(body.toString());
        info.showAndWait();
    }

    private String valueOrDash(String value) {
        return (value == null || value.trim().isEmpty()) ? "-" : value;
    }

    private void removeSongFromView(Song item) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Remove Song");
        confirm.setHeaderText("Remove \"" + item.getSongName() + "\"?");
        confirm.setContentText("This only clears the song from this list.");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                languageSongList.getItems().remove(item);
            }
        });
    }


    // ============================================================
    // WELCOME MESSAGE
    // ============================================================

    private void loadWelcomeMessage() {

        try {

            String username =
                    UserSession.getCurrentUser();

            if (username != null &&
                    !username.trim().isEmpty()) {

                welcomeLabel.setText(
                        "Welcome, " +
                                username +
                                "!"
                );

            } else {

                welcomeLabel.setText(
                        "Welcome!"
                );
            }

        } catch (Exception e) {

            welcomeLabel.setText(
                    "Welcome!"
            );

            System.out.println(
                    "Welcome error: " +
                            e.getMessage()
            );
        }
    }


    // ============================================================
    // LOAD DASHBOARD STATISTICS
    // ============================================================

    private void loadStats() {

        int songCount = 0;
        int playlistCount = 0;
        int favoriteCount = 0;
        int playedCount = 0;


        try {

            DatabaseManager db =
                    DatabaseManager.getInstance();


            Connection connection = db.getConnection();

            if (connection == null || connection.isClosed()) {
                return;
            }


                // =================================================
                // SONG COUNT
                // =================================================

                songCount =
                        getCount(
                                connection,
                                "SELECT COUNT(*) FROM Songs"
                        );


                // =================================================
                // PLAYLIST COUNT
                // =================================================

                playlistCount =
                        getCount(
                                connection,
                                "SELECT COUNT(*) FROM Playlists WHERE user_id = "
                                        + DatabaseManager.activeUserId()
                        );


                // =================================================
                // FAVORITE COUNT
                // =================================================

                favoriteCount =
                        getCount(
                                connection,
                                "SELECT COUNT(*) " +
                                        "FROM Favorites " +
                                        "WHERE user_id = " + DatabaseManager.activeUserId() + " " +
                                        "AND is_favorite = 1"
                        );


                // =================================================
                // PLAYED COUNT
                // =================================================

                playedCount =
                        getCount(
                                connection,
                                "SELECT COALESCE(" +
                                        "SUM(play_count), 0) " +
                                        "FROM UserHistory " +
                                        "WHERE user_id = " + DatabaseManager.activeUserId()
                        );
        } catch (Exception e) {

            System.out.println(
                    "Stats error: " +
                            e.getMessage()
            );

            e.printStackTrace();
        }


        // ========================================================
        // DISPLAY STATISTICS
        // ========================================================

        if (songCountLabel != null) {

            songCountLabel.setText(
                    String.valueOf(songCount)
            );
        }


        if (playlistCountLabel != null) {

            playlistCountLabel.setText(
                    String.valueOf(playlistCount)
            );
        }


        if (favoriteCountLabel != null) {

            favoriteCountLabel.setText(
                    String.valueOf(favoriteCount)
            );
        }


        if (playedCountLabel != null) {

            playedCountLabel.setText(
                    String.valueOf(playedCount)
            );
        }
    }


    // ============================================================
    // GET COUNT FROM DATABASE
    // ============================================================

    private int getCount(
            Connection connection,
            String sql) throws Exception {

        try (
                PreparedStatement statement =
                        connection.prepareStatement(sql);

                ResultSet resultSet =
                        statement.executeQuery()
        ) {

            if (resultSet.next()) {

                return resultSet.getInt(1);
            }
        }

        return 0;
    }


    // ============================================================
    // OPEN LIBRARY
    // ============================================================

    @FXML
    private void openLibrary() {

        loadScreen(
                "/fxml/Library.fxml",
                "Smart Music Player",
                1100,
                700
        );
    }


    // ============================================================
    // OPEN PLAYER
    // ============================================================

    @FXML
    private void openPlayer() {

        loadScreen(
                "/fxml/Player.fxml",
                "Smart Music Player",
                1100,
                700
        );
    }


    // ============================================================
    // OPEN PLAYLIST
    // ============================================================

    @FXML
    private void openPlaylist() {

        loadScreen(
                "/fxml/Playlist.fxml",
                "Smart Music Player",
                1100,
                700
        );
    }


    // ============================================================
    // OPEN FAVORITES
    // ============================================================

    @FXML
    private void openFavorites() {

        loadScreen(
                "/fxml/History.fxml",
                "Smart Music Player",
                1100,
                700
        );
    }


    // ============================================================
    // OPEN DASHBOARD
    // ============================================================

    @FXML
    private void openDashboard() {

        loadScreen(
                "/fxml/Dashboard.fxml",
                "Smart Music Player",
                1100,
                700
        );
    }


    // ============================================================
    // OPEN RECOMMENDATIONS
    // ============================================================

    @FXML
    private void openRecommendations() {

        loadScreen(
                "/fxml/Recommendation.fxml",
                "Smart Music Player",
                1100,
                700
        );
    }


    // ============================================================
    // OPEN HISTORY
    // ============================================================

    @FXML
    private void openHistory() {

        loadScreen(
                "/fxml/History.fxml",
                "Smart Music Player",
                1100,
                700
        );
    }


    // ============================================================
    // LOGOUT
    // ============================================================

    @FXML
    private void handleLogout() {

        try {

            UserSession.logout();

        } catch (Exception e) {

            System.out.println(
                    "Logout error: " +
                            e.getMessage()
            );
        }


        loadScreen(
                "/fxml/Login.fxml",
                "Smart Music Player",
                900,
                600
        );
    }


    // ============================================================
    // LOAD SCREEN
    // ============================================================

    private void loadScreen(
            String fxml,
            String title,
            int width,
            int height) {

        try {

            URL resource =
                    getClass().getResource(fxml);


            if (resource == null) {

                System.out.println(
                        "FXML file not found: " +
                                fxml
                );

                return;
            }


            FXMLLoader loader =
                    new FXMLLoader(resource);


            Parent root =
                    loader.load();


            if (welcomeLabel == null ||
                    welcomeLabel.getScene() == null) {

                System.out.println(
                        "Current scene not available."
                );

                return;
            }


            Stage stage =
                    (Stage) welcomeLabel
                            .getScene()
                            .getWindow();


            Scene scene =
                    new Scene(
                            root,
                            width,
                            height
                    );


            stage.setScene(scene);

            stage.setTitle(title);

            stage.centerOnScreen();


        } catch (Exception e) {

            System.out.println(
                    "Load error: " +
                            e.getMessage()
            );

            e.printStackTrace();
        }
    }
}
