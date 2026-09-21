# SMARTBEATS — INTELLIGENT OFFLINE MUSIC PLAYER
## Complete Project Documentation

---

## 1. Project Overview & Executive Summary

**SmartBeats** is a fully offline desktop music library, player and discovery application built in **Java 17** and **JavaFX 21**. It works with **zero internet connection**: tracks, playlists, favourites, listening history, per-user data and the recommendation engine all live inside one embedded **SQLite** database on the local machine.

* **Target audience**: desktop users in offline / low-connectivity environments, institutions, rural areas, travel, and privacy-conscious listeners.
* **Core philosophy**: zero data cost, zero tracking, instant local playback, and intelligent on-device discovery — no account servers, no cloud APIs.

---

## 2. Technical Stack

| Layer | Technology | Purpose |
|---|---|---|
| **Core language** | Java 17 LTS (`module-info.java`, JPMS) | Application logic, database access |
| **UI** | JavaFX 21.0.2 + FXML | Desktop UI, charts, animations |
| **Styling** | Custom CSS (dark "Electric Indigo / Sunset Pink" theme) | Visual design |
| **Database** | SQLite via `org.xerial:sqlite-jdbc 3.44.1.0` | Local persistence (tracks, users, playlists, favourites, history, listening log) |
| **Audio playback** | JavaFX MediaPlayer for media files + JLayer for legacy decode | Playback of local audio files |
| **Logging** | slf4j-nop (deliberately silent) | Suppresses JDBC noise |
| **Discovery engine** | Rule-based offline scorer (deterministic, **not ML**) | Personalised recommendations |

The build is reproducible with plain `javac` (see `run.bat` / `run.ps1`):

```
javac -encoding UTF-8 -d target/classes --module-path lib
      --add-modules javafx.controls,javafx.fxml,javafx.media,java.sql,java.desktop
      -cp "lib/*"  (all .java under src/main/java)
```

---

## 3. System Architecture & Workflow

```
+------------------------------------------------------------------+
|                  PRESENTATION LAYER (JavaFX / FXML)              |
|  Login/Register • MainShell • Home • Library • Recommendations •  |
|  Playlists • History • Favourites • Dashboard • Now-Playing bar   |
+----------------------------------+-------------------------------+
                                   |
+----------------------------------v-------------------------------+
|   CORE CONTROLLERS & SESSION (MainShellController, UserSession)  |
+------------------+------------------------+----------------------+
|   AUDIO PLAYER   |  RECOMMENDATION ENGINE |  LIBRARY SCANNER     |
| (JavaFX Media +  |  deterministic scoring |  folder crawl / sync |
|  JLayer; skip &  |  over per-user history,|  of local MP3 files, |
|  listening-time  |  favourites, genres,   |  duplicate protection|
|  tracking)       |  artists, languages)   |  on file path)       |
+--------+---------+------------+-----------+----------+-----------+
         |                       |                      |
+--------v-----------------------v----------------------v-----------+
|        LOCAL PERSISTENCE (SQLite: musicplayer.db)                |
|  Users • Songs • Genres • Artists • Playlists • PlaylistSongs •  |
|  UserHistory • Favorites • ListeningLog                         |
+------------------------------------------------------------------+
```

All user-scoped tables carry a `user_id`, so separate accounts never leak history, favourites or play counts into each other's recommendations.

---

## 4. Database Schema & Data Dictionary

The local database is `musicplayer.db`, created automatically next to the app. Older databases are migrated in place on first launch; legacy history and favourites are preserved and re-attached to `user_id = 1`.

