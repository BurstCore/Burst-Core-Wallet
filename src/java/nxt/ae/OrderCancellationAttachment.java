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

package nxt.ae;

import nxt.blockchain.Attachment;
import nxt.util.Convert;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;

public abstract class OrderCancellationAttachment extends Attachment.AbstractAttachment {

    private final long orderId;

    OrderCancellationAttachment(ByteBuffer buffer) {
        super(buffer);
        this.orderId = buffer.getLong();
    }

    OrderCancellationAttachment(JSONObject attachmentData) {
        super(attachmentData);
        this.orderId = Convert.parseUnsignedLong((String) attachmentData.get("order"));
    }

    OrderCancellationAttachment(long orderId) {
        this.orderId = orderId;
    }

    @Override
    protected int getMySize() {
        return 8;
    }

    @Override
    protected void putMyBytes(ByteBuffer buffer) {
        buffer.putLong(orderId);
    }

    @Override
    protected void putMyJSON(JSONObject attachment) {
        attachment.put("order", Long.toUnsignedString(orderId));
    }

    public long getOrderId() {
        return orderId;
    }
}
