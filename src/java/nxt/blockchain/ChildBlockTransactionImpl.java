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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

class ChildBlockTransactionImpl extends FxtTransactionImpl {

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
    public List<ChildTransactionImpl> getChildTransactions() {
        ChildBlockAttachment childBlockAttachment = (ChildBlockAttachment)getAttachment();
        return childBlockAttachment.getChildTransactions(this);
    }

    @Override
    void save(Connection con, String schemaTable) throws SQLException {
        super.save(con, schemaTable);
        ChildBlockAttachment childBlockAttachment = (ChildBlockAttachment)getAttachment();
        String childChainSchemaTable = ChildChain.getChildChain(childBlockAttachment.getChainId()).getSchemaTable("transaction");
        for (ChildTransactionImpl childTransaction : getChildTransactions()) {
            try {
                childTransaction.save(con, childChainSchemaTable);
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
        }
    }

    @Override
    public long[] getBackFees() {
        ChildBlockAttachment attachment = (ChildBlockAttachment)getAttachment();
        return attachment.getBackFees();
    }

    @Override
    boolean hasAllReferencedTransactions(int timestamp, int count) {
        for (ChildTransactionImpl childTransaction : getChildTransactions()) {
            if (!childTransaction.hasAllReferencedTransactions(timestamp, count)) {
                return false;
            }
        }
        return true;
    }

}