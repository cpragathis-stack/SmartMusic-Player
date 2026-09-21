package com.musicplayer.library;

import com.musicplayer.database.DatabaseManager;
import com.musicplayer.player.MusicPlayer;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.ResourceBundle;

public class LibraryController implements Initializable {

    public LibraryController() {
    }

    // =========================
    // FXML COMPONENTS
    // =========================

    @FXML
    private TableView<Song> songTable;

    @FXML
    private TableColumn<Song, Integer> colId;

    @FXML
    private TableColumn<Song, String> colSong;

    @FXML
    private TableColumn<Song, String> colArtist;

    @FXML
    private TableColumn<Song, String> colGenre;

    @FXML
    private TableColumn<Song, String> colAlbum;

    @FXML
    private TableColumn<Song, String> colDuration;

    @FXML
    private TableColumn<Song, String> colLanguage;

    @FXML
    private TableColumn<Song, String> colPlay;

    @FXML
    private TextField searchField;

    @FXML
    private Label statusLabel;


    // =========================
    // DATA
    // =========================

    private final ObservableList<Song> allSongs =
            FXCollections.observableArrayList();

    private final ObservableList<Song> filteredSongs =
            FXCollections.observableArrayList();

private String selectedLanguage = "All";

    private final MusicLibrary musicLibrary = new MusicLibrary();


    // =========================
    // INITIALIZE
    // =========================

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {

        setupColumns();

        syncSongsFolder();

        loadSongs();

        if (songTable != null) {
            songTable.setItems(filteredSongs);
        }

        if (searchField != null) {
            searchField.textProperty().addListener(
                    (observable, oldValue, newValue) ->
                            searchSongs(newValue)
            );
        }

        updateStatus();
    }


    // =========================
    // TABLE COLUMNS
    // =========================

    private void setupColumns() {

        if (colId != null) {
            colId.setCellValueFactory(
                    new PropertyValueFactory<>("songId")
            );
        }

        if (colSong != null) {
            colSong.setCellValueFactory(
                    new PropertyValueFactory<>("songName")
            );
        }

        if (colArtist != null) {
            colArtist.setCellValueFactory(
                    new PropertyValueFactory<>("artist")
            );
        }

        if (colGenre != null) {
            colGenre.setCellValueFactory(
                    new PropertyValueFactory<>("genre")
            );
        }

        if (colAlbum != null) {
            colAlbum.setCellValueFactory(
                    new PropertyValueFactory<>("album")
            );
        }

        if (colDuration != null) {
            colDuration.setCellValueFactory(
                    new PropertyValueFactory<>("duration")
            );
        }

        if (colLanguage != null) {
            colLanguage.setCellValueFactory(
                    new PropertyValueFactory<>("language")
            );
        }

        setupPlayColumn();
    }


    // =========================
    // PLAY BUTTON COLUMN
    // =========================

