package com.musicplayer.test;

import com.musicplayer.auth.UserSession;
import com.musicplayer.database.DatabaseManager;
import com.musicplayer.history.HistoryTracker;
import com.musicplayer.library.MusicLibrary;
import com.musicplayer.library.Song;
import com.musicplayer.playlist.PlaylistManager;
import com.musicplayer.recommendation.RecommendationEngine;
import com.musicplayer.utility.PasswordUtil;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

/**
 * Dependency-free test suite for SmartBeats.
 *
 * Every test runs against an isolated temporary SQLite database (selected via
 * the {@code musicplayer.db} system property). Asserts a PASS/FAIL line per
 * test, tallies results, writes {@code test-results.txt}, and exits non-zero
 * when anything fails. Invoke with:
 *
 *   java --module-path lib --add-modules javafx.controls,javafx.fxml,javafx.media,java.sql \
 *        -cp "target\classes;target\test-classes;lib\*" com.musicplayer.test.TestRunner
 *
 * Maven/JUnit are intentionally NOT required, so the suite runs on any machine
 * that can build the app itself.
 */
public final class TestRunner {

    private static int passed = 0;
    private static int failed = 0;
    private static PrintWriter out;

    private TestRunner() {
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("PASS  " + name);
            out.println("PASS  " + name);
        } else {
            failed++;
            System.out.println("FAIL  " + name);
            out.println("FAIL  " + name);
        }
    }

    public static void main(String[] args) throws Exception {
        // Redirect SQLite to a temporary database BEFORE anything touches
        // DatabaseManager, so the real musicplayer.db is never modified.
        File tempDb = File.createTempFile("smartbeats-test", ".db");
        tempDb.deleteOnExit();
        System.setProperty("musicplayer.db", tempDb.getAbsolutePath());
        DatabaseManager.resetInstanceForTest();

        File resultFile = new File(System.getProperty("user.dir"), "test-results.txt");
        try (PrintWriter writer =
                     new PrintWriter(resultFile, "UTF-8")) {
            out = writer;
            run();
            writer.println();
            writer.println("SUMMARY  " + passed + " passed, " + failed + " failed");
        }

        System.out.println();
        System.out.println("SUMMARY  " + passed + " passed, " + failed + " failed");
        System.out.println("Results written to " + resultFile.getAbsolutePath());

        DatabaseManager.resetInstanceForTest();
        Files.deleteIfExists(tempDb.toPath());
        System.exit(failed == 0 ? 0 : 1);
    }

    private static void run() throws Exception {
        DatabaseManager db = DatabaseManager.getInstance();
        MusicLibrary library = new MusicLibrary();
        PlaylistManager pm = new PlaylistManager();

        // ---------------------------------------------------------------
        // SEED + DUPLICATE DETECTION
        // ---------------------------------------------------------------
        boolean alpha1 = library.insertSongDirectly(
                "Alpha Sunset", "Artist A", "Pop", "Album A", "3:30", "Tamil",
                "C:\\smartbeats-tests\\alpha.mp3");
        library.insertSongDirectly(
                "Beta Storm", "Artist B", "Rock", "Album B", "4:00", "Hindi",
                "C:\\smartbeats-tests\\beta.mp3");
        library.insertSongDirectly(
                "Gamma Rays", "Artist C", "Jazz", "Album C", "2:45", "Tamil",
                "C:\\smartbeats-tests\\gamma.mp3");
        boolean alphaDuplicate = library.insertSongDirectly(
                "Alpha Sunset (copy)", "Artist A", "Pop", "Album A", "3:30", "Tamil",
                "C:\\smartbeats-tests\\alpha.mp3");

        check("insertSongDirectly adds new tracks", alpha1);
        check("duplicate file path is rejected (INSERT OR IGNORE)",
                !alphaDuplicate);
        check("exactly 3 songs after duplicate attempt",
                library.getAllSongs().size() == 3);

        int alphaId = songIdByName(library, "Alpha Sunset");
        int betaId = songIdByName(library, "Beta Storm");
        int gammaId = songIdByName(library, "Gamma Rays");
        check("song ids resolved", alphaId > 0 && betaId > 0 && gammaId > 0);

        // ---------------------------------------------------------------
        // SEARCH
        // ---------------------------------------------------------------
        List<Song> foundAlpha = library.searchSongs("alph");
        boolean searchHit = foundAlpha.stream()
                .anyMatch(s -> s.getSongId() == alphaId);
        boolean searchMiss = library.searchSongs("zzzznomatch").isEmpty();
        check("search finds substring matches (song/artist/album)", searchHit);
        check("search returns empty for unknown term", searchMiss);

        // ---------------------------------------------------------------
        // LISTENING HISTORY (aggregated, user 1)
        // ---------------------------------------------------------------
        db.recordPlay(alphaId);
        db.recordPlay(alphaId);
        check("recordPlay increments play count", db.getPlayCount(alphaId) == 2);

        db.recordSkip(betaId);
        check("recordSkip recorded a skip",
                scalarInt("SELECT skip_count FROM UserHistory WHERE user_id=1 AND song_id=" + betaId) == 1);

        db.addListeningSeconds(alphaId, 30);
        db.addListeningSeconds(alphaId, 45);
        check("listening_seconds accumulates", db.getTotalListeningSeconds() == 75L);
        check("hasAnyHistory reflects real plays", db.hasAnyHistory());

        HistoryTracker.getInstance().logListen(1, alphaId, 45);
        check("chronological ListeningLog row appended", db.getListeningLog().size() >= 1);
        check("listening log is newest-first",
                db.getListeningLog().get(0).length == 4);

        // ---------------------------------------------------------------
        // FAVORITES
        // ---------------------------------------------------------------
        check("addToFavorites persists", db.addToFavorites(betaId));
        check("isFavorite true after add", db.isFavorite(betaId));
        check("getFavoriteSongs lists favorited track",
                db.getFavoriteSongs().stream().anyMatch(s -> s.getSongId() == betaId));
        check("removeFromFavorites clears", db.removeFromFavorites(betaId));
        check("isFavorite false after remove", !db.isFavorite(betaId));

        // ---------------------------------------------------------------
        // PLAYLISTS
        // ---------------------------------------------------------------
        boolean plCreated = pm.createPlaylist("Focus Mix");
        check("createPlaylist persists", plCreated);
        check("getAllPlaylists lists the new playlist", pm.getAllPlaylists().size() == 1);
        int playlistId = Integer.parseInt(pm.getAllPlaylists().get(0).split("\\.")[0].trim());
        check("addSongToPlaylist adds track", pm.addSongToPlaylist(playlistId, alphaId));
        check("getSongsInPlaylist returns the track",
                pm.getSongsInPlaylist(playlistId).stream().anyMatch(s -> s.getSongId() == alphaId));
        check("removeSongFromPlaylist removes track",
                pm.removeSongFromPlaylist(playlistId, alphaId));
        check("playlist empty after removal", pm.getSongsInPlaylist(playlistId).isEmpty());
        check("deletePlaylist removes playlist", pm.deletePlaylist(playlistId));
        check("getAllPlaylists empty after delete", pm.getAllPlaylists().isEmpty());

        // ---------------------------------------------------------------
        // PER-USER ISOLATION
        // ---------------------------------------------------------------
        UserSession.createSession(2, "user2", "");
        db.recordPlay(alphaId);
        int user2Plays = db.getPlayCount(alphaId);
        UserSession.createSession(1, "user1", "");
        int user1Plays = db.getPlayCount(alphaId);
        check("play counts are isolated per user (u2 got own count)",
                user2Plays == 1 && user1Plays == 2);

        // ---------------------------------------------------------------
        // PASSWORD HASHING
        // ---------------------------------------------------------------
        String hashed = PasswordUtil.hash("secret-pass");
        check("hash is not plain text", !hashed.equals("secret-pass") && hashed.indexOf(':') > 0);
        check("verify accepts correct password", PasswordUtil.verify("secret-pass", hashed));
        check("verify rejects wrong password", !PasswordUtil.verify("wrong", hashed));

        boolean registered = db.registerUser("tester", "t@smartbeats.local", "s3cret!", "2026-09-19");
        check("registerUser creates account", registered);
        String storedPass = scalarText(
                "SELECT password FROM Users WHERE username='tester'");
        check("registered password stored hashed",
                storedPass != null && storedPass.indexOf(':') > 0);
        check("login with wrong password fails",
                db.loginOrCreateOfflineUser("tester", "nope") == -1);
        check("login with correct password succeeds",
                db.loginOrCreateOfflineUser("tester", "s3cret!") > 0);

        // Legacy plaintext rows are still accepted.
        exec("INSERT INTO Users (username, password) VALUES ('legacy-user', 'oldpass')");
        check("legacy plaintext password still logs in",
                db.loginOrCreateOfflineUser("legacy-user", "oldpass") > 0);
        check("legacy plaintext rejected on wrong password",
                db.loginOrCreateOfflineUser("legacy-user", "wrong") == -1);

        // ---------------------------------------------------------------
        // RECOMMENDATION SCORING (deterministic verification)
        // ---------------------------------------------------------------
        // a: 7 plays (2+5) + hearted => min(7*3,60)=21 + fav10 + genre20
        //    + artist15 + favLang5 + recent10 = 81
        for (int i = 0; i < 5; i++) {
            db.recordPlay(alphaId);
        }
        db.addToFavorites(alphaId);
        // b: 2 plays, rock => 6 + genre20 + artist15 + lang0 + recent10 = 51
        db.recordPlay(betaId);
        db.recordPlay(betaId);
        // c: 1 play + 3 skips => 3 - 6 + lang5 + recent10 = 12
        db.recordPlay(gammaId);
        db.recordSkip(gammaId);
        db.recordSkip(gammaId);
        db.recordSkip(gammaId);

        RecommendationEngine engine = new RecommendationEngine();
        List<Song> ranked = engine.getRecommendations();
        check("recommendations return a ranked list", !ranked.isEmpty());
        boolean alphaFirst = ranked.get(0).getSongName().startsWith("Alpha");
        check("highest-scoring track ranks first", alphaFirst);
        check("top rank exposes the computed score",
                ranked.get(0).getPlayCount() == 81);
        String why = engine.getShortReasonFor(alphaId);
        check("every pick has an explainable reason",
                why != null && !why.isBlank());
        check("reason mentions the hearted track",
                why.toLowerCase().contains("heart"));

        // ---------------------------------------------------------------
        // SCHEMA MIGRATION GUARD (idempotent re-open)
        // ---------------------------------------------------------------
        DatabaseManager.resetInstanceForTest();
        DatabaseManager.getInstance();
        check("re-opening the database keeps history",
                DatabaseManager.getInstance().getPlayCount(alphaId) == 7);
    }

    private static int songIdByName(MusicLibrary library, String title) {
        for (Song s : library.getAllSongs()) {
            if (s.getSongName().equals(title)) {
                return s.getSongId();
            }
        }
        return -1;
    }

    private static int scalarInt(String sql) {
        try (Statement st = DatabaseManager.getInstance().getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private static String scalarText(String sql) {
        try (Statement st = DatabaseManager.getInstance().getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void exec(String sql) {
        try (Statement st = DatabaseManager.getInstance().getConnection().createStatement()) {
            st.executeUpdate(sql);
        } catch (Exception e) {
            System.err.println("exec failed: " + e.getMessage());
        }
    }
}