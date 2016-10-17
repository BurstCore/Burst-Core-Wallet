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

package nxt.blockchain.internal;

import nxt.account.Account;
import nxt.account.AccountRestrictions;
import nxt.blockchain.Appendix;
import nxt.blockchain.Attachment;
import nxt.Constants;
import nxt.NxtException;
import nxt.blockchain.ChildChain;
import nxt.blockchain.ChildTransaction;
import nxt.blockchain.FxtChain;
import nxt.blockchain.Transaction;
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

public final class ChildTransactionImpl extends TransactionImpl implements ChildTransaction {

    public static final class BuilderImpl extends TransactionImpl.BuilderImpl implements ChildTransaction.Builder {

        private byte[] referencedTransactionFullHash;
        private long fxtTransactionId;
        private Appendix.Message message;
        private Appendix.EncryptedMessage encryptedMessage;
        private Appendix.EncryptToSelfMessage encryptToSelfMessage;
        private Appendix.PublicKeyAnnouncement publicKeyAnnouncement;
        private Appendix.Phasing phasing;
        private Appendix.PrunablePlainMessage prunablePlainMessage;
        private Appendix.PrunableEncryptedMessage prunableEncryptedMessage;

        public BuilderImpl(int chainId, byte version, byte[] senderPublicKey, long amount, long fee, short deadline,
                    Attachment.AbstractAttachment attachment) {
            super(chainId, version, senderPublicKey, amount, fee, deadline, attachment);
        }

        @Override
        public ChildTransactionImpl build(String secretPhrase) throws NxtException.NotValidException {
            List<Appendix.AbstractAppendix> list = new ArrayList<>();
            if (getAttachment() != null) {
                list.add(getAttachment());
            }
            if (this.message != null) {
                list.add(this.message);
            }
            if (this.encryptedMessage != null) {
                list.add(this.encryptedMessage);
            }
            if (this.publicKeyAnnouncement != null) {
                list.add(this.publicKeyAnnouncement);
            }
            if (this.encryptToSelfMessage != null) {
                list.add(this.encryptToSelfMessage);
            }
            if (this.phasing != null) {
                list.add(this.phasing);
            }
            if (this.prunablePlainMessage != null) {
                list.add(this.prunablePlainMessage);
            }
            if (this.prunableEncryptedMessage != null) {
                list.add(this.prunableEncryptedMessage);
            }
            appendages(Collections.unmodifiableList(list));
            preBuild(secretPhrase);
            return new ChildTransactionImpl(this, secretPhrase);
        }

        @Override
        public ChildTransactionImpl build() throws NxtException.NotValidException {
            return build(null);
        }

        @Override
        public BuilderImpl referencedTransactionFullHash(String referencedTransactionFullHash) {
            this.referencedTransactionFullHash = Convert.parseHexString(referencedTransactionFullHash);
            return this;
        }

        BuilderImpl referencedTransactionFullHash(byte[] referencedTransactionFullHash) {
            this.referencedTransactionFullHash = referencedTransactionFullHash;
            return this;
        }

        BuilderImpl fxtTransactionId(long fxtTransactionId) {
            this.fxtTransactionId = fxtTransactionId;
            return this;
        }

        @Override
        public BuilderImpl appendix(Appendix.Message message) {
            this.message = message;
            return this;
        }

        @Override
        public BuilderImpl appendix(Appendix.EncryptedMessage encryptedMessage) {
            this.encryptedMessage = encryptedMessage;
            return this;
        }

        @Override
        public BuilderImpl appendix(Appendix.EncryptToSelfMessage encryptToSelfMessage) {
            this.encryptToSelfMessage = encryptToSelfMessage;
            return this;
        }

        @Override
        public BuilderImpl appendix(Appendix.PublicKeyAnnouncement publicKeyAnnouncement) {
            this.publicKeyAnnouncement = publicKeyAnnouncement;
            return this;
        }

