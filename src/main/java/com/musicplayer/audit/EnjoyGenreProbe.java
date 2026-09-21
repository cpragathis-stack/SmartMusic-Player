package com.musicplayer.audit;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/** One-shot probe: print real distinct genres with song counts. Deleted after use. */
public final class EnjoyGenreProbe {
    public static void main(String[] args) throws Exception {
        Connection c = DriverManager.getConnection("jdbc:sqlite:musicplayer.db");
        Statement s = c.createStatement();
        ResultSet rs = s.executeQuery(
                "SELECT COALESCE(g.genre_name,'(no genre)') AS g, COUNT(*) AS n " +
                "FROM Songs s LEFT JOIN Genres g ON s.genre_id=g.genre_id " +
                "GROUP BY g ORDER BY n DESC, g");
        while (rs.next()) {
            System.out.println(rs.getString("g") + " = " + rs.getInt("n"));
        }
        rs.close(); s.close(); c.close();
        System.out.println("DONE");
    }
}