* **`Users`** — `user_id` (PK) • `username` (UNIQUE) • `password` (stored as a salted SHA-256 hash `salt:hash`; legacy plain-text rows are accepted at login and upgraded automatically) • `email` • `created_at` • `login_type`
* **`Songs`** — `song_id` (PK) • `song_name` • `artist_id` (FK) • `genre_id` (FK) • `album` • `duration` • `language` • `year` • `cover_color` • `file_path` (UNIQUE — duplicates rejected via `INSERT OR IGNORE`)
* **`Artists`** — `artist_id` (PK) • `artist_name` (UNIQUE) • `language`
* **`Genres`** — `genre_id` (PK) • `genre_name` (UNIQUE)
* **`Playlists`** — `playlist_id` (PK) • `playlist_name` • `created_date` • `user_id` (owner)
* **`PlaylistSongs`** — `id` (PK) • `playlist_id` (FK) • `song_id` (FK); a playlist references each song at most once
* **`UserHistory`** — **PK `(user_id, song_id)`** • `play_count` • `skip_count` • `listening_seconds` (cumulative real listening time) • `last_played`
* **`Favorites`** — **PK `(user_id, song_id)`** • `is_favorite` • `added_date`
* **`ListeningLog`** — chronological, unaggregated history: `log_id` (PK) • `user_id` • `song_id` • `played_at` • `played_seconds` (newest first)

---

## 5. Offline Recommendation Algorithm

The engine computes a deterministic composite score `S` for every song from the **current user's own** implicit feedback:

```
S = min(playCount x 3, 60)     // repeated plays count up to a 60-point cap
  + isFavorite x 10
  + skipCount x (-2)           // skips (a listen < 15 s is a skip) cost the song
  + inTop2Genre x 20           // top 2 genres by total plays
  + inTop2Artist x 15          // top 2 artists by total plays
  + favouriteLanguage x 5      // top language by total plays
  + playedInLast7Days x 10     // recency bonus
  + discoveryBonus x 10        // unplayed songs still get exposure
```

Songs are ranked by `S` descending and the **top 10** are returned. Every pick carries a human-readable reason, e.g. "Played 13 times", "You hearted this track", "Fresh pick from FILM, a genre you enjoy", "Fresh from [artist], an artist you like", or "In [language], a language you enjoy". No external API is ever called — scores adapt in real time to the local database.

## 6. Listening Analytics & Per-User Features

* **Listening time** — every second of playback is accumulated into `UserHistory.listening_seconds` (delta-based, so pausing/skipping/stopping never over-counts) and surfaced as a "Total Listening Time" stat on the Dashboard.
* **Chronological history** — each listen is appended to `ListeningLog(user_id, song_id, played_at, played_seconds)` and shown newest-first on the History screen.
* **Per-user data** — history, favourites, playlists and play counts are all scoped by `user_id`; recommendations only ever use the signed-in user's data.
* **Password hashing** — new accounts store salted SHA-256 hashes (`salt:hash`); legacy plain-text passwords still log in and are upgraded on first successful login.
* **Remove from playlist** — any playlist row offers a "Remove" action (button + context menu).

## 7. Test Suite (41 automated checks)

Fully self-contained tests live in `src/test/java/com/musicplayer/test/TestRunner.java`. No Maven/JUnit required — they run against an isolated temporary SQLite database and verify: duplicate detection, search, play/skip recording, listening-time accumulation, chronological log, favourites, playlist add/remove/delete, **per-user isolation**, password hashing (incl. legacy login), and the exact recommendation score for a seeded scenario. Run with:

```
java --module-path lib --add-modules javafx.controls,javafx.fxml,javafx.media,java.sql,java.desktop
     -cp "target/classes;target/test-classes;lib/*" com.musicplayer.test.TestRunner
```

Result: **41 passed, 0 failed** — capture in `test-results.txt`.

## 8. How to Run the Application

1. Double-click **`run.bat`** (or run `run.ps1`) from the project folder.
2. Log in with **`demo-user` / `demo1234`** (or register any new account). Pre-existing accounts with blank passwords also work.

The app ships with bundled demo tracks; "Add Songs", "Import Audio Files" and "Scan Audio Folder" load additional local `.mp3` files at runtime.

## 9. Presentation & Pitch Strategy

For a hackathon / competition, highlight these USPs:

1. **Zero data footprint & rural empowerment** — runs on basic offline computers in remote classrooms, trains, flights and low-connectivity regions.
2. **On-device recommendation engine** — no cloud server costs, complete user privacy, zero data leakage.
3. **Multi-format local playback** — bundled demo audio plus scanning of the user's own `.mp3` library; music works with no initial files at all thanks to bundled tracks.
4. **Clean modular Java architecture** — Java 17 JPMS (`module-info.java`), MVC-style controllers, embedded SQLite, and a dependency-free 41-check automated test suite.