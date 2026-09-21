package com.musicplayer.playlist;

import com.musicplayer.database.DatabaseManager;
import com.musicplayer.library.Song;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

@SuppressWarnings({"SqlResolve", "SqlNoDataSourceInspection", "SqlDialectInspection"})
public class PlaylistManager {

    public PlaylistManager() {
        // Explicit default constructor
    }

    private Connection getConn() {
        return DatabaseManager.getInstance().getConnection();
    }

    private int activeUserId() {
        return DatabaseManager.activeUserId();
    }

    public boolean createPlaylist(String name) {
        String sql = "INSERT INTO Playlists (user_id, playlist_name, created_date) VALUES (?, ?, ?)";
        try (PreparedStatement p = getConn().prepareStatement(sql)) {
            p.setInt(1, activeUserId());
            p.setString(2, name);
            p.setString(3, LocalDate.now().toString());
            p.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("createPlaylist error: " + e.getMessage());
            return false;
        }
    }

    public boolean deletePlaylist(int playlistId) {
        String sql1 = "DELETE FROM PlaylistSongs WHERE playlist_id = ?";
        String sql2 = "DELETE FROM Playlists WHERE playlist_id = ? AND user_id = ?";
        try (PreparedStatement p1 = getConn().prepareStatement(sql1);
             PreparedStatement p2 = getConn().prepareStatement(sql2)) {
            p1.setInt(1, playlistId);
            p1.executeUpdate();
            p2.setInt(1, playlistId);
            p2.setInt(2, activeUserId());
            p2.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("deletePlaylist error: " + e.getMessage());
            return false;
        }
    }

    public ObservableList<String> getAllPlaylists() {
        ObservableList<String> list = FXCollections.observableArrayList();
        String sql = "SELECT playlist_id, playlist_name FROM Playlists " +
                "WHERE user_id = ? ORDER BY playlist_id ASC";
        try (PreparedStatement p = getConn().prepareStatement(sql)) {
            p.setInt(1, activeUserId());
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getInt("playlist_id") + ". " + rs.getString("playlist_name"));
                }
            }
        } catch (SQLException e) {
            System.err.println("getAllPlaylists error: " + e.getMessage());
        }
        return list;
    }

    public boolean addSongToPlaylist(int playlistId, int songId) {
        String sql = "INSERT OR IGNORE INTO PlaylistSongs (playlist_id, song_id) VALUES (?, ?)";
        try (PreparedStatement p = getConn().prepareStatement(sql)) {
            p.setInt(1, playlistId);
            p.setInt(2, songId);
            int rows = p.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("addSongToPlaylist error: " + e.getMessage());
            return false;
        }
    }

    public boolean removeSongFromPlaylist(int playlistId, int songId) {
        String sql = "DELETE FROM PlaylistSongs WHERE playlist_id = ? AND song_id = ?";
        try (PreparedStatement p = getConn().prepareStatement(sql)) {
            p.setInt(1, playlistId);
            p.setInt(2, songId);
            int rows = p.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("removeSongFromPlaylist error: " + e.getMessage());
            return false;
        }
    }

    public ObservableList<Song> getSongsInPlaylist(int playlistId) {
        ObservableList<Song> songs = FXCollections.observableArrayList();
        String sql = "SELECT s.song_id, s.song_name, " +
                "COALESCE(a.artist_name, 'Unknown Artist') as artist_name, " +
                "COALESCE(g.genre_name, 'Unknown Genre') as genre_name, " +
                "s.album, s.duration, s.language, s.file_path " +
                "FROM PlaylistSongs ps " +
                "JOIN Songs s ON ps.song_id = s.song_id " +
                "LEFT JOIN Artists a ON s.artist_id = a.artist_id " +
                "LEFT JOIN Genres g ON s.genre_id = g.genre_id " +
                "WHERE ps.playlist_id = ? " +
                "ORDER BY s.song_name ASC";
        try (PreparedStatement p = getConn().prepareStatement(sql)) {
            p.setInt(1, playlistId);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    songs.add(new Song(
                            rs.getInt("song_id"),
                            rs.getString("song_name"),
                            rs.getString("artist_name"),
                            rs.getString("genre_name"),
                            rs.getString("album"),
                            rs.getString("duration"),
                            rs.getString("language"),
                            rs.getString("file_path")));
                }
            }
        } catch (SQLException e) {
            System.err.println("getSongsInPlaylist error: " + e.getMessage());
        }
        return songs;
    }
}
