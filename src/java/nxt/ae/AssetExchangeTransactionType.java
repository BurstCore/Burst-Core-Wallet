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

package nxt.ae;

import nxt.Constants;
import nxt.Nxt;
import nxt.NxtException;
import nxt.account.Account;
import nxt.account.AccountLedger;
import nxt.blockchain.Appendix;
import nxt.blockchain.ChildTransactionImpl;
import nxt.blockchain.ChildTransactionType;
import nxt.blockchain.Fee;
import nxt.blockchain.Transaction;
import nxt.blockchain.TransactionImpl;
import nxt.blockchain.TransactionType;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.util.Map;

public abstract class AssetExchangeTransactionType extends ChildTransactionType {

    private static final byte SUBTYPE_ASSET_EXCHANGE_ASSET_ISSUANCE = 0;
    private static final byte SUBTYPE_ASSET_EXCHANGE_ASSET_TRANSFER = 1;
    private static final byte SUBTYPE_ASSET_EXCHANGE_ASK_ORDER_PLACEMENT = 2;
    private static final byte SUBTYPE_ASSET_EXCHANGE_BID_ORDER_PLACEMENT = 3;
    private static final byte SUBTYPE_ASSET_EXCHANGE_ASK_ORDER_CANCELLATION = 4;
    private static final byte SUBTYPE_ASSET_EXCHANGE_BID_ORDER_CANCELLATION = 5;
    private static final byte SUBTYPE_ASSET_EXCHANGE_DIVIDEND_PAYMENT = 6;
    private static final byte SUBTYPE_ASSET_EXCHANGE_ASSET_DELETE = 7;

    public static TransactionType findTransactionType(byte subtype) {
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
    }

    private AssetExchangeTransactionType() {}

    @Override
    public final byte getType() {
        return ChildTransactionType.TYPE_ASSET_EXCHANGE;
    }

    public static final TransactionType ASSET_ISSUANCE = new AssetExchangeTransactionType() {

        private final Fee SINGLETON_ASSET_FEE = new Fee.SizeBasedFee(Constants.ONE_NXT, Constants.ONE_NXT, 32) {
            public int getSize(TransactionImpl transaction, Appendix appendage) {
                AssetIssuanceAttachment attachment = (AssetIssuanceAttachment) transaction.getAttachment();
                return attachment.getDescription().length();
            }
        };

        private final Fee ASSET_ISSUANCE_FEE = new Fee() {
            @Override
            public long getFee(TransactionImpl transaction, Appendix appendage) {
                return 1000 * Constants.ONE_NXT;
            }
            @Override
            public long[] getBackFees(long fee) {
                return new long[] {fee * 3 / 10, fee * 2 / 10, fee / 10};
            }
        };

        @Override
        public final byte getSubtype() {
            return SUBTYPE_ASSET_EXCHANGE_ASSET_ISSUANCE;
        }

        @Override
        public AccountLedger.LedgerEvent getLedgerEvent() {
            return AccountLedger.LedgerEvent.ASSET_ISSUANCE;
        }

        @Override
        public String getName() {
            return "AssetIssuance";
        }

        @Override
        public Fee getBaselineFee(Transaction transaction) {
            return isSingletonIssuance(transaction) ? SINGLETON_ASSET_FEE : ASSET_ISSUANCE_FEE;
        }

        @Override
        public AssetIssuanceAttachment parseAttachment(ByteBuffer buffer) throws NxtException.NotValidException {
            return new AssetIssuanceAttachment(buffer);
        }

        @Override
        public AssetIssuanceAttachment parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
            return new AssetIssuanceAttachment(attachmentData);
        }

        @Override
        public boolean applyAttachmentUnconfirmed(ChildTransactionImpl transaction, Account senderAccount) {
            return true;
        }

        @Override
        public void applyAttachment(ChildTransactionImpl transaction, Account senderAccount, Account recipientAccount) {
            AssetIssuanceAttachment attachment = (AssetIssuanceAttachment) transaction.getAttachment();
            long assetId = transaction.getId();
            Asset.addAsset(transaction, attachment);
            senderAccount.addToAssetAndUnconfirmedAssetBalanceQNT(getLedgerEvent(), assetId, assetId, attachment.getQuantityQNT());
        }

        @Override
        public void undoAttachmentUnconfirmed(ChildTransactionImpl transaction, Account senderAccount) {
        }

