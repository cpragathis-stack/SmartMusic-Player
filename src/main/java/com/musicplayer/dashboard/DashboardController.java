package com.musicplayer.dashboard;

import com.musicplayer.database.DatabaseManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ResourceBundle;

public class DashboardController implements Initializable {

    @FXML private Label totalSongs;
    @FXML private Label totalPlayed;
    @FXML private Label totalFavs;
    @FXML private Label favGenre;
    @FXML private Label totalTime;
    @FXML private BarChart<String, Number> topSongsChart;
    @FXML private PieChart genreChart;

    public DashboardController() {
        // Explicit default constructor for JavaFX
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        loadStats();
        loadTopSongsChart();
        loadGenreChart();
    }

    private int uid() {
        return DatabaseManager.activeUserId();
    }

    private void loadStats() {
        Connection conn = DatabaseManager.getInstance().getConnection();
        try (Statement stmt = conn.createStatement()) {

            if (totalSongs != null) {
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM Songs")) {
                    if (rs.next()) totalSongs.setText(String.valueOf(rs.getInt(1)));
                }
            }

            if (totalPlayed != null) {
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT COALESCE(SUM(play_count), 0) FROM UserHistory WHERE user_id = "
                                + uid())) {
                    if (rs.next()) totalPlayed.setText(String.valueOf(rs.getInt(1)));
                }
            }

            if (totalFavs != null) {
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT COUNT(*) FROM Favorites WHERE user_id = " + uid()
                                + " AND is_favorite = 1")) {
                    if (rs.next()) totalFavs.setText(String.valueOf(rs.getInt(1)));
                }
            }

            if (totalTime != null) {
                long seconds = DatabaseManager.getInstance().getTotalListeningSeconds();
                totalTime.setText(formatListeningTime(seconds));
            }

            if (favGenre != null) {
                String genreSql = "SELECT g.genre_name, SUM(h.play_count) as t " +
                        "FROM UserHistory h JOIN Songs s ON h.song_id = s.song_id " +
                        "JOIN Genres g ON s.genre_id = g.genre_id " +
                        "WHERE h.user_id = " + uid() + " " +
                        "GROUP BY g.genre_name ORDER BY t DESC LIMIT 1";
                try (ResultSet rs = stmt.executeQuery(genreSql)) {
                    if (rs.next()) {
                        favGenre.setText(rs.getString(1));
                    } else {
                        favGenre.setText("Exploring");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Dashboard stats error: " + e.getMessage());
        }
    }

    private String formatListeningTime(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    private void loadTopSongsChart() {
        if (topSongsChart == null) return;
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Play Count");
        Connection conn = DatabaseManager.getInstance().getConnection();
        String sql = "SELECT s.song_name, h.play_count FROM UserHistory h " +
                "JOIN Songs s ON h.song_id = s.song_id " +
                "WHERE h.user_id = " + uid() + " " +
                "ORDER BY h.play_count DESC LIMIT 5";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            boolean hasData = false;
            while (rs.next()) {
                hasData = true;
                String name = rs.getString(1);
                if (name.length() > 15) name = name.substring(0, 12) + "...";
                series.getData().add(new XYChart.Data<>(name, rs.getInt(2)));
            }

            topSongsChart.getData().clear();
            if (hasData) {
                topSongsChart.getData().add(series);
            }
            topSongsChart.setLegendVisible(false);
        } catch (SQLException e) {
            System.err.println("Top songs chart error: " + e.getMessage());
        }
    }

    private void loadGenreChart() {
        if (genreChart == null) return;
        ObservableList<PieChart.Data> data = FXCollections.observableArrayList();
        Connection conn = DatabaseManager.getInstance().getConnection();
        String sql = "SELECT g.genre_name, COUNT(s.song_id) as c " +
                "FROM Songs s JOIN Genres g ON s.genre_id = g.genre_id " +
                "GROUP BY g.genre_name HAVING c > 0";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                data.add(new PieChart.Data(rs.getString(1), rs.getInt(2)));
            }
            genreChart.setData(data);
        } catch (SQLException e) {
            System.err.println("Genre chart error: " + e.getMessage());
        }
    }

    @FXML
    public void goHome() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Home.fxml"));
            Parent root = loader.load();
            if (totalSongs != null && totalSongs.getScene() != null) {
                Stage stage = (Stage) totalSongs.getScene().getWindow();
                stage.setScene(new Scene(root, 1100, 700));
                stage.setTitle("Music Player - Home");
                stage.centerOnScreen();
            }
        } catch (IOException e) {
            System.err.println("Error navigating home: " + e.getMessage());
        }
    }
}
