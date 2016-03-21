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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;

class FxtTransactionImpl extends TransactionImpl implements FxtTransaction {

    private final long feeNQT;
    private final byte[] signature;

    private FxtTransactionImpl(BuilderImpl builder, String secretPhrase) throws NxtException.NotValidException {
        super(builder);
        if (builder.feeNQT <= 0 || (Constants.correctInvalidFees && builder.signature == null)) {
            int effectiveHeight = (getHeight() < Integer.MAX_VALUE ? getHeight() : Nxt.getBlockchain().getHeight());
            long minFee = getMinimumFeeNQT(effectiveHeight);
            this.feeNQT = Math.max(minFee, builder.feeNQT);
        } else {
            this.feeNQT = builder.feeNQT;
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

    public final Chain getChain() {
        return FxtChain.FXT;
    }

    @Override
    public long getFeeNQT() {
        return feeNQT;
    }

    @Override
    public byte[] getSignature() {
        return signature;
    }

    @Override
    ByteBuffer generateBytes() {
        //TODO
        return null;
    }

    @Override
    int signatureOffset() {
        //TODO
        return 0;
    }

    static FxtTransactionImpl loadTransaction(Connection con, ResultSet rs) throws NxtException.NotValidException {
        try {
            byte type = rs.getByte("type");
            byte subtype = rs.getByte("subtype");
            int timestamp = rs.getInt("timestamp");
            short deadline = rs.getShort("deadline");
            long amountNQT = rs.getLong("amount");
            long feeNQT = rs.getLong("fee");
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

            ByteBuffer buffer = null;
            if (attachmentBytes != null) {
                buffer = ByteBuffer.wrap(attachmentBytes);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
            }

            TransactionType transactionType = TransactionType.findTransactionType(type, subtype);
            TransactionImpl.BuilderImpl builder = new TransactionImpl.BuilderImpl(version, null,
                    amountNQT, feeNQT, deadline, transactionType.parseAttachment(buffer)) {};
            builder.timestamp(timestamp)
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
            return builder.build();
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

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
            pstmt.setLong(++i, getAmountNQT());
            pstmt.setLong(++i, getFeeNQT());
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
            pstmt.setBoolean(++i, getAttachment() instanceof Appendix.Prunable);
            pstmt.setInt(++i, getECBlockHeight());
            DbUtils.setLongZeroToNull(pstmt, ++i, getECBlockId());
            pstmt.setShort(++i, getIndex());
            pstmt.executeUpdate();
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FxtTransactionImpl && this.getId() == ((Transaction)o).getId();
    }

    @Override
    public int hashCode() {
        return (int)(getId() ^ (getId() >>> 32));
    }

}