        @Override
        public void validateAttachment(ChildTransactionImpl transaction) throws NxtException.ValidationException {
            AssetIssuanceAttachment attachment = (AssetIssuanceAttachment)transaction.getAttachment();
            if (attachment.getName().length() < Constants.MIN_ASSET_NAME_LENGTH
                    || attachment.getName().length() > Constants.MAX_ASSET_NAME_LENGTH
                    || attachment.getDescription().length() > Constants.MAX_ASSET_DESCRIPTION_LENGTH
                    || attachment.getDecimals() < 0 || attachment.getDecimals() > 8
                    || attachment.getQuantityQNT() <= 0
                    || attachment.getQuantityQNT() > Constants.MAX_ASSET_QUANTITY_QNT
                    ) {
                throw new NxtException.NotValidException("Invalid asset issuance: " + attachment.getJSONObject());
            }
            String normalizedName = attachment.getName().toLowerCase();
            for (int i = 0; i < normalizedName.length(); i++) {
                if (Constants.ALPHABET.indexOf(normalizedName.charAt(i)) < 0) {
                    throw new NxtException.NotValidException("Invalid asset name: " + normalizedName);
                }
            }
        }

        @Override
        protected void validateId(ChildTransactionImpl transaction) throws NxtException.NotCurrentlyValidException {
            if (Asset.getAsset(transaction.getId()) != null) {
                throw new NxtException.NotCurrentlyValidException("Duplicate asset id " + transaction.getStringId());
            }
        }

        @Override
        public boolean isBlockDuplicate(final Transaction transaction, final Map<TransactionType, Map<String, Integer>> duplicates) {
            return !isSingletonIssuance(transaction) && isDuplicate(AssetExchangeTransactionType.ASSET_ISSUANCE, getName(), duplicates, true);
        }

        @Override
        public boolean canHaveRecipient() {
            return false;
        }

        @Override
        public boolean isPhasingSafe() {
            return true;
        }

