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

package nxt.taggeddata;

import nxt.blockchain.Appendix;
import nxt.blockchain.ChildChain;
import nxt.blockchain.Transaction;
import nxt.blockchain.TransactionType;
import nxt.util.Convert;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;

public final class TaggedDataExtendAttachment extends TaggedDataAttachment {

    public static TaggedDataExtendAttachment parse(JSONObject attachmentData) {
        if (!Appendix.hasAppendix(TaggedDataTransactionType.TAGGED_DATA_EXTEND.getName(), attachmentData)) {
            return null;
        }
        return new TaggedDataExtendAttachment(attachmentData);
    }

    private volatile byte[] hash;
    private final byte[] taggedDataTransactionFullHash;
    private final int chainId;
    private final boolean jsonIsPruned;

    TaggedDataExtendAttachment(ByteBuffer buffer) {
        super(buffer);
        this.chainId = buffer.getInt();
        this.taggedDataTransactionFullHash = new byte[32];
        buffer.get(taggedDataTransactionFullHash);
        this.jsonIsPruned = false;
    }

    TaggedDataExtendAttachment(JSONObject attachmentData) {
        super(attachmentData);
        this.chainId = ((Long)attachmentData.get("chain")).intValue();
        this.taggedDataTransactionFullHash = Convert.parseHexString((String)attachmentData.get("taggedDataTransactionFullHash"));
        this.jsonIsPruned = attachmentData.get("data") == null;
    }

    public TaggedDataExtendAttachment(ChildChain childChain, TaggedDataHome.TaggedData taggedData) {
        super(taggedData.getName(), taggedData.getDescription(), taggedData.getTags(), taggedData.getType(),
                taggedData.getChannel(), taggedData.isText(), taggedData.getFilename(), taggedData.getData());
        this.chainId = childChain.getId();
        this.taggedDataTransactionFullHash = taggedData.getTransactionFullHash();
        this.jsonIsPruned = false;
    }

    @Override
    protected int getMySize() {
        return 4 + 32;
    }

    @Override
    protected void putMyBytes(ByteBuffer buffer) {
        buffer.putInt(chainId);
        buffer.put(taggedDataTransactionFullHash);
    }

    @Override
    protected void putMyJSON(JSONObject attachment) {
        super.putMyJSON(attachment);
        attachment.put("chain", chainId);
        attachment.put("taggedDataTransactionFullHash", Convert.toHexString(taggedDataTransactionFullHash));
    }

    @Override
    public TransactionType getTransactionType() {
        return TaggedDataTransactionType.TAGGED_DATA_EXTEND;
    }

    public byte[] getTaggedDataTransactionFullHash() {
        return taggedDataTransactionFullHash;
    }

    public int getChainId() {
        return chainId;
    }

    @Override
    public byte[] getHash() {
        if (hash == null) {
            hash = super.getHash();
        }
        if (hash == null) {
            TaggedDataUploadAttachment taggedDataUpload = (TaggedDataUploadAttachment) ChildChain.getChain(chainId).getTransactionHome()
                            .findTransaction(taggedDataTransactionFullHash).getAttachment();
            hash = taggedDataUpload.getHash();
        }
        return hash;
    }

    @Override
    byte[] getTaggedDataTransactionFullHash(Transaction transaction) {
        return taggedDataTransactionFullHash;
    }

    boolean jsonIsPruned() {
        return jsonIsPruned;
    }

    @Override
    public void restorePrunableData(Transaction transaction, int blockTimestamp, int height) {
    }

}
