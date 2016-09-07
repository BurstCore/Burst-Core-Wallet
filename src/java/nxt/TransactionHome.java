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

import nxt.db.Table;
import nxt.util.Convert;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//TODO: enforce uniqueness of derived object id's
final class TransactionHome {

    private static final Map<Chain, TransactionHome> transactionHomeMap = new HashMap<>();

    static TransactionHome forChain(Chain chain) {
        if (chain.getTransactionHome() != null) {
            throw new IllegalStateException("already set");
        }
        TransactionHome transactionHome = new TransactionHome(chain);
        transactionHomeMap.put(chain, transactionHome);
        return transactionHome;
    }

    private final Chain chain;
    private final Table transactionTable;

    private TransactionHome(Chain chain) {
        this.chain = chain;
        transactionTable = new Table(chain.getSchemaTable(chain instanceof FxtChain ? "transaction_fxt" : "transaction"));
    }

    TransactionImpl findTransaction(long transactionId) {
        return findTransaction(transactionId, Integer.MAX_VALUE);
    }

    TransactionImpl findTransaction(long transactionId, int height) {
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

    TransactionImpl findTransactionByFullHash(byte[] fullHash) {
        return findTransactionByFullHash(fullHash, Integer.MAX_VALUE);
    }

    TransactionImpl findTransactionByFullHash(byte[] fullHash, int height) {
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

    boolean hasTransactionByFullHash(byte[] fullHash, int height) {
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

    byte[] getTransactionFullHash(long transactionId) {
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

        public PrunableTransaction(long id, TransactionType transactionType, boolean prunableAttachment,
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
