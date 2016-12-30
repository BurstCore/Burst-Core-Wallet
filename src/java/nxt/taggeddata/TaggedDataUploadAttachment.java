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

import nxt.NxtException;
import nxt.blockchain.Appendix;
import nxt.blockchain.ChildChain;
import nxt.blockchain.Transaction;
import nxt.blockchain.TransactionType;
import nxt.util.Convert;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.util.Arrays;

public final class TaggedDataUploadAttachment extends TaggedDataAttachment {

    public static final PrunableAppendixParser appendixParser = new PrunableAppendixParser() {
        @Override
        public AbstractAppendix parse(ByteBuffer buffer) throws NxtException.NotValidException {
            return new TaggedDataUploadAttachment(buffer);
        }
        @Override
        public AbstractAppendix parsePrunable(ByteBuffer buffer) throws NxtException.NotValidException {
            return new TaggedDataUploadAttachment(buffer, true);
        }
        @Override
        public AbstractAppendix parse(JSONObject attachmentData) throws NxtException.NotValidException {
            if (!Appendix.hasAppendix(TaggedDataTransactionType.TAGGED_DATA_UPLOAD.getName(), attachmentData)) {
                return null;
            }
            return new TaggedDataUploadAttachment(attachmentData);
        }
    };

    private final byte[] hash;

    TaggedDataUploadAttachment(ByteBuffer buffer) {
        super(buffer);
        this.hash = new byte[32];
        buffer.get(hash);
    }

    private TaggedDataUploadAttachment(ByteBuffer buffer, boolean prunable) throws NxtException.NotValidException {
        super(buffer, prunable);
        this.hash = null;
    }

    TaggedDataUploadAttachment(JSONObject attachmentData) {
        super(attachmentData);
        String dataJSON = (String) attachmentData.get("data");
        if (dataJSON == null) {
            this.hash = Convert.parseHexString(Convert.emptyToNull((String)attachmentData.get("hash")));
        } else {
            this.hash = null;
        }
    }

    public TaggedDataUploadAttachment(String name, String description, String tags, String type, String channel, boolean isText,
                                      String filename, byte[] data) throws NxtException.NotValidException {
        super(name, description, tags, type, channel, isText, filename, data);
        this.hash = null;
        if (isText && !Arrays.equals(data, Convert.toBytes(Convert.toString(data)))) {
            throw new NxtException.NotValidException("Data is not UTF-8 text");
        }
    }

    @Override
    protected int getMySize() {
        return 32;
    }

    @Override
    protected void putMyBytes(ByteBuffer buffer) {
        buffer.put(getHash());
    }

    @Override
    protected void putMyJSON(JSONObject attachment) {
        super.putMyJSON(attachment);
        attachment.put("hash", Convert.toHexString(getHash()));
    }

    @Override
    public TransactionType getTransactionType() {
        return TaggedDataTransactionType.TAGGED_DATA_UPLOAD;
    }

    @Override
    public byte[] getHash() {
        if (hash != null) {
            return hash;
        }
        return super.getHash();
    }

    @Override
    byte[] getTaggedDataTransactionFullHash(Transaction transaction) {
        return transaction.getFullHash();
    }

    @Override
    public void restorePrunableData(Transaction transaction, int blockTimestamp, int height) {
        ((ChildChain) transaction.getChain()).getTaggedDataHome().restore(transaction, this, blockTimestamp, height);
    }

}
