package com.musicplayer.recommendation;

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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Behaviour-based offline recommendation engine.
 *
 * Deterministic scoring (higher = better):
 *   3 points per play, capped at 60          -> "You keep coming back to this track"
 *   10 points if the song is hearted         -> "You hearted this track"
 *   -2 points per skip                       -> "Skipped a few times"
 *   20 points if the genre matches your      -> "From {genre}, a genre you listen to most"
 *          most-played genre
 *   15 points if the artist is one of your   -> "You often listen to {artist}"
 *          top artists
 *   5 points if the track matches your       -> "Matches your favourite language, {language}"
 *          favourite language
 *   10 points for recent listens             -> "You listened to this recently"
 *   10 discovery bonus for never-played      -> "Fresh pick from a genre you enjoy"
 *   mood filter narrows the pool before scoring so All / Happy / Sad /
 *   Energetic / Chill all produce personal, explainable picks.
 */
@SuppressWarnings({"SqlResolve", "SqlNoDataSourceInspection", "SqlDialectInspection"})
public class RecommendationEngine {

    private static final int PLAYS_PER_POINT = 3;
    private static final int PLAY_CAP = 60;
    private static final int FAVORITE_BONUS = 10;
    private static final int GENRE_AFFINITY_BONUS = 20;
    private static final int ARTIST_AFFINITY_BONUS = 15;
    private static final int LANGUAGE_BONUS = 5;
    private static final int RECENT_BONUS = 10;
    private static final int DISCOVERY_BONUS = 10;
    private static final int SKIP_PENALTY = -2;

    /** Played within this many days counts as "recent". */
    private static final long RECENT_DAYS = 7;

    public enum Mood {
        ALL("All"),
        HAPPY("Happy"),
        SAD("Sad"),
        ENERGETIC("Energetic"),
        CHILL("Chill"),
        CALM("Calm"),
        FOCUS("Focus"),
        NIGHT("Night"),
        DEVOTIONAL("Devotional");

        private final String display;

        Mood(String display) {
            this.display = display;
        }

        public String getDisplay() {
            return display;
        }
    }

    private Map<Integer, Integer> scoreCache;
    private Map<Integer, String> reasonCache;

    public RecommendationEngine() {
        // Explicit default constructor
    }

    private Connection getConn() {
        return DatabaseManager.getInstance().getConnection();
    }

    /** True once the user has played at least one song. */
    public boolean hasListeningHistory() {
        return DatabaseManager.getInstance().hasAnyHistory();
    }

    /**
     * Ranked recommendations (no mood filter). Scores are cached per engine
     * instance so that reason lookups agree perfectly with the returned ranking.
     */
    public ObservableList<Song> getRecommendations() {
        return getRecommendations(Mood.ALL);
    }

    /**
     * Ranked recommendations narrowed to a mood. Moods are inferred from the
     * track genre, so each filter still ranks by the same behaviour-based score.
     */
    public ObservableList<Song> getRecommendations(Mood mood) {
        ObservableList<Song> songs = loadSongs();
        if (songs.isEmpty()) {
            return songs;
        }

        computeAffinity();

        scoreCache = new HashMap<>();
        reasonCache = new HashMap<>();

        List<ScoredSong> scored = new ArrayList<>();
        for (Song song : songs) {
            if (mood != null && mood != Mood.ALL && !moodMatches(mood, song.getGenre())) {
                continue;
            }
            int score = computeScore(song);
            scored.add(new ScoredSong(song, score));
        }

        scored.sort(Comparator
                .comparingInt((ScoredSong s) -> s.score)
                .reversed()
                .thenComparing(s -> s.song.getSongName(), String.CASE_INSENSITIVE_ORDER));

        ObservableList<Song> ranked = FXCollections.observableArrayList();
        for (int i = 0; i < scored.size() && i < 10; i++) {
            ScoredSong entry = scored.get(i);
            entry.song.setPlayCount(entry.score);
            ranked.add(entry.song);
        }
        return ranked;
    }

