package com.musicplayer.database;

import com.musicplayer.library.Song;
import com.musicplayer.utility.AudioDurationReader;
import com.musicplayer.utility.MusicMetadata;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Central manager for SQLite database interactions.
 */
@SuppressWarnings({"unused", "SqlNoDataSourceInspection"})
public class DatabaseManager {

    private static final String DB_URL =
            "jdbc:sqlite:" + System.getProperty("musicplayer.db",
                    System.getProperty("user.dir")
                            + "/musicplayer.db");

    private static DatabaseManager instance;
    private final Connection connection;

    private DatabaseManager() {
        try {
            connection = DriverManager.getConnection(DB_URL);

            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA journal_mode = WAL");
                statement.execute("PRAGMA busy_timeout = 5000");
            }

            createTablesIfNeeded();

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to connect to SQLite database", e
            );
        }
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }

        return instance;
    }

    /**
     * Drops the cached instance so the next {@link #getInstance()} call
     * re-opens the database. Intended for tests and tooling that switch to a
     * temporary database via the {@code musicplayer.db} system property.
     */
    public static synchronized void resetInstanceForTest() {
        if (instance != null) {
            try {
                instance.close();
            } catch (Exception ignored) {
            }
            instance = null;
        }
    }

    /** Current session user id, or 1 (the legacy shared user) when logged out. */
    public static int activeUserId() {
        int id = com.musicplayer.auth.UserSession.getCurrentUserId();
        return id > 0 ? id : 1;
    }

    // This is the only getConnection() method in the file.
    public Connection getConnection() {
        return connection;
    }

    private void createTablesIfNeeded() {
        executeUpdate("""
                CREATE TABLE IF NOT EXISTS Users (
                    user_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT NOT NULL UNIQUE,
                    password TEXT NOT NULL
                )
                """);

        executeUpdate("""
                CREATE TABLE IF NOT EXISTS Artists (
                    artist_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    artist_name TEXT NOT NULL UNIQUE
                )
                """);

        executeUpdate("""
                CREATE TABLE IF NOT EXISTS Genres (
                    genre_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    genre_name TEXT NOT NULL UNIQUE
                )
                """);

        executeUpdate("""
                CREATE TABLE IF NOT EXISTS Songs (
                    song_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    song_name TEXT NOT NULL,
                    file_path TEXT NOT NULL,
                    artist_id INTEGER,
                    genre_id INTEGER,
                    album TEXT NOT NULL DEFAULT 'Unknown Album',
                    duration TEXT NOT NULL DEFAULT '0:00',
                    language TEXT NOT NULL DEFAULT 'Unknown',
                    cover_path TEXT NOT NULL DEFAULT '',
                    year TEXT NOT NULL DEFAULT '2024',
                    cover_color TEXT NOT NULL DEFAULT '#38BDF8',
                    FOREIGN KEY (artist_id)
                        REFERENCES Artists(artist_id)
                        ON DELETE SET NULL,
                    FOREIGN KEY (genre_id)
                        REFERENCES Genres(genre_id)
                        ON DELETE SET NULL
                )
                """);

        ensureSongColumns();

        executeUpdate("""
                CREATE TABLE IF NOT EXISTS UserHistory (
                    user_id INTEGER NOT NULL DEFAULT 1,
                    song_id INTEGER NOT NULL,
                    play_count INTEGER NOT NULL DEFAULT 0,
                    skip_count INTEGER NOT NULL DEFAULT 0,
                    listening_seconds INTEGER NOT NULL DEFAULT 0,
                    last_played TEXT,
                    PRIMARY KEY (user_id, song_id),
                    FOREIGN KEY (song_id)
                        REFERENCES Songs(song_id)
                        ON DELETE CASCADE
                )
                """);

        addHistoryColumnIfMissing("skip_count", "INTEGER NOT NULL DEFAULT 0");
        addHistoryColumnIfMissing("listening_seconds", "INTEGER NOT NULL DEFAULT 0");
        addHistoryColumnIfMissing("last_played", "TEXT");

        executeUpdate("""
                CREATE TABLE IF NOT EXISTS Favorites (
                    user_id INTEGER NOT NULL DEFAULT 1,
                    song_id INTEGER NOT NULL,
                    is_favorite INTEGER NOT NULL DEFAULT 0,
                    added_date TEXT,
                    PRIMARY KEY (user_id, song_id),
                    FOREIGN KEY (song_id)
                        REFERENCES Songs(song_id)
                        ON DELETE CASCADE
                )
                """);

        addFavoriteColumnIfMissing("added_date", "TEXT");

        executeUpdate("""
                CREATE TABLE IF NOT EXISTS Playlists (
                    playlist_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL DEFAULT 1,
                    playlist_name TEXT NOT NULL,
                    created_date TEXT
                )
                """);

        ensurePlaylistColumns();

        executeUpdate("""
                CREATE TABLE IF NOT EXISTS PlaylistSongs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    playlist_id INTEGER NOT NULL,
                    song_id INTEGER NOT NULL,
                    FOREIGN KEY (playlist_id)
                        REFERENCES Playlists(playlist_id)
                        ON DELETE CASCADE,
                    FOREIGN KEY (song_id)
                        REFERENCES Songs(song_id)
                        ON DELETE CASCADE
                )
                """);

        executeUpdate("""
                CREATE TABLE IF NOT EXISTS ListeningLog (
                    log_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL DEFAULT 1,
                    song_id INTEGER NOT NULL,
                    played_at TEXT NOT NULL,
                    played_seconds INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY (song_id)
                        REFERENCES Songs(song_id)
                        ON DELETE CASCADE
                )
                """);

        ensureSongFilePathIndex();
        migrateLegacyHistoryAndFavorites();
        ensureHistoryAndFavoritesUpsertSafe();
        ensureUserColumns();
    }

    /**
     * Older databases carry per-song (single-user) UserHistory and Favorites
     * tables. If those tables are still single-user only, they are rebuilt to
     * the per-user shape (user_id, song_id) so every user keeps their own
     * history and favorites. Existing rows are assigned to user 1.
     */
    private void migrateLegacyHistoryAndFavorites() {
        if (!hasColumn("UserHistory", "user_id")) {
            rebuildTable("UserHistory",
                    "user_id, song_id, play_count, skip_count, last_played",
                    """
                            CREATE TABLE UserHistory (
                                user_id INTEGER NOT NULL DEFAULT 1,
                                song_id INTEGER NOT NULL,
                                play_count INTEGER NOT NULL DEFAULT 0,
                                skip_count INTEGER NOT NULL DEFAULT 0,
                                listening_seconds INTEGER NOT NULL DEFAULT 0,
                                last_played TEXT,
                                PRIMARY KEY (user_id, song_id),
                                FOREIGN KEY (song_id)
                                    REFERENCES Songs(song_id)
                                    ON DELETE CASCADE
                            )
                            """,
                    "SELECT 1, song_id, MAX(play_count), MAX(COALESCE(skip_count,0)), MAX(last_played) " +
                            "FROM {old} GROUP BY song_id");
        }
        if (!hasColumn("Favorites", "user_id")) {
            rebuildTable("Favorites",
                    "user_id, song_id, is_favorite, added_date",
                    """
                            CREATE TABLE Favorites (
                                user_id INTEGER NOT NULL DEFAULT 1,
                                song_id INTEGER NOT NULL,
                                is_favorite INTEGER NOT NULL DEFAULT 0,
                                added_date TEXT,
                                PRIMARY KEY (user_id, song_id),
                                FOREIGN KEY (song_id)
                                    REFERENCES Songs(song_id)
                                    ON DELETE CASCADE
                            )
                            """,
                    "SELECT 1, song_id, MAX(is_favorite), MAX(added_date) " +
                            "FROM {old} GROUP BY song_id");
        }
    }

    /**
     * Safely rebuilds a legacy table into a new shape. The table is renamed,
     * the new definition is created, matching rows are copied over, and the
     * old table is dropped.
     */
    private void rebuildTable(String tableName,
                              String columnList,
                              String createSql,
                              String insertSelect) {
        String oldName = tableName + "_pbl_old_"
                + System.currentTimeMillis();
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("ALTER TABLE " + tableName + " RENAME TO " + oldName);
            stmt.executeUpdate(createSql);
            stmt.executeUpdate("INSERT INTO " + tableName + " (" + columnList + ") "
                    + insertSelect.replace("{old}", oldName));
            stmt.executeUpdate("DROP TABLE " + oldName);
            System.out.println("Migrated " + tableName + " to per-user schema.");
        } catch (SQLException e) {
            System.err.println("Migration failed for " + tableName + ": " + e.getMessage());
        }
    }

    /** Adds missing columns to the Playlists table (user_id, created_date). */
    private void ensurePlaylistColumns() {
        String[][] defs = {
                {"user_id", "INTEGER NOT NULL DEFAULT 1"},
                {"created_date", "TEXT"},
                {"name", "TEXT"}
        };
        for (String[] def : defs) {
            if (!hasColumn("Playlists", def[0])) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate(
                            "ALTER TABLE Playlists ADD COLUMN "
                                    + def[0] + " " + def[1]);
                } catch (SQLException e) {
                    System.err.println(
                            "Could not add Playlists." + def[0] + ": " + e.getMessage());
                }
            }
        }
    }

    /** True when {@code table} has a column named {@code column}. */
    private boolean hasColumn(String table, String column) {
        return hasColumn(connection, table, column);
    }

    private static boolean hasColumn(Connection conn, String table, String column) {
        try (Statement statement = conn.createStatement();
             ResultSet columns = statement.executeQuery(
                     "PRAGMA table_info(" + table + ")")) {
            while (columns.next()) {
                if (column.equalsIgnoreCase(columns.getString("name"))) {
                    return true;
                }
            }
        } catch (SQLException e) {
            System.err.println("Could not inspect " + table + ": " + e.getMessage());
        }
        return false;
    }

    /**
     * Older databases carry UserHistory/Favorites tables with an auto-increment
     * (history_id/favorite_id) primary key and a non-unique song_id. That makes
     * every "INSERT ... ON CONFLICT(song_id) DO UPDATE" in recordPlay,
     * recordSkip and setFavorite fail with:
     *   "ON CONFLICT clause does not match any PRIMARY KEY or UNIQUE constraint".
     *
     * This migration deduplicates stray rows (keeping the oldest) and adds a
     * UNIQUE index on song_id so the upserts resolve. It is safe to run on a
     * freshly created schema (where song_id is already a primary key).
     */
    private void ensureHistoryAndFavoritesUpsertSafe() {
        // Modern per-user tables already carry a (user_id, song_id) primary key,
        // so per-user history/favorites never collide. Only legacy tables (no
        // user_id) need dedupe + the legacy unique index.
        if (hasColumn("UserHistory", "user_id") && hasColumn("Favorites", "user_id")) {
            return;
        }
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("DELETE FROM UserHistory WHERE rowid NOT IN " +
                    "(SELECT MIN(rowid) FROM UserHistory GROUP BY song_id)");
            stmt.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "ux_userhistory_song_id ON UserHistory(song_id)");
            stmt.executeUpdate("DELETE FROM Favorites WHERE rowid NOT IN " +
                    "(SELECT MIN(rowid) FROM Favorites GROUP BY song_id)");
            stmt.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "ux_favorites_song_id ON Favorites(song_id)");
            System.out.println("History/favorites upsert safety ensured.");
        } catch (SQLException e) {
            System.err.println(
                    "Could not ensure history/favorites upsert safety: "
                            + e.getMessage()
            );
        }
    }

    /**
     * Removes previously duplicated rows (same file path) keeping the oldest
     * song_id, then creates a unique index so future imports use INSERT OR
     * IGNORE and never re-index the same file twice. History and favorites
     * for removed duplicates cascade-delete together with the row.
     */
    private void ensureSongFilePathIndex() {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("DELETE FROM Songs WHERE song_id NOT IN " +
                    "(SELECT MIN(song_id) FROM Songs GROUP BY file_path)");
            stmt.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "idx_songs_file_path ON Songs(file_path)");
        } catch (SQLException e) {
            System.err.println(
                    "Could not create unique song path index: "
                            + e.getMessage()
            );
        }
    }

    private void executeUpdate(String sql) {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Database SQL error: " + e.getMessage(), e
            );
        }
    }

    /** Adds fields required by MusicLibrary when opening a database created by an older version. */
    private void ensureSongColumns() {
        addColumnIfMissing("album", "TEXT NOT NULL DEFAULT 'Unknown Album'");
        addColumnIfMissing("duration", "TEXT NOT NULL DEFAULT '0:00'");
        addColumnIfMissing("language", "TEXT NOT NULL DEFAULT 'Unknown'");
        addColumnIfMissing("cover_path", "TEXT NOT NULL DEFAULT ''");
        addColumnIfMissing("year", "TEXT NOT NULL DEFAULT '2024'");
        addColumnIfMissing("cover_color", "TEXT NOT NULL DEFAULT '#38BDF8'");
    }

    private void ensureUserColumns() {
        addUserColumnIfMissing("login_type", "TEXT NOT NULL DEFAULT 'email'");
        addUserColumnIfMissing("email", "TEXT NOT NULL DEFAULT ''");
        addUserColumnIfMissing("created_at", "TEXT NOT NULL DEFAULT ''");
    }

    private void addUserColumnIfMissing(String columnName, String definition) {
        try (Statement statement = connection.createStatement();
             ResultSet columns = statement.executeQuery("PRAGMA table_info(Users)")) {
            while (columns.next()) {
                if (columnName.equalsIgnoreCase(columns.getString("name"))) {
                    return;
                }
            }
            statement.executeUpdate(
                    "ALTER TABLE Users ADD COLUMN " + columnName + " " + definition
            );
        } catch (SQLException e) {
            throw new RuntimeException("Could not update Users table schema", e);
        }
    }

    private void addColumnIfMissing(String columnName, String definition) {
        String checkSql = "PRAGMA table_info(Songs)";

        try (Statement statement = connection.createStatement();
             ResultSet columns = statement.executeQuery(checkSql)) {

            while (columns.next()) {
                if (columnName.equalsIgnoreCase(columns.getString("name"))) {
                    return;
                }
            }

            statement.executeUpdate(
                    "ALTER TABLE Songs ADD COLUMN " + columnName + " " + definition
            );

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Could not update Songs table schema: " + e.getMessage(), e
            );
        }
    }

    private void addHistoryColumnIfMissing(String columnName, String definition) {
        try (Statement statement = connection.createStatement();
             ResultSet columns = statement.executeQuery("PRAGMA table_info(UserHistory)")) {
            while (columns.next()) {
                if (columnName.equalsIgnoreCase(columns.getString("name"))) return;
            }
            statement.executeUpdate("ALTER TABLE UserHistory ADD COLUMN " + columnName + " " + definition);
        } catch (SQLException e) {
            throw new RuntimeException("Could not update UserHistory table", e);
        }
    }

    private void addFavoriteColumnIfMissing(String columnName, String definition) {
        try (Statement statement = connection.createStatement();
             ResultSet columns = statement.executeQuery("PRAGMA table_info(Favorites)")) {
            while (columns.next()) {
                if (columnName.equalsIgnoreCase(columns.getString("name"))) return;
            }
            statement.executeUpdate("ALTER TABLE Favorites ADD COLUMN " + columnName + " " + definition);
        } catch (SQLException e) {
            throw new RuntimeException("Could not update Favorites table", e);
        }
    }

    public boolean usernameExists(String username) {
        String sql = "SELECT 1 FROM Users WHERE username = ? LIMIT 1";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            System.err.println("Username check failed: " + e.getMessage());
            return false;
        }
    }

    public boolean emailExists(String email) {
        String sql = "SELECT 1 FROM Users WHERE email = ? LIMIT 1";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, email);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            System.err.println("Email check failed: " + e.getMessage());
            return false;
        }
    }

    public boolean registerUser(
            String username,
            String email,
            String password,
            String createdAt
    ) {
        String sql = """
                INSERT INTO Users
                    (username, email, password, created_at)
                VALUES (?, ?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, email);
            ps.setString(3, com.musicplayer.utility.PasswordUtil.hash(password));
            ps.setString(4, createdAt);

            ps.executeUpdate();
            return true;

        } catch (SQLException e) {
            System.err.println(
                    "Failed to register user: " + e.getMessage()
            );
            return false;
        }
    }

    public boolean loginUser(String username, String password) {
        String sql = """
                SELECT password
                FROM Users
                WHERE username = ?
                LIMIT 1
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                String stored = rs.getString("password");
                boolean ok = com.musicplayer.utility.PasswordUtil.verify(password, stored);
                if (ok && !com.musicplayer.utility.PasswordUtil.isHashed(stored)) {
                    upgradeToHashed(username, stored);
                }
                return ok;
            }

        } catch (SQLException e) {
            System.err.println("Login failed: " + e.getMessage());
            return false;
        }
    }

    /** Replaces a legacy plain-text password with a salted hash. */
    private void upgradeToHashed(String username, String stored) {
        String sql = "UPDATE Users SET password = ? WHERE username = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, com.musicplayer.utility.PasswordUtil.hash(stored));
            ps.setString(2, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Password upgrade failed: " + e.getMessage());
        }
    }

    /**
     * Fully offline login. New usernames are created locally; existing users
     * must use the password that was saved in the local SQLite database.
     */
    public int loginOrCreateOfflineUser(String username, String password) {
        if (username == null || username.isBlank()) {
            return -1;
        }

        String cleanUsername = username.trim();
        String safePassword = password == null ? "" : password;

        try (PreparedStatement select = connection.prepareStatement(
                "SELECT user_id, password FROM Users WHERE username = ? LIMIT 1")) {
            select.setString(1, cleanUsername);
            try (ResultSet rs = select.executeQuery()) {
                if (rs.next()) {
                    String stored = rs.getString("password");
                    boolean valid =
                            com.musicplayer.utility.PasswordUtil.verify(safePassword, stored);
                    if (valid
                            && !com.musicplayer.utility.PasswordUtil.isHashed(stored)) {
                        upgradeToHashed(cleanUsername, stored);
                    }
                    return valid ? rs.getInt("user_id") : -1;
                }
            }
        } catch (SQLException e) {
            System.err.println("Offline login lookup failed: " + e.getMessage());
            return -1;
        }

        boolean hasLegacyEmailColumn = hasUserColumn("email");
        boolean hasLoginTypeColumn = hasUserColumn("login_type");
        boolean hasCreatedAtColumn = hasUserColumn("created_at");
        String insert;
        if (hasLegacyEmailColumn && hasLoginTypeColumn && hasCreatedAtColumn) {
            insert = "INSERT OR IGNORE INTO Users "
                    + "(username, email, password, login_type, created_at) "
                    + "VALUES (?, ?, ?, 'offline', datetime('now'))";
        } else if (hasLegacyEmailColumn && hasCreatedAtColumn) {
            insert = "INSERT OR IGNORE INTO Users "
                    + "(username, email, password, created_at) VALUES (?, ?, ?, datetime('now'))";
        } else {
            insert = "INSERT OR IGNORE INTO Users (username, password) VALUES (?, ?)";
        }
        String hashedPassword = com.musicplayer.utility.PasswordUtil.hash(safePassword);
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setString(1, cleanUsername);
            if (hasLegacyEmailColumn && hasCreatedAtColumn) {
                String offlineEmail = cleanUsername.toLowerCase()
                        .replaceAll("[^a-z0-9._-]", "") + "@offline.local";
                statement.setString(2, offlineEmail.equals("@offline.local")
                        ? "user@offline.local" : offlineEmail);
                statement.setString(3, hashedPassword);
            } else {
                statement.setString(2, hashedPassword);
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Offline user creation failed: " + e.getMessage());
            return -1;
        }

        try (PreparedStatement select = connection.prepareStatement(
                "SELECT user_id FROM Users WHERE username = ? LIMIT 1")) {
            select.setString(1, cleanUsername);
            try (ResultSet rs = select.executeQuery()) {
                return rs.next() ? rs.getInt("user_id") : -1;
            }
        } catch (SQLException e) {
            return -1;
        }
    }

    private boolean hasUserColumn(String columnName) {
        try (Statement statement = connection.createStatement();
             ResultSet columns = statement.executeQuery("PRAGMA table_info(Users)")) {
            while (columns.next()) {
                if (columnName.equalsIgnoreCase(columns.getString("name"))) {
                    return true;
                }
            }
        } catch (SQLException e) {
            System.err.println("Could not inspect Users table: " + e.getMessage());
        }
        return false;
    }

    public int getOrCreateArtist(String artistName) {
        if (artistName == null || artistName.isBlank()) {
            artistName = "Unknown Artist";
        }

        String insert = """
                INSERT OR IGNORE INTO Artists(artist_name)
                VALUES (?)
                """;

        String select = """
                SELECT artist_id
                FROM Artists
                WHERE artist_name = ?
                LIMIT 1
                """;

        try {
            try (PreparedStatement ps = connection.prepareStatement(insert)) {
                ps.setString(1, artistName);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = connection.prepareStatement(select)) {
                ps.setString(1, artistName);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("artist_id");
                    }
                }
            }

        } catch (SQLException e) {
            System.err.println(
                    "Artist operation failed: " + e.getMessage()
            );
        }

        return -1;
    }

    public int getOrCreateGenre(String genreName) {
        if (genreName == null || genreName.isBlank()) {
            genreName = "Unknown Genre";
        }

        String insert = """
                INSERT OR IGNORE INTO Genres(genre_name)
                VALUES (?)
                """;

        String select = """
                SELECT genre_id
                FROM Genres
                WHERE genre_name = ?
                LIMIT 1
                """;

        try {
            try (PreparedStatement ps = connection.prepareStatement(insert)) {
                ps.setString(1, genreName);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = connection.prepareStatement(select)) {
                ps.setString(1, genreName);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("genre_id");
                    }
                }
            }

        } catch (SQLException e) {
            System.err.println(
                    "Genre operation failed: " + e.getMessage()
            );
        }

        return -1;
    }

    public boolean insertSong(
            String songName,
            String filePath,
            String artistName,
            String genreName
    ) {
        if (songName == null || songName.isBlank()
                || filePath == null || filePath.isBlank()) {
            return false;
        }

        try {
            int artistId = getOrCreateArtist(artistName);
            int genreId = getOrCreateGenre(genreName);

            String sql = """
                    INSERT OR IGNORE INTO Songs
                        (song_name, file_path, artist_id, genre_id)
                    VALUES (?, ?, ?, ?)
                    """;

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, songName);
                ps.setString(2, filePath);

                if (artistId > 0) {
                    ps.setInt(3, artistId);
                } else {
                    ps.setNull(3, Types.INTEGER);
                }

                if (genreId > 0) {
                    ps.setInt(4, genreId);
                } else {
                    ps.setNull(4, Types.INTEGER);
                }

                return ps.executeUpdate() > 0;
            }

        } catch (SQLException e) {
            System.err.println(
                    "Failed to insert song: " + e.getMessage()
            );
            return false;
        }
    }

    /**
     * Inserts a real local audio file into the library using metadata read
     * from the file itself (with safe filename-derived fallbacks). Duplicate
     * file paths are ignored by the UNIQUE index on Songs(file_path).
     *
     * @return true when a brand-new row was inserted, false when the file was
     *         already indexed (or could not be stored).
     */
    public boolean insertImportedSong(MusicMetadata meta, String filePath, String language) {
        if (filePath == null || filePath.isBlank()) {
            return false;
        }

        String title = meta != null && !meta.getTitle().isBlank()
                ? meta.getTitle()
                : new File(filePath).getName();
        String artist = meta != null ? meta.getArtist() : "Unknown Artist";
        String genre = meta != null ? meta.getGenre() : "Pop";
        String album = meta != null ? meta.getAlbum() : "Unknown Album";
        String year = meta != null ? meta.getYear() : "2024";

        try {
            int artistId = getOrCreateArtist(artist);
            int genreId = getOrCreateGenre(genre);

            String realDuration = AudioDurationReader.readDurationString(
                    new File(filePath));

            if (realDuration == null) {
                realDuration = "0:00";
            }

            String sql = """
                    INSERT OR IGNORE INTO Songs
                        (song_name, artist_id, genre_id, album, duration,
                         language, year, cover_color, file_path)
                    VALUES (?, ?, ?, ?, ?, ?, ?, '#38BDF8', ?)
                    """;

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, title);
                if (artistId > 0) {
                    ps.setInt(2, artistId);
                } else {
                    ps.setNull(2, Types.INTEGER);
                }
                if (genreId > 0) {
                    ps.setInt(3, genreId);
                } else {
                    ps.setNull(3, Types.INTEGER);
                }
                ps.setString(4, album);
                ps.setString(5, realDuration);
                ps.setString(6, language);
                ps.setString(7, year);
                ps.setString(8, "#38BDF8");
                ps.setString(9, filePath);
                return ps.executeUpdate() > 0;
            }

        } catch (SQLException e) {
            System.err.println(
                    "Failed to import song: " + e.getMessage()
            );
            return false;
        }
    }

    /** Updates the stored human-readable duration after the media file has been read. */
    public boolean updateSongDuration(int songId, String duration) {
        if (songId <= 0 || duration == null || duration.isBlank()) {
            return false;
        }
        String sql = "UPDATE Songs SET duration = ? WHERE song_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, duration);
            ps.setInt(2, songId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Failed to update song duration: " + e.getMessage());
            return false;
        }
    }

    public Song getSongById(int songId) {
        String sql = """
                SELECT s.song_id, s.song_name,
                    COALESCE(a.artist_name, 'Unknown Artist') AS artist,
                    COALESCE(g.genre_name, 'Unknown') AS genre,
                    s.album, s.duration, s.language, s.year,
                    s.file_path, s.cover_color
                FROM Songs s
                LEFT JOIN Artists a ON s.artist_id = a.artist_id
                LEFT JOIN Genres g ON s.genre_id = g.genre_id
                WHERE s.song_id = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, songId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Song(
                            rs.getInt("song_id"),
                            rs.getString("song_name"),
                            rs.getString("artist"),
                            rs.getString("genre"),
                            rs.getString("album"),
                            rs.getString("duration"),
                            rs.getString("language"),
                            rs.getString("year"),
                            rs.getString("file_path"),
                            rs.getString("cover_color"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to load song by id: " + e.getMessage());
        }
        return null;
    }

    public int insertSongsBatch(List<Song> songs) {
        if (songs == null || songs.isEmpty()) {
            return 0;
        }

        String sql = """
                INSERT OR IGNORE INTO Songs
                    (song_name, file_path, artist_id, genre_id)
                VALUES (?, ?, ?, ?)
                """;

        int inserted = 0;

        try {
            boolean oldAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (Song song : songs) {
                    if (song == null
                            || song.getFilePath() == null
                            || song.getFilePath().isBlank()) {
                        continue;
                    }

                    int artistId = getOrCreateArtist(song.getArtist());
                    int genreId = getOrCreateGenre(song.getGenre());

                    ps.setString(1, song.getSongName());
                    ps.setString(2, song.getFilePath());

                    if (artistId > 0) {
                        ps.setInt(3, artistId);
                    } else {
                        ps.setNull(3, Types.INTEGER);
                    }

                    if (genreId > 0) {
                        ps.setInt(4, genreId);
                    } else {
                        ps.setNull(4, Types.INTEGER);
                    }

                    ps.addBatch();
                }

                int[] results = ps.executeBatch();

                for (int result : results) {
                    if (result > 0 || result == Statement.SUCCESS_NO_INFO) {
                        inserted++;
                    }
                }

                connection.commit();

            } catch (SQLException e) {
                connection.rollback();
                throw e;

            } finally {
                connection.setAutoCommit(oldAutoCommit);
            }

        } catch (SQLException e) {
            System.err.println(
                    "Batch song insert failed: " + e.getMessage()
            );
        }

        return inserted;
    }

    public List<Song> getAllSongs() {
        List<Song> songs = new ArrayList<>();

        String sql = """
                SELECT
                    s.song_id,
                    s.song_name,
                    COALESCE(a.artist_name, 'Unknown Artist') AS artist,
                    COALESCE(g.genre_name, 'Unknown') AS genre,
                    s.album,
                    s.duration,
                    s.language,
                    s.year,
                    s.file_path,
                    s.cover_color,
                    s.cover_path,
                    COALESCE(h.play_count, 0) AS play_count
                FROM Songs s
                LEFT JOIN Artists a ON s.artist_id = a.artist_id
                LEFT JOIN Genres g ON s.genre_id = g.genre_id
                LEFT JOIN UserHistory h ON s.song_id = h.song_id
                    AND h.user_id = """ + activeUserId() + """
                LEFT JOIN Favorites f ON s.song_id = f.song_id
                    AND f.user_id = """ + activeUserId() + """
                ORDER BY s.song_name COLLATE NOCASE
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Song song = new Song(
                        rs.getInt("song_id"),
                        rs.getString("song_name"),
                        rs.getString("artist"),
                        rs.getString("genre"),
                        rs.getString("album"),
                        rs.getString("duration"),
                        rs.getString("language"),
                        rs.getString("year"),
                        rs.getString("file_path"),
                        rs.getString("cover_color"));
                song.setCoverPath(rs.getString("cover_path"));
                song.setPlayCount(rs.getInt("play_count"));
                songs.add(song);
            }

        } catch (SQLException e) {
            System.err.println(
                    "Failed to load songs: " + e.getMessage()
            );
        }

        return songs;
    }

    public List<Song> getRecommendedSongs(int limit) {
        List<Song> songs = new ArrayList<>();

        if (limit <= 0) {
            limit = 50;
        }

        String sql = """
                SELECT
                    s.song_id,
                    s.song_name,
                    a.artist_name AS artist,
                    g.genre_name AS genre,
                    s.file_path
                FROM Songs s
                LEFT JOIN Artists a ON s.artist_id = a.artist_id
                LEFT JOIN Genres g ON s.genre_id = g.genre_id
                LEFT JOIN UserHistory h ON s.song_id = h.song_id
                    AND h.user_id = """ + activeUserId() + """
                LEFT JOIN Favorites f ON s.song_id = f.song_id
                    AND f.user_id = """ + activeUserId() + """
                ORDER BY
                    COALESCE(f.is_favorite, 0) DESC,
                    COALESCE(h.play_count, 0) DESC,
                    s.song_name COLLATE NOCASE
                LIMIT ?
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    songs.add(new Song(
                            rs.getInt("song_id"),
                            rs.getString("song_name"),
                            rs.getString("artist"),
                            rs.getString("genre"),
                            rs.getString("file_path")
                    ));
                }
            }

        } catch (SQLException e) {
            System.err.println(
                    "Failed to load recommendations: " + e.getMessage()
            );
        }

        return songs;
    }

    public boolean addToFavorites(int songId) {
        String sql = """
                INSERT INTO Favorites
                    (user_id, song_id, is_favorite, added_date)
                VALUES (?, ?, 1, date('now'))
                ON CONFLICT(user_id, song_id)
                DO UPDATE SET is_favorite = 1
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, activeUserId());
            ps.setInt(2, songId);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println(
                    "Failed to add favorite: " + e.getMessage()
            );
            return false;
        }
    }

    public boolean removeFromFavorites(int songId) {
        String sql = """
                UPDATE Favorites
                SET is_favorite = 0
                WHERE user_id = ?
                  AND song_id = ?
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, activeUserId());
            ps.setInt(2, songId);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println(
                    "Failed to remove favorite: " + e.getMessage()
            );
            return false;
        }
    }

    public boolean isFavorite(int songId) {
        String sql = """
                SELECT is_favorite
                FROM Favorites
                WHERE user_id = ?
                  AND song_id = ?
                LIMIT 1
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, activeUserId());
            ps.setInt(2, songId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt("is_favorite") == 1;
            }

        } catch (SQLException e) {
            System.err.println(
                    "Failed to check favorite: " + e.getMessage()
            );
            return false;
        }
    }

    public List<Song> getFavoriteSongs() {
        List<Song> songs = new ArrayList<>();

        String sql = """
                SELECT
                    s.song_id,
                    s.song_name,
                    a.artist_name AS artist,
                    g.genre_name AS genre,
                    s.file_path
                FROM Songs s
                LEFT JOIN Artists a ON s.artist_id = a.artist_id
                LEFT JOIN Genres g ON s.genre_id = g.genre_id
                LEFT JOIN Favorites f ON s.song_id = f.song_id
                    AND f.user_id = ?
                WHERE COALESCE(f.is_favorite, 0) = 1
                ORDER BY s.song_name COLLATE NOCASE
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, activeUserId());
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                songs.add(new Song(
                        rs.getInt("song_id"),
                        rs.getString("song_name"),
                        rs.getString("artist"),
                        rs.getString("genre"),
                        rs.getString("file_path")
                ));
            }
            rs.close();

        } catch (SQLException e) {
            System.err.println(
                    "Failed to load favorite songs: " + e.getMessage()
            );
        }

        return songs;
    }

    public void recordPlay(int songId) {
        String sql = """
                INSERT INTO UserHistory
                    (user_id, song_id, play_count, last_played)
                VALUES (?, ?, 1, date('now'))
                ON CONFLICT(user_id, song_id)
                DO UPDATE SET
                    play_count = play_count + 1,
                    last_played = date('now')
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, activeUserId());
            ps.setInt(2, songId);
            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println(
                    "Failed to record song play: " + e.getMessage()
            );
        }
    }

    /** Records an early skip (negative listening signal) for a song. */
    public void recordSkip(int songId) {
        String sql = """
                INSERT INTO UserHistory
                    (user_id, song_id, skip_count, last_played)
                VALUES (?, ?, 1, date('now'))
                ON CONFLICT(user_id, song_id)
                DO UPDATE SET skip_count = skip_count + 1
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, activeUserId());
            ps.setInt(2, songId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed to record skip: " + e.getMessage());
        }
    }

    /** Adds real listening seconds for a song (used to total listening time). */
    public void addListeningSeconds(int songId, int seconds) {
        if (songId <= 0 || seconds <= 0) {
            return;
        }
        String sql = """
                INSERT INTO UserHistory
                    (user_id, song_id, listening_seconds)
                VALUES (?, ?, ?)
                ON CONFLICT(user_id, song_id)
                DO UPDATE SET listening_seconds =
                    listening_seconds + excluded.listening_seconds
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, activeUserId());
            ps.setInt(2, songId);
            ps.setInt(3, seconds);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println(
                    "Failed to record listening seconds: " + e.getMessage()
            );
        }
    }

    /** Appends a timestamped row to the chronological ListeningLog table. */
    public void logListening(int userId, int songId, int seconds) {
        if (songId <= 0) {
            return;
        }
        String sql = """
                INSERT INTO ListeningLog (user_id, song_id, played_at, played_seconds)
                VALUES (?, ?, datetime('now', 'localtime'), ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId > 0 ? userId : 1);
            ps.setInt(2, songId);
            ps.setInt(3, Math.max(0, seconds));
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed to log listening event: " + e.getMessage());
        }
    }

    /** True when the user has at least some recorded listening behaviour. */
    public boolean hasAnyHistory() {
        String sql = "SELECT COUNT(*) FROM UserHistory WHERE user_id = "
                + activeUserId();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            System.err.println("History check failed: " + e.getMessage());
            return false;
        }
    }

    /** Total number of completed plays across all songs. */
    public int getTotalPlayCount() {
        String sql = "SELECT COALESCE(SUM(play_count), 0) FROM UserHistory WHERE user_id = "
                + activeUserId();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            System.err.println("Total play count failed: " + e.getMessage());
            return 0;
        }
    }

    /** Total listening time in seconds across all songs for the current user. */
    public long getTotalListeningSeconds() {
        String sql = "SELECT COALESCE(SUM(listening_seconds), 0) FROM UserHistory WHERE user_id = "
                + activeUserId();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            System.err.println("Total listening time failed: " + e.getMessage());
            return 0L;
        }
    }

    /** Chronological listening log for the current user (newest first). */
    public List<String[]> getListeningLog() {
        List<String[]> rows = new ArrayList<>();
        String sql = "SELECT h.played_at, h.played_seconds, s.song_name, " +
                "COALESCE(a.artist_name, 'Unknown Artist') " +
                "FROM ListeningLog h " +
                "JOIN Songs s ON h.song_id = s.song_id " +
                "LEFT JOIN Artists a ON s.artist_id = a.artist_id " +
                "WHERE h.user_id = " + activeUserId() +
                " ORDER BY h.played_at DESC";
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                rows.add(new String[]{
                        rs.getString(1),
                        String.valueOf(rs.getInt(2)),
                        rs.getString(3),
                        rs.getString(4)
                });
            }
        } catch (SQLException e) {
            System.err.println("Listening log load failed: " + e.getMessage());
        }
        return rows;
    }

    public int getPlayCount(int songId) {
        String sql = """
                SELECT play_count
                FROM UserHistory
                WHERE user_id = ?
                  AND song_id = ?
                LIMIT 1
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, activeUserId());
            ps.setInt(2, songId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("play_count");
                }
            }

        } catch (SQLException e) {
            System.err.println(
                    "Failed to get play count: " + e.getMessage()
            );
        }

        return 0;
    }

    public boolean deleteSong(int songId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM Songs WHERE song_id = ?")) {
            ps.setInt(1, songId);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println(
                    "Failed to delete song: " + e.getMessage()
            );
            return false;
        }
    }

    /**
     * Deletes a song together with every reference to it. Older databases can
     * miss ON DELETE CASCADE clauses on their child tables, so the dependent
     * rows are removed explicitly before the song itself.
     */
    public boolean deleteSongCascade(int songId) {
        String[] childStatements = {
                "DELETE FROM UserHistory WHERE song_id = ?",
                "DELETE FROM Favorites WHERE song_id = ?",
                "DELETE FROM PlaylistSongs WHERE song_id = ?",
                "DELETE FROM Songs WHERE song_id = ?"
        };
        try {
            boolean oldAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                for (String statementSql : childStatements) {
                    try (PreparedStatement ps = connection.prepareStatement(statementSql)) {
                        ps.setInt(1, songId);
                        ps.executeUpdate();
                    }
                }
                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(oldAutoCommit);
            }
        } catch (SQLException e) {
            System.err.println("Failed to delete song (cascade): " + e.getMessage());
            return false;
        }
    }

    public List<Song> searchSongs(String searchText) {
        List<Song> songs = new ArrayList<>();

        if (searchText == null) {
            searchText = "";
        }

        String sql = """
                SELECT
                    s.song_id,
                    s.song_name,
                    a.artist_name AS artist,
                    g.genre_name AS genre,
                    s.file_path
                FROM Songs s
                LEFT JOIN Artists a ON s.artist_id = a.artist_id
                LEFT JOIN Genres g ON s.genre_id = g.genre_id
                LEFT JOIN UserHistory h ON s.song_id = h.song_id
                    AND h.user_id = """ + activeUserId() + """
                LEFT JOIN Favorites f ON s.song_id = f.song_id
                    AND f.user_id = """ + activeUserId() + """
                WHERE
                    s.song_name LIKE ?
                    OR a.artist_name LIKE ?
                    OR g.genre_name LIKE ?
                ORDER BY s.song_name COLLATE NOCASE
                """;

        String pattern = "%" + searchText + "%";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    songs.add(new Song(
                            rs.getInt("song_id"),
                            rs.getString("song_name"),
                            rs.getString("artist"),
                            rs.getString("genre"),
                            rs.getString("file_path")
                    ));
                }
            }

        } catch (SQLException e) {
            System.err.println(
                    "Song search failed: " + e.getMessage()
            );
        }

        return songs;
    }

    public synchronized void close() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException e) {
                System.err.println(
                        "Failed to close database: " + e.getMessage()
                );
            }
        }
    }
}