    private void setupPlayColumn() {

        if (colPlay == null) {
            return;
        }

        colPlay.setCellFactory(column -> new TableCell<Song, String>() {

            private final Button playButton = new Button("▶ Play");

            {
                playButton.setStyle(
                        "-fx-background-color: #38BDF8;" +
                                "-fx-text-fill: #0F172A;" +
                                "-fx-font-weight: bold;" +
                                "-fx-background-radius: 8;" +
                                "-fx-cursor: hand;" +
                                "-fx-padding: 6 14;"
                );

                playButton.setOnAction(event -> {

                    Song song =
                            getTableView()
                                    .getItems()
                                    .get(getIndex());

                    playSong(song);
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {

                super.updateItem(item, empty);

                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(playButton);
                }
            }
        });
    }


    // =========================
    // LOAD SONGS FROM DATABASE
    // =========================

    public void loadSongs() {

        allSongs.clear();

        String sql =
                "SELECT " +
                        "s.song_id, " +
                        "s.song_name, " +
                        "COALESCE(a.artist_name, 'Unknown Artist') AS artist_name, " +
                        "COALESCE(g.genre_name, 'Unknown Genre') AS genre_name, " +
                        "COALESCE(s.album, 'Single') AS album, " +
                        "COALESCE(s.duration, '0:00') AS duration, " +
                        "COALESCE(s.language, 'General') AS language, " +
                        "s.file_path " +
                        "FROM Songs s " +
                        "LEFT JOIN Artists a ON s.artist_id = a.artist_id " +
                        "LEFT JOIN Genres g ON s.genre_id = g.genre_id " +
                        "ORDER BY s.song_name COLLATE NOCASE";


        try {

            Connection connection =
                    DatabaseManager
                            .getInstance()
                            .getConnection();

            if (connection == null) {

                showMessage(
                        "Database Error",
                        "Database connection is null."
                );

                return;
            }


            try (PreparedStatement statement =
                         connection.prepareStatement(sql);

                 ResultSet resultSet =
                         statement.executeQuery()) {


                while (resultSet.next()) {

                    Song song = new Song(

                            resultSet.getInt("song_id"),

                            resultSet.getString("song_name"),

                            resultSet.getString("artist_name"),

                            resultSet.getString("genre_name"),

                            resultSet.getString("album"),

                            resultSet.getString("duration"),

                            resultSet.getString("language"),

                            resultSet.getString("file_path")
                    );


                    allSongs.add(song);
                }
            }


            filteredSongs.setAll(allSongs);

            updateStatus();


        } catch (SQLException e) {

            e.printStackTrace();

            showMessage(
                    "Database Error",
                    "Could not load songs.\n\n" +
                            e.getMessage()
            );
        }
    }


    // =========================
    // SEARCH
    // =========================

    private void searchSongs(String text) {

        String search = text == null ? "" : text.trim().toLowerCase();


        ObservableList<Song> results =
                FXCollections.observableArrayList();


        for (Song song : allSongs) {

            boolean matchesLanguage = selectedLanguage.equalsIgnoreCase("All")
                    || (song.getLanguage() != null && song.getLanguage().equalsIgnoreCase(selectedLanguage));

            boolean matchesSong = search.isEmpty() || (song.getSongName() != null &&
                            song.getSongName()
                                    .toLowerCase()
                                    .contains(search));


            boolean matchesArtist =
                    search.isEmpty() || (song.getArtist() != null &&
                            song.getArtist()
                                    .toLowerCase()
                                    .contains(search));


            boolean matchesGenre =
                    search.isEmpty() || (song.getGenre() != null &&
                            song.getGenre()
                                    .toLowerCase()
                                    .contains(search));


            if (matchesLanguage && (matchesSong ||
                    matchesArtist ||
                    matchesGenre)) {

                results.add(song);
            }
        }


        filteredSongs.setAll(results);

        updateStatus();
    }

    @FXML private void showAllLanguages() { filterByLanguage("All"); }
    @FXML private void showTamilSongs() { filterByLanguage("Tamil"); }
    @FXML private void showHindiSongs() { filterByLanguage("Hindi"); }
    @FXML private void showEnglishSongs() { filterByLanguage("English"); }

    private void filterByLanguage(String language) {
        selectedLanguage = language;
        searchSongs(searchField == null ? "" : searchField.getText());
    }


    // =========================
    // IMPORTANT:
    // THIS FIXES getSelectedSong()
    // =========================

    public Song getSelectedSong() {

        if (songTable == null) {
            return null;
        }

        return songTable
                .getSelectionModel()
                .getSelectedItem();
    }


    // =========================
    // PLAY SELECTED SONG
    // =========================

    @FXML
    public void playSelectedSong() {

        Song selectedSong =
                getSelectedSong();

        if (selectedSong == null) {

            showMessage(
                    "No Song Selected",
                    "Please select a song first."
            );

            return;
        }

        int index =
                songTable
                        .getItems()
                        .indexOf(selectedSong);

        playSong(selectedSong, index);
    }


    // =========================
    // PLAY SONG
    // =========================

    private void playSong(Song song) {

        if (song == null) {
            return;
        }

        int index =
                songTable
                        .getItems()
                        .indexOf(song);

        playSong(song, index);
    }


    private void playSong(Song song, int index) {

        if (song == null) {
            return;
        }


        try {

            /*
             * IMPORTANT:
             * Give the MusicPlayer the CURRENT
             * library playlist.
             */

            MusicPlayer.getInstance()
                    .setPlaylist(
                            songTable.getItems(),
                            Math.max(index, 0)
                    );


            /*
             * Start the selected song.
             */

            MusicPlayer.getInstance()
                    .playSong(song);


            setStatus(
                    "Playing: " +
                            song.getSongName()
            );


            /*
             * Open Player screen.
             */

            openPlayer();


        } catch (Exception e) {

            e.printStackTrace();

            showMessage(
                    "Playback Error",
                    "Could not play:\n" +
                            song.getSongName() +
                            "\n\n" +
                            e.getMessage()
            );
        }
    }


    // =========================
    // ADD SONGS
    // =========================

    /**
     * Opens a file chooser so the user can pick .mp3 files from their PC.
     * They are copied into the project "songs" folder and then indexed, so
     * they appear in the player immediately and again on every launch.
     */
    @FXML
    public void addSongs() {

        if (songTable == null ||
                songTable.getScene() == null) {

            showMessage(
                    "Add Songs",
                    "Could not open the file picker."
            );

            return;
        }

        FileChooser chooser = new FileChooser();

        chooser.setTitle("Select MP3 Songs to Add");

        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(
                        "MP3 Songs",
                        "*.mp3"
                ),
                new FileChooser.ExtensionFilter(
                        "Audio Files",
                        "*.mp3", "*.wav", "*.m4a", "*.aac", "*.ogg", "*.flac"
                )
        );

        List<File> selected = chooser.showOpenMultipleDialog(
                songTable.getScene().getWindow()
        );

        if (selected == null || selected.isEmpty()) {
            return;
        }

        int added = musicLibrary.addSongsToLibrary(selected);

        syncSongsFolder();

        loadSongs();

        String message = added + " song(s) added to your library.";

        if (added == 0) {
            message = "No new songs were added.\n" +
                    "The chosen files may already be in your library.";
        }

        setStatus(message);

        showMessage(
                "Add Songs",
                message +
                        "\n\nSongs are stored in the project 'songs' folder " +
                        "and are loaded automatically."
        );
    }

    /** Re-scans the project songs folder so deleted/added files are reflected. */
    private void syncSongsFolder() {
        musicLibrary.syncSongsFolder();
    }

    // =========================
    // REFRESH
    // =========================

    @FXML
    public void refreshSongs() {

        syncSongsFolder();

        loadSongs();

        setStatus(
                "Library refreshed. " +
                        allSongs.size() +
                        " songs found."
        );
    }


    // =========================
    // OPEN PLAYER
    // =========================

    @FXML
    public void openPlayer() {

        loadScreen(
                "/fxml/Player.fxml",
                "Music Player - Now Playing",
                1100,
                700
        );
    }


    // =========================
    // HOME
    // =========================

    @FXML
    public void goHome() {

        loadScreen(
                "/fxml/Home.fxml",
                "Music Player - Home",
                1100,
                700
        );
    }


    // =========================
    // RECOMMENDATIONS
    // =========================

    @FXML
    public void openRecommendations() {

        loadScreen(
                "/fxml/Recommendation.fxml",
                "Music Player - Recommendations",
                1100,
                700
        );
    }


    // =========================
    // PLAYLIST
    // =========================

    @FXML
    public void openPlaylist() {

        loadScreen(
                "/fxml/Playlist.fxml",
                "Music Player - Playlists",
                1100,
                700
        );
    }


    // =========================
    // FAVORITES / HISTORY
    // =========================

    @FXML
    public void openFavorites() {

        loadScreen(
                "/fxml/History.fxml",
                "Music Player - Favorites",
                1100,
                700
        );
    }


    // =========================
    // LOAD SCREEN
    // =========================

    private void loadScreen(
            String fxml,
            String title,
            int width,
            int height) {

        try {

            URL resource =
                    getClass().getResource(fxml);


            if (resource == null) {

                showMessage(
                        "FXML Error",
                        "Could not find:\n" + fxml
                );

                return;
            }


            FXMLLoader loader =
                    new FXMLLoader(resource);


            Parent root =
                    loader.load();


            if (songTable == null ||
                    songTable.getScene() == null) {

                return;
            }


            Stage stage =
                    (Stage) songTable
                            .getScene()
                            .getWindow();


            stage.setScene(
                    new Scene(
                            root,
                            width,
                            height
                    )
            );


            stage.setTitle(title);

            stage.centerOnScreen();


        } catch (IOException e) {

            e.printStackTrace();

            showMessage(
                    "Navigation Error",
                    "Could not open:\n" +
                            fxml +
                            "\n\n" +
                            e.getMessage()
            );
        }
    }


    // =========================
    // STATUS
    // =========================

    private void updateStatus() {

        if (statusLabel != null) {

            statusLabel.setText(
                    "Songs: " +
                            filteredSongs.size()
            );
        }
    }


    private void setStatus(String text) {

        if (statusLabel != null) {

            statusLabel.setText(text);
        }
    }


    // =========================
    // MESSAGE
    // =========================

    private void showMessage(
            String title,
            String message) {

        Alert alert =
                new Alert(
                        Alert.AlertType.INFORMATION
                );

        alert.setTitle(title);

        alert.setHeaderText(null);

        alert.setContentText(message);

        alert.showAndWait();
    }
}
