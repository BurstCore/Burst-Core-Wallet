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
    List<ChildTransactionImpl> getChildTransactions() {
        ChildBlockAttachment childBlockAttachment = (ChildBlockAttachment)getAttachment();
        return childBlockAttachment.getChildTransactions(this);
    }

    @Override
    void save(Connection con, String schemaTable) throws SQLException {
        super.save(con, schemaTable);
        String childChainSchemaTable = getChain().getSchemaTable("transaction");
        for (ChildTransactionImpl childTransaction : getChildTransactions()) {
            try {
                childTransaction.save(con, childChainSchemaTable);
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
        }
    }

}