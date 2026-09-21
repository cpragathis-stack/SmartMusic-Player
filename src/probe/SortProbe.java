package probe;

import com.musicplayer.Main;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class SortProbe extends Application {
    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(SortProbe.class.getResource("/fxml/MainShell.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 1280, 820);
        stage.setScene(scene);
        stage.show();

        javafx.animation.PauseTransition wait = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(3));
        wait.setOnFinished(e -> {
            VBox center = (VBox) scenelookup(root);
            System.out.println("CENTER CHILDREN: " + center.getChildren().size());
            TableView<?> table = null;
            for (var n : center.getChildren()) {
                if (n instanceof TableView<?> t) { table = t; break; }
            }
            if (table == null) {
                System.out.println("NO TABLE FOUND");
                System.exit(1);
            }
            System.out.println("FIRST SCAN - items before sort: " + table.getItems().size());
            // find artist column
            TableColumn<?, ?> artistCol = null;
            for (TableColumn<?, ?> c : table.getColumns()) {
                if ("Artist".equals(c.getText())) artistCol = c;
            }
            if (artistCol == null) { System.out.println("NO ARTIST COLUMN"); System.exit(2); }
            try {
                table.getSortOrder().clear();
                table.getSortOrder().add((TableColumn) artistCol);
                table.sort();
                System.out.println("AFTER SORT BY ARTIST - items: " + table.getItems().size());
                // click again (descending) a few times
                for (int i = 0; i < 5; i++) {
                    artistCol.setSortType(artistCol.getSortType() == TableColumn.SortType.ASCENDING
                            ? TableColumn.SortType.DESCENDING : TableColumn.SortType.ASCENDING);
                    table.sort();
                    System.out.println("toggle " + i + " - items: " + table.getItems().size());
                }
                System.out.println("SORT_OK");
            } catch (Throwable t) {
                System.err.println("SORT FAILED");
                t.printStackTrace(System.err);
            }
            System.out.println("PROBE_DONE");
            System.exit(0);
        });
        wait.play();
    }

    private Object scenelookup(Parent root) {
        // find the VBox with id centerContent
        return ((VBox) root.lookup("#centerContent"));
    }
}