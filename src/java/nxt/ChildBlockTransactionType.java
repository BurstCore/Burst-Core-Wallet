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
import java.util.List;

//TODO
public final class ChildBlockTransactionType extends FxtTransactionType {

    public static final ChildBlockTransactionType instance = new ChildBlockTransactionType();

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
        for (ChildTransactionImpl childTransaction : attachment.getChildTransactions()) {
            childTransaction.validate();
        }
    }

    @Override
    boolean applyAttachmentUnconfirmed(FxtTransactionImpl transaction, Account senderAccount) {
        ChildBlockAttachment attachment = (ChildBlockAttachment) transaction.getAttachment();
        List<ChildTransactionImpl> childTransactions = attachment.getChildTransactions();
        for (int i = 0; i < childTransactions.size(); i++) {
            //TODO: if a child transaction is already in unconfirmed pool, should not call applyUnconfirmed on it again
            if (!childTransactions.get(i).applyUnconfirmed()) {
                for (int j = 0; j < i; j++) {
                    childTransactions.get(j).undoUnconfirmed();
                }
                return false;
            }
        }
        return true;
    }

    @Override
    void applyAttachment(FxtTransactionImpl transaction, Account senderAccount, Account recipientAccount) {
        ChildBlockAttachment attachment = (ChildBlockAttachment) transaction.getAttachment();
        //TODO: apply fees
        for (ChildTransactionImpl childTransaction : attachment.getChildTransactions()) {
            childTransaction.apply();
        }
    }

    @Override
    void undoAttachmentUnconfirmed(FxtTransactionImpl transaction, Account senderAccount) {
        ChildBlockAttachment attachment = (ChildBlockAttachment) transaction.getAttachment();
        //TODO: undo fees
        //TODO: do not undo if child transaction is not going to be expunged from unconfirmed pool
        for (ChildTransactionImpl childTransaction : attachment.getChildTransactions()) {
            childTransaction.undoUnconfirmed();
        }
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
