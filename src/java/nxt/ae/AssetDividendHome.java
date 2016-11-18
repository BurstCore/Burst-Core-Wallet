/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016 Jelurida IP B.V.
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of the Nxt software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

package nxt.ae;

import nxt.Nxt;
import nxt.account.Account;
import nxt.account.AccountLedger;
import nxt.account.BalanceHome;
import nxt.blockchain.ChildChain;
import nxt.blockchain.Transaction;
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
import java.util.ArrayList;
import java.util.List;

public final class AssetDividendHome {

    public enum Event {
        ASSET_DIVIDEND
    }

    public static AssetDividendHome forChain(ChildChain childChain) {
        if (childChain.getAssetDividendHome() != null) {
            throw new IllegalStateException("already set");
        }
        return new AssetDividendHome(childChain);
    }

    private final ChildChain childChain;
    private final DbKey.HashKeyFactory<AssetDividendHome.AssetDividend> dividendDbKeyFactory;
    private final EntityDbTable<AssetDividendHome.AssetDividend> assetDividendTable;

    private AssetDividendHome(ChildChain childChain) {
        this.childChain = childChain;
        this.dividendDbKeyFactory = new DbKey.HashKeyFactory<AssetDividend>("id", "full_hash") {
            @Override
            public DbKey newKey(AssetDividend assetDividend) {
                return assetDividend.dbKey;
            }
        };
        this.assetDividendTable = new EntityDbTable<AssetDividend>(childChain.getSchemaTable("asset_dividend"), dividendDbKeyFactory) {
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

    AssetDividend getLastDividend(long assetId) {
        try (DbIterator<AssetDividend> dividends = assetDividendTable.getManyBy(new DbClause.LongClause("asset_id", assetId), 0, 0)) {
            if (dividends.hasNext()) {
                return dividends.next();
            }
        }
        return null;
    }

    void payDividends(Transaction transaction, DividendPaymentAttachment attachment) {
        long totalDividend = 0;
        long issuerId = transaction.getSenderId();
        long transactionId = transaction.getId();
        List<Account.AccountAsset> accountAssets = new ArrayList<>();
        try (DbIterator<Account.AccountAsset> iterator = Account.getAssetAccounts(attachment.getAssetId(), attachment.getHeight(), 0, -1)) {
            while (iterator.hasNext()) {
                accountAssets.add(iterator.next());
            }
        }
        BalanceHome balanceHome = childChain.getBalanceHome();
        final long amountNQTPerQNT = attachment.getAmountNQTPerQNT();
        long numAccounts = 0;
        for (final Account.AccountAsset accountAsset : accountAssets) {
            if (accountAsset.getAccountId() != issuerId && accountAsset.getQuantityQNT() != 0) {
                long dividend = Math.multiplyExact(accountAsset.getQuantityQNT(), amountNQTPerQNT);
                balanceHome.getBalance(accountAsset.getAccountId())
                        .addToBalanceAndUnconfirmedBalance(AccountLedger.LedgerEvent.ASSET_DIVIDEND_PAYMENT, transactionId, dividend);
                totalDividend += dividend;
                numAccounts += 1;
            }
        }
        balanceHome.getBalance(issuerId).addToBalance(AccountLedger.LedgerEvent.ASSET_DIVIDEND_PAYMENT, transactionId, -totalDividend);
        AssetDividend assetDividend = new AssetDividend(transaction, attachment, totalDividend, numAccounts);
        assetDividendTable.insert(assetDividend);
        listeners.notify(assetDividend, Event.ASSET_DIVIDEND);
    }

    public final class AssetDividend {

        private final long id;
        private final byte[] hash;
        private final DbKey dbKey;
        private final long assetId;
        private final long amountNQTPerQNT;
        private final int dividendHeight;
        private final long totalDividend;
        private final long numAccounts;
        private final int timestamp;
        private final int height;

        private AssetDividend(Transaction transaction, DividendPaymentAttachment attachment,
                              long totalDividend, long numAccounts) {
            this.id = transaction.getId();
            this.hash = transaction.getFullHash();
            this.dbKey = dividendDbKeyFactory.newKey(this.hash, this.id);
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
            this.hash = rs.getBytes("full_hash");
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
            try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO asset_dividend (id, full_hash, asset_id, "
                    + "amount, dividend_height, total_dividend, num_accounts, timestamp, height) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                int i = 0;
                pstmt.setLong(++i, this.id);
                pstmt.setBytes(++i, this.hash);
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

        public byte[] getFullHash() {
            return hash;
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
