package com.musicplayer.probe;

import com.musicplayer.auth.UserSession;
import com.musicplayer.dashboard.MainShellController;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Captures real screenshots of every SmartBeats screen by loading the actual
 * FXML screens, letting JavaFX render them (CSS + layout) and snapshotting the
 * scene into PNG files under {@code screenshots/}.
 *
 * Run from the project root with the same module path as the app itself.
 */
public final class ScreenshotHarness extends Application {

    private static final Path OUT = Path.of(System.getProperty("user.dir"), "screenshots");
    private Scene shellScene;

    @Override
    public void start(Stage stage) throws Exception {
        OUT.toFile().mkdirs();
        UserSession.createSession(1, "Demo User", "demo@smartbeats.local");

        // ---- LOGIN SCREEN ----
        FXMLLoader loginLoader = new FXMLLoader(getClass().getResource("/fxml/Login.fxml"));
        Parent loginRoot = loginLoader.load();
        Stage loginStage = new Stage();
        loginStage.setScene(new Scene(loginRoot, 780, 620));
        loginStage.show();

        PauseTransition loginWait = new PauseTransition(Duration.millis(1200));
        loginWait.setOnFinished(e2 -> {
            saveScene(loginStage.getScene(), "login");
            loginStage.close();
            try {
                openShell();
            } catch (Exception ex) {
                System.err.println("MainShell load failed: " + ex);
                Platform.exit();
            }
        });
        loginWait.play();
    }

    private void openShell() throws Exception {
        FXMLLoader shellLoader = new FXMLLoader(getClass().getResource("/fxml/MainShell.fxml"));
        Parent shellRoot = shellLoader.load();
        MainShellController controller = shellLoader.getController();

        Stage shellStage = new Stage();
        Scene scene = new Scene(shellRoot, 1280, 820);
        shellScene = scene;
        shellStage.setScene(scene);
        shellStage.setTitle("SmartBeats - Intelligent Offline Music Player");
        shellStage.show();

        Deque<Runnable> queue = new ArrayDeque<>();
        queue.add(() -> nav(controller, MainShellController::showExplore, "explore", queue));
        queue.add(() -> nav(controller, MainShellController::showLibrary, "library", queue));
        queue.add(() -> nav(controller, MainShellController::showRecommendations, "recommendations", queue));
        queue.add(() -> nav(controller, MainShellController::showDashboard, "dashboard", queue));
        queue.add(() -> nav(controller, MainShellController::showListeningHistory, "history", queue));
        queue.add(() -> nav(controller, MainShellController::showFavorites, "favorites", queue));
        queue.add(() -> {
            System.out.println("All screenshots written to " + OUT.toAbsolutePath());
            Platform.exit();
        });

        PauseTransition first = new PauseTransition(Duration.millis(2000));
        first.setOnFinished(e -> {
            saveScene(scene, "home");
            next(queue, controller);
        });
        first.play();
    }

    private void next(Deque<Runnable> queue, MainShellController controller) {
        Runnable run = queue.poll();
        if (run != null) {
            run.run();
        }
    }

    private void nav(MainShellController controller,
                     Consumer<MainShellController> go,
                     String name,
                     Deque<Runnable> queue) {
        go.accept(controller);
        PauseTransition snap = new PauseTransition(Duration.millis(1100));
        snap.setOnFinished(e -> {
            saveScene(shellScene, name);
            next(queue, controller);
        });
        snap.play();
    }

    private void saveScene(Scene scene, String name) {
        try {
            javafx.scene.image.WritableImage img = scene.snapshot(null);
            int w = (int) img.getWidth();
            int h = (int) img.getHeight();
            BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    bi.setRGB(x, y, img.getPixelReader().getArgb(x, y));
                }
            }
            File target = OUT.resolve(name + ".png").toFile();
            ImageIO.write(bi, "png", target);
            System.out.println("Saved " + target.getName() + "  " + w + "x" + h);
        } catch (Exception ex) {
            System.err.println("Snapshot " + name + " failed: " + ex);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}