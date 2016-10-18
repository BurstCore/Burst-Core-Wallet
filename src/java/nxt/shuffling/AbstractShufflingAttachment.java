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

import nxt.blockchain.Attachment;
import nxt.util.Convert;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;

public abstract class AbstractShufflingAttachment extends Attachment.AbstractAttachment implements ShufflingAttachment {

    private final long shufflingId;
    private final byte[] shufflingStateHash;

    AbstractShufflingAttachment(ByteBuffer buffer) {
        super(buffer);
        this.shufflingId = buffer.getLong();
        this.shufflingStateHash = new byte[32];
        buffer.get(this.shufflingStateHash);
    }

    AbstractShufflingAttachment(JSONObject attachmentData) {
        super(attachmentData);
        this.shufflingId = Convert.parseUnsignedLong((String) attachmentData.get("shuffling"));
        this.shufflingStateHash = Convert.parseHexString((String) attachmentData.get("shufflingStateHash"));
    }

    AbstractShufflingAttachment(long shufflingId, byte[] shufflingStateHash) {
        this.shufflingId = shufflingId;
        this.shufflingStateHash = shufflingStateHash;
    }

    @Override
    protected int getMySize() {
        return 8 + 32;
    }

    @Override
    protected void putMyBytes(ByteBuffer buffer) {
        buffer.putLong(shufflingId);
        buffer.put(shufflingStateHash);
    }

    @Override
    protected void putMyJSON(JSONObject attachment) {
        attachment.put("shuffling", Long.toUnsignedString(shufflingId));
        attachment.put("shufflingStateHash", Convert.toHexString(shufflingStateHash));
    }

    @Override
    public final long getShufflingId() {
        return shufflingId;
    }

    @Override
    public final byte[] getShufflingStateHash() {
        return shufflingStateHash;
    }

}
