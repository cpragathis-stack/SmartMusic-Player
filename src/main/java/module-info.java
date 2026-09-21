module com.musicplayer {
    requires transitive javafx.base;
    requires transitive javafx.controls;
    requires transitive javafx.fxml;
    requires transitive javafx.media;
    requires transitive javafx.graphics;
    requires transitive java.sql;
    requires transitive java.desktop;
    requires static org.xerial.sqlitejdbc;
    requires jlayer;
    uses java.sql.Driver;

    opens com.musicplayer to javafx.fxml, javafx.graphics;
    opens com.musicplayer.auth to javafx.fxml;
    opens com.musicplayer.dashboard to javafx.fxml;
    opens com.musicplayer.history to javafx.fxml;
    opens com.musicplayer.library to javafx.fxml, javafx.base;
    opens com.musicplayer.player to javafx.fxml;
    opens com.musicplayer.playlist to javafx.fxml, javafx.base;
    opens com.musicplayer.recommendation to javafx.fxml, javafx.base;

    exports com.musicplayer;
    exports com.musicplayer.auth;
    exports com.musicplayer.dashboard;
    exports com.musicplayer.database;
    exports com.musicplayer.history;
    exports com.musicplayer.library;
    exports com.musicplayer.player;
    exports com.musicplayer.playlist;
    exports com.musicplayer.recommendation;
    exports com.musicplayer.utility;
}
