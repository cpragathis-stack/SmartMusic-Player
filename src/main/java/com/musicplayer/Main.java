package com.musicplayer;

import com.musicplayer.auth.UserSession;
import com.musicplayer.database.DatabaseManager;
import com.musicplayer.library.MusicLibrary;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main extends Application {

    public Main() {
        // Explicit default constructor for JavaFX
    }

    @Override
    public void start(Stage stage) throws Exception {
        // 1. Initialize database and preload offline music
        DatabaseManager.getInstance();

        // 2. Load every .mp3 from the project "music"/"songs" folders and drop stale/hardcoded rows.
        MusicLibrary library = new MusicLibrary();
        library.syncSongsFolder();

        // 3. Restore a persisted session when available.
        UserSession.loadSession();

        double width = UserSession.isLoggedIn() ? 1280 : 780;
        double height = UserSession.isLoggedIn() ? 820 : 620;

        // 4. Find the main screen (classpath resources, with a source-tree fallback).
        String fxmlName = UserSession.isLoggedIn() ? "/fxml/MainShell.fxml" : "/fxml/Login.fxml";
        URL fxmlUrl = resolveFxml(fxmlName);
        if (fxmlUrl == null) {
            System.err.println("CRITICAL: Cannot find UI resource " + fxmlName);
            System.err.println("Launch via run.bat / run.ps1 from the project folder, "
                    + "or ensure the fxml and css resources ship with the classes.");
            javafx.application.Platform.exit();
            return;
        }

        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root;
        try {
            root = loader.load();
        } catch (IOException e) {
            System.err.println("CRITICAL: Failed to load UI from " + fxmlUrl + ": " + e.getMessage());
            javafx.application.Platform.exit();
            return;
        }

        double screenW = javafx.stage.Screen.getPrimary().getVisualBounds().getWidth();
        double screenH = javafx.stage.Screen.getPrimary().getVisualBounds().getHeight();
        width = Math.min(width, screenW - 20);
        height = Math.min(height, screenH - 20);

        Scene scene = new Scene(root, width, height);
        stage.setTitle("SmartBeats - Intelligent Offline Music Player (v2)");
        stage.setMinWidth(700);
        stage.setMinHeight(560);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
    }

    /** Locates an FXML resource on the classpath, falling back to the source tree so the
     *  app still starts when the resources were not copied next to the compiled classes. */
    private URL resolveFxml(String classpathPath) {
        URL url = getClass().getResource(classpathPath);
        if (url != null) {
            return url;
        }
        try {
            Path source = Path.of(System.getProperty("user.dir"),
                    "src", "main", "resources", classpathPath.substring(1));
            if (Files.isRegularFile(source)) {
                return source.toUri().toURL();
            }
        } catch (IOException ignored) {
            // fall through to null -> caller reports the problem clearly
        }
        return null;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
