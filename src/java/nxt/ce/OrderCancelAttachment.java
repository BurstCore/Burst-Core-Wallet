/*
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

    private final byte[] orderHash;

    public OrderCancelAttachment(byte[] orderHash) {
        this.orderHash = orderHash;
    }

    OrderCancelAttachment(ByteBuffer buffer) {
        super(buffer);
        this.orderHash = new byte[32];
        buffer.get(this.orderHash);
    }

    OrderCancelAttachment(JSONObject data) {
        super(data);
        this.orderHash = Convert.parseHexString((String)data.get("orderHash"));
    }

    @Override
    protected int getMySize() {
        return 32;
    }

    @Override
    protected void putMyBytes(ByteBuffer buffer) {
        buffer.put(orderHash);
    }

    @Override
    protected void putMyJSON(JSONObject attachment) {
        attachment.put("orderHash", Convert.toHexString(orderHash));
    }

    /**
     * Return the exchange order hash
     *
     * @return                      Exchange order hash
     */
    public byte[] getOrderHash() {
        return orderHash;
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
