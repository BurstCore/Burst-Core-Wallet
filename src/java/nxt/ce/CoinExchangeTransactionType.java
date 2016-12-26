/*
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
 */
package nxt.ce;

import nxt.Constants;
import nxt.NxtException;
import nxt.account.Account;
import nxt.account.AccountLedger;
import nxt.account.AccountLedger.LedgerEvent;
import nxt.account.BalanceHome;
import nxt.blockchain.Chain;
import nxt.blockchain.ChildTransactionImpl;
import nxt.blockchain.ChildTransactionType;
import nxt.blockchain.Transaction;
import nxt.blockchain.TransactionType;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Coin exchange transaction types for a child chain
 */
public abstract class CoinExchangeTransactionType extends ChildTransactionType {

    private static final byte SUBTYPE_COIN_EXCHANGE_ORDER_ISSUE = 0;
    private static final byte SUBTYPE_COIN_EXCHANGE_ORDER_CANCEL = 1;

    public static TransactionType findTransactionType(byte subtype) {
        switch (subtype) {
            case SUBTYPE_COIN_EXCHANGE_ORDER_ISSUE:
                return CoinExchangeTransactionType.ORDER_ISSUE;
            case SUBTYPE_COIN_EXCHANGE_ORDER_CANCEL:
                return CoinExchangeTransactionType.ORDER_CANCEL;
            default:
                return null;
        }
    }

    private CoinExchangeTransactionType() {}

    @Override
    public final byte getType() {
        return ChildTransactionType.TYPE_COIN_EXCHANGE;
    }

    /**
     * COIN_EXCHANGE_ORDER_ISSUE transaction type
     */
    public static final TransactionType ORDER_ISSUE = new CoinExchangeTransactionType() {

        @Override
        public final byte getSubtype() {
            return SUBTYPE_COIN_EXCHANGE_ORDER_ISSUE;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.COIN_EXCHANGE_ORDER_ISSUE;
        }

        @Override
        public String getName() {
            return "CoinExchangeOrderIssue";
        }

        @Override
        public OrderIssueAttachment parseAttachment(ByteBuffer buffer) throws NxtException.NotValidException {
            return new OrderIssueAttachment(buffer);
        }

        @Override
        public OrderIssueAttachment parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
            return new OrderIssueAttachment(attachmentData);
        }

        @Override
        public boolean applyAttachmentUnconfirmed(ChildTransactionImpl transaction, Account senderAccount) {
            OrderIssueAttachment attachment = (OrderIssueAttachment)transaction.getAttachment();
            BalanceHome.Balance balance = attachment.getChain().getBalanceHome().getBalance(senderAccount.getId());
            if (balance.getUnconfirmedBalance() >= attachment.getQuantityQNT()) {
                balance.addToUnconfirmedBalance(getLedgerEvent(), AccountLedger.newEventId(transaction), -attachment.getQuantityQNT());
                return true;
            }
            return false;
        }

        @Override
        public void undoAttachmentUnconfirmed(ChildTransactionImpl transaction, Account senderAccount) {
            OrderIssueAttachment attachment = (OrderIssueAttachment)transaction.getAttachment();
            BalanceHome.Balance balance = attachment.getChain().getBalanceHome().getBalance(senderAccount.getId());
            balance.addToUnconfirmedBalance(getLedgerEvent(), AccountLedger.newEventId(transaction), attachment.getQuantityQNT());
        }

        @Override
        public void applyAttachment(ChildTransactionImpl transaction, Account senderAccount, Account recipientAccount) {
            OrderIssueAttachment attachment = (OrderIssueAttachment)transaction.getAttachment();
            CoinExchange.addOrder(transaction, attachment);
        }

        @Override
        public final void validateAttachment(ChildTransactionImpl transaction) throws NxtException.ValidationException {
            OrderIssueAttachment attachment = (OrderIssueAttachment)transaction.getAttachment();
            if (attachment.getQuantityQNT() <= 0 || attachment.getQuantityQNT() > Constants.MAX_BALANCE_NQT ||
                    attachment.getPriceNQT() <= 0 || attachment.getPriceNQT() > Constants.MAX_BALANCE_NQT) {
                throw new NxtException.NotValidException("Invalid coin exchange order: " + attachment.getJSONObject());
            }
            if (transaction.getChain() != attachment.getChain()) {
                throw new NxtException.NotValidException("Coin exchange order for chain " + attachment.getChain().getName()
                        + " was submitted on chain " + transaction.getChain().getName());
            }
            if (attachment.getChain() == attachment.getExchangeChain()) {
                throw new NxtException.NotValidException("Coin exchange order chain and exchange chain must be different: " + attachment.getJSONObject());
            }
        }

