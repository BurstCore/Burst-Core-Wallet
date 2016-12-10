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
import nxt.util.Convert;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class ChildBlockTransactionImpl extends FxtTransactionImpl {

    private List<ChildTransactionImpl> childTransactions;

    ChildBlockTransactionImpl(FxtTransactionImpl.BuilderImpl builder, String secretPhrase) throws NxtException.NotValidException {
        super(builder, secretPhrase);
    }

    @Override
    void setBlock(BlockImpl block) {
        super.setBlock(block);
        getChildTransactions().forEach(childTransaction -> childTransaction.setBlock(block));
    }

    @Override
    void unsetBlock() {
        super.unsetBlock();
        getChildTransactions().forEach(childTransaction -> childTransaction.unsetBlock());
    }

    @Override
    public void validate() throws NxtException.ValidationException {
        try {
            getChildTransactions();
        } catch (RuntimeException e) {
            throw new NxtException.NotCurrentlyValidException("Missing or invalid child transaction", e);
        }
        super.validate();
    }

    @Override
    public synchronized List<ChildTransactionImpl> getChildTransactions() {
        ChildBlockAttachment childBlockAttachment = (ChildBlockAttachment)getAttachment();
        if (childTransactions == null) {
            List<ChildTransactionImpl> list;
            if (TransactionHome.hasFxtTransaction(this.getId(), Nxt.getBlockchain().getHeight())) {
                TransactionHome transactionHome = ChildChain.getChildChain(childBlockAttachment.getChainId()).getTransactionHome();
                list = transactionHome.findChildTransactions(this.getId());
                short index = 0;
                for (ChildTransactionImpl childTransaction : list) {
                    childTransaction.setFxtTransaction(this);
                    childTransaction.setIndex(index++);
                }
            } else {
                TransactionProcessorImpl transactionProcessor = TransactionProcessorImpl.getInstance();
                byte[][] hashes = childBlockAttachment.getChildTransactionFullHashes();
                list = new ArrayList<>(hashes.length);
                for (byte[] fullHash : hashes) {
                    UnconfirmedTransaction unconfirmedTransaction = transactionProcessor.getUnconfirmedTransaction(Convert.fullHashToId(fullHash));
                    if (unconfirmedTransaction == null) {
                        throw new IllegalStateException(String.format("Missing child transaction %s", Convert.toHexString(fullHash)));
                    }
                    if (!Arrays.equals(unconfirmedTransaction.getFullHash(), fullHash)) {
                        throw new IllegalStateException(String.format("Unconfirmed transaction hash mismatch %s %s",
                                Convert.toHexString(fullHash), Convert.toHexString(unconfirmedTransaction.getFullHash())));
                    }
                    list.add((ChildTransactionImpl)unconfirmedTransaction.getTransaction());
                }
            }
            childTransactions = Collections.unmodifiableList(list);
        }
        return childTransactions;
    }

    @Override
    synchronized void save(Connection con, String schemaTable) throws SQLException {
        super.save(con, schemaTable);
        ChildBlockAttachment childBlockAttachment = (ChildBlockAttachment)getAttachment();
        String childChainSchemaTable = ChildChain.getChildChain(childBlockAttachment.getChainId()).getSchemaTable("transaction");
        for (ChildTransactionImpl childTransaction : getChildTransactions()) {
            childTransaction.save(con, childChainSchemaTable);
        }
        childTransactions = null;
    }

    @Override
    public long[] getBackFees() {
        ChildBlockAttachment attachment = (ChildBlockAttachment)getAttachment();
        return attachment.getBackFees();
    }

    @Override
    boolean hasAllReferencedTransactions(int timestamp, int count) {
        try {
            for (ChildTransactionImpl childTransaction : getChildTransactions()) {
                if (!childTransaction.hasAllReferencedTransactions(timestamp, count)) {
                    return false;
                }
            }
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

}