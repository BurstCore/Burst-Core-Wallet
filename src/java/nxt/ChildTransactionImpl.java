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

final class ChildTransactionImpl extends TransactionImpl implements ChildTransaction {

    static final class BuilderImpl extends TransactionImpl.BuilderImpl implements ChildTransaction.Builder {

        private final ChildChain childChain;
        private byte[] referencedTransactionFullHash;
        private long fxtTransactionId;
        private List<Appendix.AbstractAppendix> appendages;
        private Appendix.Message message;
        private Appendix.EncryptedMessage encryptedMessage;
        private Appendix.EncryptToSelfMessage encryptToSelfMessage;
        private Appendix.PublicKeyAnnouncement publicKeyAnnouncement;
        private Appendix.Phasing phasing;
        private Appendix.PrunablePlainMessage prunablePlainMessage;
        private Appendix.PrunableEncryptedMessage prunableEncryptedMessage;

        BuilderImpl(ChildChain childChain, byte version, byte[] senderPublicKey, long amountNQT, long feeNQT, short deadline,
                    Attachment.AbstractAttachment attachment) {
            super(version, senderPublicKey, amountNQT, feeNQT, deadline, attachment);
            this.childChain = childChain;
        }

        @Override
        public ChildTransactionImpl build(String secretPhrase) throws NxtException.NotValidException {
            preBuild(secretPhrase);
            return new ChildTransactionImpl(this, secretPhrase);
        }

        @Override
        public ChildTransactionImpl build() throws NxtException.NotValidException {
            return build(null);
        }

        void preBuild(String secretPhrase) throws NxtException.NotValidException {
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
            this.appendages = Collections.unmodifiableList(list);
            super.preBuild(secretPhrase);
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
        List<Appendix.AbstractAppendix> getAppendages() {
            return appendages;
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
        this.childChain = builder.childChain;
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
        /*
        if (builder.fee <= 0 || (Constants.correctInvalidFees && builder.signature == null)) {
            int effectiveHeight = (getHeight() < Integer.MAX_VALUE ? getHeight() : Nxt.getBlockchain().getHeight());
            long minFee = getMinimumFeeFQT(effectiveHeight);
            this.feeNQT = Math.max(minFee, builder.fee);
        } else {
            this.feeNQT = builder.fee;
        }
        */
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
            fxtTransaction = (FxtTransactionImpl)FxtChain.FXT.getTransactionHome().findChainTransaction(fxtTransactionId);
        }
        return fxtTransaction;
    }

    @Override
    public long getFxtTransactionId() {
        return fxtTransactionId;
    }

