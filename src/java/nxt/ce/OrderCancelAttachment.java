/*
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
package nxt.ce;

import java.nio.ByteBuffer;

import nxt.blockchain.Attachment;
import nxt.blockchain.TransactionType;
import nxt.util.Convert;

import org.json.simple.JSONObject;

/**
 * Coin exchange order cancel attachment for child chains
 */
public class OrderCancelAttachment extends Attachment.AbstractAttachment {

    private final long orderId;

    public OrderCancelAttachment(long orderId) {
        this.orderId = orderId;
    }

    OrderCancelAttachment(ByteBuffer buffer) {
        super(buffer);
        orderId = buffer.getLong();
    }

    OrderCancelAttachment(JSONObject data) {
        super(data);
        this.orderId = Convert.parseUnsignedLong((String)data.get("order"));
    }

    @Override
    protected int getMySize() {
        return 8;
    }

    @Override
    protected void putMyBytes(ByteBuffer buffer) {
        buffer.putLong(orderId);
    }

    @Override
    protected void putMyJSON(JSONObject attachment) {
        attachment.put("order", Long.toUnsignedString(orderId));
    }

    /**
     * Return the exchange order identifier
     *
     * @return                      Exchange order identifier
     */
    public long getOrderId() {
        return orderId;
    }

    /**
     * Return the transaction type
     *
     * @return                      Transaction type
     */
    @Override
    public TransactionType getTransactionType() {
        return CoinExchangeTransactionType.ORDER_CANCEL;
    }
}
