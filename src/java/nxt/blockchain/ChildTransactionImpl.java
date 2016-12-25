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
import nxt.account.AccountLedger;
import nxt.account.AccountRestrictions;
import nxt.account.PublicKeyAnnouncementAppendix;
import nxt.crypto.Crypto;
import nxt.db.DbUtils;
import nxt.messaging.EncryptToSelfMessageAppendix;
import nxt.messaging.EncryptedMessageAppendix;
import nxt.messaging.MessageAppendix;
import nxt.messaging.PrunableEncryptedMessageAppendix;
import nxt.messaging.PrunablePlainMessageAppendix;
import nxt.shuffling.ShufflingProcessingAttachment;
import nxt.taggeddata.TaggedDataExtendAttachment;
import nxt.taggeddata.TaggedDataUploadAttachment;
import nxt.util.Convert;
import nxt.util.Logger;
import nxt.voting.PhasingAppendix;
import nxt.voting.PhasingPollHome;
import org.json.simple.JSONObject;

import java.math.BigInteger;
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

        private ChainTransactionId referencedTransactionId;
        private long fxtTransactionId;
        private long feeRateNQTPerFXT;
        private MessageAppendix message;
        private EncryptedMessageAppendix encryptedMessage;
        private EncryptToSelfMessageAppendix encryptToSelfMessage;
        private PublicKeyAnnouncementAppendix publicKeyAnnouncement;
        private PhasingAppendix phasing;
        private PrunablePlainMessageAppendix prunablePlainMessage;
        private PrunableEncryptedMessageAppendix prunableEncryptedMessage;

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
        public BuilderImpl referencedTransaction(ChainTransactionId referencedTransactionId) {
            this.referencedTransactionId = referencedTransactionId;
            return this;
        }

        BuilderImpl fxtTransactionId(long fxtTransactionId) {
            this.fxtTransactionId = fxtTransactionId;
            return this;
        }

        @Override
        public BuilderImpl feeRateNQTPerFXT(long feeRateNQTPerFXT) {
            this.feeRateNQTPerFXT = feeRateNQTPerFXT;
            return this;
        }

        @Override
        public BuilderImpl appendix(MessageAppendix message) {
            this.message = message;
            return this;
        }

        @Override
        public BuilderImpl appendix(EncryptedMessageAppendix encryptedMessage) {
            this.encryptedMessage = encryptedMessage;
            return this;
        }

        @Override
        public BuilderImpl appendix(EncryptToSelfMessageAppendix encryptToSelfMessage) {
            this.encryptToSelfMessage = encryptToSelfMessage;
            return this;
        }

        @Override
        public BuilderImpl appendix(PublicKeyAnnouncementAppendix publicKeyAnnouncement) {
            this.publicKeyAnnouncement = publicKeyAnnouncement;
            return this;
        }

        @Override
        public BuilderImpl appendix(PrunablePlainMessageAppendix prunablePlainMessage) {
            this.prunablePlainMessage = prunablePlainMessage;
            return this;
        }

        @Override
        public BuilderImpl appendix(PrunableEncryptedMessageAppendix prunableEncryptedMessage) {
            this.prunableEncryptedMessage = prunableEncryptedMessage;
            return this;
        }

        @Override
        public BuilderImpl appendix(PhasingAppendix phasing) {
            this.phasing = phasing;
            return this;
        }

        @Override
        BuilderImpl prunableAttachments(JSONObject prunableAttachments) throws NxtException.NotValidException {
            if (prunableAttachments != null) {
                ShufflingProcessingAttachment shufflingProcessing = ShufflingProcessingAttachment.parse(prunableAttachments);
                if (shufflingProcessing != null) {
                    appendix(shufflingProcessing);
                    return this;
                }
                TaggedDataUploadAttachment taggedDataUpload = TaggedDataUploadAttachment.parse(prunableAttachments);
                if (taggedDataUpload != null) {
                    appendix(taggedDataUpload);
                    return this;
                }
                TaggedDataExtendAttachment taggedDataExtend = TaggedDataExtendAttachment.parse(prunableAttachments);
                if (taggedDataExtend != null) {
                    appendix(taggedDataExtend);
                    return this;
                }
                PrunablePlainMessageAppendix prunablePlainMessage = PrunablePlainMessageAppendix.parse(prunableAttachments);
                if (prunablePlainMessage != null) {
                    appendix(prunablePlainMessage);
                    return this;
                }
                PrunableEncryptedMessageAppendix prunableEncryptedMessage = PrunableEncryptedMessageAppendix.parse(prunableAttachments);
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
    private final ChainTransactionId referencedTransactionId;
    private final MessageAppendix message;
    private final EncryptedMessageAppendix encryptedMessage;
    private final EncryptToSelfMessageAppendix encryptToSelfMessage;
    private final PublicKeyAnnouncementAppendix publicKeyAnnouncement;
    private final PhasingAppendix phasing;
    private final PrunablePlainMessageAppendix prunablePlainMessage;
    private final PrunableEncryptedMessageAppendix prunableEncryptedMessage;

    private volatile long fxtTransactionId;

    private ChildTransactionImpl(BuilderImpl builder, String secretPhrase) throws NxtException.NotValidException {
        super(builder);
        this.childChain = ChildChain.getChildChain(builder.chainId);
        this.referencedTransactionId = builder.referencedTransactionId;
        this.fxtTransactionId = builder.fxtTransactionId;
        this.message  = builder.message;
        this.encryptedMessage = builder.encryptedMessage;
        this.publicKeyAnnouncement = builder.publicKeyAnnouncement;
        this.encryptToSelfMessage = builder.encryptToSelfMessage;
        this.phasing = builder.phasing;
        this.prunablePlainMessage = builder.prunablePlainMessage;
        this.prunableEncryptedMessage = builder.prunableEncryptedMessage;
        if (builder.fee <= 0) {
            long minFeeFQT = getMinimumFeeFQT(Nxt.getBlockchain().getHeight());
            if (builder.feeRateNQTPerFXT <= 0) {
                throw new NxtException.NotValidException(String.format("Please include fee in %s equivalent to at least %f %s",
                        childChain.getName(), ((double) minFeeFQT) / Constants.ONE_NXT, FxtChain.FXT.getName()));
            }
            BigInteger[] fee = BigInteger.valueOf(minFeeFQT).multiply(BigInteger.valueOf(builder.feeRateNQTPerFXT))
                    .divideAndRemainder(BigInteger.valueOf(Constants.ONE_NXT));
            this.fee = fee[1].equals(BigInteger.ZERO) ? fee[0].longValueExact() : fee[0].longValueExact() + 1;
        } else {
            this.fee = builder.fee;
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
    public ChildChain getChain() {
        return childChain;
    }

    @Override
    public long getFxtTransactionId() {
        return fxtTransactionId;
    }

    void setFxtTransaction(ChildBlockFxtTransactionImpl fxtTransaction) {
        this.fxtTransactionId = fxtTransaction.getId();
        setBlock(fxtTransaction.getBlock());
    }

    void unsetFxtTransaction() {
        this.fxtTransactionId = 0;
        unsetBlock();
        setIndex(-1);
    }

    @Override
    public ChainTransactionId getReferencedTransactionId() {
        return referencedTransactionId;
    }

    @Override
    public MessageAppendix getMessage() {
        return message;
    }

    @Override
    public EncryptedMessageAppendix getEncryptedMessage() {
        return encryptedMessage;
    }

    @Override
    public EncryptToSelfMessageAppendix getEncryptToSelfMessage() {
        return encryptToSelfMessage;
    }

    @Override
    public PhasingAppendix getPhasing() {
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

    @Override
    public PrunablePlainMessageAppendix getPrunablePlainMessage() {
        if (prunablePlainMessage != null) {
            prunablePlainMessage.loadPrunable(this);
        }
        return prunablePlainMessage;
    }

    @Override
    public PrunableEncryptedMessageAppendix getPrunableEncryptedMessage() {
        if (prunableEncryptedMessage != null) {
            prunableEncryptedMessage.loadPrunable(this);
        }
        return prunableEncryptedMessage;
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
        if (referencedTransactionId != null) {
            json.put("referencedTransaction", referencedTransactionId.getJSON());
        }
        return json;
    }

    @Override
    public long getMinimumFeeFQT(int blockchainHeight) {
        long totalFee = super.getMinimumFeeFQT(blockchainHeight);
        if (referencedTransactionId != null) {
            totalFee = Math.addExact(totalFee, Constants.ONE_NXT);
        }
        return totalFee;
    }

    @Override
    boolean hasAllReferencedTransactions(int timestamp, int count) {
        if (referencedTransactionId == null) {
            return timestamp - getTimestamp() < Constants.MAX_REFERENCED_TRANSACTION_TIMESPAN && count < 10;
        }
        TransactionImpl referencedTransaction = (TransactionImpl)referencedTransactionId.getTransaction();
        return referencedTransaction != null
                && referencedTransaction.getHeight() < getHeight()
                && referencedTransaction.hasAllReferencedTransactions(timestamp, count + 1);
    }

    @Override
    ByteBuffer generateBytes() {
        ByteBuffer buffer = super.generateBytes();
        if (referencedTransactionId != null) {
            referencedTransactionId.put(buffer);
        } else {
            buffer.putInt(0);
            buffer.put(new byte[32]);
        }
        return buffer;
    }

    @Override
    protected int getSize() {
        return super.getSize() + ChainTransactionId.BYTE_SIZE;
    }

    @Override
    public final boolean equals(Object o) {
        return o instanceof ChildTransactionImpl && this.getId() == ((Transaction)o).getId()
                && Arrays.equals(this.getFullHash(), ((Transaction)o).getFullHash());
    }

    @Override
    public final int hashCode() {
        return (int)(getId() ^ (getId() >>> 32));
    }

    @Override
    public void validate() throws NxtException.ValidationException {
        super.validate();
        if (ChildTransactionType.findTransactionType(getType().getType(), getType().getSubtype()) == null) {
            throw new NxtException.NotValidException("Invalid transaction type " + getType().getName() + " for ChildTransaction");
        }
        if (referencedTransactionId != null) {
            if (referencedTransactionId.getFullHash().length != 32) {
                throw new NxtException.NotValidException("Invalid referenced transaction full hash " + Convert.toHexString(referencedTransactionId.getFullHash()));
            }
            if (referencedTransactionId.getChain() == null) {
                throw new NxtException.NotValidException("Invalid referenced transaction chain " + referencedTransactionId.getChainId());
            }
        }
        boolean validatingAtFinish = phasing != null && getSignature() != null && childChain.getPhasingPollHome().getPoll(getFullHash()) != null;
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
    protected void validateId() throws NxtException.ValidationException {
        super.validateId();
        if (PhasingPollHome.hasUnfinishedPhasedTransaction(getId())) {
            throw new NxtException.NotCurrentlyValidException("Phased transaction currently exists with the same id");
        }
        for (Appendix.AbstractAppendix appendage : appendages()) {
            appendage.validateId(this);
        }
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
        AccountLedger.LedgerEventId eventId = AccountLedger.newEventId(this);
        if (referencedTransactionId != null) {
            senderAccount.addToUnconfirmedBalance(FxtChain.FXT, getType().getLedgerEvent(), eventId, (long) 0, Constants.UNCONFIRMED_POOL_DEPOSIT_FQT);
        }
        if (attachmentIsPhased()) {
            childChain.getBalanceHome().getBalance(getSenderId()).addToBalance(getType().getLedgerEvent(), eventId, 0, -fee);
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
                + " (id, deadline, recipient_id, amount, fee, referenced_transaction_chain_id, referenced_transaction_full_hash, referenced_transaction_id, "
                + "height, block_id, signature, timestamp, type, subtype, sender_id, attachment_bytes, "
                + "block_timestamp, full_hash, version, has_message, has_encrypted_message, has_public_key_announcement, "
                + "has_encrypttoself_message, phased, has_prunable_message, has_prunable_encrypted_message, "
                + "has_prunable_attachment, ec_block_height, ec_block_id, transaction_index, fxt_transaction_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            int i = 0;
            pstmt.setLong(++i, getId());
            pstmt.setShort(++i, getDeadline());
            DbUtils.setLongZeroToNull(pstmt, ++i, getRecipientId());
            pstmt.setLong(++i, getAmount());
            pstmt.setLong(++i, getFee());
            if (referencedTransactionId != null) {
                pstmt.setInt(++i, referencedTransactionId.getChainId());
                pstmt.setBytes(++i, referencedTransactionId.getFullHash());
                pstmt.setLong(++i, referencedTransactionId.getTransactionId());
            } else {
                pstmt.setNull(++i, Types.INTEGER);
                pstmt.setNull(++i, Types.BINARY);
                pstmt.setNull(++i, Types.BIGINT);
            }
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
            pstmt.setBoolean(++i, message != null);
            pstmt.setBoolean(++i, encryptedMessage != null);
            pstmt.setBoolean(++i, publicKeyAnnouncement != null);
            pstmt.setBoolean(++i, encryptToSelfMessage != null);
            pstmt.setBoolean(++i, phasing != null);
            pstmt.setBoolean(++i, prunablePlainMessage != null);
            pstmt.setBoolean(++i, prunableEncryptedMessage != null);
            pstmt.setBoolean(++i, getAttachment() instanceof Appendix.Prunable);
            pstmt.setInt(++i, getECBlockHeight());
            DbUtils.setLongZeroToNull(pstmt, ++i, getECBlockId());
            pstmt.setShort(++i, getIndex());
            pstmt.setLong(++i, getFxtTransactionId());
            pstmt.executeUpdate();
        }
    }

    @Override
    final UnconfirmedChildTransaction newUnconfirmedTransaction(long arrivalTimestamp) {
        return new UnconfirmedChildTransaction(this, arrivalTimestamp);
    }

    static ChildTransactionImpl loadTransaction(ChildChain childChain, Connection con, ResultSet rs) throws NxtException.NotValidException {
        BuilderImpl builder = (BuilderImpl)TransactionImpl.newTransactionBuilder(childChain, con, rs);
        return builder.build();
    }

    public static ChildTransactionImpl.BuilderImpl newTransactionBuilder(int chainId, byte version, long amount, long fee, short deadline,
                                                                  Attachment.AbstractAttachment attachment, ByteBuffer buffer, Connection con, ResultSet rs) throws NxtException.NotValidException {
        try {
            ChildTransactionImpl.BuilderImpl builder = new ChildTransactionImpl.BuilderImpl(chainId, version, null,
                    amount, fee, deadline, attachment);
            byte[] referencedTransactionFullHash = rs.getBytes("referenced_transaction_full_hash");
            if (referencedTransactionFullHash != null) {
                int referencedTransactionChainId = rs.getInt("referenced_transaction_chain_id");
                builder.referencedTransaction(new ChainTransactionId(referencedTransactionChainId, referencedTransactionFullHash));
            }
            long fxtTransactionId = rs.getLong("fxt_transaction_id");
            builder.fxtTransactionId(fxtTransactionId);
            if (rs.getBoolean("has_message")) {
                builder.appendix(new MessageAppendix(buffer));
            }
            if (rs.getBoolean("has_encrypted_message")) {
                builder.appendix(new EncryptedMessageAppendix(buffer));
            }
            if (rs.getBoolean("has_public_key_announcement")) {
                builder.appendix(new PublicKeyAnnouncementAppendix(buffer));
            }
            if (rs.getBoolean("has_encrypttoself_message")) {
                builder.appendix(new EncryptToSelfMessageAppendix(buffer));
            }
            if (rs.getBoolean("phased")) {
                builder.appendix(new PhasingAppendix(buffer));
            }
            if (rs.getBoolean("has_prunable_message")) {
                builder.appendix(new PrunablePlainMessageAppendix(buffer));
            }
            if (rs.getBoolean("has_prunable_encrypted_message")) {
                builder.appendix(new PrunableEncryptedMessageAppendix(buffer));
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
                childBuilder.appendix(new MessageAppendix(buffer));
            }
            position <<= 1;
            if ((flags & position) != 0) {
                childBuilder.appendix(new EncryptedMessageAppendix(buffer));
            }
            position <<= 1;
            if ((flags & position) != 0) {
                childBuilder.appendix(new PublicKeyAnnouncementAppendix(buffer));
            }
            position <<= 1;
            if ((flags & position) != 0) {
                childBuilder.appendix(new EncryptToSelfMessageAppendix(buffer));
            }
            position <<= 1;
            if ((flags & position) != 0) {
                childBuilder.appendix(new PhasingAppendix(buffer));
            }
            position <<= 1;
            if ((flags & position) != 0) {
                childBuilder.appendix(new PrunablePlainMessageAppendix(buffer));
            }
            position <<= 1;
            if ((flags & position) != 0) {
                childBuilder.appendix(new PrunableEncryptedMessageAppendix(buffer));
            }
            ChainTransactionId referencedTransaction = ChainTransactionId.parse(buffer);
            childBuilder.referencedTransaction(referencedTransaction);
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
                                                                         Attachment.AbstractAttachment attachment, JSONObject attachmentData, JSONObject transactionData) {
        try {
            ChildTransactionImpl.BuilderImpl childBuilder = new BuilderImpl(chainId, version, senderPublicKey, amount, fee, deadline, attachment);
            ChainTransactionId referencedTransaction = ChainTransactionId.parse((JSONObject)transactionData.get("referencedTransaction"));
            childBuilder.referencedTransaction(referencedTransaction);
            if (attachmentData != null) {
                childBuilder.appendix(MessageAppendix.parse(attachmentData));
                childBuilder.appendix(EncryptedMessageAppendix.parse(attachmentData));
                childBuilder.appendix(PublicKeyAnnouncementAppendix.parse(attachmentData));
                childBuilder.appendix(EncryptToSelfMessageAppendix.parse(attachmentData));
                childBuilder.appendix(PhasingAppendix.parse(attachmentData));
                childBuilder.appendix(PrunablePlainMessageAppendix.parse(attachmentData));
                childBuilder.appendix(PrunableEncryptedMessageAppendix.parse(attachmentData));
            }
            return childBuilder;
        } catch (RuntimeException e) {
            Logger.logDebugMessage("Failed to parse transaction: " + transactionData.toJSONString());
            throw e;
        }
    }

}
