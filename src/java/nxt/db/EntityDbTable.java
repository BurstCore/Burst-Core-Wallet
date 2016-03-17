/******************************************************************************
 * Copyright © 2013-2016 The Nxt Core Developers.                             *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * Nxt software, including this file, may be copied, modified, propagated,    *
 * or distributed except according to the terms contained in the LICENSE.txt  *
 * file.                                                                      *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

package nxt.db;

import nxt.Nxt;
import nxt.util.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public abstract class EntityDbTable<T> extends DerivedDbTable {

    private final boolean multiversion;
    protected final DbKey.Factory<T> dbKeyFactory;
    private final String defaultSort;
    private final String fullTextSearchColumns;

    protected EntityDbTable(String schemaTable, DbKey.Factory<T> dbKeyFactory) {
        this(schemaTable, dbKeyFactory, false, null);
    }

    protected EntityDbTable(String schemaTable, DbKey.Factory<T> dbKeyFactory, String fullTextSearchColumns) {
        this(schemaTable, dbKeyFactory, false, fullTextSearchColumns);
    }

    EntityDbTable(String schemaTable, DbKey.Factory<T> dbKeyFactory, boolean multiversion, String fullTextSearchColumns) {
        super(schemaTable);
        this.dbKeyFactory = dbKeyFactory;
        this.multiversion = multiversion;
        this.defaultSort = " ORDER BY " + (multiversion ? dbKeyFactory.getPKColumns() : " height DESC, db_id DESC ");
        if (fullTextSearchColumns != null) {
            fullTextSearchColumns = fullTextSearchColumns.toUpperCase();
        }
        this.fullTextSearchColumns = fullTextSearchColumns;
    }

    protected abstract T load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException;

    protected abstract void save(Connection con, T t) throws SQLException;

    protected String defaultSort() {
        return defaultSort;
    }

    protected void clearCache() {
        db.clearCache(schemaTable);
    }

    public void checkAvailable(int height) {
        if (multiversion && height < Nxt.getBlockchainProcessor().getMinRollbackHeight()) {
            throw new IllegalArgumentException("Historical data as of height " + height +" not available.");
        }
        if (height > Nxt.getBlockchain().getHeight()) {
            throw new IllegalArgumentException("Height " + height + " exceeds blockchain height " + Nxt.getBlockchain().getHeight());
        }
    }

    public final T newEntity(DbKey dbKey) {
        boolean cache = db.isInTransaction();
        if (cache) {
            T t = (T) db.getCache(schemaTable).get(dbKey);
            if (t != null) {
                return t;
            }
        }
        T t = dbKeyFactory.newEntity(dbKey);
        if (cache) {
            db.getCache(schemaTable).put(dbKey, t);
        }
        return t;
    }

    public final T get(DbKey dbKey) {
        return get(dbKey, true);
    }

    public final T get(DbKey dbKey, boolean cache) {
        if (cache && db.isInTransaction()) {
            T t = (T) db.getCache(schemaTable).get(dbKey);
            if (t != null) {
                return t;
            }
        }
        try (Connection con = getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + schemaTable + dbKeyFactory.getPKClause()
             + (multiversion ? " AND latest = TRUE LIMIT 1" : ""))) {
            dbKey.setPK(pstmt);
            return get(con, pstmt, cache);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public final T get(DbKey dbKey, int height) {
        if (height < 0 || height == Nxt.getBlockchain().getHeight()) {
            return get(dbKey);
        }
        checkAvailable(height);
        try (Connection con = getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + schemaTable + dbKeyFactory.getPKClause()
                     + " AND height <= ?" + (multiversion ? " AND (latest = TRUE OR EXISTS ("
                     + "SELECT 1 FROM " + schemaTable + dbKeyFactory.getPKClause() + " AND height > ?)) ORDER BY height DESC LIMIT 1" : ""))) {
            int i = dbKey.setPK(pstmt);
            pstmt.setInt(i, height);
            if (multiversion) {
                i = dbKey.setPK(pstmt, ++i);
                pstmt.setInt(i, height);
            }
            return get(con, pstmt, false);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public final T getBy(DbClause dbClause) {
        try (Connection con = getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + schemaTable
                     + " WHERE " + dbClause.getClause() + (multiversion ? " AND latest = TRUE LIMIT 1" : ""))) {
            dbClause.set(pstmt, 1);
            return get(con, pstmt, true);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public final T getBy(DbClause dbClause, int height) {
        if (height < 0 || height == Nxt.getBlockchain().getHeight()) {
            return getBy(dbClause);
        }
        checkAvailable(height);
        try (Connection con = getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + schemaTable + " AS a WHERE " + dbClause.getClause()
                     + " AND height <= ?" + (multiversion ? " AND (latest = TRUE OR EXISTS ("
                     + "SELECT 1 FROM " + schemaTable + " AS b WHERE " + dbKeyFactory.getSelfJoinClause()
                     + " AND b.height > ?)) ORDER BY height DESC LIMIT 1" : ""))) {
            int i = 0;
            i = dbClause.set(pstmt, ++i);
            pstmt.setInt(i, height);
            if (multiversion) {
                pstmt.setInt(++i, height);
            }
            return get(con, pstmt, false);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    private T get(Connection con, PreparedStatement pstmt, boolean cache) throws SQLException {
        final boolean doCache = cache && db.isInTransaction();
        try (ResultSet rs = pstmt.executeQuery()) {
            if (!rs.next()) {
                return null;
            }
            T t = null;
            DbKey dbKey = null;
            if (doCache) {
                dbKey = dbKeyFactory.newKey(rs);
                t = (T) db.getCache(schemaTable).get(dbKey);
            }
            if (t == null) {
                t = load(con, rs, dbKey);
                if (doCache) {
                    db.getCache(schemaTable).put(dbKey, t);
                }
            }
            if (rs.next()) {
                throw new RuntimeException("Multiple records found");
            }
            return t;
        }
    }

    public final DbIterator<T> getManyBy(DbClause dbClause, int from, int to) {
        return getManyBy(dbClause, from, to, defaultSort());
    }

    public final DbIterator<T> getManyBy(DbClause dbClause, int from, int to, String sort) {
        Connection con = null;
        try {
            con = getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + schemaTable
                    + " WHERE " + dbClause.getClause() + (multiversion ? " AND latest = TRUE " : " ") + sort
                    + DbUtils.limitsClause(from, to));
            int i = 0;
            i = dbClause.set(pstmt, ++i);
            i = DbUtils.setLimits(i, pstmt, from, to);
            return getManyBy(con, pstmt, true);
        } catch (SQLException e) {
            DbUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    public final DbIterator<T> getManyBy(DbClause dbClause, int height, int from, int to) {
        return getManyBy(dbClause, height, from, to, defaultSort());
    }

    public final DbIterator<T> getManyBy(DbClause dbClause, int height, int from, int to, String sort) {
        if (height < 0 || height == Nxt.getBlockchain().getHeight()) {
            return getManyBy(dbClause, from, to, sort);
        }
        checkAvailable(height);
        Connection con = null;
        try {
            con = getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + schemaTable + " AS a WHERE " + dbClause.getClause()
                    + "AND a.height <= ?" + (multiversion ? " AND (a.latest = TRUE OR (a.latest = FALSE "
                    + "AND EXISTS (SELECT 1 FROM " + schemaTable + " AS b WHERE " + dbKeyFactory.getSelfJoinClause() + " AND b.height > ?) "
                    + "AND NOT EXISTS (SELECT 1 FROM " + schemaTable + " AS b WHERE " + dbKeyFactory.getSelfJoinClause()
                    + " AND b.height <= ? AND b.height > a.height))) "
                    : " ") + sort
                    + DbUtils.limitsClause(from, to));
            int i = 0;
            i = dbClause.set(pstmt, ++i);
            pstmt.setInt(i, height);
            if (multiversion) {
                pstmt.setInt(++i, height);
                pstmt.setInt(++i, height);
            }
            i = DbUtils.setLimits(++i, pstmt, from, to);
            return getManyBy(con, pstmt, false);
        } catch (SQLException e) {
            DbUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    public final DbIterator<T> getManyBy(Connection con, PreparedStatement pstmt, boolean cache) {
        final boolean doCache = cache && db.isInTransaction();
        return new DbIterator<>(con, pstmt, (connection, rs) -> {
            T t = null;
            DbKey dbKey = null;
            if (doCache) {
                dbKey = dbKeyFactory.newKey(rs);
                t = (T) db.getCache(schemaTable).get(dbKey);
            }
            if (t == null) {
                t = load(connection, rs, dbKey);
                if (doCache) {
                    db.getCache(schemaTable).put(dbKey, t);
                }
            }
            return t;
        });
    }

    public final DbIterator<T> search(String query, DbClause dbClause, int from, int to) {
        return search(query, dbClause, from, to, " ORDER BY ft.score DESC ");
    }

    public final DbIterator<T> search(String query, DbClause dbClause, int from, int to, String sort) {
        Connection con = null;
        try {
            con = getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT " + schemaTable + ".*, ft.score FROM " + schemaTable +
                    ", ftl_search('PUBLIC', '" + schemaTable + "', ?, 2147483647, 0) ft "
                    + " WHERE " + schemaTable + ".db_id = ft.keys[0] "
                    + (multiversion ? " AND " + schemaTable + ".latest = TRUE " : " ")
                    + " AND " + dbClause.getClause() + sort
                    + DbUtils.limitsClause(from, to));
            int i = 0;
            pstmt.setString(++i, query);
            i = dbClause.set(pstmt, ++i);
            i = DbUtils.setLimits(i, pstmt, from, to);
            return getManyBy(con, pstmt, true);
        } catch (SQLException e) {
            DbUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    public final DbIterator<T> getAll(int from, int to) {
        return getAll(from, to, defaultSort());
    }

    public final DbIterator<T> getAll(int from, int to, String sort) {
        Connection con = null;
        try {
            con = getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + schemaTable
                     + (multiversion ? " WHERE latest = TRUE " : " ") + sort
                    + DbUtils.limitsClause(from, to));
            DbUtils.setLimits(1, pstmt, from, to);
            return getManyBy(con, pstmt, true);
        } catch (SQLException e) {
            DbUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    public final DbIterator<T> getAll(int height, int from, int to) {
        return getAll(height, from, to, defaultSort());
    }

    public final DbIterator<T> getAll(int height, int from, int to, String sort) {
        if (height < 0 || height == Nxt.getBlockchain().getHeight()) {
            return getAll(from, to, sort);
        }
        checkAvailable(height);
        Connection con = null;
        try {
            con = getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + schemaTable + " AS a WHERE height <= ?"
                    + (multiversion ? " AND (latest = TRUE OR (latest = FALSE "
                    + "AND EXISTS (SELECT 1 FROM " + schemaTable + " AS b WHERE b.height > ? AND " + dbKeyFactory.getSelfJoinClause()
                    + ") AND NOT EXISTS (SELECT 1 FROM " + schemaTable + " AS b WHERE b.height <= ? AND " + dbKeyFactory.getSelfJoinClause()
                    + " AND b.height > a.height))) " : " ") + sort
                    + DbUtils.limitsClause(from, to));
            int i = 0;
            pstmt.setInt(++i, height);
            if (multiversion) {
                pstmt.setInt(++i, height);
                pstmt.setInt(++i, height);
            }
            i = DbUtils.setLimits(++i, pstmt, from, to);
            return getManyBy(con, pstmt, false);
        } catch (SQLException e) {
            DbUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    public final int getCount() {
        try (Connection con = getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT COUNT(*) FROM " + schemaTable
                     + (multiversion ? " WHERE latest = TRUE" : ""))) {
            return getCount(pstmt);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public final int getCount(DbClause dbClause) {
        try (Connection con = getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT COUNT(*) FROM " + schemaTable
                     + " WHERE " + dbClause.getClause() + (multiversion ? " AND latest = TRUE" : ""))) {
            dbClause.set(pstmt, 1);
            return getCount(pstmt);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public final int getCount(DbClause dbClause, int height) {
        if (height < 0 || height == Nxt.getBlockchain().getHeight()) {
            return getCount(dbClause);
        }
        checkAvailable(height);
        Connection con = null;
        try {
            con = getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT COUNT(*) FROM " + schemaTable + " AS a WHERE " + dbClause.getClause()
                    + "AND a.height <= ?" + (multiversion ? " AND (a.latest = TRUE OR (a.latest = FALSE "
                    + "AND EXISTS (SELECT 1 FROM " + schemaTable + " AS b WHERE " + dbKeyFactory.getSelfJoinClause() + " AND b.height > ?) "
                    + "AND NOT EXISTS (SELECT 1 FROM " + schemaTable + " AS b WHERE " + dbKeyFactory.getSelfJoinClause()
                    + " AND b.height <= ? AND b.height > a.height))) "
                    : " "));
            int i = 0;
            i = dbClause.set(pstmt, ++i);
            pstmt.setInt(i, height);
            if (multiversion) {
                pstmt.setInt(++i, height);
                pstmt.setInt(++i, height);
            }
            return getCount(pstmt);
        } catch (SQLException e) {
            DbUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    public final int getRowCount() {
        try (Connection con = getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT COUNT(*) FROM " + schemaTable)) {
            return getCount(pstmt);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    private int getCount(PreparedStatement pstmt) throws SQLException {
        try (ResultSet rs = pstmt.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    public final void insert(T t) {
        if (!db.isInTransaction()) {
            throw new IllegalStateException("Not in transaction");
        }
        DbKey dbKey = dbKeyFactory.newKey(t);
        if (dbKey == null) {
            throw new RuntimeException("DbKey not set");
        }
        T cachedT = (T) db.getCache(schemaTable).get(dbKey);
        if (cachedT == null) {
            db.getCache(schemaTable).put(dbKey, t);
        } else if (t != cachedT) { // not a bug
            Logger.logDebugMessage("In cache : " + cachedT.toString() + ", inserting " + t.toString());
            throw new IllegalStateException("Different instance found in Db cache, perhaps trying to save an object "
                    + "that was read outside the current transaction");
        }
        try (Connection con = getConnection()) {
            if (multiversion) {
                try (PreparedStatement pstmt = con.prepareStatement("UPDATE " + schemaTable
                        + " SET latest = FALSE " + dbKeyFactory.getPKClause() + " AND latest = TRUE LIMIT 1")) {
                    dbKey.setPK(pstmt);
                    pstmt.executeUpdate();
                }
            }
            save(con, t);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public void rollback(int height) {
        if (multiversion) {
            VersionedEntityDbTable.rollback(db, schema, schemaTable, height, dbKeyFactory);
        } else {
            super.rollback(height);
        }
    }

    @Override
    public void trim(int height) {
        if (multiversion) {
            VersionedEntityDbTable.trim(db, schema, schemaTable, height, dbKeyFactory);
        } else {
            super.trim(height);
        }
    }

    @Override
    public final void createSearchIndex(Connection con) throws SQLException {
        if (fullTextSearchColumns != null) {
            Logger.logDebugMessage("Creating search index on " + schemaTable + " (" + fullTextSearchColumns + ")");
            FullTextTrigger.createIndex(con, schema, table, fullTextSearchColumns);
        }
    }

}
