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

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class FxtChain extends Chain {

    public static final String FXT_NAME = "ARDR";

    public static final FxtChain FXT = new FxtChain();

    static void init() {}

    private final TransactionHome transactionHome;

    private FxtChain() {
        super(1, FXT_NAME);
        this.transactionHome = TransactionHome.forChain(this);
    }

    @Override
    public String getDbSchema() {
        return "PUBLIC";
    }

    @Override
    public TransactionHome getTransactionHome() {
        return transactionHome;
    }

    @Override
    FxtTransactionImpl.BuilderImpl newTransactionBuilder(byte version, byte[] senderPublicKey, long amount, long fee, short deadline,
                                                         Attachment.AbstractAttachment attachment, JSONObject attachmentData, JSONObject transactionData) throws NxtException.NotValidException {
        return FxtTransactionImpl.newTransactionBuilder(version, senderPublicKey, amount, fee, deadline,
                attachment, attachmentData, transactionData);
    }

    @Override
    FxtTransactionImpl.BuilderImpl newTransactionBuilder(byte version, byte[] senderPublicKey, long amount, long fee, short deadline,
                                                         Attachment.AbstractAttachment attachment, int flags, ByteBuffer buffer) throws NxtException.NotValidException {
        return FxtTransactionImpl.newTransactionBuilder(version, senderPublicKey, amount, fee, deadline,
                attachment, flags, buffer);
    }

    @Override
    FxtTransactionImpl.BuilderImpl newTransactionBuilder(byte version, long amount, long fee, short deadline,
                                                         Attachment.AbstractAttachment attachment, ByteBuffer buffer, Connection con, ResultSet rs) throws NxtException.NotValidException {
        return FxtTransactionImpl.newTransactionBuilder(version, amount, fee, deadline, attachment, buffer, con, rs);
    }

    @Override
    UnconfirmedTransaction newUnconfirmedTransaction(ResultSet rs) throws SQLException, NxtException.NotValidException {
        return new UnconfirmedFxtTransaction(rs);
    }
}
