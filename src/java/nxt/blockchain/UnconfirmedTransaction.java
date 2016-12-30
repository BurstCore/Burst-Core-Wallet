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

import nxt.Nxt;
import nxt.NxtException;
import nxt.db.DbKey;
import nxt.messaging.PrunableEncryptedMessageAppendix;
import nxt.messaging.PrunablePlainMessageAppendix;
import nxt.util.Filter;
import nxt.util.JSON;
import org.json.simple.JSONObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

public abstract class UnconfirmedTransaction implements Transaction {

    static UnconfirmedTransaction load(ResultSet rs) throws SQLException {
        Chain chain = Chain.getChain(rs.getInt("chain_id"));
        try {
            return chain.newUnconfirmedTransaction(rs);
        } catch (NxtException.NotValidException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private final TransactionImpl transaction;
    private final DbKey dbKey;
    private final long arrivalTimestamp;
    private final long feePerByte;

    UnconfirmedTransaction(TransactionImpl transaction, long arrivalTimestamp) {
        this.transaction = transaction;
        this.arrivalTimestamp = arrivalTimestamp;
        this.feePerByte = transaction.getFee() / transaction.getFullSize();
        this.dbKey = TransactionProcessorImpl.getInstance().unconfirmedTransactionDbKeyFactory.newKey(transaction.getId());
    }

    UnconfirmedTransaction(TransactionImpl.BuilderImpl builder,  ResultSet rs) throws SQLException {
        try {
            this.transaction = builder.build();
            this.transaction.setHeight(rs.getInt("transaction_height"));
            this.arrivalTimestamp = rs.getLong("arrival_timestamp");
            this.feePerByte = rs.getLong("fee_per_byte");
            this.dbKey = TransactionProcessorImpl.getInstance().unconfirmedTransactionDbKeyFactory.newKey(transaction.getId());
        } catch (NxtException.ValidationException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    void save(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO unconfirmed_transaction (id, transaction_height, "
                + "fee_per_byte, expiration, transaction_bytes, prunable_json, arrival_timestamp, chain_id, height) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            int i = 0;
            pstmt.setLong(++i, transaction.getId());
            pstmt.setInt(++i, transaction.getHeight());
            pstmt.setLong(++i, feePerByte);
            pstmt.setInt(++i, transaction.getExpiration());
            pstmt.setBytes(++i, transaction.bytes());
            JSONObject prunableJSON = transaction.getPrunableAttachmentJSON();
            if (prunableJSON != null) {
                pstmt.setString(++i, JSON.toJSONString(prunableJSON));
            } else {
                pstmt.setNull(++i, Types.VARCHAR);
            }
            pstmt.setLong(++i, arrivalTimestamp);
            pstmt.setInt(++i, transaction.getChain().getId());
            pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
            pstmt.executeUpdate();
        }
    }

    public TransactionImpl getTransaction() {
        return transaction;
    }

    long getArrivalTimestamp() {
        return arrivalTimestamp;
    }

    long getFeePerByte() {
        return feePerByte;
    }

    @Override
    public final boolean equals(Object o) {
        return o instanceof UnconfirmedTransaction && transaction.equals(((UnconfirmedTransaction)o).getTransaction());
    }

    @Override
    public final int hashCode() {
        return transaction.hashCode();
    }

    @Override
    public Chain getChain() {
        return transaction.getChain();
    }

    @Override
    public long getId() {
        return transaction.getId();
    }

    DbKey getDbKey() {
        return dbKey;
    }

    @Override
    public String getStringId() {
        return transaction.getStringId();
    }

    @Override
    public long getSenderId() {
        return transaction.getSenderId();
    }

    @Override
    public byte[] getSenderPublicKey() {
        return transaction.getSenderPublicKey();
    }

    @Override
    public long getRecipientId() {
        return transaction.getRecipientId();
    }

    @Override
    public int getHeight() {
        return transaction.getHeight();
    }

    @Override
    public long getBlockId() {
        return transaction.getBlockId();
    }

    @Override
    public Block getBlock() {
        return transaction.getBlock();
    }

    @Override
    public int getTimestamp() {
        return transaction.getTimestamp();
    }

    @Override
    public int getBlockTimestamp() {
        return transaction.getBlockTimestamp();
    }

    @Override
    public short getDeadline() {
        return transaction.getDeadline();
    }

    @Override
    public int getExpiration() {
        return transaction.getExpiration();
    }

    @Override
    public long getAmount() {
        return transaction.getAmount();
    }

    @Override
    public long getFee() {
        return transaction.getFee();
    }

    @Override
    public byte[] getSignature() {
        return transaction.getSignature();
    }

    @Override
    public TransactionType getType() {
        return transaction.getType();
    }

    @Override
    public Attachment getAttachment() {
        return transaction.getAttachment();
    }

    @Override
    public boolean verifySignature() {
        return transaction.verifySignature();
    }

    @Override
    public void validate() throws NxtException.ValidationException {
        transaction.validate();
    }

    @Override
    public byte[] getBytes() {
        return transaction.getBytes();
    }

    @Override
    public byte[] getUnsignedBytes() {
        return transaction.getUnsignedBytes();
    }

    @Override
    public JSONObject getJSONObject() {
        return transaction.getJSONObject();
    }

    @Override
    public JSONObject getPrunableAttachmentJSON() {
        return transaction.getPrunableAttachmentJSON();
    }

    @Override
    public byte[] getPrunableAttachmentBytes() {
        return transaction.getPrunableAttachmentBytes();
    }

    @Override
    public byte getVersion() {
        return transaction.getVersion();
    }

    @Override
    public int getFullSize() {
        return transaction.getFullSize();
    }

    @Override
    public List<? extends Appendix> getAppendages() {
        return transaction.getAppendages();
    }

    @Override
    public List<? extends Appendix> getAppendages(boolean includeExpiredPrunable) {
        return transaction.getAppendages(includeExpiredPrunable);
    }

    @Override
    public List<? extends Appendix> getAppendages(Filter<Appendix> filter, boolean includeExpiredPrunable) {
        return transaction.getAppendages(filter, includeExpiredPrunable);
    }

    @Override
    public int getECBlockHeight() {
        return transaction.getECBlockHeight();
    }

    @Override
    public long getECBlockId() {
        return transaction.getECBlockId();
    }

    @Override
    public short getIndex() {
        return transaction.getIndex();
    }

    @Override
    public boolean isPhased() {
        return transaction.isPhased();
    }

    @Override
    public byte[] getFullHash() {
        return transaction.getFullHash();
    }

    public ChainTransactionId getReferencedTransactionId() {
        return null;
    }

    @Override
    public PrunablePlainMessageAppendix getPrunablePlainMessage() {
        return getTransaction().getPrunablePlainMessage();
    }

    @Override
    public PrunableEncryptedMessageAppendix getPrunableEncryptedMessage() {
        return getTransaction().getPrunableEncryptedMessage();
    }

}
