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

import nxt.db.DbIterator;
import nxt.util.Filter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

public interface Blockchain {

    void readLock();

    void readUnlock();

    void updateLock();

    void updateUnlock();

    Block getLastBlock();

    Block getLastBlock(int timestamp);

    int getHeight();

    int getLastBlockTimestamp();

    Block getBlock(long blockId);

    Block getBlockAtHeight(int height);

    boolean hasBlock(long blockId);

    DbIterator<? extends Block> getAllBlocks();

    DbIterator<? extends Block> getBlocks(int from, int to);

    DbIterator<? extends Block> getBlocks(long accountId, int timestamp);

    DbIterator<? extends Block> getBlocks(long accountId, int timestamp, int from, int to);

    int getBlockCount(long accountId);

    DbIterator<? extends Block> getBlocks(Connection con, PreparedStatement pstmt);

    List<Long> getBlockIdsAfter(long blockId, int limit);

    List<? extends Block> getBlocksAfter(long blockId, int limit);

    List<? extends Block> getBlocksAfter(long blockId, List<Long> blockList);

    long getBlockIdAtHeight(int height);

    Block getECBlock(int timestamp);

    Transaction getTransaction(long transactionId);

    Transaction getTransactionByFullHash(String fullHash);

    boolean hasTransaction(long transactionId);

    boolean hasTransactionByFullHash(String fullHash);

    int getTransactionCount();

    DbIterator<? extends Transaction> getAllTransactions();

    DbIterator<? extends Transaction> getTransactions(long accountId, byte type, byte subtype, int blockTimestamp,
                                                      boolean includeExpiredPrunable);

    DbIterator<? extends Transaction> getTransactions(long accountId, int numberOfConfirmations, byte type, byte subtype,
                                                      int blockTimestamp, boolean withMessage, boolean phasedOnly, boolean nonPhasedOnly,
                                                      int from, int to, boolean includeExpiredPrunable, boolean executedOnly);

    DbIterator<? extends Transaction> getTransactions(Connection con, PreparedStatement pstmt);

    List<? extends Transaction> getExpectedTransactions(Filter<Transaction> filter);

    DbIterator<? extends Transaction> getReferencingTransactions(long transactionId, int from, int to);

}
