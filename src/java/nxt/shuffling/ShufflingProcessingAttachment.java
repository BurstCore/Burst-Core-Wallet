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

package nxt.shuffling;

import nxt.blockchain.Appendix;
import nxt.blockchain.ChildChain;
import nxt.blockchain.Transaction;
import nxt.blockchain.TransactionType;
import nxt.crypto.Crypto;
import nxt.util.Convert;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;

public final class ShufflingProcessingAttachment extends AbstractShufflingAttachment implements Appendix.Prunable {

    private static final byte[] emptyDataHash = Crypto.sha256().digest();

    public static ShufflingProcessingAttachment parse(JSONObject attachmentData) {
        if (!Appendix.hasAppendix(ShufflingTransactionType.SHUFFLING_PROCESSING.getName(), attachmentData)) {
            return null;
        }
        return new ShufflingProcessingAttachment(attachmentData);
    }

    private volatile byte[][] data;
    private final byte[] hash;

    public ShufflingProcessingAttachment(ByteBuffer buffer) {
        super(buffer);
        this.hash = new byte[32];
        buffer.get(hash);
        this.data = Arrays.equals(hash, emptyDataHash) ? Convert.EMPTY_BYTES : null;
    }

    public ShufflingProcessingAttachment(JSONObject attachmentData) {
        super(attachmentData);
        JSONArray jsonArray = (JSONArray)attachmentData.get("data");
        if (jsonArray != null) {
            this.data = new byte[jsonArray.size()][];
            for (int i = 0; i < this.data.length; i++) {
                this.data[i] = Convert.parseHexString((String) jsonArray.get(i));
            }
            this.hash = null;
        } else {
            this.hash = Convert.parseHexString(Convert.emptyToNull((String)attachmentData.get("hash")));
            this.data = Arrays.equals(hash, emptyDataHash) ? Convert.EMPTY_BYTES : null;
        }
    }

    public ShufflingProcessingAttachment(long shufflingId, byte[][] data, byte[] shufflingStateHash) {
        super(shufflingId, shufflingStateHash);
        this.data = data;
        this.hash = null;
    }

    @Override
    protected int getMyFullSize() {
        int size = super.getMySize();
        if (data != null) {
            size += 1;
            for (byte[] bytes : data) {
                size += 4;
                size += bytes.length;
            }
        }
        return size / 2; // just lie
    }

    @Override
    protected int getMySize() {
        return super.getMySize() + 32;
    }

    @Override
    protected void putMyBytes(ByteBuffer buffer) {
        super.putMyBytes(buffer);
        buffer.put(getHash());
    }

    @Override
    protected void putMyJSON(JSONObject attachment) {
        super.putMyJSON(attachment);
        if (data != null) {
            JSONArray jsonArray = new JSONArray();
            attachment.put("data", jsonArray);
            for (byte[] bytes : data) {
                jsonArray.add(Convert.toHexString(bytes));
            }
        }
        attachment.put("hash", Convert.toHexString(getHash()));
    }

    @Override
    public TransactionType getTransactionType() {
        return ShufflingTransactionType.SHUFFLING_PROCESSING;
    }

    @Override
    public byte[] getHash() {
        if (hash != null) {
            return hash;
        } else if (data != null) {
            MessageDigest digest = Crypto.sha256();
            for (byte[] bytes : data) {
                digest.update(bytes);
            }
            return digest.digest();
        } else {
            throw new IllegalStateException("Both hash and data are null");
        }
    }

    public byte[][] getData() {
        return data;
    }

    @Override
    public void loadPrunable(Transaction transaction, boolean includeExpiredPrunable) {
        if (data == null && shouldLoadPrunable(transaction, includeExpiredPrunable)) {
            data = ((ChildChain) transaction.getChain()).getShufflingParticipantHome().getData(getShufflingId(), transaction.getSenderId());
        }
    }

    @Override
    public boolean hasPrunableData() {
        return data != null;
    }

    @Override
    public void restorePrunableData(Transaction transaction, int blockTimestamp, int height) {
        ((ChildChain) transaction.getChain()).getShufflingParticipantHome().restoreData(getShufflingId(), transaction.getSenderId(), getData(), transaction.getTimestamp(), height);
    }

}