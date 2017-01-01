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

package nxt.dgs;

import nxt.blockchain.Attachment;
import nxt.blockchain.TransactionType;
import nxt.util.Convert;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;

public final class PriceChangeAttachment extends Attachment.AbstractAttachment {

    private final long goodsId;
    private final long priceNQT;

    PriceChangeAttachment(ByteBuffer buffer) {
        super(buffer);
        this.goodsId = buffer.getLong();
        this.priceNQT = buffer.getLong();
    }

    PriceChangeAttachment(JSONObject attachmentData) {
        super(attachmentData);
        this.goodsId = Convert.parseUnsignedLong((String)attachmentData.get("goods"));
        this.priceNQT = Convert.parseLong(attachmentData.get("priceNQT"));
    }

    public PriceChangeAttachment(long goodsId, long priceNQT) {
        this.goodsId = goodsId;
        this.priceNQT = priceNQT;
    }

    @Override
    protected int getMySize() {
        return 8 + 8;
    }

    @Override
    protected void putMyBytes(ByteBuffer buffer) {
        buffer.putLong(goodsId);
        buffer.putLong(priceNQT);
    }

    @Override
    protected void putMyJSON(JSONObject attachment) {
        attachment.put("goods", Long.toUnsignedString(goodsId));
        attachment.put("priceNQT", priceNQT);
    }

    @Override
    public TransactionType getTransactionType() {
        return DigitalGoodsTransactionType.PRICE_CHANGE;
    }

    public long getGoodsId() { return goodsId; }

    public long getPriceNQT() { return priceNQT; }

}
