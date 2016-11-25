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

import nxt.NxtException;
import nxt.account.Account;
import nxt.account.AccountControlFxtTransactionType;
import nxt.account.PaymentFxtTransactionType;

public abstract class FxtTransactionType extends TransactionType {

    protected static final byte TYPE_CHILDCHAIN_BLOCK = -1;
    protected static final byte TYPE_PAYMENT = -2;
    protected static final byte TYPE_ACCOUNT_CONTROL = -3;

    protected static final byte SUBTYPE_CHILDCHAIN_BLOCK = 0;

    protected static final byte SUBTYPE_PAYMENT_ORDINARY_PAYMENT = 0;

    protected static final byte SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING = 0;

    public static TransactionType findTransactionType(byte type, byte subtype) {
        switch (type) {
            case TYPE_CHILDCHAIN_BLOCK:
                switch (subtype) {
                    case SUBTYPE_CHILDCHAIN_BLOCK:
                        return ChildBlockTransactionType.INSTANCE;
                    default:
                        return null;
                }
            case TYPE_PAYMENT:
                switch (subtype) {
                    case SUBTYPE_PAYMENT_ORDINARY_PAYMENT:
                        return PaymentFxtTransactionType.ORDINARY;
                    default:
                        return null;
                }
            case TYPE_ACCOUNT_CONTROL:
                switch (subtype) {
                    case SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING:
                        return AccountControlFxtTransactionType.EFFECTIVE_BALANCE_LEASING;
                    default:
                        return null;
                }
            default:
                return null;
        }
    }

    @Override
    public final boolean applyUnconfirmed(TransactionImpl transaction, Account senderAccount) {
        long amount = transaction.getAmount();
        long fee = transaction.getFee();
        long totalAmount = Math.addExact(amount, fee);
        if (senderAccount.getUnconfirmedBalanceFQT() < totalAmount) {
            return false;
        }
        senderAccount.addToUnconfirmedBalanceFQT(getLedgerEvent(), transaction.getId(), -amount, -fee);
        if (!applyAttachmentUnconfirmed(transaction, senderAccount)) {
            senderAccount.addToUnconfirmedBalanceFQT(getLedgerEvent(), transaction.getId(), amount, fee);
            return false;
        }
        return true;
    }

    @Override
    public final void apply(TransactionImpl transaction, Account senderAccount, Account recipientAccount) {
        long amount = transaction.getAmount();
        long transactionId = transaction.getId();
        //if (!transaction.attachmentIsPhased()) {
        senderAccount.addToBalanceFQT(getLedgerEvent(), transactionId, -amount, -transaction.getFee());
        /* never phased
        } else {
            senderAccount.addToBalanceFQT(getLedgerEvent(), transactionId, -amount);
        }
        */
        if (recipientAccount != null) {
            recipientAccount.addToBalanceAndUnconfirmedBalanceFQT(getLedgerEvent(), transactionId, amount);
        }
        applyAttachment(transaction, senderAccount, recipientAccount);
    }

    @Override
    public final void undoUnconfirmed(TransactionImpl transaction, Account senderAccount) {
        undoAttachmentUnconfirmed(transaction, senderAccount);
        senderAccount.addToUnconfirmedBalanceFQT(getLedgerEvent(), transaction.getId(),
                transaction.getAmount(), transaction.getFee());
    }

    @Override
    public final void validateAttachment(Transaction transaction) throws NxtException.ValidationException {
        validateAttachment((FxtTransactionImpl)transaction);
    }

    protected abstract void validateAttachment(FxtTransactionImpl transaction) throws NxtException.ValidationException;

    @Override
    protected void validateId(Transaction transaction) throws NxtException.ValidationException {
        throw new UnsupportedOperationException("Should not need to validate FxtTransaction ids");
    }

    @Override
    public final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        return applyAttachmentUnconfirmed((FxtTransactionImpl)transaction, senderAccount);
    }

    protected abstract boolean applyAttachmentUnconfirmed(FxtTransactionImpl transaction, Account senderAccount);

    @Override
    public final void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        applyAttachment((FxtTransactionImpl)transaction, senderAccount, recipientAccount);
    }

    protected abstract void applyAttachment(FxtTransactionImpl transaction, Account senderAccount, Account recipientAccount);

    @Override
    public final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        undoAttachmentUnconfirmed((FxtTransactionImpl)transaction, senderAccount);
    }

    protected abstract void undoAttachmentUnconfirmed(FxtTransactionImpl transaction, Account senderAccount);

    //TODO: remove?
    @Override
    public final boolean isPhasingSafe() {
        return true;
    }


}
