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

public abstract class Chain {

    public static Chain getChain(String name) {
        if (FxtChain.FXT_NAME.equals(name)) {
            return FxtChain.FXT;
        } else {
            return ChildChain.getChain(name);
        }
    }

    public static Chain getChain(int id) {
        if (FxtChain.FXT.getId() == id) {
            return FxtChain.FXT;
        } else {
            return ChildChain.getChain(id);
        }
    }

    private final String name;
    private final int id;

    Chain(int id, String name) {
        this.id = id;
        this.name = name.toUpperCase();
    }

    public final String getName() {
        return name;
    }

    public final int getId() {
        return id;
    }

    public String getDbSchema() {
        return name;
    }

    public final String getSchemaTable(String table) {
        if (table.contains(".")) {
            throw new IllegalArgumentException("Schema already specified: " + table);
        }
        return getDbSchema() + "." + table.toUpperCase();
    }

    public abstract TransactionHome getTransactionHome();

    abstract TransactionImpl.BuilderImpl newTransactionBuilder(byte version, byte[] senderPublicKey, long amount, long fee, short deadline,
                                                               Attachment.AbstractAttachment attachment, JSONObject attachmentData, JSONObject transactionData) throws NxtException.NotValidException;

    abstract TransactionImpl.BuilderImpl newTransactionBuilder(byte version, byte[] senderPublicKey, long amount, long fee, short deadline,
                                                               Attachment.AbstractAttachment attachment, int flags, ByteBuffer buffer) throws NxtException.NotValidException;

    abstract TransactionImpl.BuilderImpl newTransactionBuilder(byte version, long amount, long fee, short deadline,
                                                               Attachment.AbstractAttachment attachment, ByteBuffer buffer, Connection con, ResultSet rs) throws NxtException.NotValidException;

    abstract UnconfirmedTransaction newUnconfirmedTransaction(ResultSet rs) throws SQLException, NxtException.NotValidException;

}
