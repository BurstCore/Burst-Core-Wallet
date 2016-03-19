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

import nxt.AccountLedger.LedgerEvent;
import nxt.db.DbClause;
import nxt.db.DbIterator;
import nxt.db.DbKey;
import nxt.db.VersionedEntityDbTable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CurrencyExchangeOfferHome {

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

    private static final Map<ChildChain, CurrencyExchangeOfferHome> currencyExchangeOfferHomeMap = new HashMap<>();

    public static CurrencyExchangeOfferHome forChain(ChildChain childChain) {
        return currencyExchangeOfferHomeMap.get(childChain);
    }

    static void init() {
    }

    static {
        ChildChain.getAll().forEach(childChain -> currencyExchangeOfferHomeMap.put(childChain, new CurrencyExchangeOfferHome(childChain)));
    }

    private final ChildChain childChain;
    private final ExchangeHome exchangeHome;
    private final DbKey.LongKeyFactory<CurrencyBuyOffer> buyOfferDbKeyFactory;
    private final VersionedEntityDbTable<CurrencyBuyOffer> buyOfferTable;
    private final DbKey.LongKeyFactory<CurrencySellOffer> sellOfferDbKeyFactory;
    private final VersionedEntityDbTable<CurrencySellOffer> sellOfferTable;

    private CurrencyExchangeOfferHome(ChildChain childChain) {
        this.childChain = childChain;
        this.exchangeHome = ExchangeHome.forChain(childChain);
        this.buyOfferDbKeyFactory = new DbKey.LongKeyFactory<CurrencyBuyOffer>("id") {
            @Override
            public DbKey newKey(CurrencyBuyOffer offer) {
                return offer.dbKey;
            }
        };
        this.buyOfferTable = new VersionedEntityDbTable<CurrencyBuyOffer>(childChain.getSchemaTable("buy_offer"), buyOfferDbKeyFactory) {
            @Override
            protected CurrencyBuyOffer load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
                return new CurrencyBuyOffer(rs, dbKey);
            }
            @Override
            protected void save(Connection con, CurrencyBuyOffer buy) throws SQLException {
                buy.save(con, schemaTable);
            }
        };
        this.sellOfferDbKeyFactory = new DbKey.LongKeyFactory<CurrencySellOffer>("id") {
            @Override
            public DbKey newKey(CurrencySellOffer sell) {
                return sell.dbKey;
            }
        };
        this.sellOfferTable = new VersionedEntityDbTable<CurrencySellOffer>(childChain.getSchemaTable("sell_offer"), sellOfferDbKeyFactory) {
            @Override
            protected CurrencySellOffer load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
                return new CurrencySellOffer(rs, dbKey);
            }
            @Override
            protected void save(Connection con, CurrencySellOffer sell) throws SQLException {
                sell.save(con, schemaTable);
            }
        };
        Nxt.getBlockchainProcessor().addListener(block -> {
            List<CurrencyBuyOffer> expired = new ArrayList<>();
            try (DbIterator<CurrencyBuyOffer> offers = getBuyOffers(new DbClause.IntClause("expiration_height", block.getHeight()), 0, -1)) {
                for (CurrencyBuyOffer offer : offers) {
                    expired.add(offer);
                }
            }
            expired.forEach((offer) -> removeOffer(LedgerEvent.CURRENCY_OFFER_EXPIRED, offer));
        }, BlockchainProcessor.Event.AFTER_BLOCK_APPLY);
    }

    void publishOffer(Transaction transaction, Attachment.MonetarySystemPublishExchangeOffer attachment) {
        CurrencyBuyOffer previousOffer = getBuyOffer(attachment.getCurrencyId(), transaction.getSenderId());
        if (previousOffer != null) {
            removeOffer(LedgerEvent.CURRENCY_OFFER_REPLACED, previousOffer);
        }
        addBuyOffer(transaction, attachment);
        addSellOffer(transaction, attachment);
    }

    private AvailableOffers calculateTotal(List<CurrencyExchangeOffer> offers, final long units) {
        long totalAmountNQT = 0;
        long remainingUnits = units;
        long rateNQT = 0;
        for (CurrencyExchangeOffer offer : offers) {
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

    static final DbClause availableOnlyDbClause = new DbClause.LongClause("unit_limit", DbClause.Op.NE, 0)
            .and(new DbClause.LongClause("supply", DbClause.Op.NE, 0));

    public AvailableOffers getAvailableToSell(final long currencyId, final long units) {
        return calculateTotal(getAvailableBuyOffers(currencyId, 0L), units);
    }

    private List<CurrencyExchangeOffer> getAvailableBuyOffers(long currencyId, long minRateNQT) {
        List<CurrencyExchangeOffer> currencyExchangeOffers = new ArrayList<>();
        DbClause dbClause = new DbClause.LongClause("currency_id", currencyId).and(availableOnlyDbClause);
        if (minRateNQT > 0) {
            dbClause = dbClause.and(new DbClause.LongClause("rate", DbClause.Op.GTE, minRateNQT));
        }
        try (DbIterator<CurrencyBuyOffer> offers = getBuyOffers(dbClause, 0, -1,
                " ORDER BY rate DESC, creation_height ASC, transaction_height ASC, transaction_index ASC ")) {
            for (CurrencyBuyOffer offer : offers) {
                currencyExchangeOffers.add(offer);
            }
        }
        return currencyExchangeOffers;
    }

    void exchangeCurrencyForNXT(Transaction transaction, Account account, final long currencyId, final long rateNQT, final long units) {
        List<CurrencyExchangeOffer> currencyBuyOffers = getAvailableBuyOffers(currencyId, rateNQT);

        long totalAmountNQT = 0;
        long remainingUnits = units;
        for (CurrencyExchangeOffer offer : currencyBuyOffers) {
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
            counterAccount.addToBalanceNQT(LedgerEvent.CURRENCY_EXCHANGE, offer.getId(), -curAmountNQT);
            counterAccount.addToCurrencyUnits(LedgerEvent.CURRENCY_EXCHANGE, offer.getId(), currencyId, curUnits);
            counterAccount.addToUnconfirmedCurrencyUnits(LedgerEvent.CURRENCY_EXCHANGE, offer.getId(), currencyId, excess);
            exchangeHome.addExchange(transaction, currencyId, offer, account.getId(), offer.getAccountId(), curUnits);
        }
        long transactionId = transaction.getId();
        account.addToBalanceAndUnconfirmedBalanceNQT(LedgerEvent.CURRENCY_EXCHANGE, transactionId, totalAmountNQT);
        account.addToCurrencyUnits(LedgerEvent.CURRENCY_EXCHANGE, transactionId, currencyId, -(units - remainingUnits));
        account.addToUnconfirmedCurrencyUnits(LedgerEvent.CURRENCY_EXCHANGE, transactionId, currencyId, remainingUnits);
    }

    public AvailableOffers getAvailableToBuy(final long currencyId, final long units) {
        return calculateTotal(getAvailableSellOffers(currencyId, 0L), units);
    }

    private List<CurrencyExchangeOffer> getAvailableSellOffers(long currencyId, long maxRateNQT) {
        List<CurrencyExchangeOffer> currencySellOffers = new ArrayList<>();
        DbClause dbClause = new DbClause.LongClause("currency_id", currencyId).and(availableOnlyDbClause);
        if (maxRateNQT > 0) {
            dbClause = dbClause.and(new DbClause.LongClause("rate", DbClause.Op.LTE, maxRateNQT));
        }
        try (DbIterator<CurrencySellOffer> offers = getSellOffers(dbClause, 0, -1,
                " ORDER BY rate ASC, creation_height ASC, transaction_height ASC, transaction_index ASC ")) {
            for (CurrencySellOffer offer : offers) {
                currencySellOffers.add(offer);
            }
        }
        return currencySellOffers;
    }

    void exchangeNXTForCurrency(Transaction transaction, Account account, final long currencyId, final long rateNQT, final long units) {
        List<CurrencyExchangeOffer> currencySellOffers = getAvailableSellOffers(currencyId, rateNQT);
        long totalAmountNQT = 0;
        long remainingUnits = units;

        for (CurrencyExchangeOffer offer : currencySellOffers) {
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
            counterAccount.addToBalanceNQT(LedgerEvent.CURRENCY_EXCHANGE, offer.getId(), curAmountNQT);
            counterAccount.addToUnconfirmedBalanceNQT(LedgerEvent.CURRENCY_EXCHANGE, offer.getId(),
                    Math.addExact(
                            Math.multiplyExact(curUnits - excess, offer.getRateNQT() - offer.getCounterOffer().getRateNQT()),
                            Math.multiplyExact(excess, offer.getRateNQT())
                    )
            );
            counterAccount.addToCurrencyUnits(LedgerEvent.CURRENCY_EXCHANGE, offer.getId(), currencyId, -curUnits);
            exchangeHome.addExchange(transaction, currencyId, offer, offer.getAccountId(), account.getId(), curUnits);
        }
        long transactionId = transaction.getId();
        account.addToCurrencyAndUnconfirmedCurrencyUnits(LedgerEvent.CURRENCY_EXCHANGE, transactionId,
                currencyId, Math.subtractExact(units, remainingUnits));
        account.addToBalanceNQT(LedgerEvent.CURRENCY_EXCHANGE, transactionId, -totalAmountNQT);
        account.addToUnconfirmedBalanceNQT(LedgerEvent.CURRENCY_EXCHANGE, transactionId, Math.multiplyExact(units, rateNQT) - totalAmountNQT);
    }

    void removeOffer(LedgerEvent event, CurrencyBuyOffer buyOffer) {
        CurrencySellOffer sellOffer = buyOffer.getCounterOffer();

        removeBuyOffer(buyOffer);
        removeSellOffer(sellOffer);

        Account account = Account.getAccount(buyOffer.getAccountId());
        account.addToUnconfirmedBalanceNQT(event, buyOffer.getId(), Math.multiplyExact(buyOffer.getSupply(), buyOffer.getRateNQT()));
        account.addToUnconfirmedCurrencyUnits(event, buyOffer.getId(), buyOffer.getCurrencyId(), sellOffer.getSupply());
    }

    public abstract class CurrencyExchangeOffer {

        final long id;
        private final long currencyId;
        private final long accountId;
        private final long rateNQT;
        private long limit; // limit on the total sum of units for this offer across transactions
        private long supply; // total units supply for the offer
        private final int expirationHeight;
        private final int creationHeight;
        private final short transactionIndex;
        private final int transactionHeight;

        CurrencyExchangeOffer(long id, long currencyId, long accountId, long rateNQT, long limit, long supply,
                              int expirationHeight, int transactionHeight, short transactionIndex) {
            this.id = id;
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

        CurrencyExchangeOffer(ResultSet rs) throws SQLException {
            this.id = rs.getLong("id");
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
            try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO " + table + " (id, currency_id, account_id, "
                    + "rate, unit_limit, supply, expiration_height, creation_height, transaction_index, transaction_height, height, latest) "
                    + "KEY (id, height) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE)")) {
                int i = 0;
                pstmt.setLong(++i, this.id);
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

        public abstract CurrencyExchangeOffer getCounterOffer();

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

    public CurrencyBuyOffer getBuyOffer(long offerId) {
        return buyOfferTable.get(buyOfferDbKeyFactory.newKey(offerId));
    }

    public DbIterator<CurrencyBuyOffer> getAllBuyOffers(int from, int to) {
        return buyOfferTable.getAll(from, to);
    }

    public DbIterator<CurrencyBuyOffer> getBuyOffers(Currency currency, int from, int to) {
        return getCurrencyBuyOffers(currency.getId(), false, from, to);
    }

    public DbIterator<CurrencyBuyOffer> getCurrencyBuyOffers(long currencyId, boolean availableOnly, int from, int to) {
        DbClause dbClause = new DbClause.LongClause("currency_id", currencyId);
        if (availableOnly) {
            dbClause = dbClause.and(availableOnlyDbClause);
        }
        return buyOfferTable.getManyBy(dbClause, from, to, " ORDER BY rate DESC, creation_height ASC, transaction_height ASC, transaction_index ASC ");
    }

    public DbIterator<CurrencyBuyOffer> getAccountBuyOffers(long accountId, boolean availableOnly, int from, int to) {
        DbClause dbClause = new DbClause.LongClause("account_id", accountId);
        if (availableOnly) {
            dbClause = dbClause.and(availableOnlyDbClause);
        }
        return buyOfferTable.getManyBy(dbClause, from, to, " ORDER BY rate DESC, creation_height ASC, transaction_height ASC, transaction_index ASC ");
    }

    public CurrencyBuyOffer getBuyOffer(Currency currency, Account account) {
        return getBuyOffer(currency.getId(), account.getId());
    }

    public CurrencyBuyOffer getBuyOffer(final long currencyId, final long accountId) {
        return buyOfferTable.getBy(new DbClause.LongClause("currency_id", currencyId).and(new DbClause.LongClause("account_id", accountId)));
    }

    public DbIterator<CurrencyBuyOffer> getBuyOffers(DbClause dbClause, int from, int to) {
        return buyOfferTable.getManyBy(dbClause, from, to);
    }

    public DbIterator<CurrencyBuyOffer> getBuyOffers(DbClause dbClause, int from, int to, String sort) {
        return buyOfferTable.getManyBy(dbClause, from, to, sort);
    }

    void addBuyOffer(Transaction transaction, Attachment.MonetarySystemPublishExchangeOffer attachment) {
        buyOfferTable.insert(new CurrencyBuyOffer(transaction, attachment));
    }

    void removeBuyOffer(CurrencyBuyOffer buyOffer) {
        buyOfferTable.delete(buyOffer);
    }


    public final class CurrencyBuyOffer extends CurrencyExchangeOffer {

        private final DbKey dbKey;

        private CurrencyBuyOffer(Transaction transaction, Attachment.MonetarySystemPublishExchangeOffer attachment) {
            super(transaction.getId(), attachment.getCurrencyId(), transaction.getSenderId(), attachment.getBuyRateNQT(),
                    attachment.getTotalBuyLimit(), attachment.getInitialBuySupply(), attachment.getExpirationHeight(), transaction.getHeight(),
                    transaction.getIndex());
            this.dbKey = buyOfferDbKeyFactory.newKey(id);
        }

        private CurrencyBuyOffer(ResultSet rs, DbKey dbKey) throws SQLException {
            super(rs);
            this.dbKey = dbKey;
        }

        @Override
        public CurrencySellOffer getCounterOffer() {
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

    public CurrencySellOffer getSellOffer(long id) {
        return sellOfferTable.get(sellOfferDbKeyFactory.newKey(id));
    }

    public DbIterator<CurrencySellOffer> getAllSellOffers(int from, int to) {
        return sellOfferTable.getAll(from, to);
    }

    public DbIterator<CurrencySellOffer> getSellOffers(Currency currency, int from, int to) {
        return getCurrencySellOffers(currency.getId(), false, from, to);
    }

    public DbIterator<CurrencySellOffer> getCurrencySellOffers(long currencyId, boolean availableOnly, int from, int to) {
        DbClause dbClause = new DbClause.LongClause("currency_id", currencyId);
        if (availableOnly) {
            dbClause = dbClause.and(availableOnlyDbClause);
        }
        return sellOfferTable.getManyBy(dbClause, from, to, " ORDER BY rate ASC, creation_height ASC, transaction_height ASC, transaction_index ASC ");
    }

    public DbIterator<CurrencySellOffer> getAccountSellOffers(long accountId, boolean availableOnly, int from, int to) {
        DbClause dbClause = new DbClause.LongClause("account_id", accountId);
        if (availableOnly) {
            dbClause = dbClause.and(availableOnlyDbClause);
        }
        return sellOfferTable.getManyBy(dbClause, from, to, " ORDER BY rate ASC, creation_height ASC, transaction_height ASC, transaction_index ASC ");
    }

    public CurrencySellOffer getSellOffer(Currency currency, Account account) {
        return getSellOffer(currency.getId(), account.getId());
    }

    public CurrencySellOffer getSellOffer(final long currencyId, final long accountId) {
        return sellOfferTable.getBy(new DbClause.LongClause("currency_id", currencyId).and(new DbClause.LongClause("account_id", accountId)));
    }

    public DbIterator<CurrencySellOffer> getSellOffers(DbClause dbClause, int from, int to) {
        return sellOfferTable.getManyBy(dbClause, from, to);
    }

    public DbIterator<CurrencySellOffer> getSellOffers(DbClause dbClause, int from, int to, String sort) {
        return sellOfferTable.getManyBy(dbClause, from, to, sort);
    }

    void addSellOffer(Transaction transaction, Attachment.MonetarySystemPublishExchangeOffer attachment) {
        sellOfferTable.insert(new CurrencySellOffer(transaction, attachment));
    }

    void removeSellOffer(CurrencySellOffer sellOffer) {
        sellOfferTable.delete(sellOffer);
    }


    public final class CurrencySellOffer extends CurrencyExchangeOffer {

        private final DbKey dbKey;

        private CurrencySellOffer(Transaction transaction, Attachment.MonetarySystemPublishExchangeOffer attachment) {
            super(transaction.getId(), attachment.getCurrencyId(), transaction.getSenderId(), attachment.getSellRateNQT(),
                    attachment.getTotalSellLimit(), attachment.getInitialSellSupply(), attachment.getExpirationHeight(), transaction.getHeight(),
                    transaction.getIndex());
            this.dbKey = sellOfferDbKeyFactory.newKey(id);
        }

        private CurrencySellOffer(ResultSet rs, DbKey dbKey) throws SQLException {
            super(rs);
            this.dbKey = dbKey;
        }

        @Override
        public CurrencyBuyOffer getCounterOffer() {
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
