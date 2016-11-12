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

import nxt.NxtException;
import nxt.db.Table;
import nxt.dbschema.Db;
import nxt.util.Convert;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

//TODO: enforce uniqueness of derived object id's
public final class TransactionHome {

    public static TransactionHome forChain(Chain chain) {
        if (chain.getTransactionHome() != null) {
            throw new IllegalStateException("already set");
        }
        return new TransactionHome(chain);
    }

    private final Chain chain;
    private final Table transactionTable;

    private TransactionHome(Chain chain) {
        this.chain = chain;
        transactionTable = new Table(chain.getSchemaTable(chain instanceof FxtChain ? "transaction_fxt" : "transaction"));
    }

    public TransactionImpl findTransaction(long transactionId) {
        return findTransaction(transactionId, Integer.MAX_VALUE);
    }

    public TransactionImpl findTransaction(long transactionId, int height) {
        // Check the block cache
        synchronized (BlockDb.blockCache) {
            TransactionImpl transaction = BlockDb.transactionCache.get(transactionId);
            if (transaction != null) {
                return transaction.getHeight() <= height ? transaction : null;
            }
        }
        // Search the database
        try (Connection con = transactionTable.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + transactionTable.getSchemaTable() + " WHERE id = ? ORDER BY height DESC")) {
            pstmt.setLong(1, transactionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    if (rs.getInt("height") <= height) {
                        return TransactionImpl.newTransactionBuilder(chain, con, rs).build();
                    }
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        } catch (NxtException.ValidationException e) {
            throw new RuntimeException("Transaction already in database, id = " + transactionId + ", does not pass validation!", e);
        }
    }

    public TransactionImpl findTransactionByFullHash(byte[] fullHash) {
        return findTransactionByFullHash(fullHash, Integer.MAX_VALUE);
    }

    public TransactionImpl findTransactionByFullHash(byte[] fullHash, int height) {
        long transactionId = Convert.fullHashToId(fullHash);
        // Check the cache
        synchronized(BlockDb.blockCache) {
            TransactionImpl transaction = BlockDb.transactionCache.get(transactionId);
            if (transaction != null) {
                return (transaction.getHeight() <= height &&
                        Arrays.equals(transaction.fullHash(), fullHash) ? transaction : null);
            }
        }
        // Search the database
        try (Connection con = transactionTable.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + transactionTable.getSchemaTable() + " WHERE id = ?")) {
            pstmt.setLong(1, transactionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    if (Arrays.equals(rs.getBytes("full_hash"), fullHash) && rs.getInt("height") <= height) {
                        return TransactionImpl.newTransactionBuilder(chain, con, rs).build();
                    }
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        } catch (NxtException.ValidationException e) {
            throw new RuntimeException("Transaction already in database, full_hash = " + Convert.toHexString(fullHash)
                    + ", does not pass validation!", e);
        }
    }

