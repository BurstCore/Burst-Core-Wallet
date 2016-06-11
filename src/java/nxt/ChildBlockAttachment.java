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

import nxt.crypto.Crypto;
import nxt.util.Convert;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class ChildBlockAttachment extends Attachment.AbstractAttachment implements Appendix.Prunable {

    static ChildBlockAttachment parse(JSONObject attachmentData) throws NxtException.NotValidException {
        if (!Appendix.hasAppendix(ChildBlockTransactionType.instance.getName(), attachmentData)) {
            return null;
        }
        return new ChildBlockAttachment(attachmentData);
    }

    private final int chainId;
    private volatile byte[][] childTransactionFullHashes;
    private List<ChildTransactionImpl> childTransactions;
    private final byte[] hash;

    ChildBlockAttachment(ByteBuffer buffer) {
        super(buffer);
        this.chainId = buffer.getInt();
        this.hash = new byte[32];
        buffer.get(hash);
        this.childTransactionFullHashes = null;
    }

    ChildBlockAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
        super(attachmentData);
        this.chainId = ((Long)attachmentData.get("chain")).intValue();
        JSONArray jsonArray = (JSONArray)attachmentData.get("childTransactionFullHashes");
        if (jsonArray != null) {
            this.childTransactionFullHashes = new byte[jsonArray.size()][];
            for (int i = 0; i < this.childTransactionFullHashes.length; i++) {
                this.childTransactionFullHashes[i] = Convert.parseHexString((String) jsonArray.get(i));
                if (this.childTransactionFullHashes[i].length != 32) {
                    throw new NxtException.NotValidException("Invalid child transaction full hash "
                    + Convert.toHexString(this.childTransactionFullHashes[i]));
                }
            }
            this.hash = null;
        } else {
            this.hash = Convert.parseHexString(Convert.emptyToNull((String)attachmentData.get("hash")));
            this.childTransactionFullHashes = null;
        }
    }

    ChildBlockAttachment(ChildChain childChain, byte[][] childTransactionFullHashes) {
        this.chainId = childChain.getId();
        this.childTransactionFullHashes = childTransactionFullHashes;
        this.hash = null;
    }

    //TODO: include full size of child transactions???
    @Override
    int getMyFullSize() {
        if (childTransactionFullHashes == null) {
            return 4;
        }
        return 4 + 32 * childTransactionFullHashes.length;
    }

    @Override
    int getMySize() {
        return 4 + 32;
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
        buffer.putInt(chainId);
        buffer.put(getHash());
    }

    @Override
    void putMyJSON(JSONObject json) {
        json.put("chain", chainId);
        if (childTransactionFullHashes != null) {
            JSONArray jsonArray = new JSONArray();
            json.put("childTransactionsFullHashes", jsonArray);
            for (byte[] bytes : childTransactionFullHashes) {
                jsonArray.add(Convert.toHexString(bytes));
            }
        }
        json.put("hash", Convert.toHexString(getHash()));
    }

    @Override
    public TransactionType getTransactionType() {
        return ChildBlockTransactionType.instance;
    }


    public synchronized List<ChildTransactionImpl> getChildTransactions() {
        if (childTransactions == null) {
            TransactionHome transactionHome = ChildChain.getChildChain(chainId).getTransactionHome();
            childTransactions = new ArrayList<>();
            for (byte[] fullHash : childTransactionFullHashes) {
                childTransactions.add((ChildTransactionImpl)transactionHome.findChainTransactionByFullHash(fullHash));
            }
        }
        return childTransactions;
    }

    //Prunable:

    @Override
    public byte[] getHash() {
        if (hash != null) {
            return hash;
        } else if (childTransactionFullHashes != null) {
            MessageDigest digest = Crypto.sha256();
            for (byte[] bytes : childTransactionFullHashes) {
                digest.update(bytes);
            }
            return digest.digest();
        } else {
            throw new IllegalStateException("Both hash and childTransactionFullHashes are null");
        }
    }

    @Override
    void loadPrunable(Transaction transaction, boolean includeExpiredPrunable) {
        if (childTransactionFullHashes == null && shouldLoadPrunable(transaction, includeExpiredPrunable)) {
            //TODO
        }
    }

    @Override
    public boolean hasPrunableData() {
        return childTransactionFullHashes != null;
    }

    @Override
    public void restorePrunableData(Transaction transaction, int blockTimestamp, int height) {
        //TODO
    }

}
