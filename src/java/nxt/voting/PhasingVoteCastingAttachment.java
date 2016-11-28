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
import nxt.util.Convert;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;

public final class PhasingVoteCastingAttachment extends Attachment.AbstractAttachment {

    private final byte[][] transactionFullHashes;
    private final int[] transactionChainIds;
    private final byte[] revealedSecret;

    public PhasingVoteCastingAttachment(ByteBuffer buffer) throws NxtException.NotValidException {
        super(buffer);
        byte length = buffer.get();
        transactionChainIds = new int[length];
        transactionFullHashes = new byte[length][];
        for (int i = 0; i < length; i++) {
            transactionChainIds[i] = buffer.getInt();
            transactionFullHashes[i] = new byte[32];
            buffer.get(transactionFullHashes[i]);
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
        JSONArray chainIds = (JSONArray) attachmentData.get("transactionChainIds");
        transactionFullHashes = new byte[hashes.size()][];
        transactionChainIds = new int[hashes.size()];
        for (int i = 0; i < hashes.size(); i++) {
            transactionChainIds[i] = ((Long)chainIds.get(i)).intValue();
            transactionFullHashes[i] = Convert.parseHexString((String)hashes.get(i));
        }
        String revealedSecret = Convert.emptyToNull((String) attachmentData.get("revealedSecret"));
        this.revealedSecret = revealedSecret != null ? Convert.parseHexString(revealedSecret) : Convert.EMPTY_BYTE;
    }

    public PhasingVoteCastingAttachment(int[] transactionChainIds, byte[][] transactionFullHashes, byte[] revealedSecret) {
        this.transactionChainIds = transactionChainIds;
        this.transactionFullHashes = transactionFullHashes;
        this.revealedSecret = revealedSecret;
    }

    @Override
    protected int getMySize() {
        return 1 + (32 + 4) * transactionFullHashes.length + 4 + revealedSecret.length;
    }

    @Override
    protected void putMyBytes(ByteBuffer buffer) {
        buffer.put((byte) transactionFullHashes.length);
        for (int i = 0; i < transactionFullHashes.length; i++) {
            buffer.putInt(transactionChainIds[i]);
            buffer.put(transactionFullHashes[i]);
        }
        buffer.putInt(revealedSecret.length);
        buffer.put(revealedSecret);
    }

    @Override
    protected void putMyJSON(JSONObject attachment) {
        JSONArray transactionFullHashesJSON = new JSONArray();
        JSONArray transactionChainIdsJSON = new JSONArray();
        for (int i = 0; i < transactionFullHashes.length; i++) {
            transactionChainIdsJSON.add(transactionChainIds[i]);
            transactionFullHashesJSON.add(Convert.toHexString(transactionFullHashes[i]));
        }
        attachment.put("transactionFullHashes", transactionFullHashesJSON);
        attachment.put("transactionChainIds", transactionChainIdsJSON);
        if (revealedSecret.length > 0) {
            attachment.put("revealedSecret", Convert.toHexString(revealedSecret));
        }
    }

    @Override
    public TransactionType getTransactionType() {
        return VotingTransactionType.PHASING_VOTE_CASTING;
    }

    public int[] getTransactionChainIds() {
        return transactionChainIds;
    }

    public byte[][] getTransactionFullHashes() {
        return transactionFullHashes;
    }

    public byte[] getRevealedSecret() {
        return revealedSecret;
    }
}