    boolean hasTransaction(long transactionId, int height) {
        // Check the block cache
        synchronized(BlockDb.blockCache) {
            TransactionImpl transaction = BlockDb.transactionCache.get(transactionId);
            if (transaction != null) {
                return (transaction.getHeight() <= height);
            }
        }
        // Search the database
        try (Connection con = transactionTable.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT height FROM " + transactionTable.getSchemaTable() + " WHERE id = ? ORDER BY height DESC")) {
            pstmt.setLong(1, transactionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    if (rs.getInt("height") <= height) {
                        return true;
                    }
                }
                return false;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    boolean hasTransactionByFullHash(byte[] fullHash) {
        return hasTransactionByFullHash(fullHash, Integer.MAX_VALUE);
    }

    public boolean hasTransactionByFullHash(byte[] fullHash, int height) {
        long transactionId = Convert.fullHashToId(fullHash);
        // Check the block cache
        synchronized(BlockDb.blockCache) {
            TransactionImpl transaction = BlockDb.transactionCache.get(transactionId);
            if (transaction != null) {
                return (transaction.getHeight() <= height &&
                        Arrays.equals(transaction.fullHash(), fullHash));
            }
        }
        // Search the database
        try (Connection con = transactionTable.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT full_hash, height FROM " + transactionTable.getSchemaTable() + " WHERE id = ?")) {
            pstmt.setLong(1, transactionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    if (Arrays.equals(rs.getBytes("full_hash"), fullHash) && rs.getInt("height") <= height) {
                        return true;
                    }
                }
                return false;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public byte[] getTransactionFullHash(long transactionId) {
        // Check the block cache
        synchronized(BlockDb.blockCache) {
            TransactionImpl transaction = BlockDb.transactionCache.get(transactionId);
            if (transaction != null) {
                return transaction.fullHash();
            }
        }
        // Search the database
        try (Connection con = transactionTable.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT full_hash FROM " + transactionTable.getSchemaTable() + " WHERE id = ? ORDER BY height DESC LIMIT 1")) {
            pstmt.setLong(1, transactionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getBytes("full_hash") : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static List<FxtTransactionImpl> findBlockTransactions(long blockId) {
        // Check the block cache
        synchronized(BlockDb.blockCache) {
            BlockImpl block = BlockDb.blockCache.get(blockId);
            if (block != null) {
                return block.getTransactions();
            }
        }
        // Search the database
        try (Connection con = Db.getConnection()) {
            return findBlockTransactions(con, blockId);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static List<FxtTransactionImpl> findBlockTransactions(Connection con, long blockId) {
        List<FxtTransactionImpl> list = new ArrayList<>();
        try (PreparedStatement pstmt = con.prepareStatement("SELECT * FROM transaction_fxt"
                + " WHERE block_id = ? ORDER BY transaction_index")) {
            pstmt.setLong(1, blockId);
            pstmt.setFetchSize(50);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(FxtTransactionImpl.loadTransaction(con, rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        } catch (NxtException.ValidationException e) {
            throw new RuntimeException("Transaction already in database for block_id = " + Long.toUnsignedString(blockId)
                    + " does not pass validation!", e);
        }
        return list;
    }

    List<PrunableTransaction> findPrunableTransactions(Connection con, int minTimestamp, int maxTimestamp) {
        List<PrunableTransaction> result = new ArrayList<>();
        try (PreparedStatement pstmt = con.prepareStatement("SELECT id, type, subtype, "
                + "has_prunable_attachment AS prunable_attachment, "
                + "has_prunable_message AS prunable_plain_message, "
                + "has_prunable_encrypted_message AS prunable_encrypted_message "
                + "FROM " + transactionTable.getSchemaTable() + " WHERE (timestamp BETWEEN ? AND ?) AND "
                + "(has_prunable_attachment = TRUE OR has_prunable_message = TRUE OR has_prunable_encrypted_message = TRUE)")) {
            pstmt.setInt(1, minTimestamp);
            pstmt.setInt(2, maxTimestamp);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    byte type = rs.getByte("type");
                    byte subtype = rs.getByte("subtype");
                    TransactionType transactionType = ChildTransactionType.findTransactionType(type, subtype);
                    result.add(new PrunableTransaction(id, transactionType,
                            rs.getBoolean("prunable_attachment"),
                            rs.getBoolean("prunable_plain_message"),
                            rs.getBoolean("prunable_encrypted_message")));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
        return result;
    }

    static void saveTransactions(Connection con, List<FxtTransactionImpl> transactions) {
        try {
            short index = 0;
            for (FxtTransactionImpl transaction : transactions) {
                transaction.setIndex(index++);
                transaction.save(con, "transaction_fxt");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static class PrunableTransaction {
        private final long id;
        private final TransactionType transactionType;
        private final boolean prunableAttachment;
        private final boolean prunablePlainMessage;
        private final boolean prunableEncryptedMessage;

        private PrunableTransaction(long id, TransactionType transactionType, boolean prunableAttachment,
                                    boolean prunablePlainMessage, boolean prunableEncryptedMessage) {
            this.id = id;
            this.transactionType = transactionType;
            this.prunableAttachment = prunableAttachment;
            this.prunablePlainMessage = prunablePlainMessage;
            this.prunableEncryptedMessage = prunableEncryptedMessage;
        }

        public long getId() {
            return id;
        }

        public TransactionType getTransactionType() {
            return transactionType;
        }

        public boolean hasPrunableAttachment() {
            return prunableAttachment;
        }

        public boolean hasPrunablePlainMessage() {
            return prunablePlainMessage;
        }

        public boolean hasPrunableEncryptedMessage() {
            return prunableEncryptedMessage;
        }
    }

    int getTransactionCount() {
        try (Connection con = transactionTable.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT COUNT(*) FROM " + transactionTable.getSchemaTable());
             ResultSet rs = pstmt.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

}
