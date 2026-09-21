package com.musicplayer.dashboard;

import com.musicplayer.auth.UserSession;
import com.musicplayer.database.DatabaseManager;
import com.musicplayer.library.MusicLibrary;
import com.musicplayer.library.Song;
import com.musicplayer.player.MusicPlayer;
import com.musicplayer.player.PlayerBarController;
import com.musicplayer.playlist.PlaylistManager;
import com.musicplayer.recommendation.RecommendationEngine;
import com.musicplayer.utility.CoverArt;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

@SuppressWarnings({"SqlResolve", "SqlNoDataSourceInspection", "SqlDialectInspection", "unchecked"})
public class MainShellController implements Initializable {

    // Sidebar buttons
    @FXML private Button btnHome;
    @FXML private Button btnExplore;
    @FXML private Button btnLibrary;
    @FXML private Button btnRecommendations;
    @FXML private Button btnDashboard;
    @FXML private ListView<String> sidebarPlaylistList;

    // Top Navbar
    @FXML private TextField globalSearchField;
    @FXML private HBox genreChipsContainer;
    @FXML private HBox profilePill;
    @FXML private Label usernameLabel;
    @FXML private Label userAvatarLabel;

    // Center Dynamic Host
    @FXML private ScrollPane mainScrollPane;
    @FXML private VBox centerContent;

    private final MusicPlayer player = MusicPlayer.getInstance();
    private final MusicLibrary library = new MusicLibrary();
    private final PlaylistManager playlistManager = new PlaylistManager();
    private final RecommendationEngine recEngine = new RecommendationEngine();

    private Song currentActiveSong;
    private ObservableList<Song> currentQueue = FXCollections.observableArrayList();
    private boolean isShuffle = false;
    private boolean isRepeat = false;
    private Button activeNavButton;
    private Button activeChipButton;

    private boolean nowPlayingVisible;
    private boolean shortcutsAttached;

    // Now Playing widgets (built once, refreshed by listeners)
    private ImageView npCoverView;
    private Label npTitleLabel;
    private Label npArtistLabel;
    private Label npGenreLabel;
    private Label npElapsedLabel;
    private Label npDurationLabel;
    private Slider npSeekSlider;
    private Button npPlayBtn;
    private Button npHeartBtn;
    private Button npShuffleBtn;
    private Button npRepeatBtn;
    private Button npMuteBtn;
    private Slider npVolumeSlider;
    private ListView<Song> npQueueList;

    public MainShellController() {
        // Explicit default constructor for JavaFX
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        String user = UserSession.getCurrentUser();
        if (user == null || user.trim().isEmpty()) {
            user = "User";
        }
        if (usernameLabel != null) {
            usernameLabel.setText(user);
        }
        if (userAvatarLabel != null) {
            userAvatarLabel.setText(user.substring(0, 1).toUpperCase());
        }
        if (profilePill != null) {
            Tooltip.install(profilePill, new Tooltip("Profile menu"));
            profilePill.setOnMouseClicked(e -> openProfileMenu());
            profilePill.setCursor(javafx.scene.Cursor.HAND);
        }

        activeNavButton = btnHome;

        loadSidebarPlaylists();
        loadDynamicGenreChips();
        setupSearch();
        setupPlayerEngine();
        setupNowPlayingHooks();
        player.restoreSession();

        // Show Home View by default
        showHome();
    }

    private void setupNowPlayingHooks() {
        PlayerBarController.openNowPlayingHook = this::showNowPlaying;

        player.addOnSongChangeListener(song -> {
            if (nowPlayingVisible) {
                Platform.runLater(this::refreshNowPlayingView);
            }
        });
        player.addOnPlaybackStateChangeListener(playing -> {
            if (npPlayBtn != null) {
                npPlayBtn.setText(playing ? "Pause" : "Play");
            }
        });
        player.addOnProgressUpdateListener(duration -> {
            if (nowPlayingVisible && npSeekSlider != null) {
                Platform.runLater(() -> {
                    double total = player.getDurationSeconds();
                    double seconds = duration.toSeconds();
                    npSeekSlider.setMax(Math.max(1, total));
                    npSeekSlider.setValue(seconds);
                    npElapsedLabel.setText(formatTime(seconds));
                    if (total > 0) npDurationLabel.setText(formatTime(total));
                });
            }
        });

        Platform.runLater(() -> {
            if (centerContent != null && centerContent.getScene() != null) {
                attachShortcuts(centerContent.getScene());
            }
        });
    }

