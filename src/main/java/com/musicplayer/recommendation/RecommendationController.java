package com.musicplayer.recommendation;

import com.musicplayer.database.DatabaseManager;
import com.musicplayer.library.Song;
import com.musicplayer.player.MusicPlayer;
import com.musicplayer.playlist.PlaylistManager;
import com.musicplayer.utility.CoverArt;

import javafx.collections.ObservableList;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;

import javafx.scene.Parent;
import javafx.scene.Scene;

import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;

import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import javafx.stage.Stage;

import java.net.URL;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import java.util.Optional;
import java.util.ResourceBundle;


/**
 * RecommendationController
 *
 * Controls the Recommendation page of the Smart Music Player.
 *
 * Features:
 * 1. Displays recommended songs
 * 2. Displays song name
 * 3. Displays artist
 * 4. Displays genre
 * 5. Displays ranking
 * 6. Provides Play button
 * 7. Finds favourite genre
 * 8. Loads Home page
 * 9. Loads Library page
 * 10. Loads Player page
 */
public class RecommendationController implements Initializable {

    public RecommendationController() {
    }


    // ============================================================
    // FXML COMPONENTS
    // ============================================================

    @FXML
    private TableView<Song> recTable;


    @FXML
    private TableColumn<Song, Integer> colRank;


    @FXML
    private TableColumn<Song, String> colSong;


    @FXML
    private TableColumn<Song, String> colArtist;


    @FXML
    private TableColumn<Song, String> colGenre;


    @FXML
    private TableColumn<Song, String> colPlay;


    @FXML
    private Label statusLabel;


    @FXML
    private Label genreLabel;


    @FXML
    private HBox moodButtons;


    // ============================================================
    // RECOMMENDATION ENGINE & PLAYLISTS
    // ============================================================

    private final RecommendationEngine recEngine =
            new RecommendationEngine();

    private final PlaylistManager playlistManager =
            new PlaylistManager();

    private RecommendationEngine.Mood selectedMood =
            RecommendationEngine.Mood.ALL;


    // ============================================================
    // MUSIC PLAYER
    // ============================================================

    private final MusicPlayer mp =
            MusicPlayer.getInstance();


    // ============================================================
    // INITIALIZATION
    // ============================================================

    @Override
    public void initialize(
            URL url,
            ResourceBundle resourceBundle) {

        setupTable();

        loadRecommendations();
    }


    // ============================================================
    // TABLE SETUP
    // ============================================================

