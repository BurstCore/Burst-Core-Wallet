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

import nxt.Constants;
import nxt.NxtException;
import nxt.blockchain.Attachment;
import nxt.blockchain.TransactionType;
import nxt.messaging.MessagingTransactionType;
import nxt.util.Convert;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class PhasingVoteCastingAttachment extends Attachment.AbstractAttachment {

    private final List<byte[]> transactionFullHashes;
    private final byte[] revealedSecret;

    public PhasingVoteCastingAttachment(ByteBuffer buffer) throws NxtException.NotValidException {
        super(buffer);
        byte length = buffer.get();
        transactionFullHashes = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            byte[] hash = new byte[32];
            buffer.get(hash);
            transactionFullHashes.add(hash);
        }
        int secretLength = buffer.getInt();
        if (secretLength > Constants.MAX_PHASING_REVEALED_SECRET_LENGTH) {
            throw new NxtException.NotValidException("Invalid revealed secret length " + secretLength);
        }
        if (secretLength > 0) {
            revealedSecret = new byte[secretLength];
            buffer.get(revealedSecret);
        } else {
            revealedSecret = Convert.EMPTY_BYTE;
        }
    }

    public PhasingVoteCastingAttachment(JSONObject attachmentData) {
        super(attachmentData);
        JSONArray hashes = (JSONArray) attachmentData.get("transactionFullHashes");
        transactionFullHashes = new ArrayList<>(hashes.size());
        hashes.forEach(hash -> transactionFullHashes.add(Convert.parseHexString((String) hash)));
        String revealedSecret = Convert.emptyToNull((String) attachmentData.get("revealedSecret"));
        this.revealedSecret = revealedSecret != null ? Convert.parseHexString(revealedSecret) : Convert.EMPTY_BYTE;
    }

    public PhasingVoteCastingAttachment(List<byte[]> transactionFullHashes, byte[] revealedSecret) {
        this.transactionFullHashes = transactionFullHashes;
        this.revealedSecret = revealedSecret;
    }

    @Override
    protected int getMySize() {
        return 1 + 32 * transactionFullHashes.size() + 4 + revealedSecret.length;
    }

    @Override
    protected void putMyBytes(ByteBuffer buffer) {
        buffer.put((byte) transactionFullHashes.size());
        transactionFullHashes.forEach(buffer::put);
        buffer.putInt(revealedSecret.length);
        buffer.put(revealedSecret);
    }

    @Override
    protected void putMyJSON(JSONObject attachment) {
        JSONArray jsonArray = new JSONArray();
        transactionFullHashes.forEach(hash -> jsonArray.add(Convert.toHexString(hash)));
        attachment.put("transactionFullHashes", jsonArray);
        if (revealedSecret.length > 0) {
            attachment.put("revealedSecret", Convert.toHexString(revealedSecret));
        }
    }

    @Override
    public TransactionType getTransactionType() {
        return MessagingTransactionType.PHASING_VOTE_CASTING;
    }

    public List<byte[]> getTransactionFullHashes() {
        return transactionFullHashes;
    }

    public byte[] getRevealedSecret() {
        return revealedSecret;
    }
}
