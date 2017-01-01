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

package nxt.ms;

import nxt.blockchain.Attachment;
import nxt.util.Convert;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;

public abstract class ExchangeAttachment extends Attachment.AbstractAttachment implements MonetarySystemAttachment {

    private final long currencyId;
    private final long rateNQT;
    private final long units;

    ExchangeAttachment(ByteBuffer buffer) {
        super(buffer);
        this.currencyId = buffer.getLong();
        this.rateNQT = buffer.getLong();
        this.units = buffer.getLong();
    }

    ExchangeAttachment(JSONObject attachmentData) {
        super(attachmentData);
        this.currencyId = Convert.parseUnsignedLong((String)attachmentData.get("currency"));
        this.rateNQT = Convert.parseLong(attachmentData.get("rateNQT"));
        this.units = Convert.parseLong(attachmentData.get("units"));
    }

    ExchangeAttachment(long currencyId, long rateNQT, long units) {
        this.currencyId = currencyId;
        this.rateNQT = rateNQT;
        this.units = units;
    }

    @Override
    protected int getMySize() {
        return 8 + 8 + 8;
    }

    @Override
    protected void putMyBytes(ByteBuffer buffer) {
        buffer.putLong(currencyId);
        buffer.putLong(rateNQT);
        buffer.putLong(units);
    }

    @Override
    protected void putMyJSON(JSONObject attachment) {
        attachment.put("currency", Long.toUnsignedString(currencyId));
        attachment.put("rateNQT", rateNQT);
        attachment.put("units", units);
    }

    @Override
    public long getCurrencyId() {
        return currencyId;
    }

    public long getRateNQT() {
        return rateNQT;
    }

    public long getUnits() {
        return units;
    }

}
