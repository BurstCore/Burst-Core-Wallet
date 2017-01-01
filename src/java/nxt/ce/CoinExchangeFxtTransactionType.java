/*
 * Copyright © 2016-2017 Jelurida IP B.V.
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
package nxt.ce;

import nxt.Constants;
import nxt.NxtException;
import nxt.account.Account;
import nxt.account.AccountLedger;
import nxt.account.BalanceHome;
import nxt.blockchain.Chain;
import nxt.blockchain.Fee;
import nxt.blockchain.FxtChain;
import nxt.blockchain.FxtTransactionImpl;
import nxt.blockchain.FxtTransactionType;
import nxt.blockchain.Transaction;
import nxt.blockchain.TransactionType;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Coin exchange transaction types for the Fxt chain
 */
public abstract class CoinExchangeFxtTransactionType extends FxtTransactionType {

    private static final byte SUBTYPE_COIN_EXCHANGE_ORDER_ISSUE = 0;
    private static final byte SUBTYPE_COIN_EXCHANGE_ORDER_CANCEL = 1;

    private static final Fee exchangeFee = new Fee.ConstantFee(Constants.ONE_FXT * 2);

    public static TransactionType findTransactionType(byte subtype) {
        switch (subtype) {
            case SUBTYPE_COIN_EXCHANGE_ORDER_ISSUE:
                return CoinExchangeFxtTransactionType.ORDER_ISSUE;
            case SUBTYPE_COIN_EXCHANGE_ORDER_CANCEL:
                return CoinExchangeFxtTransactionType.ORDER_CANCEL;
            default:
                return null;
        }
    }

    private CoinExchangeFxtTransactionType() {}

    @Override
    public final byte getType() {
        return FxtTransactionType.TYPE_COIN_EXCHANGE;
    }

    @Override
    public Fee getBaselineFee(Transaction tx) {
        return exchangeFee;
    }

    /**
     * COIN_EXCHANGE_ORDER_ISSUE transaction type
     */
    public static final TransactionType ORDER_ISSUE = new CoinExchangeFxtTransactionType() {

        @Override
        public final byte getSubtype() {
            return SUBTYPE_COIN_EXCHANGE_ORDER_ISSUE;
        }

        @Override
        public AccountLedger.LedgerEvent getLedgerEvent() {
            return AccountLedger.LedgerEvent.COIN_EXCHANGE_ORDER_ISSUE;
        }

        @Override
        public String getName() {
            return "FxtCoinExchangeOrderIssue";
        }

        @Override
        public OrderIssueFxtAttachment parseAttachment(ByteBuffer buffer) throws NxtException.NotValidException {
            return new OrderIssueFxtAttachment(buffer);
        }

        @Override
        public OrderIssueFxtAttachment parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
            return new OrderIssueFxtAttachment(attachmentData);
        }

        @Override
        public boolean applyAttachmentUnconfirmed(FxtTransactionImpl transaction, Account senderAccount) {
            OrderIssueAttachment attachment = (OrderIssueAttachment)transaction.getAttachment();
            BalanceHome.Balance balance = attachment.getChain().getBalanceHome().getBalance(senderAccount.getId());
            if (balance.getUnconfirmedBalance() >= attachment.getQuantityQNT()) {
                balance.addToUnconfirmedBalance(getLedgerEvent(), AccountLedger.newEventId(transaction), -attachment.getQuantityQNT());
                return true;
            }
            return false;
        }

        @Override
        public void undoAttachmentUnconfirmed(FxtTransactionImpl transaction, Account senderAccount) {
            OrderIssueAttachment attachment = (OrderIssueAttachment)transaction.getAttachment();
            BalanceHome.Balance balance = attachment.getChain().getBalanceHome().getBalance(senderAccount.getId());
            balance.addToUnconfirmedBalance(getLedgerEvent(), AccountLedger.newEventId(transaction), attachment.getQuantityQNT());
        }

        @Override
        public void applyAttachment(FxtTransactionImpl transaction, Account senderAccount, Account recipientAccount) {
            OrderIssueAttachment attachment = (OrderIssueAttachment)transaction.getAttachment();
            CoinExchange.addOrder(transaction, attachment);
        }

