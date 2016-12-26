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

package nxt.blockchain;

import nxt.Constants;
import nxt.Nxt;
import nxt.NxtException;
import nxt.account.Account;
import nxt.account.AccountLedger;
import nxt.util.Convert;
import nxt.util.JSON;
import nxt.util.Logger;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ChildBlockFxtTransactionType extends FxtTransactionType {

    public static final ChildBlockFxtTransactionType INSTANCE = new ChildBlockFxtTransactionType();

    private static final Fee CHILD_BLOCK_FEE = (transaction, appendage) -> {
        long totalFee = 0;
        int blockchainHeight = Nxt.getBlockchain().getHeight();
        for (ChildTransactionImpl childTransaction : ((FxtTransactionImpl)transaction).getChildTransactions()) {
            totalFee += childTransaction.getMinimumFeeFQT(blockchainHeight);
        }
        return totalFee;
    };

    @Override
    public byte getType() {
        return FxtTransactionType.TYPE_CHILDCHAIN_BLOCK;
    }

    @Override
    public byte getSubtype() {
        return FxtTransactionType.SUBTYPE_CHILDCHAIN_BLOCK;
    }

    @Override
    public AccountLedger.LedgerEvent getLedgerEvent() {
        return AccountLedger.LedgerEvent.CHILD_BLOCK;
    }

    @Override
    public Attachment.AbstractAttachment parseAttachment(ByteBuffer buffer) throws NxtException.NotValidException {
        return new ChildBlockAttachment(buffer);
    }

    @Override
    public Attachment.AbstractAttachment parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
        return new ChildBlockAttachment(attachmentData);
    }

    @Override
    protected void validateAttachment(FxtTransactionImpl transaction) throws NxtException.ValidationException {
        ChildBlockAttachment attachment = (ChildBlockAttachment) transaction.getAttachment();
        ChildChain childChain = ChildChain.getChildChain(attachment.getChainId());
        if (childChain == null) {
            throw new NxtException.NotValidException("No such child chain id: " + attachment.getChainId());
        }
        byte[][] childTransactionHashes = attachment.getChildTransactionFullHashes();
        if (childTransactionHashes.length == 0) {
            throw new NxtException.NotValidException("Empty ChildBlock transaction");
        }
        //TODO: define child block transaction count and size limits
        if (childTransactionHashes.length > Constants.MAX_NUMBER_OF_TRANSACTIONS) {
            throw new NxtException.NotValidException("Too many child transactions: " + childTransactionHashes.length);
        }
        int blockchainHeight = Nxt.getBlockchain().getHeight();
        Set<Long> childIds = new HashSet<>();
        for (byte[] childTransactionHash : childTransactionHashes) {
            long childTransactionId = Convert.fullHashToId(childTransactionHash);
            if (!childIds.add(childTransactionId)) {
                throw new NxtException.NotValidException("Duplicate child transaction hash");
            }
            if (childChain.getTransactionHome().hasTransaction(childTransactionHash, childTransactionId, blockchainHeight)) {
                throw new NxtException.NotValidException("Child transaction already included at an earlier height");
            }
        }
        for (ChildTransactionImpl childTransaction : transaction.getChildTransactions()) {
            try {
                childTransaction.validate();
            } catch (NxtException.ValidationException e) {
                Logger.logDebugMessage("Validation failed for transaction " + JSON.toJSONString(childTransaction.getJSONObject()));
                throw e;
            }
            if (transaction.getTimestamp() < childTransaction.getTimestamp()) {
                throw new NxtException.NotValidException("ChildBlock transaction " + transaction.getStringId() + " timestamp " + transaction.getTimestamp()
                        + " is before child transaction " + childTransaction.getStringId() + " timestamp " + childTransaction.getTimestamp());
            }
            if (transaction.getExpiration() > childTransaction.getExpiration()) {
                throw new NxtException.NotValidException("ChildBlock transaction " + transaction.getStringId() + " expiration " + transaction.getExpiration()
                        + " is after child transaction " + childTransaction.getStringId() + " expiration " + childTransaction.getExpiration());
            }
        }
    }

    @Override
    protected boolean applyAttachmentUnconfirmed(FxtTransactionImpl transaction, Account senderAccount) {
        // child transactions applyAttachmentUnconfirmed called when they are accepted in the unconfirmed pool
        return true;
    }

    @Override
    protected void applyAttachment(FxtTransactionImpl transaction, Account senderAccount, Account recipientAccount) {
        ChildBlockAttachment attachment = (ChildBlockAttachment) transaction.getAttachment();
        long totalFee = 0;
        for (ChildTransactionImpl childTransaction : transaction.getChildTransactions()) {
            childTransaction.apply();
            totalFee = Math.addExact(totalFee, childTransaction.getFee());
        }
        senderAccount.addToBalanceAndUnconfirmedBalance(ChildChain.getChildChain(attachment.getChainId()),
                getLedgerEvent(), AccountLedger.newEventId(transaction), totalFee);
    }

    @Override
    protected void undoAttachmentUnconfirmed(FxtTransactionImpl transaction, Account senderAccount) {
        // child transactions undoAttachmentUnconfirmed called when they are removed from the unconfirmed pool
    }

    @Override
    public boolean canHaveRecipient() {
        return false;
    }

    @Override
    public String getName() {
        return "ChildChainBlock";
    }

    @Override
    public Fee getBaselineFee(Transaction transaction) {
        return CHILD_BLOCK_FEE;
    }

    @Override
    public boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
        ChildBlockAttachment attachment = (ChildBlockAttachment) transaction.getAttachment();
        return isDuplicate(ChildBlockFxtTransactionType.INSTANCE, String.valueOf(attachment.getChainId()), duplicates, true);
    }

}
