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

import nxt.util.Convert;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;

public final class ChainTransactionId {

    public static final int BYTE_SIZE = 4 + 32;

    private final int chainId;
    private final byte[] hash;

    public ChainTransactionId(int chainId, byte[] hash) {
        this.chainId = chainId;
        this.hash = hash;
    }

    public int getChainId() {
        return chainId;
    }

    public byte[] getFullHash() {
        return hash;
    }

    public Chain getChain() {
        return Chain.getChain(chainId);
    }

    public ChildChain getChildChain() {
        return ChildChain.getChildChain(chainId);
    }

    public Transaction getTransaction() {
        return Chain.getChain(chainId).getTransactionHome().findTransactionByFullHash(hash);
    }

    public ChildTransaction getChildTransaction() {
        return (ChildTransaction)ChildChain.getChildChain(chainId).getTransactionHome().findTransactionByFullHash(hash);
    }

    public static ChainTransactionId parse(ByteBuffer buffer) {
        int chainId = buffer.getInt();
        byte[] hash = new byte[32];
        buffer.get(hash);
        return new ChainTransactionId(chainId, hash);
    }

    public void put(ByteBuffer buffer) {
        buffer.putInt(chainId);
        buffer.put(hash);
    }

    public static ChainTransactionId parse(JSONObject json) {
        int chainId = ((Long)json.get("chainId")).intValue();
        byte[] hash = Convert.parseHexString((String)json.get("transactionFullHash"));
        return new ChainTransactionId(chainId, hash);
    }

    public JSONObject getJSON() {
        JSONObject json = new JSONObject();
        json.put("chainId", chainId);
        json.put("transactionFullHash", Convert.toHexString(hash));
        return json;
    }
}
