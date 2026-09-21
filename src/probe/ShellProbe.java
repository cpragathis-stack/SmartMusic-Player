package probe;

import com.musicplayer.dashboard.MainShellController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.lang.reflect.Method;

public class ShellProbe extends Application {

    private MainShellController ctrl;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(ShellProbe.class.getResource("/fxml/MainShell.fxml"));
        Parent root = loader.load();
        ctrl = loader.getController();
        Scene scene = new Scene(root, 1280, 820);
        stage.setScene(scene);
        stage.show();

        javafx.animation.PauseTransition wait1 = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
        wait1.setOnFinished(e -> runViews());
        wait1.play();
    }

    private void runViews() {
        String[] methods = {"showHome", "showExplore", "showLibrary", "showDashboard",
                "showRecommendations", "showNowPlaying", "showFavorites"};
        for (String name : methods) {
            try {
                Method m = MainShellController.class.getMethod(name);
                System.out.println(">>> calling " + name);
                m.invoke(ctrl);
                Thread.sleep(300);
                System.out.println(">>> " + name + " OK");
            } catch (Throwable t) {
                System.err.println(">>> " + name + " FAILED");
                t.printStackTrace(System.err);
            }
        }
        System.out.println(">>> calling showSearchResults");
        try {
            Method m = MainShellController.class.getDeclaredMethod("showSearchResults", String.class);
            m.setAccessible(true);
            m.invoke(ctrl, "anirudh");
            System.out.println(">>> showSearchResults OK");
        } catch (Throwable t) {
            System.err.println(">>> showSearchResults FAILED");
            t.printStackTrace(System.err);
        }
        System.out.println("PROBE_DONE");
        System.exit(0);
    }
}