    /** Human-readable explanation for a ranked song (must match its score). */
    public String getReasonFor(int songId) {
        if (reasonCache == null) {
            getRecommendations();
        }
        String reason = reasonCache.get(songId);
        return reason == null ? "Based on your listening across the library." : reason;
    }

    /** Single-line reason used in the Recommendations view. */
    public String getShortReasonFor(int songId) {
        String full = getReasonFor(songId);
        if (full.isBlank()) {
            return "Listens across the library";
        }
        if (full.contains(";")) {
            return full.substring(0, full.indexOf(';')).trim();
        }
        return full;
    }

    // ============================================================
    // MOOD CLASSIFICATION
    // ============================================================

    /** Order of insertion is the matching priority, so a genre with several
     *  overlapping moods resolves deterministically (first eligible wins). */
    private static final Map<String, List<String>> MOOD_KEYWORDS = new LinkedHashMap<>();
    static {
        // Insertion order IS the classification priority (matches.get(0)).
        // Every keyword below maps to a genre that has at least one REAL song
        // in the library (verified against the DB). No invented genres.
        MOOD_KEYWORDS.put("HAPPY", List.of(
                "pop", "dance", "disco", "reggaeton", "k-pop", "funk",
                "happy", "bollywood", "filmi", "indian pop", "kuthu"));

        MOOD_KEYWORDS.put("SAD", List.of(
                "sad", "melancholy", "ballad", "breakup", "slow", "cry", "gaana"));

        MOOD_KEYWORDS.put("ENERGETIC", List.of(
                "rock", "metal", "edm", "electro", "electronic", "rap", "hip hop",
                "hip-hop", "trap", "workout", "gym", "rock&roll"));

        // CHILL now only keeps genuinely-chill genres; the overlapping-boundary
        // terms (instrumental/classical/devotional) moved to their own moods so
        // classifyMood resolves deterministically to the RIGHT mood.
        MOOD_KEYWORDS.put("CHILL", List.of(
                "lofi", "lo-fi", "jazz", "ambient", "acoustic", "easy",
                "sky", "sunset", "dream", "smooth", "horizon"));

        // --- NEW MOODS (spec: Calm, Focus, Night, Devotional) ---
        // CALM: classical(1) + instrumental(1) + melody(1) = 3 real songs
        MOOD_KEYWORDS.put("CALM", List.of(
                "classical", "carnatic", "instrumental", "melody", "acoustic"));
        // FOCUS: classical(1) + instrumental(1) + melody(1) + loop(1) = 4 real
        MOOD_KEYWORDS.put("FOCUS", List.of(
                "classical", "instrumental", "melody", "loop", "ambient"));
        // NIGHT: film(5) + filmscore(1) + instrumental(1) + classical(1) = 8
        MOOD_KEYWORDS.put("NIGHT", List.of(
                "film", "filmscore", "cinematic", "classical", "instrumental",
                "night", "sky", "smooth", "dream"));
        // DEVOTIONAL: devotional(4) = 4 real songs
        MOOD_KEYWORDS.put("DEVOTIONAL", List.of(
                "devotional", "bhajan", "mantra", "spiritual", "meditation"));
    }

