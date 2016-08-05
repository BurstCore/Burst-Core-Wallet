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

class UnconfirmedChildTransaction extends UnconfirmedTransaction implements ChildTransaction {

    UnconfirmedChildTransaction(ChildTransactionImpl transaction, long arrivalTimestamp) {
        super(transaction, arrivalTimestamp);
    }

    UnconfirmedChildTransaction(ResultSet rs) throws SQLException, NxtException.NotValidException {
        super(ChildTransactionImpl.newTransactionBuilder(
                rs.getBytes("transaction_bytes"),
                rs.getString("prunable_json") != null ? (JSONObject) JSONValue.parse(rs.getString("prunable_json")) : null),
                rs);
    }

    @Override
    ChildTransactionImpl getTransaction() {
        return (ChildTransactionImpl)super.getTransaction();
    }

    @Override
    public ChildChain getChain() {
        return getTransaction().getChain();
    }

    @Override
    public FxtTransaction getFxtTransaction() {
        return getTransaction().getFxtTransaction();
    }

    @Override
    public long getFxtTransactionId() {
        return getTransaction().getFxtTransactionId();
    }

    @Override
    public String getReferencedTransactionFullHash() {
        return getTransaction().getReferencedTransactionFullHash();
    }

    @Override
    public Appendix.Message getMessage() {
        return getTransaction().getMessage();
    }

    @Override
    public Appendix.EncryptedMessage getEncryptedMessage() {
        return getTransaction().getEncryptedMessage();
    }

    @Override
    public Appendix.EncryptToSelfMessage getEncryptToSelfMessage() {
        return getTransaction().getEncryptToSelfMessage();
    }

    @Override
    public Appendix.Phasing getPhasing() {
        return getTransaction().getPhasing();
    }

    @Override
    public Appendix.PrunablePlainMessage getPrunablePlainMessage() {
        return getTransaction().getPrunablePlainMessage();
    }

    @Override
    public Appendix.PrunableEncryptedMessage getPrunableEncryptedMessage() {
        return getTransaction().getPrunableEncryptedMessage();
    }

    @Override
    byte[] referencedTransactionFullHash() {
        return getTransaction().referencedTransactionFullHash();
    }

}
