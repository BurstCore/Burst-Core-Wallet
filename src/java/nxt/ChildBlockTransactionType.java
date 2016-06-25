/******************************************************************************
 * Copyright © 2013-2016 The Nxt Core Developers.                             *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * Nxt software, including this file, may be copied, modified, propagated,    *
 * or distributed except according to the terms contained in the LICENSE.txt  *
 * file.                                                                      *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

package nxt;

import nxt.util.Convert;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;

//TODO
public final class ChildBlockTransactionType extends FxtTransactionType {

    public static final ChildBlockTransactionType INSTANCE = new ChildBlockTransactionType();

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
    Attachment.AbstractAttachment parseAttachment(ByteBuffer buffer) throws NxtException.NotValidException {
        return new ChildBlockAttachment(buffer);
    }

    @Override
    Attachment.AbstractAttachment parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
        return new ChildBlockAttachment(attachmentData);
    }

    @Override
    void validateAttachment(FxtTransactionImpl transaction) throws NxtException.ValidationException {
        ChildBlockAttachment attachment = (ChildBlockAttachment) transaction.getAttachment();
        if (ChildChain.getChildChain(attachment.getChainId()) == null) {
            throw new NxtException.NotValidException("No such child chain id: " + attachment.getChainId());
        }
        //TODO: its own validation?
        for (ChildTransactionImpl childTransaction : attachment.getChildTransactions(transaction)) {
            childTransaction.validate();
            if (transaction.getExpiration() > childTransaction.getExpiration()) {
                throw new NxtException.NotValidException("ChildBlock transaction " + transaction.getStringId() + " expiration " + transaction.getExpiration()
                        + " is after child transaction " + childTransaction.getStringId() + " expiration " + childTransaction.getExpiration());
            }
        }
    }

    @Override
    boolean applyAttachmentUnconfirmed(FxtTransactionImpl transaction, Account senderAccount) {
        // child transactions applyAttachmentUnconfirmed called when they are accepted in the unconfirmed pool
        return true;
    }

    @Override
    void applyAttachment(FxtTransactionImpl transaction, Account senderAccount, Account recipientAccount) {
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
    void undoAttachmentUnconfirmed(FxtTransactionImpl transaction, Account senderAccount) {
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
    long[] getBackFees(Transaction transaction) {
        //TODO
        return Convert.EMPTY_LONG;
    }

}
