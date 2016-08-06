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

import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class ChildChain extends Chain {

    private static final Map<String, ChildChain> childChains = new HashMap<>();
    private static final Map<Integer, ChildChain> childChainsById = new HashMap<>();

    private static final Collection<ChildChain> allChildChains = Collections.unmodifiableCollection(childChains.values());

    public static final ChildChain NXT = new ChildChain(2, "NXT");

    public static ChildChain getChildChain(String name) {
        return childChains.get(name);
    }

    public static ChildChain getChildChain(int id) {
        return childChainsById.get(id);
    }

    public static Collection<ChildChain> getAll() {
        return allChildChains;
    }

    static void init() {}

    private final AliasHome aliasHome;
    private final BalanceHome balanceHome;
    private final CurrencyFounderHome currencyFounderHome;
    private final DGSHome dgsHome;
    private final ExchangeHome exchangeHome;
    private final ExchangeOfferHome exchangeOfferHome;
    private final ExchangeRequestHome exchangeRequestHome;
    private final OrderHome orderHome;
    private final PhasingPollHome phasingPollHome;
    private final PhasingVoteHome phasingVoteHome;
    private final PollHome pollHome;
    private final PrunableMessageHome prunableMessageHome;
    private final ShufflingHome shufflingHome;
    private final ShufflingParticipantHome shufflingParticipantHome;
    private final TaggedDataHome taggedDataHome;
    private final TradeHome tradeHome;
    private final TransactionHome transactionHome;
    private final VoteHome voteHome;

    private ChildChain(int id, String name) {
        super(id, name);
        this.aliasHome = AliasHome.forChain(this);
        this.balanceHome = BalanceHome.forChain(this);
        this.currencyFounderHome = CurrencyFounderHome.forChain(this);
        this.dgsHome = DGSHome.forChain(this);
        this.exchangeHome = ExchangeHome.forChain(this);
        this.exchangeOfferHome = ExchangeOfferHome.forChain(this);
        this.exchangeRequestHome = ExchangeRequestHome.forChain(this);
        this.orderHome = OrderHome.forChain(this);
        this.phasingPollHome = PhasingPollHome.forChain(this);
        this.phasingVoteHome = PhasingVoteHome.forChain(this);
        this.pollHome = PollHome.forChain(this);
        this.prunableMessageHome = PrunableMessageHome.forChain(this);
        this.shufflingHome = ShufflingHome.forChain(this);
        this.shufflingParticipantHome = ShufflingParticipantHome.forChain(this);
        this.taggedDataHome = TaggedDataHome.forChain(this);
        this.tradeHome = TradeHome.forChain(this);
        this.transactionHome = TransactionHome.forChain(this);
        this.voteHome = VoteHome.forChain(this);
        childChains.put(name, this);
        childChainsById.put(id, this);
    }

    public AliasHome getAliasHome() {
        return aliasHome;
    }

    public BalanceHome getBalanceHome() {
        return balanceHome;
    }

    public CurrencyFounderHome getCurrencyFounderHome() {
        return currencyFounderHome;
    }

    public DGSHome getDGSHome() {
        return dgsHome;
    }

    public ExchangeHome getExchangeHome() {
        return exchangeHome;
    }

    public ExchangeOfferHome getExchangeOfferHome() {
        return exchangeOfferHome;
    }

    public ExchangeRequestHome getExchangeRequestHome() {
        return exchangeRequestHome;
    }

    public OrderHome getOrderHome() {
        return orderHome;
    }

    public PhasingPollHome getPhasingPollHome() {
        return phasingPollHome;
    }

    public PhasingVoteHome getPhasingVoteHome() {
        return phasingVoteHome;
    }

    public PollHome getPollHome() {
        return pollHome;
    }

    public PrunableMessageHome getPrunableMessageHome() {
        return prunableMessageHome;
    }

    public ShufflingHome getShufflingHome() {
        return shufflingHome;
    }

    public ShufflingParticipantHome getShufflingParticipantHome() {
        return shufflingParticipantHome;
    }

    public TaggedDataHome getTaggedDataHome() {
        return taggedDataHome;
    }

    public TradeHome getTradeHome() {
        return tradeHome;
    }

    public TransactionHome getTransactionHome() {
        return transactionHome;
    }

    public VoteHome getVoteHome() {
        return voteHome;
    }

    @Override
    ChildTransactionImpl.BuilderImpl newTransactionBuilder(byte version, byte[] senderPublicKey, long amount, long fee, short deadline,
                                                         Attachment.AbstractAttachment attachment, JSONObject attachmentData, JSONObject transactionData) throws NxtException.NotValidException {
        return ChildTransactionImpl.newTransactionBuilder(this.getId(), version, senderPublicKey, amount, fee, deadline,
                attachment, attachmentData, transactionData);
    }

    @Override
    ChildTransactionImpl.BuilderImpl newTransactionBuilder(byte version, byte[] senderPublicKey, long amount, long fee, short deadline,
                                                           Attachment.AbstractAttachment attachment, int flags, ByteBuffer buffer) throws NxtException.NotValidException {
        return ChildTransactionImpl.newTransactionBuilder(this.getId(), version, senderPublicKey, amount, fee, deadline,
                attachment, flags, buffer);
    }

    @Override
    ChildTransactionImpl.BuilderImpl newTransactionBuilder(byte version, long amount, long fee, short deadline,
                                                           Attachment.AbstractAttachment attachment, ByteBuffer buffer, Connection con, ResultSet rs) throws NxtException.NotValidException {
        return ChildTransactionImpl.newTransactionBuilder(this.getId(), version, amount, fee, deadline,
                attachment, buffer, con, rs);
    }

    @Override
    UnconfirmedTransaction newUnconfirmedTransaction(ResultSet rs) throws SQLException, NxtException.NotValidException {
        return new UnconfirmedChildTransaction(rs);
    }

}
