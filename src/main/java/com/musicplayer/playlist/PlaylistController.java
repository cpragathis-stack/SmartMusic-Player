package com.musicplayer.playlist;

import com.musicplayer.library.MusicLibrary;
import com.musicplayer.library.Song;
import com.musicplayer.player.MusicPlayer;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class PlaylistController implements Initializable {

    @FXML private ListView<String> playlistView;
    @FXML private TableView<Song> songTable;
    @FXML private TableColumn<Song, String> colName;
    @FXML private TableColumn<Song, String> colArtist;
    @FXML private TableColumn<Song, String> colGenre;
    @FXML private TableColumn<Song, String> colPlay;
    @FXML private TextField newPlaylistName;
    @FXML private Label playlistTitle;
    @FXML private Label statusLabel;

    private final PlaylistManager pm = new PlaylistManager();
    private final MusicLibrary library = new MusicLibrary();
    private int selectedPlaylistId = -1;

    public PlaylistController() {
        // Explicit default constructor for JavaFX
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupTable();
        loadPlaylists();
    }

    private void setupTable() {
        if (colName != null) colName.setCellValueFactory(new PropertyValueFactory<>("songName"));
        if (colArtist != null) colArtist.setCellValueFactory(new PropertyValueFactory<>("artist"));
        if (colGenre != null) colGenre.setCellValueFactory(new PropertyValueFactory<>("genre"));

        if (colPlay != null) {
            colPlay.setCellFactory(col -> new TableCell<>() {
                final Button btn = new Button("Play");
                {
                    btn.setStyle(
                            "-fx-background-color: #38BDF8;" +
                            "-fx-text-fill: #0B1120;" +
                            "-fx-font-size: 11px;" +
                            "-fx-font-weight: bold;" +
                            "-fx-background-radius: 6;" +
                            "-fx-cursor: hand;" +
                            "-fx-padding: 4 10;");
                    btn.setOnAction(e -> {
                        Song s = getTableView().getItems().get(getIndex());
                        playPlaylistSong(s, getIndex());
                    });
                }

                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(empty ? null : btn);
                }
            });
        }

        // Remove column: lets the user take a song out of the playlist.
        TableColumn<Song, Void> removeCol = new TableColumn<>("Remove");
        removeCol.setPrefWidth(90);
        removeCol.setCellFactory(col -> new TableCell<>() {
            final Button btn = new Button("Remove");
            {
                btn.setStyle(
                        "-fx-background-color: #EF4444;" +
                        "-fx-text-fill: #FFFFFF;" +
                        "-fx-font-size: 11px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-radius: 6;" +
                        "-fx-cursor: hand;" +
                        "-fx-padding: 4 10;");
                btn.setOnAction(e -> {
                    Song s = getTableView().getItems().get(getIndex());
                    boolean removed = pm.removeSongFromPlaylist(selectedPlaylistId, s.getSongId());
                    if (removed) {
                        if (statusLabel != null) {
                            statusLabel.setText("Removed '" + s.getSongName() + "' from the playlist.");
                        }
                        getTableView().getItems().remove(getIndex());
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });
        if (songTable != null) {
            songTable.getColumns().add(removeCol);
            songTable.setRowFactory(tv -> {
                TableRow<Song> row = new TableRow<>();
                ContextMenu menu = new ContextMenu();
                MenuItem removeItem = new MenuItem("Remove from Playlist");
                removeItem.setOnAction(e -> {
                    Song s = row.getItem();
                    if (s != null) {
                        boolean removed = pm.removeSongFromPlaylist(selectedPlaylistId, s.getSongId());
                        if (removed) {
                            if (statusLabel != null) {
                                statusLabel.setText("Removed '" + s.getSongName() + "' from the playlist.");
                            }
                            tv.getItems().remove(s);
                        }
                    }
                });
                menu.getItems().add(removeItem);
                row.contextMenuProperty().bind(
                        javafx.beans.binding.Bindings.when(row.emptyProperty())
                                .then((ContextMenu) null)
                                .otherwise(menu));
                return row;
            });
        }
    }

    private void loadPlaylists() {
        if (playlistView != null) {
            playlistView.setItems(pm.getAllPlaylists());
        }
    }

    @FXML
    public void loadPlaylistSongs() {
        if (playlistView == null) return;
        String selected = playlistView.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        try {
            selectedPlaylistId = Integer.parseInt(selected.split("\\.")[0].trim());
            if (playlistTitle != null) {
                playlistTitle.setText(selected.substring(selected.indexOf(".") + 1).trim());
            }
            if (songTable != null) {
                songTable.setItems(pm.getSongsInPlaylist(selectedPlaylistId));
            }
            if (statusLabel != null && playlistTitle != null) {
                statusLabel.setText("Loaded playlist: " + playlistTitle.getText());
            }
        } catch (Exception e) {
            System.err.println("Error loading playlist songs: " + e.getMessage());
        }
    }

    @FXML
    public void createPlaylist() {
        if (newPlaylistName == null) return;
        String name = newPlaylistName.getText().trim();
        if (name.isEmpty()) {
            if (statusLabel != null) statusLabel.setText("Please enter a playlist name.");
            return;
        }
        if (pm.createPlaylist(name)) {
            if (statusLabel != null) statusLabel.setText("Playlist created: " + name);
            newPlaylistName.clear();
            loadPlaylists();
        }
    }

    @FXML
    public void deletePlaylist() {
        if (selectedPlaylistId < 0) {
            if (statusLabel != null) statusLabel.setText("Please select a playlist first.");
            return;
        }
        if (pm.deletePlaylist(selectedPlaylistId)) {
            if (statusLabel != null) statusLabel.setText("Playlist deleted.");
            selectedPlaylistId = -1;
            if (playlistTitle != null) playlistTitle.setText("Select a Playlist");
            if (songTable != null) songTable.getItems().clear();
            loadPlaylists();
        }
    }

    @FXML
    public void showAddSong() {
        if (selectedPlaylistId < 0) {
            if (statusLabel != null) statusLabel.setText("Please select a playlist first.");
            return;
        }

        List<Song> allSongs = library.getAllSongs();
        if (allSongs.isEmpty()) {
            if (statusLabel != null) statusLabel.setText("Library is empty! Scan a music folder first.");
            return;
        }

        ChoiceDialog<Song> dialog = new ChoiceDialog<>(allSongs.get(0), allSongs);
        dialog.setTitle("Add Song to Playlist");
        dialog.setHeaderText("Add a song to: " + (playlistTitle != null ? playlistTitle.getText() : "Playlist"));
        dialog.setContentText("Choose track:");

        Optional<Song> result = dialog.showAndWait();
        result.ifPresent(song -> {
            boolean added = pm.addSongToPlaylist(selectedPlaylistId, song.getSongId());
            if (added) {
                if (statusLabel != null) statusLabel.setText("Added '" + song.getSongName() + "' to playlist!");
                if (songTable != null) songTable.setItems(pm.getSongsInPlaylist(selectedPlaylistId));
            } else {
                if (statusLabel != null) statusLabel.setText("Song is already in this playlist.");
            }
        });
    }

    private void playPlaylistSong(Song song, int index) {
        if (song == null || songTable == null) return;
        MusicPlayer.getInstance().setPlaylist(songTable.getItems(), index);
        MusicPlayer.getInstance().playSong(song);
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Player.fxml"));
            Parent root = loader.load();
            if (playlistTitle != null && playlistTitle.getScene() != null) {
                Stage stage = (Stage) playlistTitle.getScene().getWindow();
                stage.setScene(new Scene(root, 1100, 700));
                stage.setTitle("Music Player - Now Playing");
                stage.centerOnScreen();
            }
        } catch (IOException e) {
            System.err.println("Error loading Player.fxml: " + e.getMessage());
        }
    }

    @FXML
    public void goHome() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Home.fxml"));
            Parent root = loader.load();
            if (playlistTitle != null && playlistTitle.getScene() != null) {
                Stage stage = (Stage) playlistTitle.getScene().getWindow();
                stage.setScene(new Scene(root, 1100, 700));
                stage.setTitle("Music Player - Home");
                stage.centerOnScreen();
            }
        } catch (IOException e) {
            System.err.println("Error loading Home.fxml: " + e.getMessage());
        }
    }
}