        @Override
        public BuilderImpl appendix(Appendix.PrunablePlainMessage prunablePlainMessage) {
            this.prunablePlainMessage = prunablePlainMessage;
            return this;
        }

        @Override
        public BuilderImpl appendix(Appendix.PrunableEncryptedMessage prunableEncryptedMessage) {
            this.prunableEncryptedMessage = prunableEncryptedMessage;
            return this;
        }

        @Override
        public BuilderImpl appendix(Appendix.Phasing phasing) {
            this.phasing = phasing;
            return this;
        }

        @Override
        BuilderImpl prunableAttachments(JSONObject prunableAttachments) throws NxtException.NotValidException {
            if (prunableAttachments != null) {
                Attachment.ShufflingProcessing shufflingProcessing = Attachment.ShufflingProcessing.parse(prunableAttachments);
                if (shufflingProcessing != null) {
                    appendix(shufflingProcessing);
                    return this;
                }
                Attachment.TaggedDataUpload taggedDataUpload = Attachment.TaggedDataUpload.parse(prunableAttachments);
                if (taggedDataUpload != null) {
                    appendix(taggedDataUpload);
                    return this;
                }
                Attachment.TaggedDataExtend taggedDataExtend = Attachment.TaggedDataExtend.parse(prunableAttachments);
                if (taggedDataExtend != null) {
                    appendix(taggedDataExtend);
                    return this;
                }
                Appendix.PrunablePlainMessage prunablePlainMessage = Appendix.PrunablePlainMessage.parse(prunableAttachments);
                if (prunablePlainMessage != null) {
                    appendix(prunablePlainMessage);
                    return this;
                }
                Appendix.PrunableEncryptedMessage prunableEncryptedMessage = Appendix.PrunableEncryptedMessage.parse(prunableAttachments);
                if (prunableEncryptedMessage != null) {
                    appendix(prunableEncryptedMessage);
                    return this;
                }
            }
            return this;
        }

    }

    private final ChildChain childChain;
    private final long fee;
    private final byte[] signature;
    private final byte[] referencedTransactionFullHash;
    private final Appendix.Message message;
    private final Appendix.EncryptedMessage encryptedMessage;
    private final Appendix.EncryptToSelfMessage encryptToSelfMessage;
    private final Appendix.PublicKeyAnnouncement publicKeyAnnouncement;
    private final Appendix.Phasing phasing;
    private final Appendix.PrunablePlainMessage prunablePlainMessage;
    private final Appendix.PrunableEncryptedMessage prunableEncryptedMessage;

    private volatile FxtTransactionImpl fxtTransaction;
    private volatile long fxtTransactionId;

