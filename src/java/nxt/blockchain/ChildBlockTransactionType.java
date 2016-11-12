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

import nxt.Nxt;
import nxt.NxtException;
import nxt.account.Account;
import nxt.account.AccountLedger;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;

//TODO
public final class ChildBlockTransactionType extends FxtTransactionType {

    public static final ChildBlockTransactionType INSTANCE = new ChildBlockTransactionType();

    private static final Fee CHILD_BLOCK_FEE = new Fee() {
        @Override
        public long getFee(TransactionImpl transaction, Appendix appendage) {
            ChildBlockAttachment attachment = (ChildBlockAttachment) transaction.getAttachment();
            long totalFee = 0;
            int blockchainHeight = Nxt.getBlockchain().getHeight();
            for (ChildTransactionImpl childTransaction : attachment.getChildTransactions((FxtTransactionImpl)transaction)) {
                totalFee += childTransaction.getMinimumFeeFQT(blockchainHeight);
            }
            return totalFee;
        }
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
        //TODO
        return null;
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
        if (ChildChain.getChildChain(attachment.getChainId()) == null) {
            throw new NxtException.NotValidException("No such child chain id: " + attachment.getChainId());
        }
        //TODO: its own validation?
        long[] minBackFees = new long[3];
        int blockchainHeight = Nxt.getBlockchain().getHeight();
        for (ChildTransactionImpl childTransaction : attachment.getChildTransactions(transaction)) {
            childTransaction.validate();
            if (transaction.getExpiration() > childTransaction.getExpiration()) {
                throw new NxtException.NotValidException("ChildBlock transaction " + transaction.getStringId() + " expiration " + transaction.getExpiration()
                        + " is after child transaction " + childTransaction.getStringId() + " expiration " + childTransaction.getExpiration());
            }
            long[] childMinBackFees = childTransaction.getMinimumBackFeesFQT(blockchainHeight);
            for (int i = 0; i < childMinBackFees.length; i++) {
                minBackFees[i] += childMinBackFees[i];
            }
        }
        long[] backFees = attachment.getBackFees();
        for (int i = 0; i < minBackFees.length; i++) {
            if (i >= backFees.length || backFees[i] < minBackFees[i]) {
                throw new NxtException.NotValidException("Insufficient back fees");
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
        for (ChildTransactionImpl childTransaction : attachment.getChildTransactions(transaction)) {
            childTransaction.apply();
            totalFee = Math.addExact(totalFee, childTransaction.getFee());
        }
        //TODO: new account ledger event type
        senderAccount.addToBalanceAndUnconfirmedBalance(ChildChain.getChildChain(attachment.getChainId()),
                AccountLedger.LedgerEvent.BLOCK_GENERATED, transaction.getId(), totalFee);
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

}
