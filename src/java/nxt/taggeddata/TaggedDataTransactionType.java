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

package nxt.taggeddata;

import nxt.Constants;
import nxt.Nxt;
import nxt.NxtException;
import nxt.account.Account;
import nxt.account.AccountLedger;
import nxt.blockchain.Appendix;
import nxt.blockchain.Chain;
import nxt.blockchain.ChildChain;
import nxt.blockchain.ChildTransactionImpl;
import nxt.blockchain.ChildTransactionType;
import nxt.blockchain.Fee;
import nxt.blockchain.Transaction;
import nxt.blockchain.TransactionImpl;
import nxt.blockchain.TransactionType;
import nxt.util.Convert;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.util.Arrays;

public abstract class TaggedDataTransactionType extends ChildTransactionType {

    private static final byte SUBTYPE_DATA_TAGGED_DATA_UPLOAD = 0;
    private static final byte SUBTYPE_DATA_TAGGED_DATA_EXTEND = 1;

    public static TransactionType findTransactionType(byte subtype) {
        switch (subtype) {
            case SUBTYPE_DATA_TAGGED_DATA_UPLOAD:
                return TaggedDataTransactionType.TAGGED_DATA_UPLOAD;
            case SUBTYPE_DATA_TAGGED_DATA_EXTEND:
                return TaggedDataTransactionType.TAGGED_DATA_EXTEND;
            default:
                return null;
        }
    }

    private static final Fee TAGGED_DATA_FEE = new Fee.SizeBasedFee(Constants.ONE_NXT, Constants.ONE_NXT/10) {
        @Override
        public int getSize(TransactionImpl transaction, Appendix appendix) {
            return appendix.getFullSize();
        }
    };

    private TaggedDataTransactionType() {
    }

    @Override
    public final byte getType() {
        return ChildTransactionType.TYPE_DATA;
    }

    @Override
    public final Fee getBaselineFee(Transaction transaction) {
        return TAGGED_DATA_FEE;
    }

    @Override
    public final boolean applyAttachmentUnconfirmed(ChildTransactionImpl transaction, Account senderAccount) {
        return true;
    }

    @Override
    public final void undoAttachmentUnconfirmed(ChildTransactionImpl transaction, Account senderAccount) {
    }

    @Override
    public final boolean canHaveRecipient() {
        return false;
    }

    @Override
    public final boolean isPhasingSafe() {
        return false;
    }

    @Override
    public final boolean isPhasable() {
        return false;
    }

    public static final TransactionType TAGGED_DATA_UPLOAD = new TaggedDataTransactionType() {

        @Override
        public byte getSubtype() {
            return SUBTYPE_DATA_TAGGED_DATA_UPLOAD;
        }

        @Override
        public AccountLedger.LedgerEvent getLedgerEvent() {
            return AccountLedger.LedgerEvent.TAGGED_DATA_UPLOAD;
        }

        @Override
        public TaggedDataUploadAttachment parseAttachment(ByteBuffer buffer) throws NxtException.NotValidException {
            return new TaggedDataUploadAttachment(buffer);
        }

        @Override
        public TaggedDataUploadAttachment parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
            return new TaggedDataUploadAttachment(attachmentData);
        }

        @Override
        public void validateAttachment(ChildTransactionImpl transaction) throws NxtException.ValidationException {
            TaggedDataUploadAttachment attachment = (TaggedDataUploadAttachment) transaction.getAttachment();
            if (attachment.getData() == null && Nxt.getEpochTime() - transaction.getTimestamp() < Constants.MIN_PRUNABLE_LIFETIME) {
                throw new NxtException.NotCurrentlyValidException("Data has been pruned prematurely");
            }
            if (attachment.getData() != null) {
                if (attachment.getName().length() == 0 || attachment.getName().length() > Constants.MAX_TAGGED_DATA_NAME_LENGTH) {
                    throw new NxtException.NotValidException("Invalid name length: " + attachment.getName().length());
                }
                if (attachment.getDescription().length() > Constants.MAX_TAGGED_DATA_DESCRIPTION_LENGTH) {
                    throw new NxtException.NotValidException("Invalid description length: " + attachment.getDescription().length());
                }
                if (attachment.getTags().length() > Constants.MAX_TAGGED_DATA_TAGS_LENGTH) {
                    throw new NxtException.NotValidException("Invalid tags length: " + attachment.getTags().length());
                }
                if (attachment.getType().length() > Constants.MAX_TAGGED_DATA_TYPE_LENGTH) {
                    throw new NxtException.NotValidException("Invalid type length: " + attachment.getType().length());
                }
                if (attachment.getChannel().length() > Constants.MAX_TAGGED_DATA_CHANNEL_LENGTH) {
                    throw new NxtException.NotValidException("Invalid channel length: " + attachment.getChannel().length());
                }
                if (attachment.getFilename().length() > Constants.MAX_TAGGED_DATA_FILENAME_LENGTH) {
                    throw new NxtException.NotValidException("Invalid filename length: " + attachment.getFilename().length());
                }
                if (attachment.getData().length == 0 || attachment.getData().length > Constants.MAX_TAGGED_DATA_DATA_LENGTH) {
                    throw new NxtException.NotValidException("Invalid data length: " + attachment.getData().length);
                }
            }
        }

