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

package nxt.blockchain;

import nxt.util.Convert;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.util.Arrays;

public final class ChainTransactionId {

    public static final int BYTE_SIZE = 4 + 32;

    private final int chainId;
    private final byte[] hash;
    private final long id;

    public ChainTransactionId(int chainId, byte[] hash) {
        this.chainId = chainId;
        this.hash = hash;
        this.id = Convert.fullHashToId(hash);
    }

    public int getChainId() {
        return chainId;
    }

    public byte[] getFullHash() {
        return hash;
    }

    public long getTransactionId() {
        return id;
    }

    public Chain getChain() {
        return Chain.getChain(chainId);
    }

    public ChildChain getChildChain() {
        return ChildChain.getChildChain(chainId);
    }

    public Transaction getTransaction() {
        return Chain.getChain(chainId).getTransactionHome().findTransaction(hash);
    }

    public ChildTransaction getChildTransaction() {
        return (ChildTransaction)ChildChain.getChildChain(chainId).getTransactionHome().findTransaction(hash);
    }

    public static ChainTransactionId getChainTransactionId(Transaction transaction) {
        return new ChainTransactionId(transaction.getChain().getId(), transaction.getFullHash());
    }

    public static ChainTransactionId parse(ByteBuffer buffer) {
        int chainId = buffer.getInt();
        byte[] hash = new byte[32];
        buffer.get(hash);
        if (Convert.emptyToNull(hash) == null) {
            return null;
        }
        return new ChainTransactionId(chainId, hash);
    }

    public void put(ByteBuffer buffer) {
        buffer.putInt(chainId);
        buffer.put(hash);
    }

    public static ChainTransactionId parse(JSONObject json) {
        if (json == null) {
            return null;
        }
        int chainId = ((Long)json.get("chain")).intValue();
        byte[] hash = Convert.parseHexString((String)json.get("transactionFullHash"));
        return new ChainTransactionId(chainId, hash);
    }

    public JSONObject getJSON() {
        JSONObject json = new JSONObject();
        json.put("chain", chainId);
        json.put("transactionFullHash", Convert.toHexString(hash));
        return json;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ChainTransactionId && chainId == ((ChainTransactionId)object).chainId && Arrays.equals(hash, ((ChainTransactionId)object).hash);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "chain: " + Chain.getChain(chainId).getName() + ", full hash: " + Convert.toHexString(hash);
    }
}
