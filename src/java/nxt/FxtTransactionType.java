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
import java.util.Arrays;

public abstract class FxtTransactionType extends TransactionType {

    private static final byte TYPE_PAYMENT = 0;
    private static final byte SUBTYPE_PAYMENT_ORDINARY_PAYMENT = 0;

    @Override
    final boolean applyUnconfirmed(TransactionImpl transaction, Account senderAccount) {
        long amount = transaction.getAmount();
        long fee = transaction.getFee();
        long totalAmount = Math.addExact(amount, fee);
        if (senderAccount.getUnconfirmedBalanceFQT() < totalAmount
                && !(transaction.getTimestamp() == 0 && Arrays.equals(transaction.getSenderPublicKey(), Genesis.CREATOR_PUBLIC_KEY))) {
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
    final void apply(TransactionImpl transaction, Account senderAccount, Account recipientAccount) {
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
    final void undoUnconfirmed(TransactionImpl transaction, Account senderAccount) {
        undoAttachmentUnconfirmed(transaction, senderAccount);
        senderAccount.addToUnconfirmedBalanceFQT(getLedgerEvent(), transaction.getId(),
                transaction.getAmount(), transaction.getFee());
    }



    public static abstract class Payment extends FxtTransactionType {

        private Payment() {
        }

        @Override
        public final byte getType() {
            return FxtTransactionType.TYPE_PAYMENT;
        }

        @Override
        final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            return true;
        }

        @Override
        final void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        }

        @Override
        final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        }

        @Override
        public final boolean canHaveRecipient() {
            return true;
        }

        @Override
        public final boolean isPhasingSafe() {
            return true;
        }

        public static final TransactionType ORDINARY = new Payment() {

            @Override
            public final byte getSubtype() {
                return FxtTransactionType.SUBTYPE_PAYMENT_ORDINARY_PAYMENT;
            }

            @Override
            public final AccountLedger.LedgerEvent getLedgerEvent() {
                return AccountLedger.LedgerEvent.ORDINARY_PAYMENT;
            }

            @Override
            public String getName() {
                return "OrdinaryPayment";
            }

            @Override
            Attachment.EmptyAttachment parseAttachment(ByteBuffer buffer) throws NxtException.NotValidException {
                //TODO: need different attachment
                return Attachment.ORDINARY_PAYMENT;
            }

            @Override
            Attachment.EmptyAttachment parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
                return Attachment.ORDINARY_PAYMENT;
            }

            @Override
            void validateAttachment(Transaction transaction) throws NxtException.ValidationException {
                if (transaction.getAmount() <= 0 || transaction.getAmount() >= Constants.MAX_BALANCE_NQT) {
                    throw new NxtException.NotValidException("Invalid ordinary payment");
                }
            }

        };

    }

}