    private void setupTable() {

        if (recTable != null) {
            recTable.setFixedCellSize(70);
            recTable.setPlaceholder(new Label("No recommendations yet. Play or favourite songs to personalise this list."));
            recTable.setRowFactory(tv -> {
                TableRow<Song> row = new TableRow<>();
                row.setOnMouseClicked(event -> {
                    if (event.getClickCount() == 2 && (!row.isEmpty())) {
                        playThisSong(row.getItem());
                    }
                });
                row.emptyProperty().addListener((obs, wasEmpty, isEmpty) -> {
                    if (isEmpty) {
                        row.setContextMenu(null);
                    } else {
                        row.setContextMenu(createSongContextMenu(row));
                    }
                });
                return row;
            });
        }

        /*
         * Song name column
         */
        if (colSong != null) {

            colSong.setCellValueFactory(
                    new PropertyValueFactory<>("songName")
            );
            colSong.setCellFactory(column -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }
                    Song song = getTableView().getItems().get(getIndex());
                    javafx.scene.image.Image coverImage = CoverArt.getCover(song);
                    ImageView cover = new ImageView(coverImage);
                    cover.setFitWidth(48);
                    cover.setFitHeight(48);
                    cover.setPreserveRatio(false);
                    cover.setStyle("-fx-background-radius: 6;");
                    String songName = (song.getSongName() == null || song.getSongName().isBlank())
                            ? "Unknown Title" : song.getSongName();
                    String artistName = (song.getArtist() == null || song.getArtist().isBlank())
                            ? "Unknown Artist" : song.getArtist();
                    Label title = new Label(songName);
                    title.setMaxWidth(200);
                    title.setTextOverrun(OverrunStyle.CLIP);
                    title.setStyle("-fx-font-weight: bold; -fx-text-fill: #1A5276; -fx-font-size: 14px;");
                    Label artist = new Label(artistName);
                    artist.setMaxWidth(200);
                    artist.setTextOverrun(OverrunStyle.CLIP);
                    artist.setStyle("-fx-font-size: 11px; -fx-text-fill: #5D6D7E;");
                    Label plays = new Label("Played " + song.getPlayCount() + " times");
                    plays.setStyle("-fx-font-size: 10px; -fx-text-fill: #94A3B8;");
                    VBox infoBox = new VBox(3, title, artist, plays);
                    Tooltip tip = new Tooltip(songName + " - " + artistName);
                    Tooltip.install(infoBox, tip);
                    setText(null);
                    setGraphic(new HBox(12, cover, infoBox));
                }
            });
        }


        /*
         * Artist column
         */
        if (colArtist != null) {

            colArtist.setCellValueFactory(
                    new PropertyValueFactory<>("artist")
            );
            colArtist.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }
                    Song song = getTableView().getItems().get(getIndex());
                    Label artist = new Label(song.getArtist());
                    artist.setMaxWidth(220);
                    artist.setTextOverrun(OverrunStyle.CLIP);
                    artist.setStyle("-fx-font-size: 13px; -fx-text-fill: #2C3E50;");
                    Tooltip.install(artist, new Tooltip(song.getArtist()));
                    setText(null);
                    setGraphic(artist);
                }
            });
        }


        /*
         * Genre column
         */
        if (colGenre != null) {

            colGenre.setCellValueFactory(
                    new PropertyValueFactory<>("genre")
            );
            colGenre.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }
                    Label genre = new Label(item);
                    genre.setTextOverrun(OverrunStyle.CLIP);
                    genre.setMaxWidth(140);
                    genre.setStyle("-fx-font-size: 12px; -fx-text-fill: #5D6D7E;");
                    Tooltip.install(genre, new Tooltip(item));
                    setText(null);
                    setGraphic(genre);
                }
            });
        }


        // ========================================================
        // RANK COLUMN
        // ========================================================

        if (colRank != null) {

            colRank.setCellFactory(column ->
                    new TableCell<Song, Integer>() {

                        @Override
                        protected void updateItem(
                                Integer item,
                                boolean empty) {

                            super.updateItem(
                                    item,
                                    empty
                            );

                            if (empty) {

                                setText(null);

                            } else {

                                setText(
                                        String.valueOf(
                                                getIndex() + 1
                                        )
                                );
                            }
                        }
                    }
            );
        }


        // ========================================================
        // PLAY BUTTON COLUMN
        // ========================================================

        if (colPlay != null) {

            colPlay.setCellFactory(column ->
                    new TableCell<Song, String>() {

                        private final Button playButton =
                                new Button("▶ Play");


                        {
                            /*
                             * Button styling
                             */
                            playButton.setStyle(
                                    "-fx-background-color: #2E86C1;" +
                                            "-fx-text-fill: white;" +
                                            "-fx-background-radius: 5;" +
                                            "-fx-cursor: hand;" +
                                            "-fx-padding: 5 12;"
                            );


                            /*
                             * Play button action
                             */
                            playButton.setOnAction(event -> {

                                int index =
                                        getIndex();


                                if (index < 0) {
                                    return;
                                }


                                if (getTableView() == null) {
                                    return;
                                }


                                if (index >=
                                        getTableView()
                                                .getItems()
                                                .size()) {

                                    return;
                                }


                                Song selectedSong =
                                        getTableView()
                                                .getItems()
                                                .get(index);


                                playThisSong(
                                        selectedSong
                                );
                            });
                        }


                        @Override
                        protected void updateItem(
                                String item,
                                boolean empty) {

                            super.updateItem(
                                    item,
                                    empty
                            );


                            if (empty) {

                                setGraphic(null);

                            } else {

                                setGraphic(
                                        playButton
                                );
                            }
                        }
                    }
            );
        }
    }


    // ============================================================
    // LOAD RECOMMENDATIONS
    // ============================================================

    @FXML
    public void loadRecommendations() {
        loadRecommendations(selectedMood);
    }

    @FXML
    public void loadRecommendations(RecommendationEngine.Mood mood) {

        ObservableList<Song> songs =
                recEngine.getRecommendations(mood);

        if (moodButtons != null) {
            syncMoodButtons(mood);
        }


        // ========================================================
        // DISPLAY SONGS
        // ========================================================

        if (recTable != null) {
            recTable.setItems(songs);
        }


        // ========================================================
        // EMPTY RESULT
        // ========================================================

        if (songs.isEmpty()) {

            if (statusLabel != null) {

                statusLabel.setText(
                        "No songs match this mood yet - " +
                                "play a few tracks to build your picks!"
                );
            }


            if (genreLabel != null) {

                genreLabel.setText("");
            }

        } else {

            if (statusLabel != null) {

                statusLabel.setText(
                        songs.size() +
                                " songs recommended for you!"
                );
            }

            loadFavoriteGenre();
        }
    }


    // ============================================================
    // MOOD FILTER BUTTONS
    // ============================================================

    @FXML
    public void filterAll() {
        loadRecommendations(RecommendationEngine.Mood.ALL);
    }

    @FXML
    public void filterHappy() {
        loadRecommendations(RecommendationEngine.Mood.HAPPY);
    }

    @FXML
    public void filterSad() {
        loadRecommendations(RecommendationEngine.Mood.SAD);
    }

    @FXML
    public void filterEnergetic() {
        loadRecommendations(RecommendationEngine.Mood.ENERGETIC);
    }

    @FXML
    public void filterChill() {
        loadRecommendations(RecommendationEngine.Mood.CHILL);
    }

    @FXML
    public void filterCalm() {
        loadRecommendations(RecommendationEngine.Mood.CALM);
    }

    @FXML
    public void filterFocus() {
        loadRecommendations(RecommendationEngine.Mood.FOCUS);
    }

    @FXML
    public void filterNight() {
        loadRecommendations(RecommendationEngine.Mood.NIGHT);
    }

    @FXML
    public void filterDevotional() {
        loadRecommendations(RecommendationEngine.Mood.DEVOTIONAL);
    }

    private void syncMoodButtons(RecommendationEngine.Mood mood) {
        selectedMood = mood == null ? RecommendationEngine.Mood.ALL : mood;
        for (javafx.scene.Node n : moodButtons.getChildren()) {
            if (n instanceof ToggleButton) {
                ToggleButton b = (ToggleButton) n;
                String tag = String.valueOf(b.getUserData());
                b.setSelected(selectedMood.name().equals(tag));
            }
        }
    }


    // ============================================================
    // FAVOURITE GENRE
    // ============================================================

    private void loadFavoriteGenre() {

        if (genreLabel == null) {

            return;
        }


        String sql =

                "SELECT " +

                        "g.genre_name, " +

                        "SUM(h.play_count) AS total_plays " +

                        "FROM UserHistory h " +

                        "JOIN Songs s " +

                        "ON h.song_id = s.song_id " +

                        "JOIN Genres g " +

                        "ON s.genre_id = g.genre_id " +

                        "WHERE h.user_id = " + DatabaseManager.activeUserId() + " " +

                        "GROUP BY g.genre_name " +

                        "ORDER BY total_plays DESC " +

                        "LIMIT 1";


        Connection connection = DatabaseManager.getInstance().getConnection();
        try (
                Statement statement =
                        connection.createStatement();

                ResultSet resultSet =
                        statement.executeQuery(sql)

        ) {


            if (resultSet.next()) {

                String favoriteGenre =
                        resultSet.getString(
                                "genre_name"
                        );


                genreLabel.setText(
                        "Favourite Genre: " +
                                favoriteGenre
                );
            }


        } catch (Exception e) {

            System.out.println(
                    "Favourite Genre Error: " +
                            e.getMessage()
            );
        }
    }


    // ============================================================
    // SONG CONTEXT MENU
    // ============================================================

    private ContextMenu createSongContextMenu(TableRow<Song> row) {
        ContextMenu ctx = new ContextMenu();

        MenuItem playItem = new MenuItem("Play");
        playItem.setOnAction(e -> {
            if (row != null && !row.isEmpty()) {
                playThisSong(row.getItem());
            }
        });

        MenuItem addPlaylistItem = new MenuItem("Add to Playlist");
        addPlaylistItem.setOnAction(e -> {
            if (row != null && !row.isEmpty()) {
                addToPlaylist(row.getItem());
            }
        });

        MenuItem dislikeItem = new MenuItem("Not Interested");
        dislikeItem.setOnAction(e -> {
            if (row != null && !row.isEmpty()) {
                Song s = row.getItem();
                DatabaseManager.getInstance().recordSkip(s.getSongId());
                if (row.getTableView() != null) {
                    row.getTableView().getItems().remove(s);
                }
                if (statusLabel != null) {
                    statusLabel.setText("Noted \"" + s.getSongName() + "\" — fewer picks like this.");
                }
            }
        });

        MenuItem removeItem = new MenuItem("Remove from Recommendations");
        removeItem.setOnAction(e -> {
            if (row != null && row.getTableView() != null) {
                row.getTableView().getItems().remove(row.getItem());
            }
        });

        ctx.getItems().addAll(playItem, addPlaylistItem, dislikeItem, removeItem);
        return ctx;
    }

    private void addToPlaylist(Song song) {
        if (song == null) {
            return;
        }
        ObservableList<String> playlists = playlistManager.getAllPlaylists();
        if (playlists.isEmpty()) {
            TextInputDialog createDlg = new TextInputDialog();
            createDlg.setTitle(APP_TITLE);
            createDlg.setHeaderText("No playlists yet. Create one first:");
            createDlg.setContentText("Name:");
            createDlg.showAndWait().ifPresent(name -> {
                if (!name.trim().isEmpty()) {
                    playlistManager.createPlaylist(name.trim());
                }
            });
            return;
        }
        ChoiceDialog<String> dialog = new ChoiceDialog<>(playlists.get(0), playlists);
        dialog.setTitle(APP_TITLE);
        dialog.setHeaderText("Select a playlist:");
        dialog.setContentText("Playlist:");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(selected -> {
            int playlistId = Integer.parseInt(selected.split("\\.")[0].trim());
            playlistManager.addSongToPlaylist(playlistId, song.getSongId());
            if (statusLabel != null) {
                statusLabel.setText("Added \"" + song.getSongName() + "\" to a playlist.");
            }
        });
    }

    private static final String APP_TITLE =
            "SmartBeats - Intelligent Offline Music Player with Mood-Based Recommendation";


    // ============================================================
    // PLAY SONG
    // ============================================================

    private void playThisSong(Song song) {

        if (song == null) {

            return;
        }


        try {

            String filePath =
                    song.getFilePath();


            if (filePath == null ||
                    filePath.trim().isEmpty()) {

                if (statusLabel != null) {

                    statusLabel.setText(
                            "Song file not found."
                    );
                }

                return;
            }


            /*
             * Play selected song with its recommended list as the queue so
             * next/previous/auto-advance work like a real player.
             */
            if (recTable != null && recTable.getItems() != null) {
                int index = recTable.getItems().indexOf(song);
                mp.setPlaylist(recTable.getItems(), index < 0 ? 0 : index);
            }
            mp.playSong(song);


            /*
             * Update status
             */
            if (statusLabel != null) {

                statusLabel.setText(
                        "Playing: " +
                                song.getSongName()
                );
            }


        } catch (Exception e) {

            if (statusLabel != null) {

                statusLabel.setText(
                        "Unable to play song."
                );
            }


            System.out.println(
                    "Playback Error: " +
                            e.getMessage()
            );


            e.printStackTrace();
        }
    }


    // ============================================================
    // GO HOME
    // ============================================================

    @FXML
    public void goHome() {

        loadScreen(
                "/fxml/Home.fxml",
                "Smart Music Player"
        );
    }


    // ============================================================
    // GO TO LIBRARY
    // ============================================================

    @FXML
    public void goToLibrary() {

        loadScreen(
                "/fxml/Library.fxml",
                "Smart Music Player - Library"
        );
    }


    // ============================================================
    // GO TO PLAYER
    // ============================================================

    @FXML
    public void goToPlayer() {

        loadScreen(
                "/fxml/Player.fxml",
                "Smart Music Player - Playing"
        );
    }


    // ============================================================
    // LOAD SCREEN
    // ============================================================

    private void loadScreen(
            String fxml,
            String title) {


        try {


            // ====================================================
            // FIND FXML
            // ====================================================

            URL resource =
                    getClass()
                            .getResource(fxml);


            if (resource == null) {

                System.out.println(
                        "FXML file not found: " +
                                fxml
                );

                if (statusLabel != null) {

                    statusLabel.setText(
                            "Page not found: " +
                                    fxml
                    );
                }

                return;
            }


            // ====================================================
            // LOAD FXML
            // ====================================================

            FXMLLoader loader =
                    new FXMLLoader(resource);


            Parent root =
                    loader.load();


            // ====================================================
            // GET CURRENT STAGE
            // ====================================================

            if (recTable == null ||
                    recTable.getScene() == null) {

                return;
            }


            Stage stage =
                    (Stage) recTable
                            .getScene()
                            .getWindow();


            // ====================================================
            // CREATE SCENE
            // ====================================================

            Scene scene =
                    new Scene(
                            root,
                            1100,
                            700
                    );


            // ====================================================
            // SET SCENE
            // ====================================================

            stage.setScene(scene);


            stage.setTitle(title);


            stage.centerOnScreen();


        } catch (Exception e) {

            System.out.println(
                    "Screen Loading Error: " +
                            e.getMessage()
            );


            e.printStackTrace();


            if (statusLabel != null) {

                statusLabel.setText(
                        "Unable to open page."
                );
            }
        }
    }
}
