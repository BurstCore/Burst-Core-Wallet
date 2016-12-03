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

import nxt.Constants;
import nxt.Nxt;
import nxt.NxtException;
import nxt.account.Account;
import nxt.account.AccountRestrictions;
import nxt.crypto.Crypto;
import nxt.db.DbUtils;
import nxt.util.Convert;
import nxt.util.Logger;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FxtTransactionImpl extends TransactionImpl implements FxtTransaction {

    public static final class BuilderImpl extends TransactionImpl.BuilderImpl implements FxtTransaction.Builder {

        public BuilderImpl(byte version, byte[] senderPublicKey, long amount, long fee, short deadline,
                    Attachment.AbstractAttachment attachment) {
            super(FxtChain.FXT.getId(), version, senderPublicKey, amount, fee, deadline, attachment);
        }

        @Override
        public FxtTransactionImpl build(String secretPhrase) throws NxtException.NotValidException {
            List<Appendix.AbstractAppendix> list = new ArrayList<>();
            if (getAttachment() != null) {
                list.add(getAttachment());
            }
            appendages(Collections.unmodifiableList(list));
            preBuild(secretPhrase);
            return getAttachment().getTransactionType() == ChildBlockTransactionType.INSTANCE ?
                    new ChildBlockTransactionImpl(this, secretPhrase) : new FxtTransactionImpl(this, secretPhrase);
        }

        @Override
        public FxtTransactionImpl build() throws NxtException.NotValidException {
            return build(null);
        }

        @Override
        BuilderImpl prunableAttachments(JSONObject prunableAttachments) throws NxtException.NotValidException {
            if (prunableAttachments != null) {
                ChildBlockAttachment childBlockAttachment = ChildBlockAttachment.parse(prunableAttachments);
                if (childBlockAttachment != null) {
                    appendix(childBlockAttachment);
                    return this;
                }
                //TODO: support prunable message attachments?
            }
            return this;
        }

    }


    private final long feeFQT;
    private final byte[] signature;

    FxtTransactionImpl(BuilderImpl builder, String secretPhrase) throws NxtException.NotValidException {
        super(builder);
        if (builder.fee <= 0 || (Constants.correctInvalidFees && builder.signature == null)) {
            int effectiveHeight = (getHeight() < Integer.MAX_VALUE ? getHeight() : Nxt.getBlockchain().getHeight());
            long minFee = getMinimumFeeFQT(effectiveHeight);
            this.feeFQT = Math.max(minFee, builder.fee);
        } else {
            this.feeFQT = builder.fee;
        }
        if (builder.signature != null && secretPhrase != null) {
            throw new NxtException.NotValidException("Transaction is already signed");
        } else if (builder.signature != null) {
            this.signature = builder.signature;
        } else if (secretPhrase != null) {
            byte[] senderPublicKey = builder.senderPublicKey != null ? builder.senderPublicKey : Account.getPublicKey(builder.senderId);
            if (senderPublicKey != null && ! Arrays.equals(senderPublicKey, Crypto.getPublicKey(secretPhrase))) {
                throw new NxtException.NotValidException("Secret phrase doesn't match transaction sender public key");
            }
            this.signature = Crypto.sign(bytes(), secretPhrase);
            bytes = null;
        } else {
            this.signature = null;
        }
    }

    @Override
    public final Chain getChain() {
        return FxtChain.FXT;
    }

    @Override
    public long getFee() {
        return feeFQT;
    }

    @Override
    public byte[] getSignature() {
        return signature;
    }

    @Override
    public boolean isPhased() {
        return false;
    }

    @Override
    int getFlags() {
        return 0; // reserved for future use
    }

    @Override
    public boolean attachmentIsPhased() {
        return false;
    }

    @Override
    boolean hasAllReferencedTransactions(int timestamp, int count) {
        return true;
    }

    @Override
    public void validate() throws NxtException.ValidationException {
        super.validate();
        if (FxtTransactionType.findTransactionType(getType().getType(), getType().getSubtype()) == null) {
            throw new NxtException.NotValidException("Invalid transaction type " + getType().getName() + " for FxtTransaction");
        }
        for (Appendix.AbstractAppendix appendage : appendages()) {
            appendage.loadPrunable(this);
            if (! appendage.verifyVersion()) {
                throw new NxtException.NotValidException("Invalid attachment version " + appendage.getVersion());
            }
            appendage.validate(this);
        }
        if (getFullSize() > Constants.MAX_PAYLOAD_LENGTH) {
            throw new NxtException.NotValidException("Transaction size " + getFullSize() + " exceeds maximum payload size");
        }
        long minimumFeeFQT = getMinimumFeeFQT(Nxt.getBlockchain().getHeight());
        if (feeFQT < minimumFeeFQT) {
            throw new NxtException.NotCurrentlyValidException(String.format("Transaction fee %f FXT less than minimum fee %f FXT at height %d",
                    ((double) feeFQT) / Constants.ONE_NXT, ((double) minimumFeeFQT) / Constants.ONE_NXT, Nxt.getBlockchain().getHeight()));
        }
        validateEcBlock();
        AccountRestrictions.checkTransaction(this);
    }

    @Override
    void apply() {
        Account senderAccount = Account.getAccount(getSenderId());
        senderAccount.apply(getSenderPublicKey());
        Account recipientAccount = null;
        if (getRecipientId() != 0) {
            recipientAccount = Account.getAccount(getRecipientId());
            if (recipientAccount == null) {
                recipientAccount = Account.addOrGetAccount(getRecipientId());
            }
        }
        for (Appendix.AbstractAppendix appendage : appendages()) {
            appendage.loadPrunable(this);
            appendage.apply(this, senderAccount, recipientAccount);
        }
    }

    @Override
    void unsetBlock() {
        super.unsetBlock();
        setIndex(-1);
    }

    @Override
    public List<ChildTransactionImpl> getChildTransactions() {
        return Collections.emptyList();
    }

    public long[] getBackFees() {
        return Convert.EMPTY_LONG;
    }

    @Override
    final UnconfirmedFxtTransaction newUnconfirmedTransaction(long arrivalTimestamp) {
        return new UnconfirmedFxtTransaction(this, arrivalTimestamp);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FxtTransactionImpl && this.getId() == ((Transaction)o).getId();
    }

    @Override
    public int hashCode() {
        return (int)(getId() ^ (getId() >>> 32));
    }

    public static FxtTransactionImpl loadTransaction(Connection con, ResultSet rs) throws NxtException.NotValidException {
        BuilderImpl builder = (BuilderImpl)TransactionImpl.newTransactionBuilder(FxtChain.FXT, con, rs);
        return builder.build();
    }

    public static FxtTransactionImpl.BuilderImpl newTransactionBuilder(byte version, long amount, long fee, short deadline,
                                                                Attachment.AbstractAttachment attachment, ByteBuffer buffer, Connection con, ResultSet rs) {
        return new BuilderImpl(version, null, amount, fee, deadline, attachment);
    }

    @Override
    void save(Connection con, String schemaTable) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO " + schemaTable
                + " (id, deadline, recipient_id, amount, fee, height, "
                + "block_id, signature, timestamp, type, subtype, sender_id, attachment_bytes, "
                + "block_timestamp, full_hash, version, "
                + "has_prunable_attachment, ec_block_height, ec_block_id, transaction_index) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            int i = 0;
            pstmt.setLong(++i, getId());
            pstmt.setShort(++i, getDeadline());
            DbUtils.setLongZeroToNull(pstmt, ++i, getRecipientId());
            pstmt.setLong(++i, getAmount());
            pstmt.setLong(++i, getFee());
            pstmt.setInt(++i, getHeight());
            pstmt.setLong(++i, getBlockId());
            pstmt.setBytes(++i, getSignature());
            pstmt.setInt(++i, getTimestamp());
            pstmt.setByte(++i, getType().getType());
            pstmt.setByte(++i, getType().getSubtype());
            pstmt.setLong(++i, getSenderId());
            int bytesLength = 0;
            for (Appendix appendage : getAppendages()) {
                bytesLength += appendage.getSize();
            }
            if (bytesLength == 0) {
                pstmt.setNull(++i, Types.VARBINARY);
            } else {
                ByteBuffer buffer = ByteBuffer.allocate(bytesLength);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                for (Appendix appendage : getAppendages()) {
                    appendage.putBytes(buffer);
                }
                pstmt.setBytes(++i, buffer.array());
            }
            pstmt.setInt(++i, getBlockTimestamp());
            pstmt.setBytes(++i, getFullHash());
            pstmt.setByte(++i, getVersion());
            pstmt.setBoolean(++i, getAttachment() instanceof Appendix.Prunable);
            pstmt.setInt(++i, getECBlockHeight());
            DbUtils.setLongZeroToNull(pstmt, ++i, getECBlockId());
            pstmt.setShort(++i, getIndex());
            pstmt.executeUpdate();
        }
    }

    public static FxtTransactionImpl.BuilderImpl newTransactionBuilder(byte version, byte[] senderPublicKey, long amount, long fee, short deadline,
                                                                Attachment.AbstractAttachment attachment, int flags, ByteBuffer buffer) {
        try {
            //TODO: support message and prunable message attachments?
            return new BuilderImpl(version, senderPublicKey, amount, fee, deadline, attachment);
        } catch (RuntimeException e) {
            Logger.logDebugMessage("Failed to parse transaction bytes: " + Convert.toHexString(buffer.array()));
            throw e;
        }
    }

    public static FxtTransactionImpl.BuilderImpl newTransactionBuilder(byte version, byte[] senderPublicKey, long amount, long fee, short deadline,
                                                                Attachment.AbstractAttachment attachment, int flags, ByteBuffer buffer, JSONObject prunableAttachments) throws NxtException.NotValidException {
        BuilderImpl builder = newTransactionBuilder(version, senderPublicKey, amount, fee, deadline, attachment, flags, buffer);
        builder.prunableAttachments(prunableAttachments);
        return builder;
    }

    public static FxtTransactionImpl.BuilderImpl newTransactionBuilder(byte version, byte[] senderPublicKey, long amount, long fee, short deadline,
                                                                Attachment.AbstractAttachment attachment, JSONObject attachmentData, JSONObject transactionData) {
        try {
            //TODO: support message and prunable message attachments?
            return new BuilderImpl(version, senderPublicKey, amount, fee, deadline, attachment);
        } catch (RuntimeException e) {
            Logger.logDebugMessage("Failed to parse transaction: " + transactionData.toJSONString());
            throw e;
        }
    }

}
