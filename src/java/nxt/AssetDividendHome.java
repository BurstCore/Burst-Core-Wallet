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

package nxt;

import nxt.db.DbClause;
import nxt.db.DbIterator;
import nxt.db.DbKey;
import nxt.db.EntityDbTable;
import nxt.util.Listener;
import nxt.util.Listeners;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class AssetDividendHome {

    public enum Event {
        ASSET_DIVIDEND
    }

    static AssetDividendHome forChain(ChildChain childChain) {
        if (childChain.getAssetDividendHome() != null) {
            throw new IllegalStateException("already set");
        }
        return new AssetDividendHome(childChain);
    }

    private final ChildChain childChain;
    private final DbKey.LongKeyFactory<AssetDividendHome.AssetDividend> dividendDbKeyFactory;
    private final EntityDbTable<AssetDividendHome.AssetDividend> assetDividendTable;

    private AssetDividendHome(ChildChain childChain) {
        this.childChain = childChain;
        this.dividendDbKeyFactory = new DbKey.LongKeyFactory<AssetDividend>("id") {
            @Override
            public DbKey newKey(AssetDividend assetDividend) {
                return assetDividend.dbKey;
            }
        };
        this.assetDividendTable = new EntityDbTable<AssetDividend>("asset_dividend", dividendDbKeyFactory) {
            @Override
            protected AssetDividend load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
                return new AssetDividend(rs, dbKey);
            }
            @Override
            protected void save(Connection con, AssetDividend assetDividend) throws SQLException {
                assetDividend.save(con);
            }
        };
    }

    private final Listeners<AssetDividend,Event> listeners = new Listeners<>();


    public boolean addListener(Listener<AssetDividend> listener, Event eventType) {
        return listeners.addListener(listener, eventType);
    }

    public boolean removeListener(Listener<AssetDividend> listener, Event eventType) {
        return listeners.removeListener(listener, eventType);
    }

    public DbIterator<AssetDividend> getAssetDividends(long assetId, int from, int to) {
        return assetDividendTable.getManyBy(new DbClause.LongClause("asset_id", assetId), from, to);
    }

    public AssetDividend getLastDividend(long assetId) {
        try (DbIterator<AssetDividend> dividends = assetDividendTable.getManyBy(new DbClause.LongClause("asset_id", assetId), 0, 0)) {
            if (dividends.hasNext()) {
                return dividends.next();
            }
        }
        return null;
    }

    AssetDividend addAssetDividend(long transactionId, Attachment.ColoredCoinsDividendPayment attachment,
                                          long totalDividend, long numAccounts) {
        AssetDividend assetDividend = new AssetDividend(transactionId, attachment, totalDividend, numAccounts);
        assetDividendTable.insert(assetDividend);
        listeners.notify(assetDividend, Event.ASSET_DIVIDEND);
        return assetDividend;
    }

    public final class AssetDividend {

        private final long id;
        private final DbKey dbKey;
        private final long assetId;
        private final long amountNQTPerQNT;
        private final int dividendHeight;
        private final long totalDividend;
        private final long numAccounts;
        private final int timestamp;
        private final int height;

        private AssetDividend(long transactionId, Attachment.ColoredCoinsDividendPayment attachment,
                              long totalDividend, long numAccounts) {
            this.id = transactionId;
            this.dbKey = dividendDbKeyFactory.newKey(this.id);
            this.assetId = attachment.getAssetId();
            this.amountNQTPerQNT = attachment.getAmountNQTPerQNT();
            this.dividendHeight = attachment.getHeight();
            this.totalDividend = totalDividend;
            this.numAccounts = numAccounts;
            this.timestamp = Nxt.getBlockchain().getLastBlockTimestamp();
            this.height = Nxt.getBlockchain().getHeight();
        }

        private AssetDividend(ResultSet rs, DbKey dbKey) throws SQLException {
            this.id = rs.getLong("id");
            this.dbKey = dbKey;
            this.assetId = rs.getLong("asset_id");
            this.amountNQTPerQNT = rs.getLong("amount");
            this.dividendHeight = rs.getInt("dividend_height");
            this.totalDividend = rs.getLong("total_dividend");
            this.numAccounts = rs.getLong("num_accounts");
            this.timestamp = rs.getInt("timestamp");
            this.height = rs.getInt("height");
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO asset_dividend (id, asset_id, "
                    + "amount, dividend_height, total_dividend, num_accounts, timestamp, height) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                int i = 0;
                pstmt.setLong(++i, this.id);
                pstmt.setLong(++i, this.assetId);
                pstmt.setLong(++i, this.amountNQTPerQNT);
                pstmt.setInt(++i, this.dividendHeight);
                pstmt.setLong(++i, this.totalDividend);
                pstmt.setLong(++i, this.numAccounts);
                pstmt.setInt(++i, this.timestamp);
                pstmt.setInt(++i, this.height);
                pstmt.executeUpdate();
            }
        }

        public long getId() {
            return id;
        }

        public long getAssetId() {
            return assetId;
        }

        public long getAmountNQTPerQNT() {
            return amountNQTPerQNT;
        }

        public int getDividendHeight() {
            return dividendHeight;
        }

        public long getTotalDividend() {
            return totalDividend;
        }

        public long getNumAccounts() {
            return numAccounts;
        }

        public int getTimestamp() {
            return timestamp;
        }

        public int getHeight() {
            return height;
        }
    }
}