        private boolean isSingletonIssuance(Transaction transaction) {
            AssetIssuanceAttachment attachment = (AssetIssuanceAttachment)transaction.getAttachment();
            return attachment.getQuantityQNT() == 1 && attachment.getDecimals() == 0
                    && attachment.getDescription().length() <= Constants.MAX_SINGLETON_ASSET_DESCRIPTION_LENGTH;
        }

    };

    public static final TransactionType ASSET_TRANSFER = new AssetExchangeTransactionType() {

        @Override
        public final byte getSubtype() {
            return SUBTYPE_ASSET_EXCHANGE_ASSET_TRANSFER;
        }

        @Override
        public AccountLedger.LedgerEvent getLedgerEvent() {
            return AccountLedger.LedgerEvent.ASSET_TRANSFER;
        }

        @Override
        public String getName() {
            return "AssetTransfer";
        }

        @Override
        public AssetTransferAttachment parseAttachment(ByteBuffer buffer) throws NxtException.NotValidException {
            return new AssetTransferAttachment(buffer);
        }

        @Override
        public AssetTransferAttachment parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
            return new AssetTransferAttachment(attachmentData);
        }

        @Override
        public boolean applyAttachmentUnconfirmed(ChildTransactionImpl transaction, Account senderAccount) {
            AssetTransferAttachment attachment = (AssetTransferAttachment) transaction.getAttachment();
            long unconfirmedAssetBalance = senderAccount.getUnconfirmedAssetBalanceQNT(attachment.getAssetId());
            if (unconfirmedAssetBalance >= 0 && unconfirmedAssetBalance >= attachment.getQuantityQNT()) {
                senderAccount.addToUnconfirmedAssetBalanceQNT(getLedgerEvent(), transaction.getId(),
                        attachment.getAssetId(), -attachment.getQuantityQNT());
                return true;
            }
            return false;
        }

        @Override
        public void applyAttachment(ChildTransactionImpl transaction, Account senderAccount, Account recipientAccount) {
            AssetTransferAttachment attachment = (AssetTransferAttachment) transaction.getAttachment();
            senderAccount.addToAssetBalanceQNT(getLedgerEvent(), transaction.getId(), attachment.getAssetId(),
                    -attachment.getQuantityQNT());
            recipientAccount.addToAssetAndUnconfirmedAssetBalanceQNT(getLedgerEvent(), transaction.getId(),
                    attachment.getAssetId(), attachment.getQuantityQNT());
            AssetTransfer.addAssetTransfer(transaction, attachment);
        }

        @Override
        public void undoAttachmentUnconfirmed(ChildTransactionImpl transaction, Account senderAccount) {
            AssetTransferAttachment attachment = (AssetTransferAttachment) transaction.getAttachment();
            senderAccount.addToUnconfirmedAssetBalanceQNT(getLedgerEvent(), transaction.getId(),
                    attachment.getAssetId(), attachment.getQuantityQNT());
        }

        @Override
        public void validateAttachment(ChildTransactionImpl transaction) throws NxtException.ValidationException {
            AssetTransferAttachment attachment = (AssetTransferAttachment)transaction.getAttachment();
            if (transaction.getAmount() != 0 || attachment.getAssetId() == 0) {
                throw new NxtException.NotValidException("Invalid asset transfer amount or asset: " + attachment.getJSONObject());
            }
            Asset asset = Asset.getAsset(attachment.getAssetId());
            if (attachment.getQuantityQNT() <= 0 || (asset != null && attachment.getQuantityQNT() > asset.getInitialQuantityQNT())) {
                throw new NxtException.NotValidException("Invalid asset transfer asset or quantity: " + attachment.getJSONObject());
            }
            if (asset == null) {
                throw new NxtException.NotCurrentlyValidException("Asset " + Long.toUnsignedString(attachment.getAssetId()) +
                        " does not exist yet");
            }
        }

        @Override
        public boolean canHaveRecipient() {
            return true;
        }

        @Override
        public boolean isPhasingSafe() {
            return true;
        }

    };

    public static final TransactionType ASSET_DELETE = new AssetExchangeTransactionType() {

        @Override
        public final byte getSubtype() {
            return SUBTYPE_ASSET_EXCHANGE_ASSET_DELETE;
        }

        @Override
        public AccountLedger.LedgerEvent getLedgerEvent() {
            return AccountLedger.LedgerEvent.ASSET_DELETE;
        }

        @Override
        public String getName() {
            return "AssetDelete";
        }

        @Override
        public AssetDeleteAttachment parseAttachment(ByteBuffer buffer) throws NxtException.NotValidException {
            return new AssetDeleteAttachment(buffer);
        }

        @Override
        public AssetDeleteAttachment parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
            return new AssetDeleteAttachment(attachmentData);
        }

        @Override
        public boolean applyAttachmentUnconfirmed(ChildTransactionImpl transaction, Account senderAccount) {
            AssetDeleteAttachment attachment = (AssetDeleteAttachment)transaction.getAttachment();
            long unconfirmedAssetBalance = senderAccount.getUnconfirmedAssetBalanceQNT(attachment.getAssetId());
            if (unconfirmedAssetBalance >= 0 && unconfirmedAssetBalance >= attachment.getQuantityQNT()) {
                senderAccount.addToUnconfirmedAssetBalanceQNT(getLedgerEvent(), transaction.getId(),
                        attachment.getAssetId(), -attachment.getQuantityQNT());
                return true;
            }
            return false;
        }

        @Override
        public void applyAttachment(ChildTransactionImpl transaction, Account senderAccount, Account recipientAccount) {
            AssetDeleteAttachment attachment = (AssetDeleteAttachment)transaction.getAttachment();
            senderAccount.addToAssetBalanceQNT(getLedgerEvent(), transaction.getId(), attachment.getAssetId(),
                    -attachment.getQuantityQNT());
            Asset.deleteAsset(transaction, attachment.getAssetId(), attachment.getQuantityQNT());
        }

        @Override
        public void undoAttachmentUnconfirmed(ChildTransactionImpl transaction, Account senderAccount) {
            AssetDeleteAttachment attachment = (AssetDeleteAttachment)transaction.getAttachment();
            senderAccount.addToUnconfirmedAssetBalanceQNT(getLedgerEvent(), transaction.getId(),
                    attachment.getAssetId(), attachment.getQuantityQNT());
        }

        @Override
        public void validateAttachment(ChildTransactionImpl transaction) throws NxtException.ValidationException {
            AssetDeleteAttachment attachment = (AssetDeleteAttachment)transaction.getAttachment();
            if (attachment.getAssetId() == 0) {
                throw new NxtException.NotValidException("Invalid asset identifier: " + attachment.getJSONObject());
            }
            Asset asset = Asset.getAsset(attachment.getAssetId());
            if (attachment.getQuantityQNT() <= 0 || (asset != null && attachment.getQuantityQNT() > asset.getInitialQuantityQNT())) {
                throw new NxtException.NotValidException("Invalid asset delete asset or quantity: " + attachment.getJSONObject());
            }
            if (asset == null) {
                throw new NxtException.NotCurrentlyValidException("Asset " + Long.toUnsignedString(attachment.getAssetId()) +
                        " does not exist yet");
            }
        }

        @Override
        public boolean canHaveRecipient() {
            return false;
        }

        @Override
        public boolean isPhasingSafe() {
            return true;
        }

    };

    abstract static class OrderPlacement extends AssetExchangeTransactionType {

        @Override
        public final void validateAttachment(ChildTransactionImpl transaction) throws NxtException.ValidationException {
            OrderPlacementAttachment attachment = (OrderPlacementAttachment)transaction.getAttachment();
            if (attachment.getPriceNQT() <= 0 || attachment.getPriceNQT() > Constants.MAX_BALANCE_NQT
                    || attachment.getAssetId() == 0) {
                throw new NxtException.NotValidException("Invalid asset order placement: " + attachment.getJSONObject());
            }
            Asset asset = Asset.getAsset(attachment.getAssetId());
            if (attachment.getQuantityQNT() <= 0 || (asset != null && attachment.getQuantityQNT() > asset.getInitialQuantityQNT())) {
                throw new NxtException.NotValidException("Invalid asset order placement asset or quantity: " + attachment.getJSONObject());
            }
            if (asset == null) {
                throw new NxtException.NotCurrentlyValidException("Asset " + Long.toUnsignedString(attachment.getAssetId()) +
                        " does not exist yet");
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

    }

    public static final TransactionType ASK_ORDER_PLACEMENT = new OrderPlacement() {

        @Override
        public final byte getSubtype() {
            return SUBTYPE_ASSET_EXCHANGE_ASK_ORDER_PLACEMENT;
        }

        @Override
        public AccountLedger.LedgerEvent getLedgerEvent() {
            return AccountLedger.LedgerEvent.ASSET_ASK_ORDER_PLACEMENT;
        }

        @Override
        public String getName() {
            return "AskOrderPlacement";
        }

        @Override
        public AskOrderPlacementAttachment parseAttachment(ByteBuffer buffer) throws NxtException.NotValidException {
            return new AskOrderPlacementAttachment(buffer);
        }

        @Override
        public AskOrderPlacementAttachment parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
            return new AskOrderPlacementAttachment(attachmentData);
        }

        @Override
        public boolean applyAttachmentUnconfirmed(ChildTransactionImpl transaction, Account senderAccount) {
            AskOrderPlacementAttachment attachment = (AskOrderPlacementAttachment) transaction.getAttachment();
            long unconfirmedAssetBalance = senderAccount.getUnconfirmedAssetBalanceQNT(attachment.getAssetId());
            if (unconfirmedAssetBalance >= 0 && unconfirmedAssetBalance >= attachment.getQuantityQNT()) {
                senderAccount.addToUnconfirmedAssetBalanceQNT(getLedgerEvent(), transaction.getId(),
                        attachment.getAssetId(), -attachment.getQuantityQNT());
                return true;
            }
            return false;
        }

        @Override
        public void applyAttachment(ChildTransactionImpl transaction, Account senderAccount, Account recipientAccount) {
            AskOrderPlacementAttachment attachment = (AskOrderPlacementAttachment) transaction.getAttachment();
            transaction.getChain().getOrderHome().addAskOrder(transaction, attachment);
        }

        @Override
        public void undoAttachmentUnconfirmed(ChildTransactionImpl transaction, Account senderAccount) {
            AskOrderPlacementAttachment attachment = (AskOrderPlacementAttachment) transaction.getAttachment();
            senderAccount.addToUnconfirmedAssetBalanceQNT(getLedgerEvent(), transaction.getId(),
                    attachment.getAssetId(), attachment.getQuantityQNT());
        }

        @Override
        protected void validateId(ChildTransactionImpl transaction) throws NxtException.NotCurrentlyValidException {
            if (transaction.getChain().getOrderHome().getAskOrder(transaction.getId()) != null) {
                throw new NxtException.NotCurrentlyValidException("Duplicate ask order id " + transaction.getStringId());
            }
        }

    };

    public final static TransactionType BID_ORDER_PLACEMENT = new OrderPlacement() {

        @Override
        public final byte getSubtype() {
            return SUBTYPE_ASSET_EXCHANGE_BID_ORDER_PLACEMENT;
        }

        @Override
        public AccountLedger.LedgerEvent getLedgerEvent() {
            return AccountLedger.LedgerEvent.ASSET_BID_ORDER_PLACEMENT;
        }

        @Override
        public String getName() {
            return "BidOrderPlacement";
        }

        @Override
        public BidOrderPlacementAttachment parseAttachment(ByteBuffer buffer) throws NxtException.NotValidException {
            return new BidOrderPlacementAttachment(buffer);
        }

        @Override
        public BidOrderPlacementAttachment parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
            return new BidOrderPlacementAttachment(attachmentData);
        }

        @Override
        public boolean applyAttachmentUnconfirmed(ChildTransactionImpl transaction, Account senderAccount) {
            BidOrderPlacementAttachment attachment = (BidOrderPlacementAttachment) transaction.getAttachment();
            if (senderAccount.getUnconfirmedBalance(transaction.getChain()) >= Math.multiplyExact(attachment.getQuantityQNT(), attachment.getPriceNQT())) {
                senderAccount.addToUnconfirmedBalance(transaction.getChain(), getLedgerEvent(), transaction.getId(),
                        -Math.multiplyExact(attachment.getQuantityQNT(), attachment.getPriceNQT()));
                return true;
            }
            return false;
        }

        @Override
        public void applyAttachment(ChildTransactionImpl transaction, Account senderAccount, Account recipientAccount) {
            BidOrderPlacementAttachment attachment = (BidOrderPlacementAttachment) transaction.getAttachment();
            transaction.getChain().getOrderHome().addBidOrder(transaction, attachment);
        }

        @Override
        public void undoAttachmentUnconfirmed(ChildTransactionImpl transaction, Account senderAccount) {
            BidOrderPlacementAttachment attachment = (BidOrderPlacementAttachment) transaction.getAttachment();
            senderAccount.addToUnconfirmedBalance(transaction.getChain(), getLedgerEvent(), transaction.getId(),
                    Math.multiplyExact(attachment.getQuantityQNT(), attachment.getPriceNQT()));
        }

        @Override
        protected void validateId(ChildTransactionImpl transaction) throws NxtException.NotCurrentlyValidException {
            if (transaction.getChain().getOrderHome().getBidOrder(transaction.getId()) != null) {
                throw new NxtException.NotCurrentlyValidException("Duplicate bid order id " + transaction.getStringId());
            }
        }

    };

    abstract static class OrderCancellation extends AssetExchangeTransactionType {

        @Override
        public final boolean applyAttachmentUnconfirmed(ChildTransactionImpl transaction, Account senderAccount) {
            return true;
        }

        @Override
        public final void undoAttachmentUnconfirmed(ChildTransactionImpl transaction, Account senderAccount) {
        }

        @Override
        public boolean isUnconfirmedDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            OrderCancellationAttachment attachment = (OrderCancellationAttachment) transaction.getAttachment();
            return TransactionType.isDuplicate(AssetExchangeTransactionType.ASK_ORDER_CANCELLATION, Long.toUnsignedString(attachment.getOrderId()), duplicates, true);
        }

        @Override
        public final boolean canHaveRecipient() {
            return false;
        }

        @Override
        public final boolean isPhasingSafe() {
            return true;
        }

    }

    public static final TransactionType ASK_ORDER_CANCELLATION = new OrderCancellation() {

        @Override
        public final byte getSubtype() {
            return SUBTYPE_ASSET_EXCHANGE_ASK_ORDER_CANCELLATION;
        }

        @Override
        public AccountLedger.LedgerEvent getLedgerEvent() {
            return AccountLedger.LedgerEvent.ASSET_ASK_ORDER_CANCELLATION;
        }

        @Override
        public String getName() {
            return "AskOrderCancellation";
        }

        @Override
        public AskOrderCancellationAttachment parseAttachment(ByteBuffer buffer) throws NxtException.NotValidException {
            return new AskOrderCancellationAttachment(buffer);
        }

        @Override
        public AskOrderCancellationAttachment parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
            return new AskOrderCancellationAttachment(attachmentData);
        }

        @Override
        public void applyAttachment(ChildTransactionImpl transaction, Account senderAccount, Account recipientAccount) {
            AskOrderCancellationAttachment attachment = (AskOrderCancellationAttachment) transaction.getAttachment();
            OrderHome orderHome = transaction.getChain().getOrderHome();
            OrderHome.Order order = orderHome.getAskOrder(attachment.getOrderId());
            orderHome.removeAskOrder(attachment.getOrderId());
            if (order != null) {
                senderAccount.addToUnconfirmedAssetBalanceQNT(getLedgerEvent(), transaction.getId(),
                        order.getAssetId(), order.getQuantityQNT());
            }
        }

        @Override
        public void validateAttachment(ChildTransactionImpl transaction) throws NxtException.ValidationException {
            AskOrderCancellationAttachment attachment = (AskOrderCancellationAttachment) transaction.getAttachment();
            OrderHome.Order ask = transaction.getChain().getOrderHome().getAskOrder(attachment.getOrderId());
            if (ask == null) {
                throw new NxtException.NotCurrentlyValidException("Invalid ask order: " + Long.toUnsignedString(attachment.getOrderId()));
            }
            if (ask.getAccountId() != transaction.getSenderId()) {
                throw new NxtException.NotValidException("Order " + Long.toUnsignedString(attachment.getOrderId()) + " was created by account "
                        + Long.toUnsignedString(ask.getAccountId()));
            }
        }

    };

    public static final TransactionType BID_ORDER_CANCELLATION = new OrderCancellation() {

        @Override
        public final byte getSubtype() {
            return SUBTYPE_ASSET_EXCHANGE_BID_ORDER_CANCELLATION;
        }

        @Override
        public AccountLedger.LedgerEvent getLedgerEvent() {
            return AccountLedger.LedgerEvent.ASSET_BID_ORDER_CANCELLATION;
        }

        @Override
        public String getName() {
            return "BidOrderCancellation";
        }

        @Override
        public BidOrderCancellationAttachment parseAttachment(ByteBuffer buffer) throws NxtException.NotValidException {
            return new BidOrderCancellationAttachment(buffer);
        }

        @Override
        public BidOrderCancellationAttachment parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
            return new BidOrderCancellationAttachment(attachmentData);
        }

        @Override
        public void applyAttachment(ChildTransactionImpl transaction, Account senderAccount, Account recipientAccount) {
            BidOrderCancellationAttachment attachment = (BidOrderCancellationAttachment) transaction.getAttachment();
            OrderHome orderHome = transaction.getChain().getOrderHome();
            OrderHome.Order order = orderHome.getBidOrder(attachment.getOrderId());
            orderHome.removeBidOrder(attachment.getOrderId());
            if (order != null) {
                senderAccount.addToUnconfirmedBalance(transaction.getChain(), getLedgerEvent(), transaction.getId(),
                        Math.multiplyExact(order.getQuantityQNT(), order.getPriceNQT()));
            }
        }

        @Override
        public void validateAttachment(ChildTransactionImpl transaction) throws NxtException.ValidationException {
            BidOrderCancellationAttachment attachment = (BidOrderCancellationAttachment) transaction.getAttachment();
            OrderHome.Order bid = transaction.getChain().getOrderHome().getBidOrder(attachment.getOrderId());
            if (bid == null) {
                throw new NxtException.NotCurrentlyValidException("Invalid bid order: " + Long.toUnsignedString(attachment.getOrderId()));
            }
            if (bid.getAccountId() != transaction.getSenderId()) {
                throw new NxtException.NotValidException("Order " + Long.toUnsignedString(attachment.getOrderId()) + " was created by account "
                        + Long.toUnsignedString(bid.getAccountId()));
            }
        }

    };

    public static final TransactionType DIVIDEND_PAYMENT = new AssetExchangeTransactionType() {

        @Override
        public final byte getSubtype() {
            return SUBTYPE_ASSET_EXCHANGE_DIVIDEND_PAYMENT;
        }

        @Override
        public AccountLedger.LedgerEvent getLedgerEvent() {
            return AccountLedger.LedgerEvent.ASSET_DIVIDEND_PAYMENT;
        }

        @Override
        public String getName() {
            return "DividendPayment";
        }

        @Override
        public DividendPaymentAttachment parseAttachment(ByteBuffer buffer) {
            return new DividendPaymentAttachment(buffer);
        }

        @Override
        public DividendPaymentAttachment parseAttachment(JSONObject attachmentData) {
            return new DividendPaymentAttachment(attachmentData);
        }

        @Override
        public boolean applyAttachmentUnconfirmed(ChildTransactionImpl transaction, Account senderAccount) {
            DividendPaymentAttachment attachment = (DividendPaymentAttachment)transaction.getAttachment();
            long assetId = attachment.getAssetId();
            Asset asset = Asset.getAsset(assetId, attachment.getHeight());
            if (asset == null) {
                return true;
            }
            long quantityQNT = asset.getQuantityQNT() - senderAccount.getAssetBalanceQNT(assetId, attachment.getHeight());
            long totalDividendPayment = Math.multiplyExact(attachment.getAmountNQTPerQNT(), quantityQNT);
            if (senderAccount.getUnconfirmedBalance(transaction.getChain()) >= totalDividendPayment) {
                senderAccount.addToUnconfirmedBalance(transaction.getChain(), getLedgerEvent(), transaction.getId(), -totalDividendPayment);
                return true;
            }
            return false;
        }

        @Override
        public void applyAttachment(ChildTransactionImpl transaction, Account senderAccount, Account recipientAccount) {
            DividendPaymentAttachment attachment = (DividendPaymentAttachment)transaction.getAttachment();
            transaction.getChain().getAssetDividendHome().payDividends(transaction, attachment);
        }

        @Override
        public void undoAttachmentUnconfirmed(ChildTransactionImpl transaction, Account senderAccount) {
            DividendPaymentAttachment attachment = (DividendPaymentAttachment)transaction.getAttachment();
            long assetId = attachment.getAssetId();
            Asset asset = Asset.getAsset(assetId, attachment.getHeight());
            if (asset == null) {
                return;
            }
            long quantityQNT = asset.getQuantityQNT() - senderAccount.getAssetBalanceQNT(assetId, attachment.getHeight());
            long totalDividendPayment = Math.multiplyExact(attachment.getAmountNQTPerQNT(), quantityQNT);
            senderAccount.addToUnconfirmedBalance(transaction.getChain(), getLedgerEvent(), transaction.getId(), totalDividendPayment);
        }

        @Override
        public void validateAttachment(ChildTransactionImpl transaction) throws NxtException.ValidationException {
            DividendPaymentAttachment attachment = (DividendPaymentAttachment)transaction.getAttachment();
            if (attachment.getHeight() > Nxt.getBlockchain().getHeight()) {
                throw new NxtException.NotCurrentlyValidException("Invalid dividend payment height: " + attachment.getHeight()
                        + ", must not exceed current blockchain height " + Nxt.getBlockchain().getHeight());
            }
            if (attachment.getHeight() <= attachment.getFinishValidationHeight(transaction) - Constants.MAX_DIVIDEND_PAYMENT_ROLLBACK) {
                throw new NxtException.NotCurrentlyValidException("Invalid dividend payment height: " + attachment.getHeight()
                        + ", must be less than " + Constants.MAX_DIVIDEND_PAYMENT_ROLLBACK
                        + " blocks before " + attachment.getFinishValidationHeight(transaction));
            }
            Asset asset = Asset.getAsset(attachment.getAssetId(), attachment.getHeight());
            if (asset == null) {
                throw new NxtException.NotCurrentlyValidException("Asset " + Long.toUnsignedString(attachment.getAssetId())
                        + " for dividend payment doesn't exist yet");
            }
            if (asset.getAccountId() != transaction.getSenderId() || attachment.getAmountNQTPerQNT() <= 0) {
                throw new NxtException.NotValidException("Invalid dividend payment sender or amount " + attachment.getJSONObject());
            }
            AssetDividendHome.AssetDividend lastDividend = transaction.getChain().getAssetDividendHome().getLastDividend(attachment.getAssetId());
            if (lastDividend != null && lastDividend.getHeight() > Nxt.getBlockchain().getHeight() - 60) {
                throw new NxtException.NotCurrentlyValidException("Last dividend payment for asset " + Long.toUnsignedString(attachment.getAssetId())
                        + " was less than 60 blocks ago at " + lastDividend.getHeight() + ", current height is " + Nxt.getBlockchain().getHeight()
                        + ", limit is one dividend per 60 blocks");
            }
        }

        @Override
        public boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            DividendPaymentAttachment attachment = (DividendPaymentAttachment) transaction.getAttachment();
            return isDuplicate(AssetExchangeTransactionType.DIVIDEND_PAYMENT, Long.toUnsignedString(attachment.getAssetId()), duplicates, true);
        }

        @Override
        public boolean canHaveRecipient() {
            return false;
        }

        @Override
        public boolean isPhasingSafe() {
            return false;
        }

    };

}