        @Override
        protected void validateId(ChildTransactionImpl transaction) throws NxtException.NotCurrentlyValidException {
            if (CoinExchange.getOrder(transaction.getId()) != null) {
                throw new NxtException.NotCurrentlyValidException(
                        "Duplicate coin exchange order id " + transaction.getStringId());
            }
        }

        @Override
        public final boolean canHaveRecipient() {
            return false;
        }

        @Override
        public final boolean isPhasingSafe() {
            return true;
        }
    };

    /**
     * COIN_EXCHANGE_ORDER_CANCEL transaction type
     */
    public static final TransactionType ORDER_CANCEL = new CoinExchangeTransactionType() {

        @Override
        public final byte getSubtype() {
            return SUBTYPE_COIN_EXCHANGE_ORDER_CANCEL;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.COIN_EXCHANGE_ORDER_CANCEL;
        }

        @Override
        public String getName() {
            return "CoinExchangeOrderCancel";
        }

        @Override
        public OrderCancelAttachment parseAttachment(ByteBuffer buffer) throws NxtException.NotValidException {
            return new OrderCancelAttachment(buffer);
        }

        @Override
        public OrderCancelAttachment parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
            return new OrderCancelAttachment(attachmentData);
        }

        @Override
        public boolean applyAttachmentUnconfirmed(ChildTransactionImpl transaction, Account senderAccount) {
            return true;
        }

        @Override
        public void undoAttachmentUnconfirmed(ChildTransactionImpl transaction, Account senderAccount) {
        }

        @Override
        public boolean isUnconfirmedDuplicate(Transaction transaction,
                                              Map<TransactionType, Map<String, Integer>> duplicates) {
            OrderCancelAttachment attachment = (OrderCancelAttachment)transaction.getAttachment();
            return TransactionType.isDuplicate(CoinExchangeTransactionType.ORDER_CANCEL,
                    Long.toUnsignedString(attachment.getOrderId()), duplicates, true);
        }

        @Override
        public void applyAttachment(ChildTransactionImpl transaction, Account senderAccount, Account recipientAccount) {
            OrderCancelAttachment attachment = (OrderCancelAttachment)transaction.getAttachment();
            CoinExchange.Order order = CoinExchange.getOrder(attachment.getOrderId());
            if (order != null) {
                CoinExchange.removeOrder(attachment.getOrderId());
                BalanceHome.Balance balance = Chain.getChain(order.getChainId()).getBalanceHome().getBalance(senderAccount.getId());
                balance.addToUnconfirmedBalance(LedgerEvent.COIN_EXCHANGE_ORDER_CANCEL, AccountLedger.newEventId(transaction),
                        order.getQuantity());
            }
        }

        @Override
        public final void validateAttachment(ChildTransactionImpl transaction) throws NxtException.ValidationException {
            OrderCancelAttachment attachment = (OrderCancelAttachment)transaction.getAttachment();
            CoinExchange.Order order = CoinExchange.getOrder(attachment.getOrderId());
            if (order == null) {
                throw new NxtException.NotCurrentlyValidException(
                        "Invalid coin exchange order: " + Long.toUnsignedString(attachment.getOrderId()));
            }
            if (order.getAccountId() != transaction.getSenderId()) {
                throw new NxtException.NotValidException("Order " + Long.toUnsignedString(order.getId())
                        + " was created by account "
                        + Long.toUnsignedString(order.getAccountId()));
            }
            if (transaction.getChain().getId() != order.getChainId()) {
                throw new NxtException.NotValidException("Coin exchange order cancellation for order on chain "
                        + Chain.getChain(order.getChainId()).getName()
                        + " was submitted on chain " + transaction.getChain().getName());
            }
        }

        @Override
        public final boolean canHaveRecipient() {
            return false;
        }

        @Override
        public final boolean isPhasingSafe() {
            return true;
        }
    };
}
