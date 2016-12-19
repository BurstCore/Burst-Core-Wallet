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

final class ChildBlockFxtTransactionImpl extends FxtTransactionImpl implements ChildBlockFxtTransaction {

    private List<ChildTransactionImpl> childTransactions;

    ChildBlockFxtTransactionImpl(FxtTransactionImpl.BuilderImpl builder, String secretPhrase) throws NxtException.NotValidException {
        super(builder, secretPhrase);
    }

    @Override
    public ChildChain getChildChain() {
        return ChildChain.getChildChain(((ChildBlockAttachment)attachment).getChainId());
    }

    @Override
    public byte[][] getChildTransactionFullHashes() {
        return ((ChildBlockAttachment)getAttachment()).getChildTransactionFullHashes();
    }

    @Override
    synchronized void setBlock(BlockImpl block) {
        super.setBlock(block);
        if (childTransactions != null) {
            short index = 0;
            for (ChildTransactionImpl childTransaction : getChildTransactions()) {
                childTransaction.setFxtTransaction(this);
                childTransaction.setIndex(index++);
            }
        }
    }

    @Override
    synchronized void unsetBlock() {
        super.unsetBlock();
        getChildTransactions().forEach(childTransaction -> childTransaction.unsetFxtTransaction());
        childTransactions = null;
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
            byte[][] hashes = childBlockAttachment.getChildTransactionFullHashes();
            if (TransactionHome.hasFxtTransaction(this.getId(), Nxt.getBlockchain().getHeight() + 1)) {
                TransactionHome transactionHome = ChildChain.getChildChain(childBlockAttachment.getChainId()).getTransactionHome();
                list = transactionHome.findChildTransactions(this.getId());
                if (list.size() != hashes.length) {
                    throw new IllegalStateException("Not all child transactions found");
                }
                for (int i = 0; i < hashes.length; i++) {
                    if (!Arrays.equals(hashes[i], list.get(i).getFullHash())) {
                        throw new IllegalStateException(String.format("Child transaction hash mismatch %s %s",
                                Convert.toHexString(hashes[i]), Convert.toHexString(list.get(i).getFullHash())));
                    }
                }
                for (ChildTransactionImpl childTransaction : list) {
                    childTransaction.setBlock(this.getBlock());
                }
            } else {
                TransactionProcessorImpl transactionProcessor = TransactionProcessorImpl.getInstance();
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
    public synchronized void setChildTransactions(List<? extends ChildTransaction> childTransactions) throws NxtException.NotValidException {
        byte[][] childTransactionHashes = getChildTransactionFullHashes();
        if (childTransactions.size() != childTransactionHashes.length) {
            throw new NxtException.NotValidException(String.format("Child transactions size %d does not match child hashes count %d",
                    childTransactions.size(), childTransactionHashes.length));
        }
        List<ChildTransactionImpl> list = new ArrayList<>();
        for (int i = 0; i < childTransactionHashes.length; i++) {
            ChildTransactionImpl childTransaction = (ChildTransactionImpl)childTransactions.get(i);
            if (!Arrays.equals(childTransactionHashes[i], childTransaction.getFullHash())) {
                throw new NxtException.NotValidException(String.format("Child transaction full hash mismatch: %s, %s",
                        Convert.toHexString(childTransactionHashes[i]), Convert.toHexString(childTransaction.getFullHash())));
            }
            list.add(childTransaction);
        }
        this.childTransactions = list;
    }

    @Override
    synchronized void save(Connection con, String schemaTable) throws SQLException {
        super.save(con, schemaTable);
        ChildBlockAttachment childBlockAttachment = (ChildBlockAttachment)getAttachment();
        String childChainSchemaTable = ChildChain.getChildChain(childBlockAttachment.getChainId()).getSchemaTable("transaction");
        if (childTransactions == null) {
            throw new IllegalStateException("Child transactions must be loaded first");
        }
        short index = 0;
        for (ChildTransactionImpl childTransaction : getChildTransactions()) {
            if (childTransaction.getFxtTransactionId() != this.getId()) {
                throw new IllegalStateException(String.format("Child transaction fxtTransactionId set to %s, must be %s",
                        Long.toUnsignedString(childTransaction.getFxtTransactionId()), this.getStringId()));
            }
            if (childTransaction.getIndex() != index++) {
                throw new IllegalStateException(String.format("Child transaction index set to %d, must be %d",
                        childTransaction.getIndex(), --index));
            }
            childTransaction.save(con, childChainSchemaTable);
        }
        childTransactions = null;
    }

    @Override
    public long[] getBackFees() {
        ChildBlockAttachment attachment = (ChildBlockAttachment)getAttachment();
        long[] myBackFees = attachment.getFee(this, Nxt.getBlockchain().getHeight()).getBackFees(getFee());
        long[] childBackFees = attachment.getBackFees();
        long[] backFees = new long[Math.min(myBackFees.length, childBackFees.length)];
        for (int i = 0; i < backFees.length; i++) {
            backFees[i] = myBackFees[i];
            backFees[i] += childBackFees[i];
        }
        return backFees;
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