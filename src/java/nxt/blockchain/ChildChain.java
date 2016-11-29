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

package nxt.blockchain;

import nxt.NxtException;
import nxt.ae.AssetDividendHome;
import nxt.ae.OrderHome;
import nxt.ae.TradeHome;
import nxt.aliases.AliasHome;
import nxt.dgs.DigitalGoodsHome;
import nxt.messaging.PrunableMessageHome;
import nxt.ms.CurrencyFounderHome;
import nxt.ms.ExchangeHome;
import nxt.ms.ExchangeOfferHome;
import nxt.ms.ExchangeRequestHome;
import nxt.shuffling.ShufflingHome;
import nxt.shuffling.ShufflingParticipantHome;
import nxt.taggeddata.TaggedDataHome;
import nxt.voting.PhasingPollHome;
import nxt.voting.PhasingVoteHome;
import nxt.voting.PollHome;
import nxt.voting.VoteHome;
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

    public static final ChildChain IGNIS = new ChildChain(2, "IGNIS");
    public static final ChildChain BTC = new ChildChain(3, "BTC");

    public static ChildChain getChildChain(String name) {
        return childChains.get(name);
    }

    public static ChildChain getChildChain(int id) {
        return childChainsById.get(id);
    }

    public static Collection<ChildChain> getAll() {
        return allChildChains;
    }

    public static void init() {}

    private final AliasHome aliasHome;
    private final AssetDividendHome assetDividendHome;
    private final CurrencyFounderHome currencyFounderHome;
    private final DigitalGoodsHome digitalGoodsHome;
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
    private final VoteHome voteHome;

    private ChildChain(int id, String name) {
        super(id, name);
        this.aliasHome = AliasHome.forChain(this);
        this.assetDividendHome = AssetDividendHome.forChain(this);
        this.currencyFounderHome = CurrencyFounderHome.forChain(this);
        this.digitalGoodsHome = DigitalGoodsHome.forChain(this);
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
        this.voteHome = VoteHome.forChain(this);
        childChains.put(name, this);
        childChainsById.put(id, this);
    }

    public AliasHome getAliasHome() {
        return aliasHome;
    }

    public AssetDividendHome getAssetDividendHome() {
        return assetDividendHome;
    }

    public CurrencyFounderHome getCurrencyFounderHome() {
        return currencyFounderHome;
    }

    public DigitalGoodsHome getDigitalGoodsHome() {
        return digitalGoodsHome;
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

    public VoteHome getVoteHome() {
        return voteHome;
    }

    @Override
    public ChildTransactionImpl.BuilderImpl newTransactionBuilder(byte version, byte[] senderPublicKey, long amount, long fee, short deadline,
                                                                  Attachment.AbstractAttachment attachment, JSONObject attachmentData, JSONObject transactionData) throws NxtException.NotValidException {
        return ChildTransactionImpl.newTransactionBuilder(this.getId(), version, senderPublicKey, amount, fee, deadline,
                attachment, attachmentData, transactionData);
    }

    @Override
    public ChildTransactionImpl.BuilderImpl newTransactionBuilder(byte version, byte[] senderPublicKey, long amount, long fee, short deadline,
                                                                  Attachment.AbstractAttachment attachment, int flags, ByteBuffer buffer) throws NxtException.NotValidException {
        return ChildTransactionImpl.newTransactionBuilder(this.getId(), version, senderPublicKey, amount, fee, deadline,
                attachment, flags, buffer);
    }

    @Override
    public ChildTransactionImpl.BuilderImpl newTransactionBuilder(byte version, long amount, long fee, short deadline,
                                                                  Attachment.AbstractAttachment attachment, ByteBuffer buffer, Connection con, ResultSet rs) throws NxtException.NotValidException {
        return ChildTransactionImpl.newTransactionBuilder(this.getId(), version, amount, fee, deadline,
                attachment, buffer, con, rs);
    }

    @Override
    public UnconfirmedTransaction newUnconfirmedTransaction(ResultSet rs) throws SQLException, NxtException.NotValidException {
        return new UnconfirmedChildTransaction(rs);
    }

}
