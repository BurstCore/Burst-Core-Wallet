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

package nxt.http;

import nxt.Nxt;
import nxt.blockchain.ChildChain;
import nxt.blockchain.Transaction;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static nxt.http.JSONResponses.PRUNED_TRANSACTION;
import static nxt.http.JSONResponses.UNKNOWN_TRANSACTION;

public class RetrievePrunedTransaction extends APIServlet.APIRequestHandler {

    static final RetrievePrunedTransaction instance = new RetrievePrunedTransaction();

    private RetrievePrunedTransaction() {
        super(new APITag[] {APITag.TRANSACTIONS}, "transactionFullHash");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {
        byte[] transactionFullHash = ParameterParser.getBytes(req, "transactionFullHash", true);
        ChildChain childChain = ParameterParser.getChildChain(req);
        Transaction transaction = Nxt.getBlockchain().getTransactionByFullHash(childChain, transactionFullHash);
        if (transaction == null) {
            return UNKNOWN_TRANSACTION;
        }
        transaction = Nxt.getBlockchainProcessor().restorePrunedTransaction(childChain, transactionFullHash);
        if (transaction == null) {
            return PRUNED_TRANSACTION;
        }
        return JSONData.transaction(transaction);
    }

    @Override
    protected final boolean requirePost() {
        return true;
    }

    @Override
    protected final boolean allowRequiredBlockParameters() {
        return false;
    }

}
