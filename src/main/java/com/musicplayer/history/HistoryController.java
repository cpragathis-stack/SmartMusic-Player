package com.musicplayer.history;

import com.musicplayer.database.DatabaseManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ResourceBundle;

@SuppressWarnings({"SqlResolve", "SqlNoDataSourceInspection", "SqlDialectInspection"})
public class HistoryController implements Initializable {

    @FXML private TableView<ObservableList<String>> historyTable;
    @FXML private TableColumn<ObservableList<String>, String> colSong;
    @FXML private TableColumn<ObservableList<String>, String> colArtist;
    @FXML private TableColumn<ObservableList<String>, String> colPlays;
    @FXML private TableColumn<ObservableList<String>, String> colSkips;
    @FXML private TableColumn<ObservableList<String>, String> colLast;

    public HistoryController() {
        // Explicit default constructor for JavaFX
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        if (colSong != null) colSong.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().get(0)));
        if (colArtist != null) colArtist.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().get(1)));
        if (colPlays != null) colPlays.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().get(2)));
        if (colSkips != null) colSkips.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().get(3)));
        if (colLast != null) colLast.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().get(4)));
        loadHistory();
    }

    private void loadHistory() {
        ObservableList<ObservableList<String>> data = FXCollections.observableArrayList();
        Connection conn = DatabaseManager.getInstance().getConnection();
        String sql = "SELECT s.song_name, COALESCE(a.artist_name, 'Unknown Artist'), " +
                "h.play_count, h.skip_count, h.last_played " +
                "FROM UserHistory h " +
                "JOIN Songs s ON h.song_id = s.song_id " +
                "LEFT JOIN Artists a ON s.artist_id = a.artist_id " +
                "WHERE h.user_id = " + DatabaseManager.activeUserId() + " " +
                "ORDER BY h.play_count DESC, h.last_played DESC";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                ObservableList<String> row = FXCollections.observableArrayList();
                row.add(rs.getString(1));
                row.add(rs.getString(2));
                row.add(String.valueOf(rs.getInt(3)));
                row.add(String.valueOf(rs.getInt(4)));
                row.add(rs.getString(5) != null ? rs.getString(5) : "Recently");
                data.add(row);
            }
        } catch (SQLException e) {
            System.err.println("Load history error: " + e.getMessage());
        }
        if (historyTable != null) {
            historyTable.setItems(data);
        }
    }

    @FXML
    public void clearHistory() {
        Connection conn = DatabaseManager.getInstance().getConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM UserHistory WHERE user_id = "
                    + DatabaseManager.activeUserId());
            if (historyTable != null) {
                historyTable.getItems().clear();
            }
        } catch (SQLException e) {
            System.err.println("Clear history error: " + e.getMessage());
        }
    }

    @FXML
    public void goHome() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Home.fxml"));
            Parent root = loader.load();
            if (historyTable != null && historyTable.getScene() != null) {
                Stage stage = (Stage) historyTable.getScene().getWindow();
                stage.setScene(new Scene(root, 1100, 700));
                stage.setTitle("Music Player - Home");
                stage.centerOnScreen();
            }
        } catch (IOException e) {
            System.err.println("Error navigating home: " + e.getMessage());
        }
    }
}
