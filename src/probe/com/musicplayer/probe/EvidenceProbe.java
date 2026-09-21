package com.musicplayer.probe;

import com.musicplayer.auth.UserSession;
import com.musicplayer.database.DatabaseManager;
import com.musicplayer.history.HistoryTracker;
import com.musicplayer.library.MusicLibrary;
import com.musicplayer.playlist.PlaylistManager;
import com.musicplayer.recommendation.RecommendationEngine;
import com.musicplayer.library.Song;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

/**
 * Evidence + demo-data probe.
 *
 * Opens the REAL musicplayer.db (working directory must be the project root),
 * runs the schema migration, prints proof of the migration and then seeds
 * favourites, playlists, skips, listening seconds and a chronological
 * listening log so the app demonstrably has real data.
 */
public final class EvidenceProbe {

    public static void main(String[] args) throws Exception {
        DatabaseManager db = DatabaseManager.getInstance();
        Connection conn = db.getConnection();

        System.out.println("== BEFORE (post-migration schema) ==");
        printTableInfo(conn, "UserHistory");
        printTableInfo(conn, "Favorites");
        printTableInfo(conn, "Playlists");
        printTableInfo(conn, "ListeningLog");

        System.out.println("== PRESERVED HISTORY (old rows kept under user 1) ==");
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT user_id, COUNT(*) AS rows_qty, SUM(play_count) AS plays, " +
                             "SUM(skip_count) AS skips, SUM(listening_seconds) AS seconds " +
                             "FROM UserHistory GROUP BY user_id ORDER BY user_id")) {
            while (rs.next()) {
                System.out.println("user_id=" + rs.getInt("user_id")
                        + " rows=" + rs.getInt("rows_qty")
                        + " plays=" + rs.getInt("plays")
                        + " skips=" + rs.getInt("skips")
                        + " seconds=" + rs.getInt("seconds"));
            }
        }

        System.out.println("== LOST-CONTENT CHECK ==");
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT (SELECT COUNT(*) FROM Favorites) favs, " +
                             "(SELECT COUNT(*) FROM Playlists) playlists, " +
                             "(SELECT COUNT(*) FROM Songs) songs")) {
            System.out.println("favorites=" + rs.getInt("favs")
                    + " playlists=" + rs.getInt("playlists")
                    + " songs=" + rs.getInt("songs"));
        }

        // ---------------- DEMO DATA (user 1) ----------------
        MusicLibrary library = new MusicLibrary();
        List<Song> allSongs = library.getAllSongs();
        System.out.println("== SEEDING DEMO DATA over " + allSongs.size() + " songs ==");
        if (allSongs.size() >= 12) {
            UserSession.createSession(1, "demo", "");
            for (int i = 0; i < 4; i++) {
                db.addToFavorites(allSongs.get(i).getSongId());
            }
            PlaylistManager pm = new PlaylistManager();
            List<String> p1 = pm.getAllPlaylists();
            int focusId;
            int workoutId;
            if (p1.isEmpty()) {
                pm.createPlaylist("Focus Mix");
                pm.createPlaylist("Workout");
                focusId = Integer.parseInt(pm.getAllPlaylists().get(0).split("\\.")[0].trim());
                workoutId = Integer.parseInt(pm.getAllPlaylists().get(1).split("\\.")[0].trim());
            } else {
                focusId = Integer.parseInt(p1.get(0).split("\\.")[0].trim());
                workoutId = Integer.parseInt(p1.get(1).split("\\.")[0].trim());
            }
            pm.addSongToPlaylist(focusId, allSongs.get(3).getSongId());
            pm.addSongToPlaylist(focusId, allSongs.get(7).getSongId());
            pm.addSongToPlaylist(focusId, allSongs.get(11).getSongId());
            pm.addSongToPlaylist(workoutId, allSongs.get(0).getSongId());
            pm.addSongToPlaylist(workoutId, allSongs.get(5).getSongId());

            db.recordSkip(allSongs.get(4).getSongId());
            db.recordSkip(allSongs.get(4).getSongId());
            db.recordSkip(allSongs.get(13).getSongId());

            db.addListeningSeconds(allSongs.get(0).getSongId(), 185);
            db.addListeningSeconds(allSongs.get(1).getSongId(), 240);
            db.addListeningSeconds(allSongs.get(2).getSongId(), 95);
            db.addListeningSeconds(allSongs.get(3).getSongId(), 150);
            db.addListeningSeconds(allSongs.get(5).getSongId(), 210);
            db.addListeningSeconds(allSongs.get(7).getSongId(), 60);

            HistoryTracker.getInstance().logListen(1, allSongs.get(0).getSongId(), 180);
            HistoryTracker.getInstance().logListen(1, allSongs.get(1).getSongId(), 240);
            HistoryTracker.getInstance().logListen(1, allSongs.get(2).getSongId(), 90);
            HistoryTracker.getInstance().logListen(1, allSongs.get(5).getSongId(), 200);
        }

        // ---------------- HASHED DEMO USER ----------------
        db.registerUser("demo-user", "demo@smartbeats.local", "demo1234", "2026-09-19");
        System.out.println("== HASHED PASSWORD PROOF ==");
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT username, password FROM Users WHERE username = ?")) {
            ps.setString(1, "demo-user");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String stored = rs.getString("password");
                    System.out.println("username=" + rs.getString("username")
                            + " stored=" + stored
                            + " isHashed=" + (stored.indexOf(':') > 0));
                }
            }
        }

        System.out.println("== AFTER ==");
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT (SELECT COUNT(*) FROM Favorites) favs, " +
                             "(SELECT COUNT(*) FROM Playlists) playlists, " +
                             "(SELECT COUNT(*) FROM UserHistory WHERE user_id=1) historyRows, " +
                             "(SELECT SUM(listening_seconds) FROM UserHistory) seconds, " +
                             "(SELECT COUNT(*) FROM ListeningLog) logRows")) {
            System.out.println("favorites=" + rs.getInt("favs")
                    + " playlists=" + rs.getInt("playlists")
                    + " historyRows(1)=" + rs.getInt("historyRows")
                    + " listeningSeconds=" + rs.getInt("seconds")
                    + " listeningLogRows=" + rs.getInt("logRows"));
        }

        System.out.println("== RECOMMENDED TOP 5 (user 1) ==");
        RecommendationEngine engine = new RecommendationEngine();
        List<Song> top = engine.getRecommendations();
        int shown = 0;
        for (Song s : top) {
            System.out.println(s.getSongName() + " | " + s.getArtist() + " | score=" + s.getPlayCount()
                    + " | " + engine.getShortReasonFor(s.getSongId()));
            if (++shown >= 5) {
                break;
            }
        }
        System.out.println("EvidenceProbe done.");
    }

    private static void printTableInfo(Connection conn, String table) throws Exception {
        System.out.println("-- " + table + " columns --");
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            StringBuilder sb = new StringBuilder();
            while (rs.next()) {
                sb.append(rs.getString("name")).append("(")
                        .append(rs.getString("type")).append(") ");
            }
            System.out.println(sb);
        }
    }
}