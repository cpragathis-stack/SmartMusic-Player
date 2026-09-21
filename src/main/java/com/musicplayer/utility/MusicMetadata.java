package com.musicplayer.utility;

/**
 * Lightweight container for audio metadata discovered during import.
 *
 * <p>Only fields that are reasonably available offline are populated.
 * Missing fields fall back to safe defaults derived from the file name.
 */
public class MusicMetadata {

    private String title;
    private String artist;
    private String album;
    private String genre;
    private String year;

    public MusicMetadata() {
        this.title = "";
        this.artist = "Unknown Artist";
        this.album = "Unknown Album";
        this.genre = "Pop";
        this.year = "2024";
    }

    public String getTitle() {
        return title == null ? "" : title;
    }

    public void setTitle(String title) {
        this.title = title == null ? "" : title.trim();
    }

    public String getArtist() {
        return artist == null || artist.isBlank() ? "Unknown Artist" : artist;
    }

    public void setArtist(String artist) {
        this.artist = artist == null ? "" : artist.trim();
    }

    public String getAlbum() {
        return album == null || album.isBlank() ? "Unknown Album" : album;
    }

    public void setAlbum(String album) {
        this.album = album == null ? "" : album.trim();
    }

    public String getGenre() {
        return genre == null || genre.isBlank() ? "Pop" : genre;
    }

    public void setGenre(String genre) {
        this.genre = genre == null ? "" : genre.trim();
    }

    public String getYear() {
        return year == null || year.isBlank() ? "2024" : year;
    }

    public void setYear(String year) {
        this.year = year == null ? "" : year.trim();
    }
}