    private ChildTransactionImpl(BuilderImpl builder, String secretPhrase) throws NxtException.NotValidException {
        super(builder);
        this.childChain = ChildChain.getChildChain(builder.chainId);
        this.referencedTransactionFullHash = builder.referencedTransactionFullHash;
        this.fxtTransactionId = builder.fxtTransactionId;
        this.message  = builder.message;
        this.encryptedMessage = builder.encryptedMessage;
        this.publicKeyAnnouncement = builder.publicKeyAnnouncement;
        this.encryptToSelfMessage = builder.encryptToSelfMessage;
        this.phasing = builder.phasing;
        this.prunablePlainMessage = builder.prunablePlainMessage;
        this.prunableEncryptedMessage = builder.prunableEncryptedMessage;
        if (builder.fee <= 0) {
            //TODO
            throw new NxtException.NotValidException("Minimum fee calculation not yet implemented");
        }
        this.fee = builder.fee;
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
    public ChildChain getChain() {
        return childChain;
    }

    @Override
    public FxtTransactionImpl getFxtTransaction() {
        if (fxtTransaction == null && fxtTransactionId != 0) {
            fxtTransaction = (FxtTransactionImpl) FxtChain.FXT.getTransactionHome().findTransaction(fxtTransactionId);
        }
        return fxtTransaction;
    }

    @Override
    public long getFxtTransactionId() {
        return fxtTransactionId;
    }

    //TODO: set when including in a ChildBlock
    public void setFxtTransaction(FxtTransactionImpl fxtTransaction) {
        this.fxtTransactionId = fxtTransaction.getId();
        this.fxtTransaction = fxtTransaction;
        setBlock(fxtTransaction.getBlock());
    }

    //TODO: unset - when?
    void unsetFxtTransaction() {
        this.fxtTransactionId = 0;
        this.fxtTransaction = null;
        unsetBlock();
    }

    @Override
    public String getReferencedTransactionFullHash() {
        return Convert.toHexString(referencedTransactionFullHash);
    }

    public byte[] referencedTransactionFullHash() {
        return referencedTransactionFullHash;
    }

    @Override
    public Appendix.Message getMessage() {
        return message;
    }

    @Override
    public Appendix.EncryptedMessage getEncryptedMessage() {
        return encryptedMessage;
    }

    @Override
    public Appendix.EncryptToSelfMessage getEncryptToSelfMessage() {
        return encryptToSelfMessage;
    }

    @Override
    public Appendix.Phasing getPhasing() {
        return phasing;
    }

    @Override
    public boolean isPhased() {
        return phasing != null;
    }

    @Override
    public boolean attachmentIsPhased() {
        return attachment.isPhased(this);
    }

    Appendix.PublicKeyAnnouncement getPublicKeyAnnouncement() {
        return publicKeyAnnouncement;
    }

    @Override
    public Appendix.PrunablePlainMessage getPrunablePlainMessage() {
        if (prunablePlainMessage != null) {
            prunablePlainMessage.loadPrunable(this);
        }
        return prunablePlainMessage;
    }

    boolean hasPrunablePlainMessage() {
        return prunablePlainMessage != null;
    }

    @Override
    public Appendix.PrunableEncryptedMessage getPrunableEncryptedMessage() {
        if (prunableEncryptedMessage != null) {
            prunableEncryptedMessage.loadPrunable(this);
        }
        return prunableEncryptedMessage;
    }

    boolean hasPrunableEncryptedMessage() {
        return prunableEncryptedMessage != null;
    }

    @Override
    public long getFee() {
        return fee;
    }

    @Override
    public byte[] getSignature() {
        return signature;
    }

    @Override
    public JSONObject getJSONObject() {
        JSONObject json = super.getJSONObject();
        if (referencedTransactionFullHash != null) {
            json.put("referencedTransactionFullHash", Convert.toHexString(referencedTransactionFullHash));
        }
        return json;
    }

    @Override
    public long getMinimumFeeFQT(int blockchainHeight) {
        long totalFee = super.getMinimumFeeFQT(blockchainHeight);
        if (referencedTransactionFullHash != null) {
            totalFee = Math.addExact(totalFee, Constants.ONE_NXT);
        }
        return totalFee;
    }

    public long[] getMinimumBackFeesFQT(int blockchainHeight) {
        long minimumFee = getMinimumFeeFQT(blockchainHeight);
        if (minimumFee == 0) {
            return Convert.EMPTY_LONG;
        }
        return attachment.getFee(this, blockchainHeight).getBackFees(minimumFee);
    }

    @Override
    boolean hasAllReferencedTransactions(int timestamp, int count) {
        if (referencedTransactionFullHash == null) {
            return timestamp - getTimestamp() < Constants.MAX_REFERENCED_TRANSACTION_TIMESPAN && count < 10;
        }
        //TODO: allow referencing transactions from other chains
        TransactionImpl referencedTransaction = childChain.getTransactionHome().findTransactionByFullHash(referencedTransactionFullHash);
        return referencedTransaction != null
                && referencedTransaction.getHeight() < getHeight()
                && referencedTransaction.hasAllReferencedTransactions(timestamp, count + 1);
    }

    @Override
    ByteBuffer generateBytes() {
        ByteBuffer buffer = super.generateBytes();
        if (referencedTransactionFullHash != null) {
            buffer.put(referencedTransactionFullHash);
        } else {
            buffer.put(new byte[32]);
        }
        return buffer;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ChildTransactionImpl && this.getId() == ((Transaction)o).getId();
    }

    @Override
    public int hashCode() {
        return (int)(getId() ^ (getId() >>> 32));
    }

    @Override
    public void validate() throws NxtException.ValidationException {
        super.validate();
        if (referencedTransactionFullHash != null && referencedTransactionFullHash.length != 32) {
            throw new NxtException.NotValidException("Invalid referenced transaction full hash " + Convert.toHexString(referencedTransactionFullHash));
        }
        boolean validatingAtFinish = phasing != null && getSignature() != null && childChain.getPhasingPollHome().getPoll(getId()) != null;
        for (Appendix.AbstractAppendix appendage : appendages()) {
            appendage.loadPrunable(this);
            if (! appendage.verifyVersion()) {
                throw new NxtException.NotValidException("Invalid attachment version " + appendage.getVersion());
            }
            if (validatingAtFinish) {
                appendage.validateAtFinish(this);
            } else {
                appendage.validate(this);
            }
        }

        if (getFullSize() > Constants.MAX_PAYLOAD_LENGTH) {
            throw new NxtException.NotValidException("Transaction size " + getFullSize() + " exceeds maximum payload size");
        }
        if (!validatingAtFinish) {
            validateEcBlock();
        }
        AccountRestrictions.checkTransaction(this, validatingAtFinish);
    }

    @Override
    public void apply() {
        Account senderAccount = Account.getAccount(getSenderId());
        senderAccount.apply(getSenderPublicKey());
        Account recipientAccount = null;
        if (getRecipientId() != 0) {
            recipientAccount = Account.getAccount(getRecipientId());
            if (recipientAccount == null) {
                recipientAccount = Account.addOrGetAccount(getRecipientId());
            }
        }
        if (referencedTransactionFullHash != null) {
            senderAccount.addToUnconfirmedBalanceFQT(getType().getLedgerEvent(), getId(),
                    0, Constants.UNCONFIRMED_POOL_DEPOSIT_FQT);
        }
        if (attachmentIsPhased()) {
            childChain.getBalanceHome().getBalance(getSenderId()).addToBalance(getType().getLedgerEvent(), getId(), 0, -fee);
        }
        for (Appendix.AbstractAppendix appendage : appendages()) {
            if (!appendage.isPhased(this)) {
                appendage.loadPrunable(this);
                appendage.apply(this, senderAccount, recipientAccount);
            }
        }
    }

    @Override
    int getFlags() {
        int flags = 0;
        int position = 1;
        if (message != null) {
            flags |= position;
        }
        position <<= 1;
        if (encryptedMessage != null) {
            flags |= position;
        }
        position <<= 1;
        if (publicKeyAnnouncement != null) {
            flags |= position;
        }
        position <<= 1;
        if (encryptToSelfMessage != null) {
            flags |= position;
        }
        position <<= 1;
        if (phasing != null) {
            flags |= position;
        }
        position <<= 1;
        if (prunablePlainMessage != null) {
            flags |= position;
        }
        position <<= 1;
        if (prunableEncryptedMessage != null) {
            flags |= position;
        }
        return flags;
    }

    @Override
    void save(Connection con, String schemaTable) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO " + schemaTable
                + " (id, deadline, recipient_id, amount, fee, referenced_transaction_full_hash, height, "
                + "block_id, signature, timestamp, type, subtype, sender_id, attachment_bytes, "
                + "block_timestamp, full_hash, version, has_message, has_encrypted_message, has_public_key_announcement, "
                + "has_encrypttoself_message, phased, has_prunable_message, has_prunable_encrypted_message, "
                + "has_prunable_attachment, ec_block_height, ec_block_id, transaction_index, fxt_transaction_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            int i = 0;
            pstmt.setLong(++i, getId());
            pstmt.setShort(++i, getDeadline());
            DbUtils.setLongZeroToNull(pstmt, ++i, getRecipientId());
            pstmt.setLong(++i, getAmount());
            pstmt.setLong(++i, getFee());
            DbUtils.setBytes(pstmt, ++i, referencedTransactionFullHash());
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
            pstmt.setBytes(++i, fullHash());
            pstmt.setByte(++i, getVersion());
            pstmt.setBoolean(++i, getMessage() != null);
            pstmt.setBoolean(++i, getEncryptedMessage() != null);
            pstmt.setBoolean(++i, getPublicKeyAnnouncement() != null);
            pstmt.setBoolean(++i, getEncryptToSelfMessage() != null);
            pstmt.setBoolean(++i, getPhasing() != null);
            pstmt.setBoolean(++i, hasPrunablePlainMessage());
            pstmt.setBoolean(++i, hasPrunableEncryptedMessage());
            pstmt.setBoolean(++i, getAttachment() instanceof Appendix.Prunable);
            pstmt.setInt(++i, getECBlockHeight());
            DbUtils.setLongZeroToNull(pstmt, ++i, getECBlockId());
            pstmt.setShort(++i, getIndex());
            pstmt.setLong(++i, getFxtTransactionId());
            pstmt.executeUpdate();
        }
        if (referencedTransactionFullHash() != null) {
            try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO referenced_transaction "
                    + "(transaction_id, referenced_transaction_id) VALUES (?, ?)")) {
                pstmt.setLong(1, getId());
                pstmt.setLong(2, Convert.fullHashToId(referencedTransactionFullHash()));
                pstmt.executeUpdate();
            }
        }
    }

    static ChildTransactionImpl loadTransaction(ChildChain childChain, Connection con, ResultSet rs) throws NxtException.NotValidException {
        BuilderImpl builder = (BuilderImpl)TransactionImpl.newTransactionBuilder(childChain, con, rs);
        return builder.build();
    }

    public static ChildTransactionImpl.BuilderImpl newTransactionBuilder(int chainId, byte version, long amount, long fee, short deadline,
                                                                  Attachment.AbstractAttachment attachment, ByteBuffer buffer, Connection con, ResultSet rs) throws NxtException.NotValidException {
        try {
            byte[] referencedTransactionFullHash = rs.getBytes("referenced_transaction_full_hash");
            long fxtTransactionId = rs.getLong("fxt_transaction_id");
            ChildTransactionImpl.BuilderImpl builder = new ChildTransactionImpl.BuilderImpl(chainId, version, null,
                    amount, fee, deadline, attachment);
            builder.referencedTransactionFullHash(referencedTransactionFullHash)
                    .fxtTransactionId(fxtTransactionId);
            if (rs.getBoolean("has_message")) {
                builder.appendix(new Appendix.Message(buffer));
            }
            if (rs.getBoolean("has_encrypted_message")) {
                builder.appendix(new Appendix.EncryptedMessage(buffer));
            }
            if (rs.getBoolean("has_public_key_announcement")) {
                builder.appendix(new Appendix.PublicKeyAnnouncement(buffer));
            }
            if (rs.getBoolean("has_encrypttoself_message")) {
                builder.appendix(new Appendix.EncryptToSelfMessage(buffer));
            }
            if (rs.getBoolean("phased")) {
                builder.appendix(new Appendix.Phasing(buffer));
            }
            if (rs.getBoolean("has_prunable_message")) {
                builder.appendix(new Appendix.PrunablePlainMessage(buffer));
            }
            if (rs.getBoolean("has_prunable_encrypted_message")) {
                builder.appendix(new Appendix.PrunableEncryptedMessage(buffer));
            }
            return builder;
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public static ChildTransactionImpl.BuilderImpl newTransactionBuilder(int chainId, byte version, byte[] senderPublicKey, long amount, long fee, short deadline,
                                                                  Attachment.AbstractAttachment attachment, int flags, ByteBuffer buffer) throws NxtException.NotValidException {
        try {
            ChildTransactionImpl.BuilderImpl childBuilder = new BuilderImpl(chainId, version, senderPublicKey, amount, fee, deadline, attachment);
            int position = 1;
            if ((flags & position) != 0) {
                childBuilder.appendix(new Appendix.Message(buffer));
            }
            position <<= 1;
            if ((flags & position) != 0) {
                childBuilder.appendix(new Appendix.EncryptedMessage(buffer));
            }
            position <<= 1;
            if ((flags & position) != 0) {
                childBuilder.appendix(new Appendix.PublicKeyAnnouncement(buffer));
            }
            position <<= 1;
            if ((flags & position) != 0) {
                childBuilder.appendix(new Appendix.EncryptToSelfMessage(buffer));
            }
            position <<= 1;
            if ((flags & position) != 0) {
                childBuilder.appendix(new Appendix.Phasing(buffer));
            }
            position <<= 1;
            if ((flags & position) != 0) {
                childBuilder.appendix(new Appendix.PrunablePlainMessage(buffer));
            }
            position <<= 1;
            if ((flags & position) != 0) {
                childBuilder.appendix(new Appendix.PrunableEncryptedMessage(buffer));
            }
            byte[] referencedTransactionFullHash = new byte[32];
            buffer.get(referencedTransactionFullHash);
            referencedTransactionFullHash = Convert.emptyToNull(referencedTransactionFullHash);
            childBuilder.referencedTransactionFullHash(referencedTransactionFullHash);
            return childBuilder;
        } catch (NxtException.NotValidException|RuntimeException e) {
            Logger.logDebugMessage("Failed to parse transaction bytes: " + Convert.toHexString(buffer.array()));
            throw e;
        }
    }

    public static ChildTransactionImpl.BuilderImpl newTransactionBuilder(int chainId, byte version, byte[] senderPublicKey, long amount, long fee, short deadline,
                                                                  Attachment.AbstractAttachment attachment, int flags, ByteBuffer buffer, JSONObject prunableAttachments) throws NxtException.NotValidException {
        BuilderImpl builder = newTransactionBuilder(chainId, version, senderPublicKey, amount, fee, deadline, attachment, flags, buffer);
        builder.prunableAttachments(prunableAttachments);
        return builder;
    }

    public static ChildTransactionImpl.BuilderImpl newTransactionBuilder(int chainId, byte version, byte[] senderPublicKey, long amount, long fee, short deadline,
                                                                         Attachment.AbstractAttachment attachment, JSONObject attachmentData, JSONObject transactionData) throws NxtException.NotValidException {
        try {
            ChildTransactionImpl.BuilderImpl childBuilder = new BuilderImpl(chainId, version, senderPublicKey, amount, fee, deadline, attachment);
            String referencedTransactionFullHash = (String) transactionData.get("referencedTransactionFullHash");
            childBuilder.referencedTransactionFullHash(referencedTransactionFullHash);
            if (attachmentData != null) {
                childBuilder.appendix(Appendix.Message.parse(attachmentData));
                childBuilder.appendix(Appendix.EncryptedMessage.parse(attachmentData));
                childBuilder.appendix(Appendix.PublicKeyAnnouncement.parse(attachmentData));
                childBuilder.appendix(Appendix.EncryptToSelfMessage.parse(attachmentData));
                childBuilder.appendix(Appendix.Phasing.parse(attachmentData));
                childBuilder.appendix(Appendix.PrunablePlainMessage.parse(attachmentData));
                childBuilder.appendix(Appendix.PrunableEncryptedMessage.parse(attachmentData));
            }
            return childBuilder;
        } catch (RuntimeException e) {
            Logger.logDebugMessage("Failed to parse transaction: " + transactionData.toJSONString());
            throw e;
        }
    }

}