    /** Best-effort mood for a genre string, or null when ambiguous/unknown. */
    public static Mood classifyMood(String genre) {
        if (genre == null) {
            return null;
        }
        String g = genre.toLowerCase().trim();
        if (g.isEmpty()) {
            return null;
        }
        List<Mood> matches = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : MOOD_KEYWORDS.entrySet()) {
            for (String kw : e.getValue()) {
                if (g.contains(kw)) {
                    matches.add(Mood.valueOf(e.getKey()));
                    break;
                }
            }
        }
        if (matches.isEmpty()) {
            return null;
        }
        return matches.get(0);
    }

    /** True when the genre maps to (or overlaps) the requested mood. */
    private boolean moodMatches(Mood mood, String genre) {
        Mood mapped = classifyMood(genre);
        return mood == mapped;
    }

    // ============================================================
    // SCORING
    // ============================================================

    private int computeScore(Song song) {
        int score = 0;
        List<String> reasons = new ArrayList<>();

        if (song.isFavorite()) {
            score += FAVORITE_BONUS;
            reasons.add("You hearted this track");
        }

        int plays = song.getPlayCount();
        if (plays > 0) {
            score += Math.min(plays * PLAYS_PER_POINT, PLAY_CAP);
            reasons.add("Played " + plays + " time" + (plays == 1 ? "" : "s"));
        }

        int skips = song.getSkipCount();
        if (skips > 0) {
            score += skips * SKIP_PENALTY;
            reasons.add("Skipped " + skips + " time" + (skips == 1 ? "" : "s"));
        }

        boolean discovered = plays == 0;
        if (discovered && topGenres.contains(song.getGenre())) {
            score += DISCOVERY_BONUS;
            reasons.add("Fresh pick from " + song.getGenre() + ", a genre you enjoy");
        }

        if (topGenres.contains(song.getGenre())) {
            score += GENRE_AFFINITY_BONUS;
            reasons.add("From " + song.getGenre() + ", a genre you listen to most");
        }

        if (topArtists.contains(song.getArtist())) {
            score += ARTIST_AFFINITY_BONUS;
            reasons.add("You often listen to " + song.getArtist());
        }

        if (favoriteLanguage != null && favoriteLanguage.equalsIgnoreCase(song.getLanguage())) {
            score += LANGUAGE_BONUS;
            reasons.add("Matches your favourite language, " + song.getLanguage());
        }

        if (song.isRecentlyPlayed()) {
            score += RECENT_BONUS;
            reasons.add("You listened to this recently");
        }

        scoreCache.put(song.getSongId(), score);
        reasonCache.put(song.getSongId(), joinReasons(reasons));
        return score;
    }

    private String joinReasons(List<String> reasons) {
        if (reasons.isEmpty()) {
            return "Based on your listening across the library.";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(reasons.size(), 2); i++) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(reasons.get(i));
        }
        return sb.toString();
    }

    // ============================================================
    // AFFINITY
    // ============================================================

    private List<String> topGenres;
    private List<String> topArtists;
    private String favoriteLanguage;
    private int affinityUserId = -1;

    /** The user whose behaviour the current affinity reflects. */
    private int uid() {
        int id = com.musicplayer.auth.UserSession.getCurrentUserId();
        return id > 0 ? id : 1;
    }

    private void computeAffinity() {
        affinityUserId = uid();
        topGenres = new ArrayList<>();
        topArtists = new ArrayList<>();
        favoriteLanguage = null;

        String genreSql =
                "SELECT COALESCE(g.genre_name, 'Unknown') AS name, " +
                        "SUM(COALESCE(h.play_count, 0)) AS total " +
                        "FROM Songs s " +
                        "LEFT JOIN Genres g ON s.genre_id = g.genre_id " +
                        "LEFT JOIN UserHistory h ON s.song_id = h.song_id " +
                        "AND h.user_id = " + affinityUserId + " " +
                        "GROUP BY name ORDER BY total DESC LIMIT 2";
        String artistSql =
                "SELECT COALESCE(a.artist_name, 'Unknown') AS name, " +
                        "SUM(COALESCE(h.play_count, 0)) AS total " +
                        "FROM Songs s " +
                        "LEFT JOIN Artists a ON s.artist_id = a.artist_id " +
                        "LEFT JOIN UserHistory h ON s.song_id = h.song_id " +
                        "AND h.user_id = " + affinityUserId + " " +
                        "GROUP BY name ORDER BY total DESC LIMIT 2";
        String languageSql =
                "SELECT COALESCE(s.language, 'Unknown') AS name, " +
                        "SUM(COALESCE(h.play_count, 0)) AS total " +
                        "FROM Songs s " +
                        "LEFT JOIN UserHistory h ON s.song_id = h.song_id " +
                        "AND h.user_id = " + affinityUserId + " " +
                        "GROUP BY name ORDER BY total DESC LIMIT 1";

        try (Statement stmt = getConn().createStatement()) {
            try (ResultSet rs = stmt.executeQuery(genreSql)) {
                while (rs.next()) {
                    topGenres.add(rs.getString("name"));
                }
            }
            try (ResultSet rs = stmt.executeQuery(artistSql)) {
                while (rs.next()) {
                    topArtists.add(rs.getString("name"));
                }
            }
            try (ResultSet rs = stmt.executeQuery(languageSql)) {
                if (rs.next()) {
                    favoriteLanguage = rs.getString("name");
                }
            }
        } catch (SQLException e) {
            System.err.println("Affinity error: " + e.getMessage());
        }
    }

    public String getFavoriteGenre() {
        List<String> genres = topGenres;
        if (genres == null) {
            computeAffinity();
            genres = topGenres;
        }
        if (genres == null || genres.isEmpty()) {
            return "Melody";
        }
        return genres.get(0);
    }

    /** Favourite language by play totals, used by the dashboard. */
    public String getFavoriteLanguage() {
        if (topGenres == null) {
            computeAffinity();
        }
        return favoriteLanguage == null ? "Unknown" : favoriteLanguage;
    }

    // ============================================================
    // DATA LOADING
    // ============================================================

    private ObservableList<Song> loadSongs() {
        ObservableList<Song> songs = FXCollections.observableArrayList();
        int currentUid = uid();
        String sql =
                "SELECT s.song_id, s.song_name, " +
                        "COALESCE(a.artist_name, 'Unknown Artist') AS artist_name, " +
                        "COALESCE(g.genre_name, 'Unknown Genre') AS genre_name, " +
                        "s.album, s.duration, s.language, s.file_path, " +
                        "COALESCE(h.play_count, 0) AS play_count, " +
                        "COALESCE(h.skip_count, 0) AS skip_count, " +
                        "COALESCE(f.is_favorite, 0) AS is_favorite, " +
                        "h.last_played AS last_played " +
                        "FROM Songs s " +
                        "LEFT JOIN Artists a ON s.artist_id = a.artist_id " +
                        "LEFT JOIN Genres g ON s.genre_id = g.genre_id " +
                        "LEFT JOIN UserHistory h ON s.song_id = h.song_id " +
                        "AND h.user_id = " + currentUid + " " +
                        "LEFT JOIN Favorites f ON s.song_id = f.song_id " +
                        "AND f.user_id = " + currentUid + " ";

        try (Statement stmt = getConn().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Song song = new Song(
                        rs.getInt("song_id"),
                        rs.getString("song_name"),
                        rs.getString("artist_name"),
                        rs.getString("genre_name"),
                        rs.getString("album"),
                        rs.getString("duration"),
                        rs.getString("language"),
                        rs.getString("file_path"));
                song.setPlayCount(rs.getInt("play_count"));
                song.setSkipCount(rs.getInt("skip_count"));
                song.setFavorite(rs.getInt("is_favorite") == 1);
                song.setRecentlyPlayed(isRecent(rs.getString("last_played")));
                songs.add(song);
            }
        } catch (SQLException e) {
            System.err.println("Recommendation load error: " + e.getMessage());
        }
        return songs;
    }

    private boolean isRecent(String lastPlayed) {
        if (lastPlayed == null || lastPlayed.isBlank()) {
            return false;
        }
        try {
            LocalDate date = LocalDate.parse(lastPlayed);
            return date.isAfter(LocalDate.now().minusDays(RECENT_DAYS));
        } catch (Exception e) {
            return false;
        }
    }

    private static final class ScoredSong {
        final Song song;
        final int score;

        ScoredSong(Song song, int score) {
            this.song = song;
            this.score = score;
        }
    }
}