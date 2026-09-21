package probe;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/** Prints the distinct real genres with song counts from the live DB. */
public final class GenreCountProbe {

    public static void main(String[] args) throws Exception {
        File dbFile = new File("D:/SmartMusicPlayer/musicplayer.db");
        if (!dbFile.isFile()) {
            System.out.println("DB missing: " + dbFile);
            return;
        }

        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        Map<String, Integer> counts = new LinkedHashMap<>();
        int total = 0;

        try (Connection c = DriverManager.getConnection(url);
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT COALESCE(g.genre_name, '(none)') AS g, COUNT(*) AS n " +
                             "FROM Songs s LEFT JOIN Genres g ON s.genre_id = g.genre_id " +
                             "GROUP BY g ORDER BY n DESC, g")) {
            while (rs.next()) {
                counts.put(rs.getString("g"), rs.getInt("n"));
            }
        }

        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            total += e.getValue();
            System.out.println(e.getKey() + " = " + e.getValue());
        }

        System.out.println("DISTINCT=" + counts.size() + " TOTAL_SONGS=" + total);
    }
}