        @Override
        public void applyAttachment(ChildTransactionImpl transaction, Account senderAccount, Account recipientAccount) {
            TaggedDataUploadAttachment attachment = (TaggedDataUploadAttachment) transaction.getAttachment();
            transaction.getChain().getTaggedDataHome().add(transaction, attachment);
        }

        @Override
        public String getName() {
            return "TaggedDataUpload";
        }

        @Override
        public boolean isPruned(Chain chain, byte[] fullHash) {
            return ((ChildChain) chain).getTaggedDataHome().isPruned(fullHash);
        }

    };

    public static final TransactionType TAGGED_DATA_EXTEND = new TaggedDataTransactionType() {

        @Override
        public byte getSubtype() {
            return SUBTYPE_DATA_TAGGED_DATA_EXTEND;
        }

        @Override
        public AccountLedger.LedgerEvent getLedgerEvent() {
            return AccountLedger.LedgerEvent.TAGGED_DATA_EXTEND;
        }

        @Override
        public TaggedDataExtendAttachment parseAttachment(ByteBuffer buffer) throws NxtException.NotValidException {
            return new TaggedDataExtendAttachment(buffer);
        }

        @Override
        public TaggedDataExtendAttachment parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
            return new TaggedDataExtendAttachment(attachmentData);
        }

        @Override
        public void validateAttachment(ChildTransactionImpl transaction) throws NxtException.ValidationException {
            TaggedDataExtendAttachment attachment = (TaggedDataExtendAttachment) transaction.getAttachment();
            if ((attachment.jsonIsPruned() || attachment.getData() == null) && Nxt.getEpochTime() - transaction.getTimestamp() < Constants.MIN_PRUNABLE_LIFETIME) {
                throw new NxtException.NotCurrentlyValidException("Data has been pruned prematurely");
            }
            ChildChain childChain = transaction.getChain();
            if (attachment.getChainId() != childChain.getId()) {
                throw new NxtException.NotValidException("Invalid chain id");
            }
            TransactionImpl uploadTransaction = childChain.getTransactionHome().findTransactionByFullHash(
                    attachment.getTaggedDataTransactionFullHash(), Nxt.getBlockchain().getHeight());
            if (uploadTransaction == null) {
                throw new NxtException.NotCurrentlyValidException("No such tagged data upload "
                        + Long.toUnsignedString(Convert.fullHashToId(attachment.getTaggedDataTransactionFullHash())));
            }
            if (uploadTransaction.getType() != TAGGED_DATA_UPLOAD) {
                throw new NxtException.NotValidException("Transaction " + Long.toUnsignedString(Convert.fullHashToId(attachment.getTaggedDataTransactionFullHash()))
                        + " is not a tagged data upload");
            }
            if (attachment.getData() != null) {
                TaggedDataUploadAttachment taggedDataUpload = (TaggedDataUploadAttachment)uploadTransaction.getAttachment();
                if (!Arrays.equals(attachment.getHash(), taggedDataUpload.getHash())) {
                    throw new NxtException.NotValidException("Hashes don't match! Extend hash: " + Convert.toHexString(attachment.getHash())
                            + " upload hash: " + Convert.toHexString(taggedDataUpload.getHash()));
                }
            }
            TaggedDataHome.TaggedData taggedData = childChain.getTaggedDataHome().getData(attachment.getTaggedDataTransactionFullHash());
            if (taggedData != null && taggedData.getTransactionTimestamp() > Nxt.getEpochTime() + 6 * Constants.MIN_PRUNABLE_LIFETIME) {
                throw new NxtException.NotCurrentlyValidException("Data already extended, timestamp is " + taggedData.getTransactionTimestamp());
            }
        }

        @Override
        public void applyAttachment(ChildTransactionImpl transaction, Account senderAccount, Account recipientAccount) {
            TaggedDataExtendAttachment attachment = (TaggedDataExtendAttachment) transaction.getAttachment();
            transaction.getChain().getTaggedDataHome().extend(transaction, attachment);
        }

        @Override
        public String getName() {
            return "TaggedDataExtend";
        }

        @Override
        public boolean isPruned(Chain chain, byte[] fullHash) {
            return false;
        }

    };

}
