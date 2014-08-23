package nxt.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public abstract class VersioningValuesDbTable<T, V> extends ValuesDbTable<T, V> {

    protected VersioningValuesDbTable() {
        super(true);
    }

    @Override
    final void rollback(int height) {
        try (Connection con = Db.getConnection();
             PreparedStatement pstmtSelectToDelete = con.prepareStatement("SELECT DISTINCT id FROM " + table()
                     + " WHERE height >= ?");
             PreparedStatement pstmtDelete = con.prepareStatement("DELETE FROM " + table()
                     + " WHERE height >= ?");
             PreparedStatement pstmtSetLatest = con.prepareStatement("UPDATE " + table()
                     + " SET latest = TRUE WHERE id = ? AND height ="
                     + " (SELECT MAX(height) FROM " + table() + " WHERE id = ?)")) {
            pstmtSelectToDelete.setInt(1, height);
            try (ResultSet rs = pstmtSelectToDelete.executeQuery()) {
                while (rs.next()) {
                    Long id = rs.getLong("id");
                    pstmtDelete.setInt(1, height);
                    pstmtDelete.executeUpdate();
                    pstmtSetLatest.setLong(1, id);
                    pstmtSetLatest.setLong(2, id);
                    pstmtSetLatest.executeUpdate();
                    Db.getCache(table()).remove(id);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

}
