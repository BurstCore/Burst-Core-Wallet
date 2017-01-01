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

import nxt.blockchain.TransactionType;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;

public final class AskOrderCancellationAttachment extends OrderCancellationAttachment {

    AskOrderCancellationAttachment(ByteBuffer buffer) {
        super(buffer);
    }

    AskOrderCancellationAttachment(JSONObject attachmentData) {
        super(attachmentData);
    }

    public AskOrderCancellationAttachment(long orderId) {
        super(orderId);
    }

    @Override
    public TransactionType getTransactionType() {
        return AssetExchangeTransactionType.ASK_ORDER_CANCELLATION;
    }

}
