package com.musicplayer.library;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

public class Song {
    private final SimpleIntegerProperty songId;
    private final SimpleStringProperty songName;
    private final SimpleStringProperty artist;
    private final SimpleStringProperty genre;
    private final SimpleStringProperty album;
    private final SimpleStringProperty duration;
    private final SimpleStringProperty language;
    private final SimpleStringProperty filePath;
    private final SimpleStringProperty coverPath;
    private final SimpleIntegerProperty playCount;
    private final SimpleIntegerProperty skipCount;
    private boolean favorite;
    private boolean recentlyPlayed;
    private final String year;
    private final String coverColor;

    // Simplified constructor for database queries
    public Song(int songId, String songName, String artist, String genre, String filePath) {
        // Provide default placeholder values for unused fields
        this(songId, songName, artist, genre, "Unknown Album", "0:00", "Unknown", "2024", filePath, "#38BDF8");
    }

    /** Constructor used by library queries that do not persist year or cover color. */
    public Song(int songId, String songName, String artist, String genre,
                String album, String duration, String language, String filePath) {
        this(songId, songName, artist, genre, album, duration, language,
                "2024", filePath, "#38BDF8");
    }




    public Song(int songId, String songName, String artist,
                String genre, String album, String duration,
                String language, String year, String filePath, String coverColor) {
        this.songId = new SimpleIntegerProperty(songId);
        this.songName = new SimpleStringProperty(songName);
        this.artist = new SimpleStringProperty(artist);
        this.genre = new SimpleStringProperty(genre);
        this.album = new SimpleStringProperty(album);
        this.duration = new SimpleStringProperty(duration);
        this.language = new SimpleStringProperty(language);
        this.filePath = new SimpleStringProperty(filePath);
        this.coverPath = new SimpleStringProperty("");
        this.playCount = new SimpleIntegerProperty(0);
        this.skipCount = new SimpleIntegerProperty(0);
        this.year = year != null ? year : "2024";
        this.coverColor = coverColor != null ? coverColor : "#38BDF8";
    }

    public int getSongId() { return songId.get(); }
    public String getSongName() { return songName.get(); }
    public String getArtist() { return artist.get(); }
    public String getGenre() { return genre.get(); }
    public String getAlbum() { return album.get(); }
    public String getDuration() { return duration.get(); }
    public String getLanguage() { return language.get(); }
    public String getFilePath() { return filePath.get(); }
    public String getCoverPath() { return coverPath.get(); }
    public int getPlayCount() { return playCount.get(); }
    public String getYear() { return year; }
    public String getCoverColor() { return coverColor; }

    public SimpleIntegerProperty songIdProperty() { return songId; }
    public SimpleStringProperty songNameProperty() { return songName; }
    public SimpleStringProperty artistProperty() { return artist; }
    public SimpleStringProperty genreProperty() { return genre; }
    public SimpleStringProperty albumProperty() { return album; }
    public SimpleStringProperty durationProperty() { return duration; }
    public SimpleStringProperty languageProperty() { return language; }
    public SimpleStringProperty coverPathProperty() { return coverPath; }
    public SimpleIntegerProperty playCountProperty() { return playCount; }

    public void setCoverPath(String value) { coverPath.set(value == null ? "" : value); }
    public void setPlayCount(int value) { playCount.set(Math.max(0, value)); }

    public int getSkipCount() { return skipCount.get(); }
    public void setSkipCount(int value) { skipCount.set(Math.max(0, value)); }

    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }

    public boolean isRecentlyPlayed() { return recentlyPlayed; }
    public void setRecentlyPlayed(boolean recentlyPlayed) { this.recentlyPlayed = recentlyPlayed; }

    @Override
    public String toString() {
        return getSongName() + " - " + getArtist();
    }
}
