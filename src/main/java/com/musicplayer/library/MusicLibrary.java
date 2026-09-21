package com.musicplayer.library;

import com.musicplayer.database.DatabaseManager;
import com.musicplayer.utility.FileNameParser;
import com.musicplayer.utility.Mp3TagReader;
import com.musicplayer.utility.MusicMetadata;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"SqlResolve", "SqlNoDataSourceInspection", "SqlDialectInspection"})
public class MusicLibrary {

    /** Folder the player treats as its main library: every .mp3 here is shown automatically. */
    public static final String SONGS_FOLDER_NAME = "songs";

    /** Folder containing the old generated demo tones that must not appear in the library. */
    private static final String DEMO_SONGS_FOLDER = "smartbeats-demo-music";

    public MusicLibrary() {
        // Explicit default constructor
    }

    private Connection getConn() {
        return DatabaseManager.getInstance().getConnection();
    }

    /** Returns the project songs folder, creating it (with full write access) if it does not exist. */
    public File getSongsFolder() {
        File folder = new File(System.getProperty("user.dir"), SONGS_FOLDER_NAME);
        if (!folder.exists() && !folder.mkdirs()) {
            System.err.println("Could not create songs folder: " + folder.getAbsolutePath());
        }
        return folder;
    }

    /**
     * Re-scans the library folders (music + songs) so the app always mirrors
     * the audio files on disk.
     *
     * Rows whose audio file is missing (or lives in the old generated-demo
     * folder) are dropped. Every existing file's displayed title is refreshed
     * from the file name, so each song always maps to its own MP3. Any audio
     * file not yet indexed is imported.
     *
     * @return number of new tracks indexed by this sync.
     */
    public int syncSongsFolder() {
        List<File> sourceFolders = getLibraryFolders();

        int staleRemoved = purgeInvalidRows();

        List<File> audioFiles = new ArrayList<>();
        for (File folder : sourceFolders) {
            collectAudioFiles(folder, audioFiles);
        }

        Map<String, Integer> rowIdsByPath = new HashMap<>();
        String mapSql = "SELECT song_id, file_path FROM Songs";
        try (Statement stmt = getConn().createStatement();
             ResultSet rs = stmt.executeQuery(mapSql)) {
            while (rs.next()) {
                String path = rs.getString("file_path");
                if (path != null && !path.isBlank()) {
                    rowIdsByPath.put(canonicalPath(new File(path)), rs.getInt("song_id"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Songs folder sync (map) error: " + e.getMessage());
        }

        int imported = 0;
        int refreshed = 0;
        for (File file : audioFiles) {
            String canonical = canonicalPath(file);
            Integer existingId = rowIdsByPath.get(canonical);
            if (existingId == null) {
                List<File> single = new ArrayList<>();
                single.add(file);
                imported += importFiles(single);
            } else {
                if (refreshDisplayName(existingId, file)) {
                    refreshed++;
                }
                refreshMetadataIfCorrupt(existingId, file);
            }
        }
        System.out.println("Songs folder sync: " + audioFiles.size() + " audio file(s), "
                + imported + " new, " + refreshed + " title(s) refreshed, "
                + staleRemoved + " stale removed.");
        return imported;
    }

    /**
     * Removes library rows that no longer correspond to a real local file, or
     * that point at the old generated demo tones.
     */
    private int purgeInvalidRows() {
        List<Integer> staleIds = new ArrayList<>();
        String sql = "SELECT song_id, file_path FROM Songs";
        try (Statement stmt = getConn().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String path = rs.getString("file_path");
                if (path == null || path.isBlank()) {
                    staleIds.add(rs.getInt("song_id"));
                    continue;
                }
                File audio = new File(path);
                if (!audio.isFile() || isDemoAudio(audio)) {
                    staleIds.add(rs.getInt("song_id"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Songs folder sync (scan) error: " + e.getMessage());
        }

        for (Integer id : staleIds) {
            if (DatabaseManager.getInstance().deleteSongCascade(id)) {
                System.out.println("Removed stale/hardcoded song row id=" + id);
            }
        }
        return staleIds.size();
    }

    /** True when the file lives inside the legacy generated-demo folder. */
    private boolean isDemoAudio(File audio) {
        String canonical = canonicalPath(audio);
        String demo = canonicalPath(new File(System.getProperty("user.dir"), DEMO_SONGS_FOLDER));
        return canonical != null && demo != null
                && canonical.toLowerCase().startsWith(demo.toLowerCase() + File.separator);
    }

    /** The library root folders: the real music folder plus the drop-in songs folder. */
    public List<File> getLibraryFolders() {
        List<File> folders = new ArrayList<>();
        for (String name : new String[]{"music", SONGS_FOLDER_NAME}) {
            File folder = new File(System.getProperty("user.dir"), name);
            if (!folder.exists() && !folder.mkdirs()) {
                System.err.println("Could not create library folder: " + folder.getAbsolutePath());
            }
            folders.add(folder);
        }
        return folders;
    }

    /** Forces the display title to come from the file name so song -> MP3 mapping is exact. */
    private boolean refreshDisplayName(int songId, File audioFile) {
        String desired = FileNameParser.fromFile(audioFile).getTitle();
        if (desired == null || desired.isBlank()) {
            return false;
        }
        String sql = "UPDATE Songs SET song_name = ? WHERE song_id = ?";
        try (PreparedStatement p = getConn().prepareStatement(sql)) {
            p.setString(1, desired);
            p.setInt(2, songId);
            return p.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Song name refresh error: " + e.getMessage());
            return false;
        }
    }

    /** True when the value looks like the mojibake produced by old tag readers
     *  (ID3 text stored as UTF-16 LE but decoded as big-endian/corrupted CJK). */
    private static boolean isCorruptedText(String value) {
        if (value == null || value.isBlank()) return false;
        for (int i = 0; i < value.length(); i++) {
            int cp = value.codePointAt(i);
            if (cp >= 0x2E80 && cp <= 0x9FFF) return true;
            if (cp >= 0xAC00 && cp <= 0xD7A3) return true;
            if (cp >= 0xF900 && cp <= 0xFAFF) return true;
            if (cp >= 0xFF00 && cp <= 0xFFEF) return true;
            if (cp == 0xFFFD) return true;
            if (Character.isHighSurrogate(value.charAt(i))) i++;
        }
        return false;
    }

    /** Replaces stored artist/album/genre/year when they are corrupted, reading the
     *  correct values from the audio file's own tags. Leaves healthy metadata alone. */
    private boolean refreshMetadataIfCorrupt(int songId, File audioFile) {
        String sql = "SELECT COALESCE(a.artist_name,''), COALESCE(g.genre_name,''), " +
                "COALESCE(s.album,''), COALESCE(s.year,'') " +
                "FROM Songs s LEFT JOIN Artists a ON s.artist_id = a.artist_id " +
                "LEFT JOIN Genres g ON s.genre_id = g.genre_id WHERE s.song_id = ?";
        boolean corrupt;
        try (PreparedStatement p = getConn().prepareStatement(sql)) {
            p.setInt(1, songId);
            try (ResultSet rs = p.executeQuery()) {
                if (!rs.next()) return false;
                corrupt = isCorruptedText(rs.getString(1)) || isCorruptedText(rs.getString(2))
                        || isCorruptedText(rs.getString(3)) || isCorruptedText(rs.getString(4));
            }
        } catch (SQLException e) {
            return false;
        }
        if (!corrupt) return false;

        MusicMetadata meta = Mp3TagReader.read(audioFile);
        boolean updated = false;

        String artist = meta.getArtist();
        if (artist != null && !artist.isBlank() && !isCorruptedText(artist)) {
            int artistId = getOrCreateArtist(artist);
            try (PreparedStatement p = getConn().prepareStatement(
                    "UPDATE Songs SET artist_id = ? WHERE song_id = ?")) {
                p.setInt(1, artistId);
                p.setInt(2, songId);
                updated |= p.executeUpdate() > 0;
            } catch (SQLException e) {
                System.err.println("Artist repair error: " + e.getMessage());
            }
        }
        String genre = meta.getGenre();
        if (genre != null && !genre.isBlank() && !isCorruptedText(genre)) {
            int genreId = getOrCreateGenre(genre);
            try (PreparedStatement p = getConn().prepareStatement(
                    "UPDATE Songs SET genre_id = ? WHERE song_id = ?")) {
                p.setInt(1, genreId);
                p.setInt(2, songId);
                updated |= p.executeUpdate() > 0;
            } catch (SQLException e) {
                System.err.println("Genre repair error: " + e.getMessage());
            }
        }
        String album = meta.getAlbum();
        String albumValue = (album != null && !album.isBlank() && !isCorruptedText(album)) ? album : "Single";
        String year = meta.getYear();
        String yearValue = (year != null && !year.isBlank() && !isCorruptedText(year)) ? year : "2024";
        try (PreparedStatement p = getConn().prepareStatement(
                "UPDATE Songs SET album = ?, year = ? WHERE song_id = ?")) {
            p.setString(1, albumValue);
            p.setString(2, yearValue);
            p.setInt(3, songId);
            updated |= p.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Album/year repair error: " + e.getMessage());
        }

        if (updated) {
            System.out.println("Auto-repaired corrupt metadata for song_id=" + songId);
        }
        return updated;
    }

    /**
     * Copies the user-selected audio files into the project songs folder (so
     * they are kept and are re-loaded automatically next launch) and then
     * indexes them. Files already inside the songs folder are imported
     * directly without copying.
     *
     * @return number of new tracks added to the library.
     */
    public int addSongsToLibrary(List<File> files) {
        if (files == null || files.isEmpty()) {
            return 0;
        }
        File songsFolder = getSongsFolder();
        String folderPrefix = canonicalPath(songsFolder);
        List<File> toImport = new ArrayList<>();
        int copied = 0;

        for (File file : files) {
            if (!isSupportedAudioFile(file)) {
                continue;
            }
            String canonical = canonicalPath(file);
            if (canonical != null && folderPrefix != null
                    && canonical.toLowerCase().startsWith(folderPrefix.toLowerCase() + File.separator)) {
                toImport.add(file);
                continue;
            }
            File target = new File(songsFolder, file.getName());
            try {
                Files.copy(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                toImport.add(target);
                copied++;
            } catch (IOException e) {
                System.err.println("Could not copy " + file.getName() + " into songs folder: " + e.getMessage());
                toImport.add(file);
            }
        }

        int imported = importFiles(toImport);
        System.out.println("Add songs: copied=" + copied + ", new tracks=" + imported);
        return imported;
    }

    /** Extensions accepted by the importer and the folder scanner. */
    private static final String[] SUPPORTED_EXTENSIONS =
            {".mp3", ".wav", ".m4a", ".flac", ".aac", ".ogg"};

    private static boolean isSupportedAudioFile(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        String name = file.getName().toLowerCase();
        for (String extension : SUPPORTED_EXTENSIONS) {
            if (name.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reliably imports a list of user-selected audio files.
     *
     * Each file is canonicalised to its real absolute path, duplicate paths
     * are ignored, and metadata is read from the file (MP3 tags when present,
     * otherwise a filename-derived fallback). Only rows actually inserted are
     * counted.
     */
    public int importFiles(List<File> files) {
        if (files == null || files.isEmpty()) {
            return 0;
        }
        int imported = 0;
        int skipped = 0;
        for (File file : files) {
            if (!isSupportedAudioFile(file)) {
                continue;
            }
            String canonicalPath = canonicalPath(file);
            if (canonicalPath == null) {
                System.err.println("Cannot resolve path for: " + file.getAbsolutePath());
                skipped++;
                continue;
            }
            MusicMetadata meta = Mp3TagReader.read(file);
            patchMissingMetadata(meta, file);

            if (DatabaseManager.getInstance()
                    .insertImportedSong(meta, canonicalPath, detectLanguage(file))) {
                imported++;
            } else {
                skipped++;
            }
        }
        System.out.println("Import: added=" + imported + ", skipped/duplicate=" + skipped);
        return imported;
    }

    /** Fills any fields the tag reader could not provide with filename-derived values.
     *  The displayed song name always comes from the file name. */
    private void patchMissingMetadata(MusicMetadata meta, File file) {
        MusicMetadata fallback = FileNameParser.fromFile(file);
        meta.setTitle(fallback.getTitle());
        if ("Unknown Artist".equalsIgnoreCase(meta.getArtist())) {
            meta.setArtist(fallback.getArtist());
        }
        if ("Unknown Album".equalsIgnoreCase(meta.getAlbum())) {
            meta.setAlbum(fallback.getAlbum());
        }
        if ("Pop".equalsIgnoreCase(meta.getGenre())) {
            meta.setGenre(fallback.getGenre());
        }
        if ("2024".equalsIgnoreCase(meta.getYear())) {
            meta.setYear(fallback.getYear());
        }
    }

    private String canonicalPath(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            return file.getAbsolutePath();
        }
    }

    public ObservableList<Song> getAllSongs() {
        ObservableList<Song> songs = FXCollections.observableArrayList();
        String sql = "SELECT s.song_id, s.song_name, " +
                "COALESCE(a.artist_name, 'Offline Artist') as artist_name, " +
                "COALESCE(g.genre_name, 'Pop') as genre_name, " +
                "s.album, s.duration, s.language, s.file_path " +
                "FROM Songs s " +
                "LEFT JOIN Artists a ON s.artist_id = a.artist_id " +
                "LEFT JOIN Genres g ON s.genre_id = g.genre_id " +
                "ORDER BY s.song_name ASC";
        try (Statement stmt = getConn().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
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
        } catch (SQLException e) {
            System.err.println("getAllSongs Error: " + e.getMessage());
        }
        return songs;
    }

    public ObservableList<Song> searchSongs(String query) {
        ObservableList<Song> songs = FXCollections.observableArrayList();
        String sql = "SELECT s.song_id, s.song_name, " +
                "COALESCE(a.artist_name, 'Offline Artist') as artist_name, " +
                "COALESCE(g.genre_name, 'Pop') as genre_name, " +
                "s.album, s.duration, s.language, s.file_path " +
                "FROM Songs s " +
                "LEFT JOIN Artists a ON s.artist_id = a.artist_id " +
                "LEFT JOIN Genres g ON s.genre_id = g.genre_id " +
                "WHERE s.song_name LIKE ? OR a.artist_name LIKE ? OR s.album LIKE ?";
        try (PreparedStatement p = getConn().prepareStatement(sql)) {
            String term = "%" + query.trim() + "%";
            p.setString(1, term);
            p.setString(2, term);
            p.setString(3, term);
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
            System.err.println("Search error: " + e.getMessage());
        }
        return songs;
    }

    public ObservableList<Song> filterByGenre(String genre) {
        if (genre == null || genre.equalsIgnoreCase("ALL")) {
            return getAllSongs();
        }
        ObservableList<Song> songs = FXCollections.observableArrayList();
        String sql = "SELECT s.song_id, s.song_name, " +
                "COALESCE(a.artist_name, 'Offline Artist') as artist_name, " +
                "g.genre_name, s.album, s.duration, s.language, s.file_path " +
                "FROM Songs s " +
                "LEFT JOIN Artists a ON s.artist_id = a.artist_id " +
                "JOIN Genres g ON s.genre_id = g.genre_id " +
                "WHERE g.genre_name = ?";
        try (PreparedStatement p = getConn().prepareStatement(sql)) {
            p.setString(1, genre);
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
            System.err.println("Filter error: " + e.getMessage());
        }
        return songs;
    }

    /** Returns all songs, or only songs in the requested Tamil/Hindi/English category. */
    public ObservableList<Song> filterByLanguage(String language) {
        if (language == null || language.equalsIgnoreCase("All")) {
            return getAllSongs();
        }
        ObservableList<Song> songs = FXCollections.observableArrayList();
        String sql = "SELECT s.song_id, s.song_name, COALESCE(a.artist_name, 'Offline Artist') AS artist_name, "
                + "COALESCE(g.genre_name, 'Pop') AS genre_name, s.album, s.duration, s.language, s.file_path "
                + "FROM Songs s LEFT JOIN Artists a ON s.artist_id = a.artist_id "
                + "LEFT JOIN Genres g ON s.genre_id = g.genre_id WHERE lower(s.language) = lower(?) "
                + "ORDER BY s.song_name ASC";
        try (PreparedStatement p = getConn().prepareStatement(sql)) {
            p.setString(1, language);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    songs.add(new Song(rs.getInt("song_id"), rs.getString("song_name"),
                            rs.getString("artist_name"), rs.getString("genre_name"),
                            rs.getString("album"), rs.getString("duration"),
                            rs.getString("language"), rs.getString("file_path")));
                }
            }
        } catch (SQLException e) {
            System.err.println("Language filter error: " + e.getMessage());
        }
        return songs;
    }

    public int scanMusicFolder(String folderPath) {
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) return 0;

        List<File> audioFiles = new ArrayList<>();
        collectAudioFiles(folder, audioFiles);
        return importFiles(audioFiles);
    }

    /**
     * Scans and indexes a large local collection off the JavaFX application
     * thread. The returned task reports progress and yields the number of
     * tracks actually added (existing files are skipped as duplicates).
     */
    public Task<Integer> scanFolderTask(File folder) {
        return new Task<>() {
            @Override
            protected Integer call() throws Exception {
                if (folder == null || !folder.isDirectory()) {
                    updateMessage("Choose a valid music folder.");
                    return 0;
                }

                List<File> files = new ArrayList<>();
                collectAudioFiles(folder, files);
                int total = files.size();
                updateProgress(0, Math.max(1, total));
                updateMessage("Found " + total + " audio files. Indexing...");

                int imported = 0;
                for (int index = 0; index < total; index++) {
                    if (isCancelled()) {
                        break;
                    }
                    File file = files.get(index);
                    if (!isSupportedAudioFile(file)) {
                        continue;
                    }
                    String canonicalPath;
                    try {
                        canonicalPath = file.getCanonicalPath();
                    } catch (IOException e) {
                        canonicalPath = file.getAbsolutePath();
                    }
                    MusicMetadata meta = Mp3TagReader.read(file);
                    patchMissingMetadata(meta, file);
                    if (DatabaseManager.getInstance()
                            .insertImportedSong(meta, canonicalPath, detectLanguage(file))) {
                        imported++;
                    }
                    updateProgress(index + 1, total);
                    updateMessage("Indexed " + (index + 1) + "/" + total + "...");
                }
                updateMessage("Done. Added " + imported + " new track(s).");
                return imported;
            }
        };
    }

    private void collectAudioFiles(File dir, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectAudioFiles(f, result);
            } else if (f.isFile()) {
                String lower = f.getName().toLowerCase();
                if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".m4a") || lower.endsWith(".flac") || lower.endsWith(".aac") || lower.endsWith(".ogg")) {
                    result.add(f);
                }
            }
        }
    }

    private String detectLanguage(File file) {
        String source = (file.getAbsolutePath() + " " + file.getName()).toLowerCase();
        if (source.contains("tamil") || source.contains("kollywood")) return "Tamil";
        if (source.contains("hindi") || source.contains("bollywood")) return "Hindi";
        if (source.contains("english") || source.contains("hollywood")) return "English";
        return "English";
    }

    private int getOrCreateArtist(String artistName) {
        try (PreparedStatement select = getConn().prepareStatement(
                "SELECT artist_id FROM Artists WHERE artist_name = ?")) {
            select.setString(1, artistName);
            try (ResultSet rs = select.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }

            try (PreparedStatement insert = getConn().prepareStatement(
                    "INSERT INTO Artists (artist_name) VALUES (?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                insert.setString(1, artistName);
                insert.executeUpdate();
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getInt(1);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Artist get/create error: " + e.getMessage());
        }
        return 1;
    }

    private int getOrCreateGenre(String genreName) {
        try (PreparedStatement select = getConn().prepareStatement(
                "SELECT genre_id FROM Genres WHERE genre_name = ?")) {
            select.setString(1, genreName);
            try (ResultSet rs = select.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }

            try (PreparedStatement insert = getConn().prepareStatement(
                    "INSERT INTO Genres (genre_name) VALUES (?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                insert.setString(1, genreName);
                insert.executeUpdate();
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getInt(1);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Genre get/create error: " + e.getMessage());
        }
        return 1;
    }

    public List<String> getAllGenres() {
        List<String> genres = new ArrayList<>();
        genres.add("ALL");
        try (Statement stmt = getConn().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT genre_name FROM Genres ORDER BY genre_name ASC")) {
            while (rs.next()) {
                genres.add(rs.getString("genre_name"));
            }
        } catch (SQLException e) {
            System.err.println("getAllGenres error: " + e.getMessage());
        }
        return genres;
    }

    public boolean insertSongDirectly(String title, String artist, String genre, String album, String duration, String language, String filePath) {
        int artistId = getOrCreateArtist(artist);
        int genreId = getOrCreateGenre(genre);
        String sql = "INSERT OR IGNORE INTO Songs (song_name, artist_id, genre_id, album, duration, language, year, cover_color, file_path) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setInt(2, artistId);
            ps.setInt(3, genreId);
            ps.setString(4, album != null ? album : "Single");
            ps.setString(5, duration != null ? duration : "0:00");
            ps.setString(6, language != null ? language : "General");
            ps.setString(7, "2024");
            ps.setString(8, "#4F46E5");
            ps.setString(9, filePath);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("insertSongDirectly error: " + e.getMessage());
            return false;
        }
    }
}
