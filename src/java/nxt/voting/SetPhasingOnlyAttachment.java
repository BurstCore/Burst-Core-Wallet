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

package nxt.voting;

import nxt.blockchain.Attachment;
import nxt.blockchain.TransactionType;
import nxt.util.Convert;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;

public final class SetPhasingOnlyAttachment extends Attachment.AbstractAttachment {

    private final PhasingParams phasingParams;
    private final long maxFees;
    private final short minDuration;
    private final short maxDuration;

    public SetPhasingOnlyAttachment(PhasingParams params, long maxFees, short minDuration, short maxDuration) {
        phasingParams = params;
        this.maxFees = maxFees;
        this.minDuration = minDuration;
        this.maxDuration = maxDuration;
    }

    SetPhasingOnlyAttachment(ByteBuffer buffer) {
        super(buffer);
        phasingParams = new PhasingParams(buffer);
        maxFees = buffer.getLong();
        minDuration = buffer.getShort();
        maxDuration = buffer.getShort();
    }

    SetPhasingOnlyAttachment(JSONObject attachmentData) {
        super(attachmentData);
        JSONObject phasingControlParams = (JSONObject) attachmentData.get("phasingControlParams");
        phasingParams = new PhasingParams(phasingControlParams);
        maxFees = Convert.parseLong(attachmentData.get("controlMaxFees"));
        minDuration = ((Long)attachmentData.get("controlMinDuration")).shortValue();
        maxDuration = ((Long)attachmentData.get("controlMaxDuration")).shortValue();
    }

    @Override
    public TransactionType getTransactionType() {
        return AccountControlTransactionType.SET_PHASING_ONLY;
    }

    @Override
    protected int getMySize() {
        return phasingParams.getMySize() + 8 + 2 + 2;
    }

    @Override
    protected void putMyBytes(ByteBuffer buffer) {
        phasingParams.putMyBytes(buffer);
        buffer.putLong(maxFees);
        buffer.putShort(minDuration);
        buffer.putShort(maxDuration);
    }

    @Override
    protected void putMyJSON(JSONObject json) {
        JSONObject phasingControlParams = new JSONObject();
        phasingParams.putMyJSON(phasingControlParams);
        json.put("phasingControlParams", phasingControlParams);
        json.put("controlMaxFees", maxFees);
        json.put("controlMinDuration", minDuration);
        json.put("controlMaxDuration", maxDuration);
    }

    public PhasingParams getPhasingParams() {
        return phasingParams;
    }

    public long getMaxFees() {
        return maxFees;
    }

    public short getMinDuration() {
        return minDuration;
    }

    public short getMaxDuration() {
        return maxDuration;
    }

}
