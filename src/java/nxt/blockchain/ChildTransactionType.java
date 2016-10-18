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

import nxt.account.Account;
import nxt.Constants;
import nxt.account.PaymentTransactionType;
import nxt.ae.AssetExchangeTransactionType;
import nxt.NxtException;
import nxt.dgs.DigitalGoodsTransactionType;
import nxt.messaging.MessagingTransactionType;
import nxt.messaging.TaggedDataTransactionType;
import nxt.ms.MonetarySystemTransactionType;
import nxt.shuffling.ShufflingTransactionType;
import nxt.voting.AccountControlTransactionType;

import java.util.Arrays;

public abstract class ChildTransactionType extends TransactionType {

    protected static final byte TYPE_PAYMENT = 0;
    protected static final byte TYPE_MESSAGING = 1;
    protected static final byte TYPE_ASSET_EXCHANGE = 2;
    protected static final byte TYPE_DIGITAL_GOODS = 3;
    protected static final byte TYPE_ACCOUNT_CONTROL = 4;
    protected static final byte TYPE_MONETARY_SYSTEM = 5;
    protected static final byte TYPE_DATA = 6;
    protected static final byte TYPE_SHUFFLING = 7;

    protected static final byte SUBTYPE_PAYMENT_ORDINARY_PAYMENT = 0;

    protected static final byte SUBTYPE_MESSAGING_ARBITRARY_MESSAGE = 0;
    protected static final byte SUBTYPE_MESSAGING_ALIAS_ASSIGNMENT = 1;
    protected static final byte SUBTYPE_MESSAGING_POLL_CREATION = 2;
    protected static final byte SUBTYPE_MESSAGING_VOTE_CASTING = 3;
    protected static final byte SUBTYPE_MESSAGING_HUB_ANNOUNCEMENT = 4;
    protected static final byte SUBTYPE_MESSAGING_ACCOUNT_INFO = 5;
    protected static final byte SUBTYPE_MESSAGING_ALIAS_SELL = 6;
    protected static final byte SUBTYPE_MESSAGING_ALIAS_BUY = 7;
    protected static final byte SUBTYPE_MESSAGING_ALIAS_DELETE = 8;
    protected static final byte SUBTYPE_MESSAGING_PHASING_VOTE_CASTING = 9;
    protected static final byte SUBTYPE_MESSAGING_ACCOUNT_PROPERTY = 10;
    protected static final byte SUBTYPE_MESSAGING_ACCOUNT_PROPERTY_DELETE = 11;

    protected static final byte SUBTYPE_ASSET_EXCHANGE_ASSET_ISSUANCE = 0;
    protected static final byte SUBTYPE_ASSET_EXCHANGE_ASSET_TRANSFER = 1;
    protected static final byte SUBTYPE_ASSET_EXCHANGE_ASK_ORDER_PLACEMENT = 2;
    protected static final byte SUBTYPE_ASSET_EXCHANGE_BID_ORDER_PLACEMENT = 3;
    protected static final byte SUBTYPE_ASSET_EXCHANGE_ASK_ORDER_CANCELLATION = 4;
    protected static final byte SUBTYPE_ASSET_EXCHANGE_BID_ORDER_CANCELLATION = 5;
    protected static final byte SUBTYPE_ASSET_EXCHANGE_DIVIDEND_PAYMENT = 6;
    protected static final byte SUBTYPE_ASSET_EXCHANGE_ASSET_DELETE = 7;

    protected static final byte SUBTYPE_DIGITAL_GOODS_LISTING = 0;
    protected static final byte SUBTYPE_DIGITAL_GOODS_DELISTING = 1;
    protected static final byte SUBTYPE_DIGITAL_GOODS_PRICE_CHANGE = 2;
    protected static final byte SUBTYPE_DIGITAL_GOODS_QUANTITY_CHANGE = 3;
    protected static final byte SUBTYPE_DIGITAL_GOODS_PURCHASE = 4;
    protected static final byte SUBTYPE_DIGITAL_GOODS_DELIVERY = 5;
    protected static final byte SUBTYPE_DIGITAL_GOODS_FEEDBACK = 6;
    protected static final byte SUBTYPE_DIGITAL_GOODS_REFUND = 7;

    protected static final byte SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING = 0;
    protected static final byte SUBTYPE_ACCOUNT_CONTROL_PHASING_ONLY = 1;

    protected static final byte SUBTYPE_DATA_TAGGED_DATA_UPLOAD = 0;
    protected static final byte SUBTYPE_DATA_TAGGED_DATA_EXTEND = 1;

