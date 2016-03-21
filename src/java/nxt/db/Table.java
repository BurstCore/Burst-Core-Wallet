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

import nxt.Db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class Table {

    protected static final TransactionalDb db = Db.db;

    protected final String schema;
    protected final String table;
    protected final String schemaTable;

    public Table(String schemaTable) {
        String[] s = schemaTable.toUpperCase().split("\\.");
        if (s.length != 2) {
            throw new IllegalArgumentException("Missing schema name " + schemaTable);
        }
        this.schema = s[0];
        this.table = s[1];
        this.schemaTable = schemaTable;
    }

    public final Connection getConnection() throws SQLException {
        return db.getConnection(schema);
    }

    public void truncate() {
        if (!db.isInTransaction()) {
            throw new IllegalStateException("Not in transaction");
        }
        try (Connection con = getConnection();
             Statement stmt = con.createStatement()) {
            stmt.executeUpdate("TRUNCATE TABLE " + schemaTable);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public final String getSchemaTable() {
        return schemaTable;
    }

    @Override
    public final String toString() {
        return schemaTable;
    }

}
