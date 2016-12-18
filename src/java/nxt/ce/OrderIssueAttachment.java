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

import nxt.NxtException;
import nxt.blockchain.Attachment;
import nxt.blockchain.Chain;
import nxt.blockchain.TransactionType;
import nxt.util.Convert;

import org.json.simple.JSONObject;

import java.nio.ByteBuffer;

/**
 * Coin exchange order issue attachment for a child chain
 */
public class OrderIssueAttachment extends Attachment.AbstractAttachment {

    private final byte[] chainNameBytes;
    private final Chain chain;
    private final byte[] exchangeNameBytes;
    private final Chain exchangeChain;
    private final long quantityQNT;
    private final long priceNQT;

    public OrderIssueAttachment(Chain chain, Chain exchangeChain, long quantityQNT, long priceNQT) {
        this.chain = chain;
        this.exchangeChain = exchangeChain;
        this.quantityQNT = quantityQNT;
        this.priceNQT = priceNQT;
        this.chainNameBytes = Convert.toBytes(chain.getName());
        this.exchangeNameBytes = Convert.toBytes(exchangeChain.getName());
    }

    OrderIssueAttachment(ByteBuffer buffer) throws NxtException.NotValidException {
        super(buffer);
        int length = buffer.get();
        if (length == 0) {
            throw new NxtException.NotValidException("Chain name missing");
        }
        this.chainNameBytes = new byte[length];
        buffer.get(chainNameBytes);
        String name = Convert.toString(chainNameBytes);
        this.chain = Chain.getChain(name);
        if (this.chain == null) {
            throw new NxtException.NotValidException("Chain '" + name + "' not defined");
        }
        length = buffer.get();
        if (length == 0) {
            throw new NxtException.NotValidException("Exchange chain name missing");
        }
        this.exchangeNameBytes = new byte[length];
        buffer.get(exchangeNameBytes);
        name = Convert.toString(exchangeNameBytes);
        this.exchangeChain = Chain.getChain(name);
        if (this.exchangeChain == null) {
            throw new NxtException.NotValidException("Exchange chain '" + name + "' not defined");
        }
        if (chain == exchangeChain) {
            throw new NxtException.NotValidException("Chain and exchange chain must be different");
        }
        this.quantityQNT = buffer.getLong();
        this.priceNQT = buffer.getLong();
    }

    OrderIssueAttachment(JSONObject data) throws NxtException.NotValidException {
        super(data);
        String name = Convert.emptyToNull((String)data.get("chain"));
        if (name == null) {
            throw new NxtException.NotValidException("Chain name missing");
        }
        this.chainNameBytes = Convert.toBytes(name);
        this.chain = Chain.getChain(name);
        if (this.chain == null) {
            throw new NxtException.NotValidException("Chain '" + name + "' not defined");
        }
        name = Convert.emptyToNull((String)data.get("exchangeChain"));
        if (name == null) {
            throw new NxtException.NotValidException("Exchange chain name missing");
        }
        this.exchangeNameBytes = Convert.toBytes(name);
        this.exchangeChain = Chain.getChain(name);
        if (this.exchangeChain == null) {
            throw new NxtException.NotValidException("Exchange chain '" + name + "' not defined");
        }
        if (chain == exchangeChain) {
            throw new NxtException.NotValidException("Chain and exchange chain must be different");
        }
        this.quantityQNT = Convert.parseLong(data.get("quantityQNT"));
        this.priceNQT = Convert.parseLong(data.get("priceNQT"));
    }

    @Override
    protected int getMySize() {
        return 1 + chainNameBytes.length + 1 + exchangeNameBytes.length + 8 + 8;
    }

    @Override
    protected void putMyBytes(ByteBuffer buffer) {
        buffer.put((byte)chainNameBytes.length);
        if (chainNameBytes.length > 0) {
            buffer.put(chainNameBytes);
        }
        buffer.put((byte)exchangeNameBytes.length);
        if (exchangeNameBytes.length > 0) {
            buffer.put(exchangeNameBytes);
        }
        buffer.putLong(quantityQNT).putLong(priceNQT);
    }

    @Override
    protected void putMyJSON(JSONObject attachment) {
        attachment.put("chain", chain != null ? chain.getName() : "");
        attachment.put("exchangeChain", exchangeChain != null ? exchangeChain.getName() : "");
        attachment.put("quantityQNT", quantityQNT);
        attachment.put("priceNQT", priceNQT);
    }

    /**
     * Return the chain
     *
     * @return                  Chain or null if the chain is not valid
     */
    public Chain getChain() {
        return chain;
    }

    /**
     * Return the exchange chain
     *
     * @return                  Exchange chain or null if the chain is not valid
     */
    public Chain getExchangeChain() {
        return exchangeChain;
    }

    /**
     * Return the exchange amount with an implied 8 decimal places
     *
     * @return                  Exchange amount
     */
    public long getQuantityQNT() {
        return quantityQNT;
    }

    /**
     * Return the exchange price with an implied 8 decimal places
     *
     * @return                  Exchange price
     */
    public long getPriceNQT() {
        return priceNQT;
    }

    /**
     * Return the transaction type
     *
     * @return                  Transaction type
     */
    @Override
    public TransactionType getTransactionType() {
        return CoinExchangeTransactionType.ORDER_ISSUE;
    }
}