    //TODO: set when including in a ChildBlock
    void setFxtTransaction(FxtTransactionImpl fxtTransaction) {
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

    @Override
    byte[] referencedTransactionFullHash() {
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
    boolean attachmentIsPhased() {
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
        json.put("chain", childChain.getId());
        if (referencedTransactionFullHash != null) {
            json.put("referencedTransactionFullHash", Convert.toHexString(referencedTransactionFullHash));
        }
        return json;
    }

    @Override
    long getMinimumFeeFQT(int blockchainHeight) {
        long totalFee = super.getMinimumFeeFQT(blockchainHeight);
        if (referencedTransactionFullHash != null) {
            totalFee = Math.addExact(totalFee, Constants.ONE_NXT);
        }
        return totalFee;
    }

    @Override
    ByteBuffer generateBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(getSize());
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(childChain.getId());
        buffer.put(getType().getType());
        buffer.put(getType().getSubtype());
        buffer.put(getVersion());
        buffer.putInt(getTimestamp());
        buffer.putShort(getDeadline());
        buffer.put(getSenderPublicKey());
        buffer.putLong(getType().canHaveRecipient() ? getRecipientId() : Genesis.CREATOR_ID);
        buffer.putLong(getAmount());
        buffer.putLong(getFee());
        if (referencedTransactionFullHash != null) {
            buffer.put(referencedTransactionFullHash);
        } else {
            buffer.put(new byte[32]);
        }
        buffer.put(getSignature() != null ? getSignature() : new byte[64]);
        buffer.putInt(getFlags());
        buffer.putInt(getECBlockHeight());
        buffer.putLong(getECBlockId());
        for (Appendix appendage : appendages()) {
            appendage.putBytes(buffer);
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
        AccountRestrictions.checkTransaction(this, validatingAtFinish);
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
        if (referencedTransactionFullHash != null) {
            childChain.getBalanceHome().getBalance(getSenderId()).addToUnconfirmedBalance(getType().getLedgerEvent(), getId(),
                    0, Constants.UNCONFIRMED_POOL_DEPOSIT_NQT);
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
    int signatureOffset() {
        return 4 + 1 + 1 + 1 + 4 + 2 + 32 + 8 + 8 + 8 + 32;
    }

    private int getFlags() {
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
        try {
            byte type = rs.getByte("type");
            byte subtype = rs.getByte("subtype");
            int timestamp = rs.getInt("timestamp");
            short deadline = rs.getShort("deadline");
            long amountNQT = rs.getLong("amount");
            long feeNQT = rs.getLong("fee");
            byte[] referencedTransactionFullHash = rs.getBytes("referenced_transaction_full_hash");
            int ecBlockHeight = rs.getInt("ec_block_height");
            long ecBlockId = rs.getLong("ec_block_id");
            byte[] signature = rs.getBytes("signature");
            long blockId = rs.getLong("block_id");
            int height = rs.getInt("height");
            long id = rs.getLong("id");
            long senderId = rs.getLong("sender_id");
            byte[] attachmentBytes = rs.getBytes("attachment_bytes");
            int blockTimestamp = rs.getInt("block_timestamp");
            byte[] fullHash = rs.getBytes("full_hash");
            byte version = rs.getByte("version");
            short transactionIndex = rs.getShort("transaction_index");
            long fxtTransactionId = rs.getLong("fxt_transaction_id");

            ByteBuffer buffer = null;
            if (attachmentBytes != null) {
                buffer = ByteBuffer.wrap(attachmentBytes);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
            }

            TransactionType transactionType = ChildTransactionType.findTransactionType(type, subtype);
            ChildTransactionImpl.BuilderImpl builder = new ChildTransactionImpl.BuilderImpl(childChain, version, null,
                    amountNQT, feeNQT, deadline, transactionType.parseAttachment(buffer));
            builder.referencedTransactionFullHash(referencedTransactionFullHash)
                    .fxtTransactionId(fxtTransactionId)
                    .timestamp(timestamp)
                    .signature(signature)
                    .blockId(blockId)
                    .height(height)
                    .id(id)
                    .senderId(senderId)
                    .blockTimestamp(blockTimestamp)
                    .fullHash(fullHash)
                    .ecBlockHeight(ecBlockHeight)
                    .ecBlockId(ecBlockId)
                    .index(transactionIndex);
            if (transactionType.canHaveRecipient()) {
                long recipientId = rs.getLong("recipient_id");
                if (! rs.wasNull()) {
                    builder.recipientId(recipientId);
                }
            }
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
            return builder.build();
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static ChildTransactionImpl parseTransaction(JSONObject transactionData) throws NxtException.NotValidException {
        ChildTransactionImpl transaction = newTransactionBuilder(transactionData).build();
        if (transaction.getSignature() != null && !transaction.checkSignature()) {
            throw new NxtException.NotValidException("Invalid transaction signature for transaction " + transaction.getJSONObject().toJSONString());
        }
        return transaction;
    }

    static ChildTransactionImpl.BuilderImpl newTransactionBuilder(byte[] bytes) throws NxtException.NotValidException {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            int chainId = buffer.getInt();
            byte type = buffer.get();
            byte subtype = buffer.get();
            byte version = buffer.get();
            int timestamp = buffer.getInt();
            short deadline = buffer.getShort();
            byte[] senderPublicKey = new byte[32];
            buffer.get(senderPublicKey);
            long recipientId = buffer.getLong();
            long amountNQT = buffer.getLong();
            long feeNQT = buffer.getLong();
            byte[] referencedTransactionFullHash = new byte[32];
            buffer.get(referencedTransactionFullHash);
            referencedTransactionFullHash = Convert.emptyToNull(referencedTransactionFullHash);
            byte[] signature = new byte[64];
            buffer.get(signature);
            signature = Convert.emptyToNull(signature);
            int flags = 0;
            int ecBlockHeight = 0;
            long ecBlockId = 0;
            if (version > 0) {
                flags = buffer.getInt();
                ecBlockHeight = buffer.getInt();
                ecBlockId = buffer.getLong();
            }
            TransactionType transactionType = ChildTransactionType.findTransactionType(type, subtype);
            ChildTransactionImpl.BuilderImpl builder = new BuilderImpl(ChildChain.getChildChain(chainId), version, senderPublicKey, amountNQT, feeNQT,
                    deadline, transactionType.parseAttachment(buffer));
            builder.referencedTransactionFullHash(referencedTransactionFullHash)
                    .timestamp(timestamp)
                    .signature(signature)
                    .ecBlockHeight(ecBlockHeight)
                    .ecBlockId(ecBlockId);
            if (transactionType.canHaveRecipient()) {
                builder.recipientId(recipientId);
            }
            int position = 1;
            if ((flags & position) != 0 || (version == 0 && transactionType == ChildTransactionType.Messaging.ARBITRARY_MESSAGE)) {
                builder.appendix(new Appendix.Message(buffer));
            }
            position <<= 1;
            if ((flags & position) != 0) {
                builder.appendix(new Appendix.EncryptedMessage(buffer));
            }
            position <<= 1;
            if ((flags & position) != 0) {
                builder.appendix(new Appendix.PublicKeyAnnouncement(buffer));
            }
            position <<= 1;
            if ((flags & position) != 0) {
                builder.appendix(new Appendix.EncryptToSelfMessage(buffer));
            }
            position <<= 1;
            if ((flags & position) != 0) {
                builder.appendix(new Appendix.Phasing(buffer));
            }
            position <<= 1;
            if ((flags & position) != 0) {
                builder.appendix(new Appendix.PrunablePlainMessage(buffer));
            }
            position <<= 1;
            if ((flags & position) != 0) {
                builder.appendix(new Appendix.PrunableEncryptedMessage(buffer));
            }
            if (buffer.hasRemaining()) {
                throw new NxtException.NotValidException("Transaction bytes too long, " + buffer.remaining() + " extra bytes");
            }
            return builder;
        } catch (NxtException.NotValidException|RuntimeException e) {
            Logger.logDebugMessage("Failed to parse transaction bytes: " + Convert.toHexString(bytes));
            throw e;
        }
    }

    static ChildTransactionImpl.BuilderImpl newTransactionBuilder(byte[] bytes, JSONObject prunableAttachments) throws NxtException.NotValidException {
        BuilderImpl builder = newTransactionBuilder(bytes);
        if (prunableAttachments != null) {
            Attachment.ShufflingProcessing shufflingProcessing = Attachment.ShufflingProcessing.parse(prunableAttachments);
            if (shufflingProcessing != null) {
                builder.appendix(shufflingProcessing);
            }
            Attachment.TaggedDataUpload taggedDataUpload = Attachment.TaggedDataUpload.parse(prunableAttachments);
            if (taggedDataUpload != null) {
                builder.appendix(taggedDataUpload);
            }
            Attachment.TaggedDataExtend taggedDataExtend = Attachment.TaggedDataExtend.parse(prunableAttachments);
            if (taggedDataExtend != null) {
                builder.appendix(taggedDataExtend);
            }
            Appendix.PrunablePlainMessage prunablePlainMessage = Appendix.PrunablePlainMessage.parse(prunableAttachments);
            if (prunablePlainMessage != null) {
                builder.appendix(prunablePlainMessage);
            }
            Appendix.PrunableEncryptedMessage prunableEncryptedMessage = Appendix.PrunableEncryptedMessage.parse(prunableAttachments);
            if (prunableEncryptedMessage != null) {
                builder.appendix(prunableEncryptedMessage);
            }
        }
        return builder;
    }

    static ChildTransactionImpl.BuilderImpl newTransactionBuilder(JSONObject transactionData) throws NxtException.NotValidException {
        try {
            int chainId = ((Long) transactionData.get("chain")).intValue();
            byte type = ((Long) transactionData.get("type")).byteValue();
            byte subtype = ((Long) transactionData.get("subtype")).byteValue();
            int timestamp = ((Long) transactionData.get("timestamp")).intValue();
            short deadline = ((Long) transactionData.get("deadline")).shortValue();
            byte[] senderPublicKey = Convert.parseHexString((String) transactionData.get("senderPublicKey"));
            long amountNQT = Convert.parseLong(transactionData.get("amountNQT"));
            long feeNQT = Convert.parseLong(transactionData.get("feeNQT"));
            String referencedTransactionFullHash = (String) transactionData.get("referencedTransactionFullHash");
            byte[] signature = Convert.parseHexString((String) transactionData.get("signature"));
            Long versionValue = (Long) transactionData.get("version");
            byte version = versionValue == null ? 0 : versionValue.byteValue();
            JSONObject attachmentData = (JSONObject) transactionData.get("attachment");
            int ecBlockHeight = 0;
            long ecBlockId = 0;
            if (version > 0) {
                ecBlockHeight = ((Long) transactionData.get("ecBlockHeight")).intValue();
                ecBlockId = Convert.parseUnsignedLong((String) transactionData.get("ecBlockId"));
            }

            TransactionType transactionType = ChildTransactionType.findTransactionType(type, subtype);
            if (transactionType == null) {
                throw new NxtException.NotValidException("Invalid transaction type: " + type + ", " + subtype);
            }
            ChildTransactionImpl.BuilderImpl builder = new BuilderImpl(ChildChain.getChildChain(chainId), version, senderPublicKey,
                    amountNQT, feeNQT, deadline,
                    transactionType.parseAttachment(attachmentData));
            builder.referencedTransactionFullHash(referencedTransactionFullHash)
                    .timestamp(timestamp)
                    .signature(signature)
                    .ecBlockHeight(ecBlockHeight)
                    .ecBlockId(ecBlockId);
            if (transactionType.canHaveRecipient()) {
                long recipientId = Convert.parseUnsignedLong((String) transactionData.get("recipient"));
                builder.recipientId(recipientId);
            }
            if (attachmentData != null) {
                builder.appendix(Appendix.Message.parse(attachmentData));
                builder.appendix(Appendix.EncryptedMessage.parse(attachmentData));
                builder.appendix((Appendix.PublicKeyAnnouncement.parse(attachmentData)));
                builder.appendix(Appendix.EncryptToSelfMessage.parse(attachmentData));
                builder.appendix(Appendix.Phasing.parse(attachmentData));
                builder.appendix(Appendix.PrunablePlainMessage.parse(attachmentData));
                builder.appendix(Appendix.PrunableEncryptedMessage.parse(attachmentData));
            }
            return builder;
        } catch (NxtException.NotValidException|RuntimeException e) {
            Logger.logDebugMessage("Failed to parse transaction: " + transactionData.toJSONString());
            throw e;
        }
    }

}
