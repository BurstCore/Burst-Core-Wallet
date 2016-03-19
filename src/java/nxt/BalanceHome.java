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

import nxt.db.DbKey;
import nxt.db.VersionedEntityDbTable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public final class BalanceHome {

    private static final Map<ChildChain, BalanceHome> balanceHomeMap = new HashMap<>();

    public static BalanceHome forChain(ChildChain childChain) {
        return balanceHomeMap.get(childChain);
    }

    static void init() {}

    static {
        ChildChain.getAll().forEach(childChain -> balanceHomeMap.put(childChain, new BalanceHome(childChain)));
    }

    private final DbKey.LongKeyFactory<Balance> balanceDbKeyFactory;
    private final VersionedEntityDbTable<Balance> balanceTable;
    private final ChildChain childChain;

    private BalanceHome(ChildChain childChain) {
        this.childChain = childChain;
        this.balanceDbKeyFactory = new DbKey.LongKeyFactory<Balance>("account_id") {
            @Override
            public DbKey newKey(Balance balance) {
                return balance.dbKey == null ? newKey(balance.accountId) : balance.dbKey;
            }
        };
        this.balanceTable = new VersionedEntityDbTable<Balance>(childChain.getSchemaTable("balance"), balanceDbKeyFactory) {
            @Override
            protected Balance load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
                return new Balance(rs, dbKey);
            }
            @Override
            protected void save(Connection con, Balance balance) throws SQLException {
                balance.save(con);
            }
        };
    }

    public Balance getBalance(long accountId) {
        return balanceTable.get(balanceDbKeyFactory.newKey(accountId));
    }

    public Balance getBalance(long accountId, int height) {
        return balanceTable.get(balanceDbKeyFactory.newKey(accountId), height);
    }

    public final class Balance {

        private final long accountId;
        private final DbKey dbKey;
        private long balance;
        private long unconfirmedBalance;

        Balance(long accountId, DbKey dbKey, long balance, long unconfirmedBalance) {
            this.accountId = accountId;
            this.dbKey = dbKey;
            this.balance = balance;
            this.unconfirmedBalance = unconfirmedBalance;
        }

        private Balance(ResultSet rs, DbKey dbKey) throws SQLException {
            this.accountId = rs.getLong("account_id");
            this.dbKey = dbKey;
            this.balance = rs.getLong("balance");
            this.unconfirmedBalance = rs.getLong("unconfirmed_balance");
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO balance (account_id, "
                    + "balance, unconfirmed_balance, height, latest) "
                    + "KEY (account_id, height) VALUES (?, ?, ?, ?, TRUE)")) {
                int i = 0;
                pstmt.setLong(++i, this.accountId);
                pstmt.setLong(++i, this.balance);
                pstmt.setLong(++i, this.unconfirmedBalance);
                pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
                pstmt.executeUpdate();
            }
        }

        private void save() {
            if (balance == 0 && unconfirmedBalance == 0) {
                balanceTable.delete(this, true);
            } else {
                balanceTable.insert(this);
            }
        }

        public long getAccountId() {
            return accountId;
        }

        public long getBalance() {
            return balance;
        }

        public long getUnconfirmedBalance() {
            return unconfirmedBalance;
        }

        void addToBalance(AccountLedger.LedgerEvent event, long eventId, long amount) {
            addToBalance(event, eventId, amount, 0);
        }

        void addToBalance(AccountLedger.LedgerEvent event, long eventId, long amount, long fee) {
            if (amount == 0 && fee == 0) {
                return;
            }
            long totalAmount = Math.addExact(amount, fee);
            this.balance = Math.addExact(this.balance, totalAmount);
            //TODO: Account.checkBalance(accountId, this.balanceNQT, this.unconfirmedBalanceNQT);
            save();
            //TODO: Account.listeners.notify(this, Event.BALANCE);
            if (AccountLedger.mustLogEntry(this.accountId, false)) {
                if (fee != 0) {
                    AccountLedger.logEntry(new AccountLedger.LedgerEntry(AccountLedger.LedgerEvent.TRANSACTION_FEE, eventId, this.accountId,
                            AccountLedger.LedgerHolding.NXT_BALANCE, null, fee, this.balance - amount));
                }
                if (amount != 0) {
                    AccountLedger.logEntry(new AccountLedger.LedgerEntry(event, eventId, this.accountId,
                            AccountLedger.LedgerHolding.NXT_BALANCE, null, amount, this.balance));
                }
            }
        }

        void addToUnconfirmedBalance(AccountLedger.LedgerEvent event, long eventId, long amount) {
            addToUnconfirmedBalance(event, eventId, amount, 0);
        }

        void addToUnconfirmedBalance(AccountLedger.LedgerEvent event, long eventId, long amount, long fee) {
            if (amount == 0 && fee == 0) {
                return;
            }
            long totalAmount = Math.addExact(amount, fee);
            this.unconfirmedBalance = Math.addExact(this.unconfirmedBalance, totalAmount);
            //TODO: Account.checkBalance(this.id, this.balanceNQT, this.unconfirmedBalanceNQT);
            save();
            //TODO: listeners.notify(this, Event.UNCONFIRMED_BALANCE);
            //TODO: use chain holding id in account ledger
            if (AccountLedger.mustLogEntry(this.accountId, true)) {
                if (fee != 0) {
                    AccountLedger.logEntry(new AccountLedger.LedgerEntry(AccountLedger.LedgerEvent.TRANSACTION_FEE, eventId, this.accountId,
                            AccountLedger.LedgerHolding.UNCONFIRMED_NXT_BALANCE, null, fee, this.unconfirmedBalance - amount));
                }
                if (amount != 0) {
                    AccountLedger.logEntry(new AccountLedger.LedgerEntry(event, eventId, this.accountId,
                            AccountLedger.LedgerHolding.UNCONFIRMED_NXT_BALANCE, null, amount, this.unconfirmedBalance));
                }
            }
        }

        void addToBalanceAndUnconfirmedBalance(AccountLedger.LedgerEvent event, long eventId, long amount) {
            addToBalanceAndUnconfirmedBalance(event, eventId, amount, 0);
        }

        void addToBalanceAndUnconfirmedBalance(AccountLedger.LedgerEvent event, long eventId, long amount, long fee) {
            if (amount == 0 && fee == 0) {
                return;
            }
            long totalAmount = Math.addExact(amount, fee);
            this.balance = Math.addExact(this.balance, totalAmount);
            this.unconfirmedBalance = Math.addExact(this.unconfirmedBalance, totalAmount);
            //TODO: Account.checkBalance(this.id, this.balanceNQT, this.unconfirmedBalanceNQT);
            save();
            //TODO: Account.listeners.notify(this, Event.BALANCE);
            //TODO: Account.listeners.notify(this, Event.UNCONFIRMED_BALANCE);
            if (AccountLedger.mustLogEntry(this.accountId, true)) {
                if (fee != 0) {
                    AccountLedger.logEntry(new AccountLedger.LedgerEntry(AccountLedger.LedgerEvent.TRANSACTION_FEE, eventId, this.accountId,
                            AccountLedger.LedgerHolding.UNCONFIRMED_NXT_BALANCE, null, fee, this.unconfirmedBalance - amount));
                }
                if (amount != 0) {
                    AccountLedger.logEntry(new AccountLedger.LedgerEntry(event, eventId, this.accountId,
                            AccountLedger.LedgerHolding.UNCONFIRMED_NXT_BALANCE, null, amount, this.unconfirmedBalance));
                }
            }
            if (AccountLedger.mustLogEntry(this.accountId, false)) {
                if (fee != 0) {
                    AccountLedger.logEntry(new AccountLedger.LedgerEntry(AccountLedger.LedgerEvent.TRANSACTION_FEE, eventId, this.accountId,
                            AccountLedger.LedgerHolding.NXT_BALANCE, null, fee, this.balance - amount));
                }
                if (amount != 0) {
                    AccountLedger.logEntry(new AccountLedger.LedgerEntry(event, eventId, this.accountId,
                            AccountLedger.LedgerHolding.NXT_BALANCE, null, amount, this.balance));
                }
            }
        }

    }
}
