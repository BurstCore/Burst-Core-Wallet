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

package nxt.peer;

import nxt.Nxt;
import nxt.Transaction;

import java.util.ArrayList;
import java.util.List;

public class GetTransactions {

    private GetTransactions() {}

    /**
     * Process the GetTransactions message and return the Transactions message.
     * The request consists of a list of unconfirmed transactions to return.
     *
     * A maximum of 100 transactions can be requested.
     *
     * @param   peer                    Peer
     * @param   message                 Request message
     * @return                          Response message
     */
    static NetworkMessage processRequest(PeerImpl peer, NetworkMessage.GetTransactionsMessage request) {
        List<Long> transactionIds = request.getTransactionIds();
        if (transactionIds.size() > 100) {
            throw new IllegalArgumentException(Errors.TOO_MANY_TRANSACTIONS_REQUESTED);
        }
        List<Transaction> transactions = new ArrayList<>(transactionIds.size());
        for (Long transactionId : transactionIds) {
            Transaction transaction = Nxt.getTransactionProcessor().getUnconfirmedTransaction(transactionId);
            if (transaction != null) {
                transactions.add(transaction);
            }
        }
        return new NetworkMessage.TransactionsMessage(request.getMessageId(), transactions);
    }
}