        @Override
        public final void validateAttachment(FxtTransactionImpl transaction) throws NxtException.ValidationException {
            OrderIssueAttachment attachment = (OrderIssueAttachment)transaction.getAttachment();
            if (attachment.getQuantityQNT() <= 0 || attachment.getQuantityQNT() > Constants.MAX_BALANCE_NQT ||
                    attachment.getPriceNQT() <= 0 || attachment.getPriceNQT() > Constants.MAX_BALANCE_NQT) {
                throw new NxtException.NotValidException("Invalid coin exchange order: " + attachment.getJSONObject());
            }
            if (attachment.getChain() != FxtChain.FXT && attachment.getExchangeChain() != FxtChain.FXT) {
                throw new NxtException.NotValidException("Only exchange orders to/from Ardor may be submitted on the Fxt chain");
            }
            if (attachment.getChain() == attachment.getExchangeChain()) {
                throw new NxtException.NotValidException("Coin exchange order chain and exchange chain must be different: " + attachment.getJSONObject());
            }
        }

        @Override
        protected void validateId(FxtTransactionImpl transaction) throws NxtException.NotCurrentlyValidException {
            if (CoinExchange.getOrder(transaction.getId()) != null) {
                throw new NxtException.NotCurrentlyValidException(
                        "Duplicate coin exchange order id " + transaction.getStringId());
            }
        }

        @Override
        public final boolean canHaveRecipient() {
            return false;
        }
    };

    /**
     * COIN_EXCHANGE_ORDER_CANCEL transaction type
     */
    public static final TransactionType ORDER_CANCEL = new CoinExchangeFxtTransactionType() {

        @Override
        public final byte getSubtype() {
            return SUBTYPE_COIN_EXCHANGE_ORDER_CANCEL;
        }

        @Override
        public AccountLedger.LedgerEvent getLedgerEvent() {
            return AccountLedger.LedgerEvent.COIN_EXCHANGE_ORDER_CANCEL;
        }

        @Override
        public String getName() {
            return "FxtCoinExchangeOrderCancel";
        }

        @Override
        public OrderCancelFxtAttachment parseAttachment(ByteBuffer buffer) throws NxtException.NotValidException {
            return new OrderCancelFxtAttachment(buffer);
        }

        @Override
        public OrderCancelFxtAttachment parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
            return new OrderCancelFxtAttachment(attachmentData);
        }

        @Override
        public boolean applyAttachmentUnconfirmed(FxtTransactionImpl transaction, Account senderAccount) {
            return true;
        }

        @Override
        public void undoAttachmentUnconfirmed(FxtTransactionImpl transaction, Account senderAccount) {
        }

        @Override
        public boolean isUnconfirmedDuplicate(Transaction transaction,
                                              Map<TransactionType, Map<String, Integer>> duplicates) {
            OrderCancelAttachment attachment = (OrderCancelAttachment)transaction.getAttachment();
            return TransactionType.isDuplicate(CoinExchangeTransactionType.ORDER_CANCEL,
                    Long.toUnsignedString(attachment.getOrderId()), duplicates, true);
        }

        @Override
        public void applyAttachment(FxtTransactionImpl transaction, Account senderAccount, Account recipientAccount) {
            OrderCancelAttachment attachment = (OrderCancelAttachment)transaction.getAttachment();
            CoinExchange.Order order = CoinExchange.getOrder(attachment.getOrderId());
            if (order != null) {
                CoinExchange.removeOrder(attachment.getOrderId());
                BalanceHome.Balance balance = Chain.getChain(order.getChainId()).getBalanceHome().getBalance(senderAccount.getId());
                balance.addToUnconfirmedBalance(AccountLedger.LedgerEvent.COIN_EXCHANGE_ORDER_CANCEL, AccountLedger.newEventId(transaction),
                        order.getQuantity());
            }
        }

        @Override
        public final void validateAttachment(FxtTransactionImpl transaction) throws NxtException.ValidationException {
            OrderCancelAttachment attachment = (OrderCancelAttachment)transaction.getAttachment();
            CoinExchange.Order order = CoinExchange.getOrder(attachment.getOrderId());
            if (order == null) {
                throw new NxtException.NotCurrentlyValidException(
                        "Invalid coin exchange order: " + Long.toUnsignedString(attachment.getOrderId()));
            }
            if (order.getAccountId() != transaction.getSenderId()) {
                throw new NxtException.NotValidException(
                        "Order " + Long.toUnsignedString(order.getId())
                        + " was created by account "
                        + Long.toUnsignedString(order.getAccountId()));
            }
            if (order.getChainId() != FxtChain.FXT.getId() && order.getExchangeId() != FxtChain.FXT.getId()) {
                throw new NxtException.NotValidException("Only cancellations of orders to/from Ardor may be submitted on the Fxt chain");
            }
        }

        @Override
        public final boolean canHaveRecipient() {
            return false;
        }
    };
}
