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

    static final byte TYPE_CHILDCHAIN_BLOCK = -1;
    private static final byte TYPE_PAYMENT = -2;
    private static final byte TYPE_ACCOUNT_CONTROL = -3;

    static final byte SUBTYPE_CHILDCHAIN_BLOCK = 0;

    private static final byte SUBTYPE_PAYMENT_ORDINARY_PAYMENT = 0;

    private static final byte SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING = 0;

    public static TransactionType findTransactionType(byte type, byte subtype) {
        switch (type) {
            case TYPE_CHILDCHAIN_BLOCK:
                switch (subtype) {
                    case SUBTYPE_CHILDCHAIN_BLOCK:
                        return ChildBlockTransactionType.instance;
                    default:
                        return null;
                }
            case TYPE_PAYMENT:
                switch (subtype) {
                    case SUBTYPE_PAYMENT_ORDINARY_PAYMENT:
                        return FxtTransactionType.Payment.ORDINARY;
                    default:
                        return null;
                }
            case TYPE_ACCOUNT_CONTROL:
                switch (subtype) {
                    case SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING:
                        return AccountControl.EFFECTIVE_BALANCE_LEASING;
                    default:
                        return null;
                }
            default:
                return null;
        }
    }

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

    @Override
    final void validateAttachment(Transaction transaction) throws NxtException.ValidationException {
        validateAttachment((FxtTransactionImpl)transaction);
    }

    abstract void validateAttachment(FxtTransactionImpl transaction) throws NxtException.ValidationException;

    @Override
    final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        return applyAttachmentUnconfirmed((FxtTransactionImpl)transaction, senderAccount);
    }

    abstract boolean applyAttachmentUnconfirmed(FxtTransactionImpl transaction, Account senderAccount);

    @Override
    final void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        applyAttachment((FxtTransactionImpl)transaction, senderAccount, recipientAccount);
    }

    abstract void applyAttachment(FxtTransactionImpl transaction, Account senderAccount, Account recipientAccount);

    @Override
    final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        undoAttachmentUnconfirmed((FxtTransactionImpl)transaction, senderAccount);
    }

    abstract void undoAttachmentUnconfirmed(FxtTransactionImpl transaction, Account senderAccount);

    //TODO: remove?
    @Override
    public final boolean isPhasingSafe() {
        return true;
    }


    public static abstract class Payment extends FxtTransactionType {

        private Payment() {
        }

        @Override
        public final byte getType() {
            return FxtTransactionType.TYPE_PAYMENT;
        }

        @Override
        final boolean applyAttachmentUnconfirmed(FxtTransactionImpl transaction, Account senderAccount) {
            return true;
        }

        @Override
        final void applyAttachment(FxtTransactionImpl transaction, Account senderAccount, Account recipientAccount) {
        }

        @Override
        final void undoAttachmentUnconfirmed(FxtTransactionImpl transaction, Account senderAccount) {
        }

        @Override
        public final boolean canHaveRecipient() {
            return true;
        }

        public static final TransactionType ORDINARY = new Payment() {

            @Override
            public final byte getSubtype() {
                return FxtTransactionType.SUBTYPE_PAYMENT_ORDINARY_PAYMENT;
            }

            @Override
            public final AccountLedger.LedgerEvent getLedgerEvent() {
                //TODO
                return null;
            }

            @Override
            public String getName() {
                return "FxtPayment";
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
            void validateAttachment(FxtTransactionImpl transaction) throws NxtException.ValidationException {
                if (transaction.getAmount() <= 0 || transaction.getAmount() >= Constants.MAX_BALANCE_NQT) {
                    throw new NxtException.NotValidException("Invalid ordinary payment");
                }
            }

        };

    }

    public static abstract class AccountControl extends FxtTransactionType {

        private AccountControl() {
        }

        @Override
        public final byte getType() {
            return FxtTransactionType.TYPE_ACCOUNT_CONTROL;
        }

        @Override
        final boolean applyAttachmentUnconfirmed(FxtTransactionImpl transaction, Account senderAccount) {
            return true;
        }

        @Override
        final void undoAttachmentUnconfirmed(FxtTransactionImpl transaction, Account senderAccount) {
        }

        public static final TransactionType EFFECTIVE_BALANCE_LEASING = new AccountControl() {

            @Override
            public final byte getSubtype() {
                return FxtTransactionType.SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING;
            }

            @Override
            public AccountLedger.LedgerEvent getLedgerEvent() {
                return AccountLedger.LedgerEvent.ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING;
            }

            @Override
            public String getName() {
                return "EffectiveBalanceLeasing";
            }

            @Override
            Attachment.AccountControlEffectiveBalanceLeasing parseAttachment(ByteBuffer buffer) throws NxtException.NotValidException {
                //TODO?
                return new Attachment.AccountControlEffectiveBalanceLeasing(buffer);
            }

            @Override
            Attachment.AccountControlEffectiveBalanceLeasing parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
                return new Attachment.AccountControlEffectiveBalanceLeasing(attachmentData);
            }

            @Override
            void applyAttachment(FxtTransactionImpl transaction, Account senderAccount, Account recipientAccount) {
                Attachment.AccountControlEffectiveBalanceLeasing attachment = (Attachment.AccountControlEffectiveBalanceLeasing) transaction.getAttachment();
                Account.getAccount(transaction.getSenderId()).leaseEffectiveBalance(transaction.getRecipientId(), attachment.getPeriod());
            }

            @Override
            void validateAttachment(FxtTransactionImpl transaction) throws NxtException.ValidationException {
                Attachment.AccountControlEffectiveBalanceLeasing attachment = (Attachment.AccountControlEffectiveBalanceLeasing) transaction.getAttachment();
                if (transaction.getSenderId() == transaction.getRecipientId()) {
                    throw new NxtException.NotValidException("Account cannot lease balance to itself");
                }
                if (transaction.getAmount() != 0) {
                    throw new NxtException.NotValidException("Transaction amount must be 0 for effective balance leasing");
                }
                if (attachment.getPeriod() < Constants.LEASING_DELAY || attachment.getPeriod() > 65535) {
                    throw new NxtException.NotValidException("Invalid effective balance leasing period: " + attachment.getPeriod());
                }
                byte[] recipientPublicKey = Account.getPublicKey(transaction.getRecipientId());
                if (recipientPublicKey == null) {
                    throw new NxtException.NotValidException("Invalid effective balance leasing: "
                            + " recipient account " + Long.toUnsignedString(transaction.getRecipientId()) + " not found or no public key published");
                }
                if (transaction.getRecipientId() == Genesis.CREATOR_ID) {
                    throw new NxtException.NotValidException("Leasing to Genesis account not allowed");
                }
            }

            @Override
            public boolean canHaveRecipient() {
                return true;
            }

        };
    }

}