    public static TransactionType findTransactionType(byte type, byte subtype) {
        switch (type) {
            case TYPE_PAYMENT:
                switch (subtype) {
                    case SUBTYPE_PAYMENT_ORDINARY_PAYMENT:
                        return PaymentTransactionType.ORDINARY;
                    default:
                        return null;
                }
            case TYPE_MESSAGING:
                switch (subtype) {
                    case SUBTYPE_MESSAGING_ARBITRARY_MESSAGE:
                        return MessagingTransactionType.ARBITRARY_MESSAGE;
                    case SUBTYPE_MESSAGING_ALIAS_ASSIGNMENT:
                        return MessagingTransactionType.ALIAS_ASSIGNMENT;
                    case SUBTYPE_MESSAGING_POLL_CREATION:
                        return MessagingTransactionType.POLL_CREATION;
                    case SUBTYPE_MESSAGING_VOTE_CASTING:
                        return MessagingTransactionType.VOTE_CASTING;
                    case SUBTYPE_MESSAGING_HUB_ANNOUNCEMENT:
                        throw new IllegalArgumentException("Hub Announcement no longer supported");
                    case SUBTYPE_MESSAGING_ACCOUNT_INFO:
                        return MessagingTransactionType.ACCOUNT_INFO;
                    case SUBTYPE_MESSAGING_ALIAS_SELL:
                        return MessagingTransactionType.ALIAS_SELL;
                    case SUBTYPE_MESSAGING_ALIAS_BUY:
                        return MessagingTransactionType.ALIAS_BUY;
                    case SUBTYPE_MESSAGING_ALIAS_DELETE:
                        return MessagingTransactionType.ALIAS_DELETE;
                    case SUBTYPE_MESSAGING_PHASING_VOTE_CASTING:
                        return MessagingTransactionType.PHASING_VOTE_CASTING;
                    case SUBTYPE_MESSAGING_ACCOUNT_PROPERTY:
                        return MessagingTransactionType.ACCOUNT_PROPERTY;
                    case SUBTYPE_MESSAGING_ACCOUNT_PROPERTY_DELETE:
                        return MessagingTransactionType.ACCOUNT_PROPERTY_DELETE;
                    default:
                        return null;
                }
            case TYPE_ASSET_EXCHANGE:
                switch (subtype) {
                    case SUBTYPE_ASSET_EXCHANGE_ASSET_ISSUANCE:
                        return AssetExchangeTransactionType.ASSET_ISSUANCE;
                    case SUBTYPE_ASSET_EXCHANGE_ASSET_TRANSFER:
                        return AssetExchangeTransactionType.ASSET_TRANSFER;
                    case SUBTYPE_ASSET_EXCHANGE_ASK_ORDER_PLACEMENT:
                        return AssetExchangeTransactionType.ASK_ORDER_PLACEMENT;
                    case SUBTYPE_ASSET_EXCHANGE_BID_ORDER_PLACEMENT:
                        return AssetExchangeTransactionType.BID_ORDER_PLACEMENT;
                    case SUBTYPE_ASSET_EXCHANGE_ASK_ORDER_CANCELLATION:
                        return AssetExchangeTransactionType.ASK_ORDER_CANCELLATION;
                    case SUBTYPE_ASSET_EXCHANGE_BID_ORDER_CANCELLATION:
                        return AssetExchangeTransactionType.BID_ORDER_CANCELLATION;
                    case SUBTYPE_ASSET_EXCHANGE_DIVIDEND_PAYMENT:
                        return AssetExchangeTransactionType.DIVIDEND_PAYMENT;
                    case SUBTYPE_ASSET_EXCHANGE_ASSET_DELETE:
                        return AssetExchangeTransactionType.ASSET_DELETE;
                    default:
                        return null;
                }
            case TYPE_DIGITAL_GOODS:
                switch (subtype) {
                    case SUBTYPE_DIGITAL_GOODS_LISTING:
                        return DigitalGoodsTransactionType.LISTING;
                    case SUBTYPE_DIGITAL_GOODS_DELISTING:
                        return DigitalGoodsTransactionType.DELISTING;
                    case SUBTYPE_DIGITAL_GOODS_PRICE_CHANGE:
                        return DigitalGoodsTransactionType.PRICE_CHANGE;
                    case SUBTYPE_DIGITAL_GOODS_QUANTITY_CHANGE:
                        return DigitalGoodsTransactionType.QUANTITY_CHANGE;
                    case SUBTYPE_DIGITAL_GOODS_PURCHASE:
                        return DigitalGoodsTransactionType.PURCHASE;
                    case SUBTYPE_DIGITAL_GOODS_DELIVERY:
                        return DigitalGoodsTransactionType.DELIVERY;
                    case SUBTYPE_DIGITAL_GOODS_FEEDBACK:
                        return DigitalGoodsTransactionType.FEEDBACK;
                    case SUBTYPE_DIGITAL_GOODS_REFUND:
                        return DigitalGoodsTransactionType.REFUND;
                    default:
                        return null;
                }
            case TYPE_ACCOUNT_CONTROL:
                switch (subtype) {
                    case SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING:
                        throw new IllegalArgumentException("Effective balance leasing is an Fxt transaction type now");
                    case SUBTYPE_ACCOUNT_CONTROL_PHASING_ONLY:
                        return AccountControlTransactionType.SET_PHASING_ONLY;
                    default:
                        return null;
                }
            case TYPE_MONETARY_SYSTEM:
                return MonetarySystemTransactionType.findTransactionType(subtype);
            case TYPE_DATA:
                switch (subtype) {
                    case SUBTYPE_DATA_TAGGED_DATA_UPLOAD:
                        return TaggedDataTransactionType.TAGGED_DATA_UPLOAD;
                    case SUBTYPE_DATA_TAGGED_DATA_EXTEND:
                        return TaggedDataTransactionType.TAGGED_DATA_EXTEND;
                    default:
                        return null;
                }
            case TYPE_SHUFFLING:
                return ShufflingTransactionType.findTransactionType(subtype);
            default:
                return null;
        }
    }