    private void attachShortcuts(Scene scene) {
        if (shortcutsAttached || scene == null) {
            return;
        }
        shortcutsAttached = true;
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getTarget() instanceof TextInputControl) {
                return;
            }
            KeyCode code = event.getCode();
            switch (code) {
                case SPACE -> player.togglePlayPause();
                case RIGHT -> player.seek(player.getCurrentTimeSeconds() + 5);
                case LEFT -> player.seek(player.getCurrentTimeSeconds() - 5);
                case UP -> player.setVolume(Math.min(1.0, player.getVolume() + 0.05));
                case DOWN -> player.setVolume(Math.max(0.0, player.getVolume() - 0.05));
                case N -> {
                    player.playNext();
                    if (nowPlayingVisible) refreshNowPlayingView();
                }
                case P -> {
                    player.playPrevious();
                    if (nowPlayingVisible) refreshNowPlayingView();
                }
                case S -> {
                    player.setShuffle(!player.isShuffle());
                    refreshTransportButtons();
                }
                case R -> {
                    player.setRepeat(!player.isRepeat());
                    refreshTransportButtons();
                }
                case M -> {
                    player.toggleMute();
                    refreshTransportButtons();
                }
                case F -> toggleFavorite();
                default -> {
                    return;
                }
            }
            event.consume();
        });
    }

    private void setActiveNav(Button btn) {
        if (activeNavButton != null) {
            activeNavButton.getStyleClass().remove("sidebar-nav-btn-active");
            activeNavButton.getStyleClass().add("sidebar-nav-btn");
        }
        activeNavButton = btn;
        if (activeNavButton != null) {
            activeNavButton.getStyleClass().remove("sidebar-nav-btn");
            activeNavButton.getStyleClass().add("sidebar-nav-btn-active");
        }
    }

    private void loadDynamicGenreChips() {
        if (genreChipsContainer == null) return;
        genreChipsContainer.getChildren().clear();

        Button allChip = new Button("All");
        allChip.getStyleClass().add("filter-chip-active");
        activeChipButton = allChip;
        allChip.setOnAction(e -> {
            setActiveChip(allChip);
            showHome();
        });
        genreChipsContainer.getChildren().add(allChip);

        List<String> genres = library.getAllGenres();
        for (String g : genres) {
            if (g.equalsIgnoreCase("ALL")) continue;
            Button chip = new Button(g);
            chip.getStyleClass().add("filter-chip");
            chip.setMinWidth(100);
            chip.setOnAction(e -> {
                setActiveChip(chip);
                filterByGenreView(g);
            });
            genreChipsContainer.getChildren().add(chip);
        }
    }

    private void setActiveChip(Button chip) {
        if (activeChipButton != null) {
            activeChipButton.getStyleClass().remove("filter-chip-active");
            activeChipButton.getStyleClass().add("filter-chip");
        }
        activeChipButton = chip;
        if (activeChipButton != null) {
            activeChipButton.getStyleClass().remove("filter-chip");
            activeChipButton.getStyleClass().add("filter-chip-active");
        }
    }

    private void setupSearch() {
        if (globalSearchField == null) return;
        globalSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.trim().isEmpty()) {
                showSearchResults(newVal.trim());
            } else if (activeNavButton == btnHome) {
                showHome();
            }
        });
    }

    private void setupPlayerEngine() {
        player.setOnTrackFinishedListener(() -> {
            Platform.runLater(() -> {
                Song next = player.playNext();
                if (next != null) {
                    // Player bar listens for song-change events and updates itself.
                }
            });
        });

        ObservableList<Song> all = library.getAllSongs();
        if (!all.isEmpty()) {
            currentActiveSong = all.get(0);
            player.setPlaylist(all, 0);
        }
    }

    public void loadSidebarPlaylists() {
        if (sidebarPlaylistList != null) {
            sidebarPlaylistList.setItems(playlistManager.getAllPlaylists());
            sidebarPlaylistList.setCellFactory(list -> new javafx.scene.control.ListCell<String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }
                    String safe = item.isBlank() ? "Unnamed Playlist" : item;
                    Label name = new Label(safe);
                    name.setMaxWidth(210);
                    name.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
                    name.setStyle("-fx-text-fill: #CBD5E1; -fx-font-size: 12px; -fx-font-weight: bold;");
                    if (!safe.equals(item)) {
                        name.setStyle("-fx-text-fill: #7F8C8D; -fx-font-size: 12px; -fx-font-style: italic;");
                    }
                    Tooltip.install(name, new Tooltip(safe));
                    setText(null);
                    setGraphic(name);
                }
            });
        }
    }

    // ============================================================================================================================= HOME VIEW =============================================================================================================================
    @FXML
    public void showHome() {
        setActiveNav(btnHome);
        if (centerContent == null) return;
        centerContent.getChildren().clear();

        // 1. Hero Greeting Banner
        VBox heroBox = new VBox(6);
        String name = usernameLabel != null ? usernameLabel.getText() : "User";
        String greeting = getGreetingTime() + ", " + name;
        Label heroGreeting = new Label(greeting);
        heroGreeting.getStyleClass().add("hero-greeting");

        Label heroSub = new Label("Your full-fledged offline music library and smart recommendation hub.");
        heroSub.getStyleClass().add("section-subtitle");
        heroBox.getChildren().addAll(heroGreeting, heroSub);
        centerContent.getChildren().add(heroBox);

        // 2. Quick Access Top 6 Grid
        Label quickTitle = new Label("Jump Back In");
        quickTitle.getStyleClass().add("section-header-title");
        centerContent.getChildren().add(quickTitle);

        GridPane quickGrid = new GridPane();
        quickGrid.setHgap(16);
        quickGrid.setVgap(14);

        ObservableList<Song> allSongs = library.getAllSongs();
        int count = Math.min(allSongs.size(), 6);
        for (int i = 0; i < count; i++) {
            Song s = allSongs.get(i);
            HBox card = createQuickPlayCard(s, allSongs, i);
            quickGrid.add(card, i % 2, i / 2);
            GridPane.setHgrow(card, Priority.ALWAYS);
        }
        centerContent.getChildren().add(quickGrid);

        // 3. Featured Offline Music Mixes
        Label featuredTitle = new Label("Featured Offline Tracks");
        featuredTitle.getStyleClass().add("section-header-title");
        centerContent.getChildren().add(featuredTitle);

        HBox musicCardsRow = new HBox(16);
        for (Song s : allSongs) {
            musicCardsRow.getChildren().add(createMusicCard(s, allSongs));
            if (musicCardsRow.getChildren().size() >= 6) break;
        }
        ScrollPane scrollCards = new ScrollPane(musicCardsRow);
        scrollCards.setFitToHeight(true);
        scrollCards.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        centerContent.getChildren().add(scrollCards);

        // 4. Made For You (Offline AI Recommendations)
        Label recTitle = new Label("Made For You - Offline AI Recommendations");
        recTitle.getStyleClass().add("section-header-title");
        centerContent.getChildren().add(recTitle);

        ObservableList<Song> recs = recEngine.getRecommendations();
        HBox recCardsRow = new HBox(16);
        for (Song s : recs) {
            recCardsRow.getChildren().add(createMusicCard(s, recs));
            if (recCardsRow.getChildren().size() >= 6) break;
        }
        ScrollPane scrollRec = new ScrollPane(recCardsRow);
        scrollRec.setFitToHeight(true);
        scrollRec.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        centerContent.getChildren().add(scrollRec);

        // 5. Complete Track List Table
        Label tableTitle = new Label("All Songs in Library");
        tableTitle.getStyleClass().add("section-header-title");
        centerContent.getChildren().add(tableTitle);

        TableView<Song> table = createSongTableView(allSongs);
        centerContent.getChildren().add(table);
    }

    private String getGreetingTime() {
        int hour = LocalTime.now().getHour();
        if (hour < 12) return "Good morning";
        if (hour < 17) return "Good afternoon";
        return "Good evening";
    }

    private HBox createQuickPlayCard(Song song, ObservableList<Song> playlist, int index) {
        HBox card = new HBox(12);
        card.getStyleClass().add("quick-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPrefHeight(56);

        javafx.scene.image.Image coverImage = CoverArt.getCover(song);
        ImageView cover = new ImageView(coverImage);
        cover.setFitWidth(56);
        cover.setFitHeight(56);
        cover.setPreserveRatio(false);
        cover.setStyle("-fx-background-radius: 8 0 0 8;");

        String cardTitle = (song.getSongName() == null || song.getSongName().isBlank()) ? "Unknown Title" : song.getSongName();
        String cardArtist = (song.getArtist() == null || song.getArtist().isBlank()) ? "Unknown Artist" : song.getArtist();
        Label title = new Label(cardTitle);
        title.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
        title.getStyleClass().add("quick-card-title");
        Tooltip.install(title, new Tooltip(cardTitle + " - " + cardArtist));
        HBox.setHgrow(title, Priority.ALWAYS);

        Label artist = new Label(cardArtist);
        artist.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
        artist.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");

        card.getChildren().addAll(cover, title, artist);
        card.setOnMouseClicked(e -> playTrack(song, playlist, index));
        return card;
    }

    private VBox createMusicCard(Song song, ObservableList<Song> playlist) {
        VBox card = new VBox(8);
        card.getStyleClass().add("music-card");
        card.setPrefWidth(170);
        card.setMaxWidth(170);

        StackPane art = new StackPane();
        art.getStyleClass().add("music-card-artwork");

        javafx.scene.image.Image coverImage = CoverArt.getCover(song);
        ImageView cover = new ImageView(coverImage);
        cover.setFitWidth(140);
        cover.setFitHeight(140);
        cover.setPreserveRatio(false);
        cover.setStyle("-fx-background-radius: 8;");
        art.getChildren().add(cover);

        Button playOverlay = new Button("Play");
        playOverlay.getStyleClass().add("music-card-play-overlay");
        playOverlay.setOpacity(0);
        playOverlay.setOnAction(e -> playTrack(song, playlist, playlist.indexOf(song)));
        art.getChildren().add(playOverlay);
        art.setOnMouseEntered(e -> playOverlay.setOpacity(1));
        art.setOnMouseExited(e -> playOverlay.setOpacity(0));

        String cardTitle = (song.getSongName() == null || song.getSongName().isBlank()) ? "Unknown Title" : song.getSongName();
        String cardArtist = (song.getArtist() == null || song.getArtist().isBlank()) ? "Unknown Artist" : song.getArtist();
        Label title = new Label(cardTitle);
        title.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
        title.getStyleClass().add("music-card-title");
        Tooltip.install(title, new Tooltip(cardTitle + " - " + cardArtist));

        Label artist = new Label(cardArtist);
        artist.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
        artist.getStyleClass().add("music-card-subtitle");

        Label badge = new Label(song.getGenre() == null || song.getGenre().isBlank() ? "Local" : song.getGenre());
        badge.getStyleClass().add("music-card-badge");

        card.getChildren().addAll(art, title, artist, badge);
        card.setOnMouseClicked(e -> playTrack(song, playlist, playlist.indexOf(song)));
        return card;
    }

    private TableView<Song> createSongTableView(ObservableList<Song> songs) {
        TableView<Song> table = new TableView<>(songs);
        table.getStyleClass().add("spotify-table");
        table.setPrefHeight(350);
        table.setFixedCellSize(52);
        table.setPlaceholder(new Label("No local audio is indexed yet. Use Import MP3 or Scan Folder to add your music."));

        TableColumn<Song, Number> colIndex = new TableColumn<>("#");
        colIndex.setPrefWidth(40);
        colIndex.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : String.valueOf(getIndex() + 1));
            }
        });

        TableColumn<Song, String> colTitle = new TableColumn<>("Title");
        colTitle.setCellValueFactory(new PropertyValueFactory<>("songName"));
        colTitle.setCellFactory(col -> new TableCell<Song, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Song song = getTableView().getItems().get(getIndex());
                ImageView cover = new ImageView(CoverArt.getCover(song));
                cover.setFitWidth(40);
                cover.setFitHeight(40);
                cover.setPreserveRatio(false);
                cover.setStyle("-fx-background-radius: 5;");
                String songName = (song.getSongName() == null || song.getSongName().isBlank())
                        ? "Unknown Title" : song.getSongName();
                Label title = new Label(songName);
                title.setMaxWidth(180);
                title.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
                title.setStyle("-fx-text-fill: #F8FAFC; -fx-font-weight: bold; -fx-font-size: 13px;");
                Label sub = new Label(song.getAlbum() == null || song.getAlbum().isBlank() ? (song.getGenre() == null || song.getGenre().isBlank() ? "Local" : song.getGenre()) : song.getAlbum());
                sub.setMaxWidth(180);
                sub.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
                sub.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 11px;");
                VBox box = new VBox(3, title, sub);
                Tooltip.install(box, new Tooltip(songName));
                setText(null);
                setGraphic(new HBox(10, cover, box));
            }
        });
        colTitle.setPrefWidth(260);

        TableColumn<Song, String> colArtist = new TableColumn<>("Artist");
        colArtist.setCellValueFactory(new PropertyValueFactory<>("artist"));
        colArtist.setPrefWidth(200);

        TableColumn<Song, String> colAlbum = new TableColumn<>("Album");
        colAlbum.setCellValueFactory(new PropertyValueFactory<>("album"));
        colAlbum.setPrefWidth(180);

        TableColumn<Song, String> colGenre = new TableColumn<>("Genre");
        colGenre.setCellValueFactory(new PropertyValueFactory<>("genre"));
        colGenre.setPrefWidth(130);

        TableColumn<Song, String> colDuration = new TableColumn<>("Duration");
        colDuration.setCellValueFactory(new PropertyValueFactory<>("duration"));
        colDuration.setPrefWidth(90);

        TableColumn<Song, String> colPlay = new TableColumn<>("Play");
        colPlay.setPrefWidth(80);
        colPlay.setCellFactory(col -> new TableCell<>() {
            final Button btn = new Button("Play");
            {
                btn.setStyle("-fx-background-color: #38BDF8; -fx-text-fill: black; -fx-font-weight: bold; -fx-background-radius: 500; -fx-cursor: hand; -fx-padding: 4 10;");
                btn.setOnAction(e -> {
                    Song s = getTableView().getItems().get(getIndex());
                    playTrack(s, songs, getIndex());
                });
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        table.getColumns().add(colIndex);
        table.getColumns().add(colTitle);
        table.getColumns().add(colArtist);
        table.getColumns().add(colAlbum);
        table.getColumns().add(colGenre);
        table.getColumns().add(colDuration);
        table.getColumns().add(colPlay);

        table.setRowFactory(tv -> {
            TableRow<Song> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    playTrack(row.getItem(), songs, row.getIndex());
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

        return table;
    }

    private ContextMenu createSongContextMenu(TableRow<Song> row) {
        ContextMenu ctx = new ContextMenu();

        MenuItem playItem = new MenuItem("Play");
        playItem.setOnAction(e -> {
            if (!row.isEmpty()) {
                playTrack(row.getItem(), row.getTableView().getItems(), row.getIndex());
            }
        });

        MenuItem addPlaylistItem = new MenuItem("Add to Playlist");
        addPlaylistItem.setOnAction(e -> addToPlaylist(row));

        MenuItem removeItem = new MenuItem("Remove from View");
        removeItem.setOnAction(e -> {
            if (row.getTableView() != null) {
                row.getTableView().getItems().remove(row.getItem());
            }
        });

        ctx.getItems().addAll(playItem, addPlaylistItem, removeItem);
        return ctx;
    }

    private void addToPlaylist(TableRow<Song> row) {
        Song song = row.getItem();
        if (song == null) {
            return;
        }
        ObservableList<String> playlists = playlistManager.getAllPlaylists();
        if (playlists.isEmpty()) {
            TextInputDialog createDlg = new TextInputDialog();
            createDlg.setTitle("Create Playlist");
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
        dialog.setTitle("Add to Playlist");
        dialog.setHeaderText("Select a playlist:");
        dialog.setContentText("Playlist:");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(selected -> {
            int playlistId = Integer.parseInt(selected.split("\\.")[0].trim());
            playlistManager.addSongToPlaylist(playlistId, song.getSongId());
        });
    }

    // ==================== EXPLORE / GENRES VIEW ====================
    @FXML
    public void showExplore() {
        setActiveNav(btnExplore);
        if (centerContent == null) return;
        centerContent.getChildren().clear();

        Label title = new Label("Browse by Genre & Mood");
        title.getStyleClass().add("hero-greeting");
        centerContent.getChildren().add(title);

        FlowPane flow = new FlowPane(16, 16);
        List<String> genres = library.getAllGenres();

        String[] colors = {"#0284C7", "#0EA5E9", "#38BDF8", "#6366F1", "#8B5CF6", "#059669", "#D97706", "#2563EB", "#0D9488"};
        int cIdx = 0;

        for (String g : genres) {
            if (g.equalsIgnoreCase("ALL")) continue;
            String color = colors[cIdx % colors.length];
            cIdx++;

            VBox card = new VBox(8);
            card.getStyleClass().add("music-card");
            card.setPrefSize(220, 130);
            card.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 8px; -fx-padding: 16;");

            Label catTitle = new Label(g);
            catTitle.setStyle("-fx-font-size: 20px; -fx-font-weight: 900; -fx-text-fill: white;");

            Label badge = new Label("Explore Tracks");
            badge.setStyle("-fx-background-color: rgba(0,0,0,0.4); -fx-text-fill: white; -fx-padding: 3 8; -fx-background-radius: 4; -fx-font-size: 11px;");

            card.getChildren().addAll(catTitle, badge);
            card.setOnMouseClicked(e -> filterByGenreView(g));
            flow.getChildren().add(card);
        }
        centerContent.getChildren().add(flow);
    }

    private void filterByGenreView(String genre) {
        if (centerContent == null) return;
        centerContent.getChildren().clear();
        Label title = new Label("Genre: " + genre);
        title.getStyleClass().add("hero-greeting");
        centerContent.getChildren().addAll(title, createSongTableView(library.filterByGenre(genre)));
    }

    // ============================================================================================================================= LIBRARY VIEW =============================================================================================================================
    @FXML
    public void showLibrary() {
        setActiveNav(btnLibrary);
        if (centerContent == null) return;
        centerContent.getChildren().clear();

        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Your Offline Library");
        title.getStyleClass().add("hero-greeting");
        HBox.setHgrow(title, Priority.ALWAYS);

        Button btnAddSongs = new Button("Add Songs");
        btnAddSongs.setStyle("-fx-background-color: #EC4899; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 18; -fx-background-radius: 500; -fx-cursor: hand;");
        btnAddSongs.setOnAction(e -> addNewSongs());

        Button btnImport = new Button("Import Audio Files");
        btnImport.setStyle("-fx-background-color: #38BDF8; -fx-text-fill: black; -fx-font-weight: bold; -fx-padding: 10 18; -fx-background-radius: 500; -fx-cursor: hand;");
        btnImport.setOnAction(e -> importAudioFiles());

        Button btnScan = new Button("Scan Audio Folder");
        btnScan.setStyle("-fx-background-color: #509BF5; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 18; -fx-background-radius: 500; -fx-cursor: hand;");
        btnScan.setOnAction(e -> scanLocalFolder());

        library.syncSongsFolder();

        header.getChildren().addAll(title, btnAddSongs, btnImport, btnScan);
        centerContent.getChildren().addAll(header, createSongTableView(library.getAllSongs()));
    }

    /**
     * Opens a file picker, copies the chosen .mp3 files into the project
     * "songs" folder and indexes them immediately.
     */
    @FXML
    public void addNewSongs() {
        if (centerContent == null || centerContent.getScene() == null) return;
        FileChooser fc = new FileChooser();
        fc.setTitle("Select MP3 Songs to Add");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("MP3 Songs", "*.mp3"),
                new FileChooser.ExtensionFilter("Audio Files", "*.mp3", "*.wav", "*.m4a", "*.aac", "*.ogg", "*.flac")
        );
        List<File> files = fc.showOpenMultipleDialog(centerContent.getScene().getWindow());
        if (files != null && !files.isEmpty()) {
            int added = library.addSongsToLibrary(files);
            library.syncSongsFolder();
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Add Songs");
            alert.setHeaderText(added + " song(s) added to your library!");
            alert.setContentText("The MP3 files were copied into the project 'songs' folder and are loaded automatically every launch.");
            alert.showAndWait();
            loadDynamicGenreChips();
            showLibrary();
        }
    }

    // ============================================================================================================================= AI RECOMMENDATIONS VIEW =============================================================================================================================
    @FXML
    public void showRecommendations() {
        setActiveNav(btnRecommendations);
        if (centerContent == null) return;
        centerContent.getChildren().clear();
        showRecommendations(RecommendationEngine.Mood.ALL);
    }

    private void showRecommendations(RecommendationEngine.Mood mood) {
        centerContent.getChildren().clear();

        Label title = new Label("For You - Offline AI Mood-Based Recommendations");
        title.getStyleClass().add("hero-greeting");

        HBox moodRow = new HBox(8);
        moodRow.setAlignment(Pos.CENTER_LEFT);
        for (RecommendationEngine.Mood m : RecommendationEngine.Mood.values()) {
            Button chip = new Button(m.getDisplay());
            chip.getStyleClass().add("filter-chip");
            if (m == mood) {
                chip.getStyleClass().add("filter-chip-active");
            }
            chip.setOnAction(e -> showRecommendations(m));
            moodRow.getChildren().add(chip);
        }
        centerContent.getChildren().addAll(title, moodRow);

        ObservableList<Song> recs = recEngine.getRecommendations(mood);

        if (!recEngine.hasListeningHistory()) {
            Label sub = new Label("Recommendations need a little listening first.");
            sub.getStyleClass().add("empty-state-title");
            Label hint = new Label("Play a few songs from your library - favourites, plays and skips are used to build your personal picks. Everything runs offline.");
            hint.getStyleClass().add("empty-state-hint");
            centerContent.getChildren().addAll(sub, hint);
            return;
        }

        Label sub = new Label("Ranked from your favourites, play counts, skips, favourite language and listening time. Every track shows why it was chosen. Filter by mood to narrow your picks.");
        sub.getStyleClass().add("section-subtitle");

        centerContent.getChildren().addAll(sub, createRecommendationTableView(recs));
    }

    private TableView<Song> createRecommendationTableView(ObservableList<Song> songs) {
        TableView<Song> table = new TableView<>(songs);
        table.getStyleClass().add("spotify-table");
        table.setPrefHeight(400);
        table.setFixedCellSize(52);
        table.setPlaceholder(new Label("No recommendations yet. Play or favourite songs to personalise this list."));

        TableColumn<Song, Number> colIndex = new TableColumn<>("#");
        colIndex.setPrefWidth(40);
        colIndex.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : String.valueOf(getIndex() + 1));
            }
        });

        TableColumn<Song, String> colTitle = new TableColumn<>("Title");
        colTitle.setCellValueFactory(new PropertyValueFactory<>("songName"));
        colTitle.setCellFactory(col -> new TableCell<Song, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Song song = getTableView().getItems().get(getIndex());
                ImageView cover = new ImageView(CoverArt.getCover(song));
                cover.setFitWidth(40);
                cover.setFitHeight(40);
                cover.setPreserveRatio(false);
                cover.setStyle("-fx-background-radius: 5;");
                String songName = (song.getSongName() == null || song.getSongName().isBlank())
                        ? "Unknown Title" : song.getSongName();
                String artistName = (song.getArtist() == null || song.getArtist().isBlank())
                        ? "Unknown Artist" : song.getArtist();
                Label title = new Label(songName);
                title.setMaxWidth(160);
                title.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
                title.setStyle("-fx-text-fill: #F8FAFC; -fx-font-weight: bold; -fx-font-size: 13px;");
                Label sub = new Label(artistName);
                sub.setMaxWidth(160);
                sub.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
                sub.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 11px;");
                VBox box = new VBox(3, title, sub);
                Tooltip.install(box, new Tooltip(songName + " - " + artistName));
                setText(null);
                setGraphic(new HBox(10, cover, box));
            }
        });
        colTitle.setPrefWidth(220);

        TableColumn<Song, String> colArtist = new TableColumn<>("Artist");
        colArtist.setCellValueFactory(new PropertyValueFactory<>("artist"));
        colArtist.setPrefWidth(160);

        TableColumn<Song, String> colGenre = new TableColumn<>("Genre");
        colGenre.setCellValueFactory(new PropertyValueFactory<>("genre"));
        colGenre.setPrefWidth(110);

        TableColumn<Song, String> colDuration = new TableColumn<>("Duration");
        colDuration.setCellValueFactory(new PropertyValueFactory<>("duration"));
        colDuration.setPrefWidth(80);

        TableColumn<Song, String> colWhy = new TableColumn<>("Why we picked it");
        colWhy.setPrefWidth(320);
        colWhy.setCellValueFactory(col -> new javafx.beans.property.SimpleStringProperty(""));
        colWhy.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                        setText(null);
                        setStyle("");
                        return;
                    }
                    Song song = getTableView().getItems().get(getIndex());
                    setText("Why: " + recEngine.getShortReasonFor(song.getSongId()));
                    setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 11px;");
                }
            });

        TableColumn<Song, String> colPlay = new TableColumn<>("Play");
        colPlay.setPrefWidth(80);
        colPlay.setCellFactory(col -> new TableCell<>() {
            final Button btn = new Button("Play");
            {
                btn.setStyle("-fx-background-color: #38BDF8; -fx-text-fill: black; -fx-font-weight: bold; -fx-background-radius: 500; -fx-cursor: hand; -fx-padding: 4 10;");
                btn.setOnAction(e -> {
                    Song s = getTableView().getItems().get(getIndex());
                    playTrack(s, songs, getIndex());
                });
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        table.getColumns().addAll(colIndex, colTitle, colArtist, colGenre, colDuration, colWhy, colPlay);

        table.setRowFactory(tv -> {
            TableRow<Song> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    playTrack(row.getItem(), songs, row.getIndex());
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

        return table;
    }

    // ============================================================================================================================= DASHBOARD & ANALYTICS VIEW =============================================================================================================================
    // =======================================================
    // NOW PLAYING VIEW
    // =======================================================
    @FXML
    public void showNowPlaying() {
        setActiveNav(null);
        if (centerContent == null) {
            return;
        }

        if (nowPlayingVisible && !centerContent.getChildren().isEmpty()) {
            refreshNowPlayingView();
            return;
        }

        nowPlayingVisible = true;
        centerContent.getChildren().clear();

        Label title = new Label("Now Playing");
        title.getStyleClass().add("hero-greeting");
        centerContent.getChildren().add(title);

        HBox mainRow = new HBox(36);
        mainRow.setAlignment(Pos.CENTER_LEFT);

        StackPane artFrame = new StackPane();
        artFrame.getStyleClass().add("np-art-frame");
        npCoverView = new ImageView();
        npCoverView.setFitWidth(340);
        npCoverView.setFitHeight(340);
        npCoverView.setPreserveRatio(false);
        artFrame.getChildren().add(npCoverView);
        mainRow.getChildren().add(artFrame);

        VBox details = new VBox(12);
        details.setAlignment(Pos.CENTER_LEFT);

        npTitleLabel = new Label("Select a song");
        npTitleLabel.getStyleClass().add("np-title");
        npArtistLabel = new Label("SmartBeats Offline");
        npArtistLabel.getStyleClass().add("np-artist");
        npGenreLabel = new Label("");
        npGenreLabel.getStyleClass().add("np-genre");

        HBox timeRow = new HBox(10);
        timeRow.setAlignment(Pos.CENTER_LEFT);
        npElapsedLabel = new Label("0:00");
        npElapsedLabel.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 12px;");
        npSeekSlider = new Slider(0, 1, 0);
        npSeekSlider.setPrefWidth(420);
        npSeekSlider.getStyleClass().add("np-seek");
        npDurationLabel = new Label("0:00");
        npDurationLabel.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 12px;");
        HBox.setHgrow(npSeekSlider, Priority.ALWAYS);
        timeRow.getChildren().addAll(npElapsedLabel, npSeekSlider, npDurationLabel);
        npSeekSlider.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
            if (!isChanging) {
                player.seek(npSeekSlider.getValue());
            }
        });

        HBox transport = new HBox(12);
        transport.setAlignment(Pos.CENTER_LEFT);
        npShuffleBtn = controlButton("Shuffle");
        npShuffleBtn.setOnAction(e -> {
            player.setShuffle(!player.isShuffle());
            refreshTransportButtons();
        });
        Button prevBtn = controlButton("Previous");
        prevBtn.setOnAction(e -> {
            player.playPrevious();
            refreshNowPlayingView();
        });
        npPlayBtn = new Button("Play");
        npPlayBtn.getStyleClass().add("np-play");
        npPlayBtn.setOnAction(e -> player.togglePlayPause());
        Button nextBtn = controlButton("Next");
        nextBtn.setOnAction(e -> {
            player.playNext();
            refreshNowPlayingView();
        });
        npRepeatBtn = controlButton("Repeat");
        npRepeatBtn.setOnAction(e -> {
            player.setRepeat(!player.isRepeat());
            refreshTransportButtons();
        });
        transport.getChildren().addAll(npShuffleBtn, prevBtn, npPlayBtn, nextBtn, npRepeatBtn);

HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_LEFT);
        npHeartBtn = new Button("Like");
        npHeartBtn.getStyleClass().add("np-heart");
        npHeartBtn.setOnAction(e -> toggleFavorite());
        npMuteBtn = controlButton("Mute");
        npMuteBtn.setOnAction(e -> {
            player.toggleMute();
            refreshTransportButtons();
        });
        npVolumeSlider = new Slider(0, 100, player.getVolume() * 100);
        npVolumeSlider.setPrefWidth(140);
        npVolumeSlider.getStyleClass().add("np-seek");
        npVolumeSlider.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
            if (!isChanging) {
                player.setVolume(npVolumeSlider.getValue() / 100.0);
            }
        });
        npVolumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (npVolumeSlider.isValueChanging()) {
                player.setVolume(newVal.doubleValue() / 100.0);
            }
        });
        actions.getChildren().addAll(npHeartBtn, npMuteBtn, npVolumeSlider);
        details.getChildren().addAll(npTitleLabel, npArtistLabel, npGenreLabel, timeRow, transport, actions);
        mainRow.getChildren().add(details);
        centerContent.getChildren().add(mainRow);

        Label queueTitle = new Label("Up Next");
        queueTitle.getStyleClass().add("section-header-title");
        centerContent.getChildren().add(queueTitle);

        npQueueList = new ListView<>();
        npQueueList.getStyleClass().add("np-queue");
        npQueueList.setPrefHeight(260);
        npQueueList.setFixedCellSize(56);
        npQueueList.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Song item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                ImageView cover = new ImageView(CoverArt.getCover(item));
                cover.setFitWidth(40);
                cover.setFitHeight(40);
                cover.setPreserveRatio(false);
                Label pos = new Label(String.format("%02d", getIndex() + 1));
                pos.setStyle("-fx-text-fill: #64748B; -fx-font-weight: bold; -fx-font-size: 12px;");
                String qName = (item.getSongName() == null || item.getSongName().isBlank()) ? "Unknown Title" : item.getSongName();
                String qArtist = (item.getArtist() == null || item.getArtist().isBlank()) ? "Unknown Artist" : item.getArtist();
                Label name = new Label(qName);
                name.setMaxWidth(260);
                name.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
                name.setStyle("-fx-text-fill: #F8FAFC; -fx-font-weight: bold; -fx-font-size: 13px;");
                Label artist = new Label(qArtist);
                artist.setMaxWidth(260);
                artist.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
                artist.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 11px;");
                HBox cell = new HBox(10, pos, cover, new VBox(2, name, artist));
                cell.setAlignment(Pos.CENTER_LEFT);
                Tooltip.install(name, new Tooltip(qName + " - " + qArtist));
                setText(null);
                setGraphic(cell);
            }
        });
        npQueueList.setOnMouseClicked(event -> {
            Song selected = npQueueList.getSelectionModel().getSelectedItem();
            if (selected == null || event.getClickCount() < 2) {
                return;
            }
            player.playIndex(npQueueList.getSelectionModel().getSelectedIndex());
            refreshNowPlayingView();
        });
        centerContent.getChildren().add(npQueueList);

        refreshNowPlayingView();
    }

    private Button controlButton(String text) {
        Button btn = new Button(text);
        btn.getStyleClass().add("np-control-btn");
        return btn;
    }

    private void refreshNowPlayingView() {
        Song song = player.getCurrentSong();
        if (npTitleLabel == null) {
            return;
        }
        if (song == null) {
            npTitleLabel.setText("Select a song");
            npArtistLabel.setText("SmartBeats Offline");
            npGenreLabel.setText("");
            if (npQueueList != null) {
                npQueueList.setItems(FXCollections.observableArrayList());
            }
            return;
        }
        npTitleLabel.setText(song.getSongName());
        npArtistLabel.setText(song.getArtist());
        npGenreLabel.setText(song.getGenre() + "  \u2022  " + song.getLanguage());
        javafx.scene.image.Image cover = CoverArt.getCover(song);
        if (cover != null) {
            npCoverView.setImage(cover);
        }
        boolean fav = DatabaseManager.getInstance().isFavorite(song.getSongId());
        npHeartBtn.setText(fav ? "Liked" : "Like");
        npHeartBtn.setStyle("-fx-text-fill: " + (fav ? "#38BDF8;" : "#94A3B8;") + " -fx-font-weight: bold; -fx-background-color: transparent; -fx-cursor: hand;");

        ObservableList<Song> queue = player.getPlaylist();
        if (npQueueList != null) {
            npQueueList.setItems(queue);
            if (!queue.isEmpty()) {
                npQueueList.getSelectionModel().select(player.getCurrentIndex());
                npQueueList.scrollTo(Math.max(0, player.getCurrentIndex()));
            }
        }
        refreshTransportButtons();
    }

    private void refreshTransportButtons() {
        if (npShuffleBtn != null) {
            npShuffleBtn.setStyle("-fx-text-fill: " + (player.isShuffle() ? "#38BDF8;" : "#94A3B8;") + " -fx-font-weight: bold; -fx-background-color: transparent; -fx-cursor: hand;");
        }
        if (npRepeatBtn != null) {
            npRepeatBtn.setStyle("-fx-text-fill: " + (player.isRepeat() ? "#38BDF8;" : "#94A3B8;") + " -fx-font-weight: bold; -fx-background-color: transparent; -fx-cursor: hand;");
        }
        if (npMuteBtn != null) {
            npMuteBtn.setText(player.isMuted() ? "Muted" : "Mute");
            npMuteBtn.setStyle("-fx-text-fill: " + (player.isMuted() ? "#38BDF8;" : "#94A3B8;") + " -fx-font-weight: bold; -fx-background-color: transparent; -fx-cursor: hand;");
        }
        if (npVolumeSlider != null && !npVolumeSlider.isValueChanging()) {
            npVolumeSlider.setValue(player.getVolume() * 100);
        }
    }

    private void toggleFavorite() {
        Song song = player.getCurrentSong();
        if (song == null) {
            return;
        }
        DatabaseManager db = DatabaseManager.getInstance();
        if (db.isFavorite(song.getSongId())) {
            db.removeFromFavorites(song.getSongId());
        } else {
            db.addToFavorites(song.getSongId());
        }
        if (npHeartBtn != null) {
            boolean fav = db.isFavorite(song.getSongId());
            npHeartBtn.setText(fav ? "Liked" : "Like");
        }
    }

    private String formatTime(double seconds) {
        int value = (int) Math.max(0, seconds);
        return value / 60 + ":" + String.format("%02d", value % 60);
    }

    @FXML
    public void showDashboard() {
        setActiveNav(btnDashboard);
        if (centerContent == null) return;
        centerContent.getChildren().clear();

        Label title = new Label("Your Listening Analytics");
        title.getStyleClass().add("hero-greeting");
        centerContent.getChildren().add(title);

        HBox statsRow = new HBox(16);
        statsRow.getChildren().addAll(
                createStatCard("Total Tracks", String.valueOf(library.getAllSongs().size()), "#38BDF8"),
                createStatCard("Playlists", String.valueOf(playlistManager.getAllPlaylists().size()), "#509BF5"),
                createStatCard("Top Genre Affinity", recEngine.getFavoriteGenre(), "#38BDF8")
        );
        centerContent.getChildren().add(statsRow);

        HBox chartsRow = new HBox(20);
        chartsRow.setPrefHeight(320);

        BarChart<String, Number> topChart = new BarChart<>(new CategoryAxis(), new NumberAxis());
        topChart.setTitle("Most Played Tracks");
        topChart.setStyle("-fx-text-fill: white;");
        topChart.setLegendVisible(false);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        Connection conn = DatabaseManager.getInstance().getConnection();
        String sql = "SELECT s.song_name, h.play_count FROM UserHistory h JOIN Songs s ON h.song_id=s.song_id " +
                "WHERE h.user_id = " + DatabaseManager.activeUserId() + " ORDER BY h.play_count DESC LIMIT 5";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                series.getData().add(new XYChart.Data<>(rs.getString(1), rs.getInt(2)));
            }
        } catch (SQLException ignored) {}
        topChart.getData().add(series);
        HBox.setHgrow(topChart, Priority.ALWAYS);

        chartsRow.getChildren().add(topChart);
        centerContent.getChildren().add(chartsRow);
    }

    private VBox createStatCard(String label, String value, String color) {
        VBox card = new VBox(6);
        card.getStyleClass().add("music-card");
        card.setPrefWidth(220);
        card.setAlignment(Pos.CENTER);

        Label val = new Label(value);
        val.setStyle("-fx-font-size: 26px; -fx-font-weight: 900; -fx-text-fill: " + color + ";");

        Label lbl = new Label(label);
        lbl.getStyleClass().add("music-card-subtitle");

        card.getChildren().addAll(val, lbl);
        return card;
    }

    private void openProfileMenu() {
        if (profilePill == null) return;
        ContextMenu menu = new ContextMenu();
        MenuItem viewProfile = new MenuItem("View Profile");
        viewProfile.setOnAction(e -> showProfile());
        MenuItem history = new MenuItem("Listening History");
        history.setOnAction(e -> showListeningHistory());
        MenuItem liked = new MenuItem("Liked Songs");
        liked.setOnAction(e -> showFavorites());
        MenuItem playlist = new MenuItem("Create Playlist");
        playlist.setOnAction(e -> createNewPlaylist());
        MenuItem logout = new MenuItem("Logout");
        logout.setOnAction(e -> handleLogout());
        menu.getItems().addAll(viewProfile, history, liked, playlist, new SeparatorMenuItem(), logout);
        menu.show(profilePill, Side.BOTTOM, 0, 0);
    }

    public void showProfile() {
        setActiveNav(btnLibrary);
        if (centerContent == null) return;
        centerContent.getChildren().clear();

        String user = usernameLabel != null ? usernameLabel.getText() : "User";
        String initial = user.isEmpty() ? "U" : user.substring(0, 1).toUpperCase();

        StackPane bigAvatar = new StackPane();
        bigAvatar.setPrefSize(84, 84);
        bigAvatar.setMaxSize(84, 84);
        bigAvatar.setStyle("-fx-background-color: linear-gradient(to bottom right, #38BDF8, #4F46E5); "
                + "-fx-background-radius: 500px; -fx-border-color: #7DD3FC; -fx-border-radius: 500px; -fx-border-width: 2;");
        Label bigLetter = new Label(initial);
        bigLetter.setStyle("-fx-font-size: 36px; -fx-font-weight: bold; -fx-text-fill: #FFFFFF;");
        bigAvatar.getChildren().add(bigLetter);

        Label name = new Label(user);
        name.setStyle("-fx-font-size: 30px; -fx-font-weight: 900; -fx-text-fill: #F8FAFC;");
        Label caption = new Label("Your Profile");
        caption.setStyle("-fx-font-size: 13px; -fx-text-fill: #94A3B8;");
        VBox identity = new VBox(4);
        identity.setAlignment(Pos.CENTER_LEFT);
        identity.getChildren().addAll(name, caption);

        HBox header = new HBox(20);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getChildren().addAll(bigAvatar, identity);
        centerContent.getChildren().add(header);

        int likes = 0;
        try (Statement stmt = DatabaseManager.getInstance().getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM Favorites WHERE user_id = "
                     + DatabaseManager.activeUserId() + " AND is_favorite = 1")) {
            if (rs.next()) likes = rs.getInt(1);
        } catch (SQLException ignored) {}

        int plays = 0;
        try (Statement stmt = DatabaseManager.getInstance().getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COALESCE(SUM(play_count),0) FROM UserHistory WHERE user_id = "
                     + DatabaseManager.activeUserId())) {
            if (rs.next()) plays = rs.getInt(1);
        } catch (SQLException ignored) {}

        HBox statsRow = new HBox(16);
        statsRow.getChildren().addAll(
                createStatCard("Tracks in Library", String.valueOf(library.getAllSongs().size()), "#38BDF8"),
                createStatCard("Liked Songs", String.valueOf(likes), "#EC4899"),
                createStatCard("Playlists", String.valueOf(playlistManager.getAllPlaylists().size()), "#509BF5"),
                createStatCard("Total Plays", String.valueOf(plays), "#22C55E")
        );
        centerContent.getChildren().add(statsRow);

        Label recentTitle = new Label("Recent Listening");
        recentTitle.getStyleClass().add("section-header-title");
        centerContent.getChildren().add(recentTitle);
        centerContent.getChildren().add(buildHistoryTable());

        HBox quickRow = new HBox(12);
        Button dashBtn = new Button("Open Listening Dashboard");
        dashBtn.setStyle("-fx-background-color: #38BDF8; -fx-text-fill: #0B1120; -fx-font-weight: bold; "
                + "-fx-background-radius: 500px; -fx-padding: 9 18; -fx-cursor: hand;");
        dashBtn.setOnAction(e -> showDashboard());
        Button favBtn = new Button("Open Liked Songs");
        favBtn.setStyle("-fx-background-color: #1E293B; -fx-text-fill: #38BDF8; -fx-font-weight: bold; "
                + "-fx-background-radius: 500px; -fx-padding: 9 18; -fx-cursor: hand; -fx-border-color: #334155;");
        favBtn.setOnAction(e -> showFavorites());
        quickRow.getChildren().addAll(dashBtn, favBtn);
        centerContent.getChildren().add(quickRow);
    }

    public void showListeningHistory() {
        setActiveNav(btnDashboard);
        if (centerContent == null) return;
        centerContent.getChildren().clear();
        Label title = new Label("Listening History");
        title.getStyleClass().add("hero-greeting");
        centerContent.getChildren().add(title);
        centerContent.getChildren().add(buildHistoryTable());
    }

    private TableView<ObservableList<String>> buildHistoryTable() {
        TableView<ObservableList<String>> table = new TableView<>();
        table.setPrefHeight(260);
        table.setStyle("-fx-background-color: transparent; -fx-border-color: #334155; -fx-border-radius: 12px; -fx-background-radius: 12px;");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<ObservableList<String>, String> cSong = new TableColumn<>("Song");
        cSong.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().get(0)));
        TableColumn<ObservableList<String>, String> cArtist = new TableColumn<>("Artist");
        cArtist.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().get(1)));
        TableColumn<ObservableList<String>, String> cPlays = new TableColumn<>("Plays");
        cPlays.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().get(2)));
        cPlays.setPrefWidth(70);
        TableColumn<ObservableList<String>, String> cLast = new TableColumn<>("Last Played");
        cLast.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().get(3)));
        cLast.setPrefWidth(140);
        table.getColumns().addAll(cSong, cArtist, cPlays, cLast);

        ObservableList<ObservableList<String>> rows = FXCollections.observableArrayList();
        try (Statement stmt = DatabaseManager.getInstance().getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT s.song_name, COALESCE(a.artist_name, 'Unknown Artist'), h.play_count, "
                     + "COALESCE(h.last_played, 'Recently') "
                     + "FROM UserHistory h JOIN Songs s ON h.song_id = s.song_id "
                     + "LEFT JOIN Artists a ON s.artist_id = a.artist_id "
                     + "ORDER BY h.play_count DESC, h.last_played DESC LIMIT 15")) {
            while (rs.next()) {
                ObservableList<String> row = FXCollections.observableArrayList();
                row.add(rs.getString(1));
                row.add(rs.getString(2));
                row.add(String.valueOf(rs.getInt(3)));
                row.add(rs.getString(4));
                rows.add(row);
            }
        } catch (SQLException e) {
            System.err.println("Profile history error: " + e.getMessage());
        }
        table.setItems(rows);
        return table;
    }

    // ============================================================================================================================= PLAYLISTS & FAVORITES =============================================================================================================================
    @FXML
    public void showFavorites() {
        if (centerContent == null) return;
        centerContent.getChildren().clear();
        Label title = new Label("Liked Songs");
        title.getStyleClass().add("hero-greeting");

        ObservableList<Song> favs = FXCollections.observableArrayList();
        Connection conn = DatabaseManager.getInstance().getConnection();
        String sql = "SELECT s.song_id, s.song_name, a.artist_name, g.genre_name, s.album, s.duration, s.language, s.year, s.file_path, s.cover_color " +
                "FROM Favorites f JOIN Songs s ON f.song_id = s.song_id " +
                "LEFT JOIN Artists a ON s.artist_id = a.artist_id " +
                "LEFT JOIN Genres g ON s.genre_id = g.genre_id " +
                "WHERE f.is_favorite = 1 AND f.user_id = " + DatabaseManager.activeUserId();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                favs.add(new Song(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10)));
            }
        } catch (SQLException ignored) {}

        centerContent.getChildren().addAll(title, createSongTableView(favs));
    }

    @FXML
    public void handlePlaylistClick() {
        if (sidebarPlaylistList == null) return;
        String selected = sidebarPlaylistList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        int playlistId = Integer.parseInt(selected.split("\\.")[0].trim());
        String name = selected.substring(selected.indexOf(".") + 1).trim();

        if (centerContent == null) return;
        centerContent.getChildren().clear();
        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Playlist: " + name);
        title.getStyleClass().add("hero-greeting");
        HBox.setHgrow(title, Priority.ALWAYS);

        Button btnAdd = new Button("Add Song");
        btnAdd.setStyle("-fx-background-color: #38BDF8; -fx-text-fill: black; -fx-font-weight: bold; -fx-padding: 8 16; -fx-background-radius: 500; -fx-cursor: hand;");
        btnAdd.setOnAction(e -> {
            ObservableList<Song> all = library.getAllSongs();
            if (all.isEmpty()) return;
            ChoiceDialog<Song> dialog = new ChoiceDialog<>(all.get(0), all);
            dialog.setTitle("Add to " + name);
            dialog.setHeaderText("Select Track:");
            Optional<Song> res = dialog.showAndWait();
            res.ifPresent(song -> {
                playlistManager.addSongToPlaylist(playlistId, song.getSongId());
                handlePlaylistClick();
            });
        });

        header.getChildren().addAll(title, btnAdd);
        centerContent.getChildren().addAll(header, createSongTableView(playlistManager.getSongsInPlaylist(playlistId)));
    }

    @FXML
    public void createNewPlaylist() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Create Playlist");
        dialog.setHeaderText("Name your offline playlist:");
        dialog.setContentText("Title:");
        Optional<String> res = dialog.showAndWait();
        res.ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                playlistManager.createPlaylist(name.trim());
                loadSidebarPlaylists();
            }
        });
    }

    private void showSearchResults(String query) {
        if (centerContent == null) return;
        centerContent.getChildren().clear();
        Label title = new Label("Search Results for \"" + query + "\"");
        title.getStyleClass().add("hero-greeting");
        centerContent.getChildren().addAll(title, createSongTableView(library.searchSongs(query)));
    }

    // ============================================================================================================================= PLAYER BAR ACTIONS =============================================================================================================================
    public void playTrack(Song song, ObservableList<Song> playlist, int index) {
        if (song == null) return;
        currentActiveSong = song;
        currentQueue = playlist;
        player.setPlaylist(playlist, index);
        player.playSong(song);
        if (nowPlayingVisible) {
            refreshNowPlayingView();
        }
        // The persistent player bar updates itself via MusicPlayer listeners.
    }

    @FXML
    public void importAudioFiles() {
        if (centerContent == null || centerContent.getScene() == null) return;
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Audio / MP3 Files to Import into Music Player");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Audio Files (*.mp3, *.wav, *.m4a, *.aac, *.ogg, *.flac)", "*.mp3", "*.wav", "*.m4a", "*.aac", "*.ogg", "*.flac"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        List<File> files = fc.showOpenMultipleDialog(centerContent.getScene().getWindow());
        if (files != null && !files.isEmpty()) {
            int count = library.importFiles(files);
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Import Complete");
            alert.setHeaderText("Imported " + count + " Track(s)!");
            alert.setContentText("Metadata (title, artist, album, genre, duration) was read from each file. Existing files were skipped as duplicates.");
            alert.showAndWait();
            loadDynamicGenreChips();
            showLibrary();
        }
    }

    @FXML
    public void scanLocalFolder() {
        if (centerContent == null || centerContent.getScene() == null) return;
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Select Audio Folder to Scan");
        File dir = dc.showDialog(centerContent.getScene().getWindow());
        if (dir != null) {
            Task<Integer> scanTask = library.scanFolderTask(dir);
            Alert progressAlert = new Alert(Alert.AlertType.INFORMATION);
            progressAlert.setTitle("Scanning Local Music");
            progressAlert.setHeaderText("Preparing local music scan...");
            progressAlert.setContentText("Please keep this window open while SmartBeats indexes your files.");
            progressAlert.getButtonTypes().clear();
            scanTask.messageProperty().addListener((observable, oldMessage, newMessage) ->
                    progressAlert.setHeaderText(newMessage));
            scanTask.setOnSucceeded(event -> {
                progressAlert.close();
                Alert complete = new Alert(Alert.AlertType.INFORMATION);
                complete.setTitle("Scan Completed");
                complete.setHeaderText("Indexed " + scanTask.getValue() + " new local track(s).");
                complete.setContentText("Metadata was read from each file and only real file paths are stored.");
                complete.showAndWait();
                loadDynamicGenreChips();
                showLibrary();
            });
            scanTask.setOnFailed(event -> {
                progressAlert.close();
                new Alert(Alert.AlertType.ERROR, "Could not scan this folder: "
                        + scanTask.getException().getMessage()).showAndWait();
            });
            Thread scanThread = new Thread(scanTask, "smartbeats-folder-scan");
            scanThread.setDaemon(true);
            scanThread.start();
            progressAlert.show();
        }
    }

    @FXML
    public void handleLogout() {
        UserSession.logout();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Login.fxml"));
            Parent root = loader.load();
            if (centerContent != null && centerContent.getScene() != null) {
                Stage stage = (Stage) centerContent.getScene().getWindow();
                stage.setScene(new Scene(root, 780, 640));
                stage.setTitle("SmartBeats - Intelligent Offline Music Player (v2)");
                stage.setResizable(true);
                stage.centerOnScreen();
            }
        } catch (IOException e) {
            System.err.println("Error on logout: " + e.getMessage());
        }
    }
}
