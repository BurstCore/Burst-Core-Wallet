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

import nxt.Nxt;
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
    private final long taggedDataId;
    private final boolean jsonIsPruned;

    TaggedDataExtendAttachment(ByteBuffer buffer) {
        super(buffer);
        this.taggedDataId = buffer.getLong();
        this.jsonIsPruned = false;
    }

    TaggedDataExtendAttachment(JSONObject attachmentData) {
        super(attachmentData);
        this.taggedDataId = Convert.parseUnsignedLong((String)attachmentData.get("taggedData"));
        this.jsonIsPruned = attachmentData.get("data") == null;
    }

    public TaggedDataExtendAttachment(TaggedDataHome.TaggedData taggedData) {
        super(taggedData.getName(), taggedData.getDescription(), taggedData.getTags(), taggedData.getType(),
                taggedData.getChannel(), taggedData.isText(), taggedData.getFilename(), taggedData.getData());
        this.taggedDataId = taggedData.getId();
        this.jsonIsPruned = false;
    }

    @Override
    protected int getMySize() {
        return 8;
    }

    @Override
    protected void putMyBytes(ByteBuffer buffer) {
        buffer.putLong(taggedDataId);
    }

    @Override
    protected void putMyJSON(JSONObject attachment) {
        super.putMyJSON(attachment);
        attachment.put("taggedData", Long.toUnsignedString(taggedDataId));
    }

    @Override
    public TransactionType getTransactionType() {
        return TaggedDataTransactionType.TAGGED_DATA_EXTEND;
    }

    public long getTaggedDataId() {
        return taggedDataId;
    }

    @Override
    public byte[] getHash() {
        if (hash == null) {
            hash = super.getHash();
        }
        if (hash == null) {
            //TODO: store data hash, or the child chain id for the TaggedDataUpload chain
            TaggedDataUploadAttachment taggedDataUpload = (TaggedDataUploadAttachment) Nxt.getBlockchain().getTransaction(ChildChain.IGNIS, taggedDataId).getAttachment();
            hash = taggedDataUpload.getHash();
        }
        return hash;
    }

    @Override
    long getTaggedDataId(Transaction transaction) {
        return taggedDataId;
    }

    boolean jsonIsPruned() {
        return jsonIsPruned;
    }

    @Override
    public void restorePrunableData(Transaction transaction, int blockTimestamp, int height) {
    }

}
