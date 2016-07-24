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

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.sql.ResultSet;
import java.sql.SQLException;

class UnconfirmedFxtTransaction extends UnconfirmedTransaction implements FxtTransaction {

    UnconfirmedFxtTransaction(FxtTransactionImpl transaction, long arrivalTimestamp) {
        super(transaction, arrivalTimestamp);
    }

    UnconfirmedFxtTransaction(ResultSet rs) throws SQLException, NxtException.NotValidException {
        super(FxtTransactionImpl.newTransactionBuilder(
                rs.getBytes("transaction_bytes"),
                (JSONObject) (rs.getString("prunable_json") != null ? JSONValue.parse(rs.getString("prunable_json")) : null)),
                rs);
    }

    @Override
    FxtTransactionImpl getTransaction() {
        return (FxtTransactionImpl)super.getTransaction();
    }

    @Override
    byte[] referencedTransactionFullHash() {
        return null;
    }

}