    @Override
    public final boolean applyUnconfirmed(TransactionImpl transaction, Account senderAccount) {
        ChildChain childChain = (ChildChain) transaction.getChain();
        long amount = transaction.getAmount();
        long fee = transaction.getFee();
        long deposit = 0;
        if (((ChildTransactionImpl)transaction).referencedTransactionFullHash() != null) {
            if (senderAccount.getUnconfirmedBalanceFQT() < Constants.UNCONFIRMED_POOL_DEPOSIT_FQT) {
                return false;
            }
            deposit = Constants.UNCONFIRMED_POOL_DEPOSIT_FQT;
            senderAccount.addToUnconfirmedBalanceFQT(getLedgerEvent(), transaction.getId(), 0, -deposit);
        }
        long totalAmount = Math.addExact(amount, fee);
        if (senderAccount.getUnconfirmedBalance(childChain) < totalAmount
                && !(transaction.getTimestamp() == 0 && Arrays.equals(transaction.getSenderPublicKey(), Genesis.CREATOR_PUBLIC_KEY))) {
            senderAccount.addToUnconfirmedBalanceFQT(getLedgerEvent(), transaction.getId(), 0, deposit);
            return false;
        }
        senderAccount.addToUnconfirmedBalance(childChain, getLedgerEvent(), transaction.getId(), -amount, -fee);
        if (!applyAttachmentUnconfirmed(transaction, senderAccount)) {
            senderAccount.addToUnconfirmedBalance(childChain, getLedgerEvent(), transaction.getId(), amount, fee);
            senderAccount.addToUnconfirmedBalanceFQT(getLedgerEvent(), transaction.getId(), 0, deposit);
            return false;
        }
        return true;
    }

    @Override
    public final void apply(TransactionImpl transaction, Account senderAccount, Account recipientAccount) {
        ChildChain childChain = (ChildChain) transaction.getChain();
        long amount = transaction.getAmount();
        long transactionId = transaction.getId();
        if (!transaction.attachmentIsPhased()) {
            senderAccount.addToBalance(childChain, getLedgerEvent(), transactionId, -amount, -transaction.getFee());
        } else {
            senderAccount.addToBalance(childChain, getLedgerEvent(), transactionId, -amount);
        }
        if (recipientAccount != null) {
            recipientAccount.addToBalanceAndUnconfirmedBalance(childChain, getLedgerEvent(), transactionId, amount);
        }
        applyAttachment(transaction, senderAccount, recipientAccount);
    }

    @Override
    public final void undoUnconfirmed(TransactionImpl transaction, Account senderAccount) {
        ChildChain childChain = (ChildChain) transaction.getChain();
        undoAttachmentUnconfirmed(transaction, senderAccount);
        senderAccount.addToUnconfirmedBalance(childChain, getLedgerEvent(), transaction.getId(),
                transaction.getAmount(), transaction.getFee());
        if (((ChildTransactionImpl)transaction).referencedTransactionFullHash() != null) {
            senderAccount.addToUnconfirmedBalanceFQT(getLedgerEvent(), transaction.getId(), 0,
                    Constants.UNCONFIRMED_POOL_DEPOSIT_FQT);
        }
    }

    @Override
    public final void validateAttachment(Transaction transaction) throws NxtException.ValidationException {
        validateAttachment((ChildTransactionImpl)transaction);
    }

    public abstract void validateAttachment(ChildTransactionImpl transaction) throws NxtException.ValidationException;

    @Override
    public final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        return applyAttachmentUnconfirmed((ChildTransactionImpl)transaction, senderAccount);
    }

    public abstract boolean applyAttachmentUnconfirmed(ChildTransactionImpl transaction, Account senderAccount);

    @Override
    public final void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        applyAttachment((ChildTransactionImpl)transaction, senderAccount, recipientAccount);
    }

    public abstract void applyAttachment(ChildTransactionImpl transaction, Account senderAccount, Account recipientAccount);

    @Override
    public final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        undoAttachmentUnconfirmed((ChildTransactionImpl)transaction, senderAccount);
    }

    public abstract void undoAttachmentUnconfirmed(ChildTransactionImpl transaction, Account senderAccount);


}
