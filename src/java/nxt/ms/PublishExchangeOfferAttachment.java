/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2017 Jelurida IP B.V.
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

import nxt.blockchain.Attachment;
import nxt.blockchain.TransactionType;
import nxt.util.Convert;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;

public final class PublishExchangeOfferAttachment extends Attachment.AbstractAttachment implements MonetarySystemAttachment {

    private final long currencyId;
    private final long buyRateNQT;
    private final long sellRateNQT;
    private final long totalBuyLimit;
    private final long totalSellLimit;
    private final long initialBuySupply;
    private final long initialSellSupply;
    private final int expirationHeight;

    public PublishExchangeOfferAttachment(ByteBuffer buffer) {
        super(buffer);
        this.currencyId = buffer.getLong();
        this.buyRateNQT = buffer.getLong();
        this.sellRateNQT = buffer.getLong();
        this.totalBuyLimit = buffer.getLong();
        this.totalSellLimit = buffer.getLong();
        this.initialBuySupply = buffer.getLong();
        this.initialSellSupply = buffer.getLong();
        this.expirationHeight = buffer.getInt();
    }

    public PublishExchangeOfferAttachment(JSONObject attachmentData) {
        super(attachmentData);
        this.currencyId = Convert.parseUnsignedLong((String)attachmentData.get("currency"));
        this.buyRateNQT = Convert.parseLong(attachmentData.get("buyRateNQT"));
        this.sellRateNQT = Convert.parseLong(attachmentData.get("sellRateNQT"));
        this.totalBuyLimit = Convert.parseLong(attachmentData.get("totalBuyLimit"));
        this.totalSellLimit = Convert.parseLong(attachmentData.get("totalSellLimit"));
        this.initialBuySupply = Convert.parseLong(attachmentData.get("initialBuySupply"));
        this.initialSellSupply = Convert.parseLong(attachmentData.get("initialSellSupply"));
        this.expirationHeight = ((Long)attachmentData.get("expirationHeight")).intValue();
    }

    public PublishExchangeOfferAttachment(long currencyId, long buyRateNQT, long sellRateNQT, long totalBuyLimit,
                                          long totalSellLimit, long initialBuySupply, long initialSellSupply, int expirationHeight) {
        this.currencyId = currencyId;
        this.buyRateNQT = buyRateNQT;
        this.sellRateNQT = sellRateNQT;
        this.totalBuyLimit = totalBuyLimit;
        this.totalSellLimit = totalSellLimit;
        this.initialBuySupply = initialBuySupply;
        this.initialSellSupply = initialSellSupply;
        this.expirationHeight = expirationHeight;
    }

    @Override
    protected int getMySize() {
        return 8 + 8 + 8 + 8 + 8 + 8 + 8 + 4;
    }

    @Override
    protected void putMyBytes(ByteBuffer buffer) {
        buffer.putLong(currencyId);
        buffer.putLong(buyRateNQT);
        buffer.putLong(sellRateNQT);
        buffer.putLong(totalBuyLimit);
        buffer.putLong(totalSellLimit);
        buffer.putLong(initialBuySupply);
        buffer.putLong(initialSellSupply);
        buffer.putInt(expirationHeight);
    }

    @Override
    protected void putMyJSON(JSONObject attachment) {
        attachment.put("currency", Long.toUnsignedString(currencyId));
        attachment.put("buyRateNQT", buyRateNQT);
        attachment.put("sellRateNQT", sellRateNQT);
        attachment.put("totalBuyLimit", totalBuyLimit);
        attachment.put("totalSellLimit", totalSellLimit);
        attachment.put("initialBuySupply", initialBuySupply);
        attachment.put("initialSellSupply", initialSellSupply);
        attachment.put("expirationHeight", expirationHeight);
    }

    @Override
    public TransactionType getTransactionType() {
        return MonetarySystemTransactionType.PUBLISH_EXCHANGE_OFFER;
    }

    @Override
    public long getCurrencyId() {
        return currencyId;
    }

    public long getBuyRateNQT() {
        return buyRateNQT;
    }

    public long getSellRateNQT() {
        return sellRateNQT;
    }

    public long getTotalBuyLimit() {
        return totalBuyLimit;
    }

    public long getTotalSellLimit() {
        return totalSellLimit;
    }

    public long getInitialBuySupply() {
        return initialBuySupply;
    }

    public long getInitialSellSupply() {
        return initialSellSupply;
    }

    public int getExpirationHeight() {
        return expirationHeight;
    }

}
