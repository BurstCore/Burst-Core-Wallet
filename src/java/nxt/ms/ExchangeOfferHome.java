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

package nxt.ms;

import nxt.Nxt;
import nxt.account.Account;
import nxt.account.AccountLedger;
import nxt.account.AccountLedger.LedgerEvent;
import nxt.blockchain.BlockchainProcessor;
import nxt.blockchain.ChildChain;
import nxt.blockchain.Transaction;
import nxt.db.DbClause;
import nxt.db.DbIterator;
import nxt.db.DbKey;
import nxt.db.VersionedEntityDbTable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class ExchangeOfferHome {

    public static final class AvailableOffers {

        private final long rateNQT;
        private final long units;
        private final long amountNQT;

        private AvailableOffers(long rateNQT, long units, long amountNQT) {
            this.rateNQT = rateNQT;
            this.units = units;
            this.amountNQT = amountNQT;
        }

        public long getRateNQT() {
            return rateNQT;
        }

        public long getUnits() {
            return units;
        }

        public long getAmountNQT() {
            return amountNQT;
        }

    }

    public static ExchangeOfferHome forChain(ChildChain childChain) {
        if (childChain.getExchangeOfferHome() != null) {
            throw new IllegalStateException("already set");
        }
        return new ExchangeOfferHome(childChain);
    }

    private final ChildChain childChain;
    private final ExchangeHome exchangeHome;
    private final DbKey.LongKeyFactory<BuyOffer> buyOfferDbKeyFactory;
    private final VersionedEntityDbTable<BuyOffer> buyOfferTable;
    private final DbKey.LongKeyFactory<SellOffer> sellOfferDbKeyFactory;
    private final VersionedEntityDbTable<SellOffer> sellOfferTable;

    private ExchangeOfferHome(ChildChain childChain) {
        this.childChain = childChain;
        this.exchangeHome = childChain.getExchangeHome();
        this.buyOfferDbKeyFactory = new DbKey.LongKeyFactory<BuyOffer>("id") {
            @Override
            public DbKey newKey(BuyOffer offer) {
                return offer.dbKey;
            }
        };
        this.buyOfferTable = new VersionedEntityDbTable<BuyOffer>(childChain.getSchemaTable("buy_offer"), buyOfferDbKeyFactory) {
            @Override
            protected BuyOffer load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
                return new BuyOffer(rs, dbKey);
            }
            @Override
            protected void save(Connection con, BuyOffer buy) throws SQLException {
                buy.save(con, schemaTable);
            }
        };
        this.sellOfferDbKeyFactory = new DbKey.LongKeyFactory<SellOffer>("id") {
            @Override
            public DbKey newKey(SellOffer sell) {
                return sell.dbKey;
            }
        };
        this.sellOfferTable = new VersionedEntityDbTable<SellOffer>(childChain.getSchemaTable("sell_offer"), sellOfferDbKeyFactory) {
            @Override
            protected SellOffer load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
                return new SellOffer(rs, dbKey);
            }
            @Override
            protected void save(Connection con, SellOffer sell) throws SQLException {
                sell.save(con, schemaTable);
            }
        };
        Nxt.getBlockchainProcessor().addListener(block -> {
            List<BuyOffer> expired = new ArrayList<>();
            try (DbIterator<BuyOffer> offers = getBuyOffers(new DbClause.IntClause("expiration_height", block.getHeight()), 0, -1)) {
                for (BuyOffer offer : offers) {
                    expired.add(offer);
                }
            }
            expired.forEach((offer) -> removeOffer(LedgerEvent.CURRENCY_OFFER_EXPIRED, offer));
        }, BlockchainProcessor.Event.AFTER_BLOCK_APPLY);
    }

    void publishOffer(Transaction transaction, PublishExchangeOfferAttachment attachment) {
        BuyOffer previousOffer = getBuyOffer(attachment.getCurrencyId(), transaction.getSenderId());
        if (previousOffer != null) {
            removeOffer(LedgerEvent.CURRENCY_OFFER_REPLACED, previousOffer);
        }
        addBuyOffer(transaction, attachment);
        addSellOffer(transaction, attachment);
    }

    private AvailableOffers calculateTotal(List<ExchangeOffer> offers, final long units) {
        long totalAmountNQT = 0;
        long remainingUnits = units;
        long rateNQT = 0;
        for (ExchangeOffer offer : offers) {
            if (remainingUnits == 0) {
                break;
            }
            rateNQT = offer.getRateNQT();
            long curUnits = Math.min(Math.min(remainingUnits, offer.getSupply()), offer.getLimit());
            long curAmountNQT = Math.multiplyExact(curUnits, offer.getRateNQT());
            totalAmountNQT = Math.addExact(totalAmountNQT, curAmountNQT);
            remainingUnits = Math.subtractExact(remainingUnits, curUnits);
        }
        return new AvailableOffers(rateNQT, Math.subtractExact(units, remainingUnits), totalAmountNQT);
    }

    private static final DbClause availableOnlyDbClause = new DbClause.LongClause("unit_limit", DbClause.Op.NE, 0)
            .and(new DbClause.LongClause("supply", DbClause.Op.NE, 0));

    public AvailableOffers getAvailableToSell(final long currencyId, final long units) {
        return calculateTotal(getAvailableBuyOffers(currencyId, 0L), units);
    }

    private List<ExchangeOffer> getAvailableBuyOffers(long currencyId, long minRateNQT) {
        List<ExchangeOffer> exchangeOffers = new ArrayList<>();
        DbClause dbClause = new DbClause.LongClause("currency_id", currencyId).and(availableOnlyDbClause);
        if (minRateNQT > 0) {
            dbClause = dbClause.and(new DbClause.LongClause("rate", DbClause.Op.GTE, minRateNQT));
        }
        try (DbIterator<BuyOffer> offers = getBuyOffers(dbClause, 0, -1,
                " ORDER BY rate DESC, creation_height ASC, transaction_height ASC, transaction_index ASC ")) {
            for (BuyOffer offer : offers) {
                exchangeOffers.add(offer);
            }
        }
        return exchangeOffers;
    }

    void exchangeCurrencyForNXT(Transaction transaction, Account account, final long currencyId, final long rateNQT, final long units) {
        List<ExchangeOffer> currencyBuyOffers = getAvailableBuyOffers(currencyId, rateNQT);

        long totalAmountNQT = 0;
        long remainingUnits = units;
        for (ExchangeOffer offer : currencyBuyOffers) {
            if (remainingUnits == 0) {
                break;
            }
            long curUnits = Math.min(Math.min(remainingUnits, offer.getSupply()), offer.getLimit());
            long curAmountNQT = Math.multiplyExact(curUnits, offer.getRateNQT());

            totalAmountNQT = Math.addExact(totalAmountNQT, curAmountNQT);
            remainingUnits = Math.subtractExact(remainingUnits, curUnits);

            offer.decreaseLimitAndSupply(curUnits);
            long excess = offer.getCounterOffer().increaseSupply(curUnits);

            Account counterAccount = Account.getAccount(offer.getAccountId());
            AccountLedger.LedgerEventId eventId = AccountLedger.newEventId(offer.getId(), offer.getFullHash(), childChain);
            counterAccount.addToBalance(childChain, LedgerEvent.CURRENCY_EXCHANGE, eventId, -curAmountNQT);
            counterAccount.addToCurrencyUnits(LedgerEvent.CURRENCY_EXCHANGE, eventId, currencyId, curUnits);
            counterAccount.addToUnconfirmedCurrencyUnits(LedgerEvent.CURRENCY_EXCHANGE, eventId, currencyId, excess);
            exchangeHome.addExchange(transaction, currencyId, offer, account.getId(), offer.getAccountId(), curUnits);
        }
        AccountLedger.LedgerEventId eventId = AccountLedger.newEventId(transaction);
        account.addToBalanceAndUnconfirmedBalance(childChain, LedgerEvent.CURRENCY_EXCHANGE, eventId, totalAmountNQT);
        account.addToCurrencyUnits(LedgerEvent.CURRENCY_EXCHANGE, eventId, currencyId, -(units - remainingUnits));
        account.addToUnconfirmedCurrencyUnits(LedgerEvent.CURRENCY_EXCHANGE, eventId, currencyId, remainingUnits);
    }

    public AvailableOffers getAvailableToBuy(final long currencyId, final long units) {
        return calculateTotal(getAvailableSellOffers(currencyId, 0L), units);
    }

    private List<ExchangeOffer> getAvailableSellOffers(long currencyId, long maxRateNQT) {
        List<ExchangeOffer> currencySellOffers = new ArrayList<>();
        DbClause dbClause = new DbClause.LongClause("currency_id", currencyId).and(availableOnlyDbClause);
        if (maxRateNQT > 0) {
            dbClause = dbClause.and(new DbClause.LongClause("rate", DbClause.Op.LTE, maxRateNQT));
        }
        try (DbIterator<SellOffer> offers = getSellOffers(dbClause, 0, -1,
                " ORDER BY rate ASC, creation_height ASC, transaction_height ASC, transaction_index ASC ")) {
            for (SellOffer offer : offers) {
                currencySellOffers.add(offer);
            }
        }
        return currencySellOffers;
    }

    void exchangeNXTForCurrency(Transaction transaction, Account account, final long currencyId, final long rateNQT, final long units) {
        List<ExchangeOffer> currencySellOffers = getAvailableSellOffers(currencyId, rateNQT);
        long totalAmountNQT = 0;
        long remainingUnits = units;

        for (ExchangeOffer offer : currencySellOffers) {
            if (remainingUnits == 0) {
                break;
            }
            long curUnits = Math.min(Math.min(remainingUnits, offer.getSupply()), offer.getLimit());
            long curAmountNQT = Math.multiplyExact(curUnits, offer.getRateNQT());

            totalAmountNQT = Math.addExact(totalAmountNQT, curAmountNQT);
            remainingUnits = Math.subtractExact(remainingUnits, curUnits);

            offer.decreaseLimitAndSupply(curUnits);
            long excess = offer.getCounterOffer().increaseSupply(curUnits);

            Account counterAccount = Account.getAccount(offer.getAccountId());
            AccountLedger.LedgerEventId eventId = AccountLedger.newEventId(offer.getId(), offer.getFullHash(), childChain);
            counterAccount.addToBalance(childChain, LedgerEvent.CURRENCY_EXCHANGE, eventId, curAmountNQT);
            counterAccount.addToUnconfirmedBalance(childChain, LedgerEvent.CURRENCY_EXCHANGE, eventId,
                    Math.addExact(
                            Math.multiplyExact(curUnits - excess, offer.getRateNQT() - offer.getCounterOffer().getRateNQT()),
                            Math.multiplyExact(excess, offer.getRateNQT())
                    )
            );
            counterAccount.addToCurrencyUnits(LedgerEvent.CURRENCY_EXCHANGE, eventId, currencyId, -curUnits);
            exchangeHome.addExchange(transaction, currencyId, offer, offer.getAccountId(), account.getId(), curUnits);
        }
        AccountLedger.LedgerEventId eventId = AccountLedger.newEventId(transaction);
        account.addToCurrencyAndUnconfirmedCurrencyUnits(LedgerEvent.CURRENCY_EXCHANGE, eventId,
                currencyId, Math.subtractExact(units, remainingUnits));
        account.addToBalance(childChain, LedgerEvent.CURRENCY_EXCHANGE, eventId, -totalAmountNQT);
        account.addToUnconfirmedBalance(childChain, LedgerEvent.CURRENCY_EXCHANGE, eventId, Math.multiplyExact(units, rateNQT) - totalAmountNQT);
    }

    void removeOffer(LedgerEvent event, BuyOffer buyOffer) {
        SellOffer sellOffer = buyOffer.getCounterOffer();

        removeBuyOffer(buyOffer);
        removeSellOffer(sellOffer);

        Account account = Account.getAccount(buyOffer.getAccountId());
        AccountLedger.LedgerEventId eventId = AccountLedger.newEventId(buyOffer.getId(), buyOffer.getFullHash(), childChain);
        account.addToUnconfirmedBalance(childChain, event, eventId, Math.multiplyExact(buyOffer.getSupply(), buyOffer.getRateNQT()));
        account.addToUnconfirmedCurrencyUnits(event, eventId, buyOffer.getCurrencyId(), sellOffer.getSupply());
    }

    public abstract class ExchangeOffer {

        protected final long id;
        private final byte[] hash;
        private final long currencyId;
        private final long accountId;
        private final long rateNQT;
        private long limit; // limit on the total sum of units for this offer across transactions
        private long supply; // total units supply for the offer
        private final int expirationHeight;
        private final int creationHeight;
        private final short transactionIndex;
        private final int transactionHeight;

        ExchangeOffer(long id, byte[] hash, long currencyId, long accountId, long rateNQT, long limit, long supply,
                      int expirationHeight, int transactionHeight, short transactionIndex) {
            this.id = id;
            this.hash = hash;
            this.currencyId = currencyId;
            this.accountId = accountId;
            this.rateNQT = rateNQT;
            this.limit = limit;
            this.supply = supply;
            this.expirationHeight = expirationHeight;
            this.creationHeight = Nxt.getBlockchain().getHeight();
            this.transactionIndex = transactionIndex;
            this.transactionHeight = transactionHeight;
        }

        ExchangeOffer(ResultSet rs) throws SQLException {
            this.id = rs.getLong("id");
            this.hash = rs.getBytes("full_hash");
            this.currencyId = rs.getLong("currency_id");
            this.accountId = rs.getLong("account_id");
            this.rateNQT = rs.getLong("rate");
            this.limit = rs.getLong("unit_limit");
            this.supply = rs.getLong("supply");
            this.expirationHeight = rs.getInt("expiration_height");
            this.creationHeight = rs.getInt("creation_height");
            this.transactionIndex = rs.getShort("transaction_index");
            this.transactionHeight = rs.getInt("transaction_height");
        }

        void save(Connection con, String table) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO " + table + " (id, full_hash, currency_id, account_id, "
                    + "rate, unit_limit, supply, expiration_height, creation_height, transaction_index, transaction_height, height, latest) "
                    + "KEY (id, height) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE)")) {
                int i = 0;
                pstmt.setLong(++i, this.id);
                pstmt.setBytes(++i, this.hash);
                pstmt.setLong(++i, this.currencyId);
                pstmt.setLong(++i, this.accountId);
                pstmt.setLong(++i, this.rateNQT);
                pstmt.setLong(++i, this.limit);
                pstmt.setLong(++i, this.supply);
                pstmt.setInt(++i, this.expirationHeight);
                pstmt.setInt(++i, this.creationHeight);
                pstmt.setShort(++i, this.transactionIndex);
                pstmt.setInt(++i, this.transactionHeight);
                pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
                pstmt.executeUpdate();
            }
        }

        public long getId() {
            return id;
        }

        public byte[] getFullHash() {
            return hash;
        }

        public long getCurrencyId() {
            return currencyId;
        }

        public long getAccountId() {
            return accountId;
        }

        public long getRateNQT() {
            return rateNQT;
        }

        public long getLimit() {
            return limit;
        }

        public long getSupply() {
            return supply;
        }

        public int getExpirationHeight() {
            return expirationHeight;
        }

        public int getHeight() {
            return creationHeight;
        }

        public abstract ExchangeOffer getCounterOffer();

        public ChildChain getChildChain() {
            return childChain;
        }

        long increaseSupply(long delta) {
            long excess = Math.max(Math.addExact(supply, Math.subtractExact(delta, limit)), 0);
            supply += delta - excess;
            return excess;
        }

        void decreaseLimitAndSupply(long delta) {
            limit -= delta;
            supply -= delta;
        }
    }

    public int getBuyOfferCount() {
        return buyOfferTable.getCount();
    }

    public BuyOffer getBuyOffer(long offerId) {
        return buyOfferTable.get(buyOfferDbKeyFactory.newKey(offerId));
    }

    public DbIterator<BuyOffer> getAllBuyOffers(int from, int to) {
        return buyOfferTable.getAll(from, to);
    }

    public DbIterator<BuyOffer> getBuyOffers(Currency currency, int from, int to) {
        return getCurrencyBuyOffers(currency.getId(), false, from, to);
    }

    public DbIterator<BuyOffer> getCurrencyBuyOffers(long currencyId, boolean availableOnly, int from, int to) {
        DbClause dbClause = new DbClause.LongClause("currency_id", currencyId);
        if (availableOnly) {
            dbClause = dbClause.and(availableOnlyDbClause);
        }
        return buyOfferTable.getManyBy(dbClause, from, to, " ORDER BY rate DESC, creation_height ASC, transaction_height ASC, transaction_index ASC ");
    }

    public DbIterator<BuyOffer> getAccountBuyOffers(long accountId, boolean availableOnly, int from, int to) {
        DbClause dbClause = new DbClause.LongClause("account_id", accountId);
        if (availableOnly) {
            dbClause = dbClause.and(availableOnlyDbClause);
        }
        return buyOfferTable.getManyBy(dbClause, from, to, " ORDER BY rate DESC, creation_height ASC, transaction_height ASC, transaction_index ASC ");
    }

    public BuyOffer getBuyOffer(Currency currency, Account account) {
        return getBuyOffer(currency.getId(), account.getId());
    }

    public BuyOffer getBuyOffer(final long currencyId, final long accountId) {
        return buyOfferTable.getBy(new DbClause.LongClause("currency_id", currencyId).and(new DbClause.LongClause("account_id", accountId)));
    }

    public DbIterator<BuyOffer> getBuyOffers(DbClause dbClause, int from, int to) {
        return buyOfferTable.getManyBy(dbClause, from, to);
    }

    public DbIterator<BuyOffer> getBuyOffers(DbClause dbClause, int from, int to, String sort) {
        return buyOfferTable.getManyBy(dbClause, from, to, sort);
    }

    private void addBuyOffer(Transaction transaction, PublishExchangeOfferAttachment attachment) {
        buyOfferTable.insert(new BuyOffer(transaction, attachment));
    }

    private void removeBuyOffer(BuyOffer buyOffer) {
        buyOfferTable.delete(buyOffer);
    }


    public final class BuyOffer extends ExchangeOffer {

        private final DbKey dbKey;

        private BuyOffer(Transaction transaction, PublishExchangeOfferAttachment attachment) {
            super(transaction.getId(), transaction.getFullHash(), attachment.getCurrencyId(), transaction.getSenderId(), attachment.getBuyRateNQT(),
                    attachment.getTotalBuyLimit(), attachment.getInitialBuySupply(), attachment.getExpirationHeight(), transaction.getHeight(),
                    transaction.getIndex());
            this.dbKey = buyOfferDbKeyFactory.newKey(id);
        }

        private BuyOffer(ResultSet rs, DbKey dbKey) throws SQLException {
            super(rs);
            this.dbKey = dbKey;
        }

        @Override
        public SellOffer getCounterOffer() {
            return getSellOffer(id);
        }

        long increaseSupply(long delta) {
            long excess = super.increaseSupply(delta);
            buyOfferTable.insert(this);
            return excess;
        }

        void decreaseLimitAndSupply(long delta) {
            super.decreaseLimitAndSupply(delta);
            buyOfferTable.insert(this);
        }

    }

    public int getSellOfferCount() {
        return sellOfferTable.getCount();
    }

    public SellOffer getSellOffer(long id) {
        return sellOfferTable.get(sellOfferDbKeyFactory.newKey(id));
    }

    public DbIterator<SellOffer> getAllSellOffers(int from, int to) {
        return sellOfferTable.getAll(from, to);
    }

    public DbIterator<SellOffer> getSellOffers(Currency currency, int from, int to) {
        return getCurrencySellOffers(currency.getId(), false, from, to);
    }

    public DbIterator<SellOffer> getCurrencySellOffers(long currencyId, boolean availableOnly, int from, int to) {
        DbClause dbClause = new DbClause.LongClause("currency_id", currencyId);
        if (availableOnly) {
            dbClause = dbClause.and(availableOnlyDbClause);
        }
        return sellOfferTable.getManyBy(dbClause, from, to, " ORDER BY rate ASC, creation_height ASC, transaction_height ASC, transaction_index ASC ");
    }

    public DbIterator<SellOffer> getAccountSellOffers(long accountId, boolean availableOnly, int from, int to) {
        DbClause dbClause = new DbClause.LongClause("account_id", accountId);
        if (availableOnly) {
            dbClause = dbClause.and(availableOnlyDbClause);
        }
        return sellOfferTable.getManyBy(dbClause, from, to, " ORDER BY rate ASC, creation_height ASC, transaction_height ASC, transaction_index ASC ");
    }

    public SellOffer getSellOffer(Currency currency, Account account) {
        return getSellOffer(currency.getId(), account.getId());
    }

    public SellOffer getSellOffer(final long currencyId, final long accountId) {
        return sellOfferTable.getBy(new DbClause.LongClause("currency_id", currencyId).and(new DbClause.LongClause("account_id", accountId)));
    }

    public DbIterator<SellOffer> getSellOffers(DbClause dbClause, int from, int to) {
        return sellOfferTable.getManyBy(dbClause, from, to);
    }

    public DbIterator<SellOffer> getSellOffers(DbClause dbClause, int from, int to, String sort) {
        return sellOfferTable.getManyBy(dbClause, from, to, sort);
    }

    private void addSellOffer(Transaction transaction, PublishExchangeOfferAttachment attachment) {
        sellOfferTable.insert(new SellOffer(transaction, attachment));
    }

    private void removeSellOffer(SellOffer sellOffer) {
        sellOfferTable.delete(sellOffer);
    }


    public final class SellOffer extends ExchangeOffer {

        private final DbKey dbKey;

        private SellOffer(Transaction transaction, PublishExchangeOfferAttachment attachment) {
            super(transaction.getId(), transaction.getFullHash(), attachment.getCurrencyId(), transaction.getSenderId(), attachment.getSellRateNQT(),
                    attachment.getTotalSellLimit(), attachment.getInitialSellSupply(), attachment.getExpirationHeight(), transaction.getHeight(),
                    transaction.getIndex());
            this.dbKey = sellOfferDbKeyFactory.newKey(id);
        }

        private SellOffer(ResultSet rs, DbKey dbKey) throws SQLException {
            super(rs);
            this.dbKey = dbKey;
        }

        @Override
        public BuyOffer getCounterOffer() {
            return getBuyOffer(id);
        }

        long increaseSupply(long delta) {
            long excess = super.increaseSupply(delta);
            sellOfferTable.insert(this);
            return excess;
        }

        void decreaseLimitAndSupply(long delta) {
            super.decreaseLimitAndSupply(delta);
            sellOfferTable.insert(this);
        }
    }
}
