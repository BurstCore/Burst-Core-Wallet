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

import org.json.simple.JSONObject;

import java.nio.ByteBuffer;

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
        return null;
    }

    @Override
    Attachment.AbstractAttachment parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
        return null;
    }

    @Override
    void validateAttachment(Transaction transaction) throws NxtException.ValidationException {

    }

    @Override
    boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        return false;
    }

    @Override
    void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {

    }

    @Override
    void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {

    }

    @Override
    public boolean canHaveRecipient() {
        return false;
    }

    @Override
    public String getName() {
        return "ChildChainBlock";
    }
}
