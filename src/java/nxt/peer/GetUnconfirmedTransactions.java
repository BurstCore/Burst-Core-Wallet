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

package nxt.peer;

import nxt.Nxt;
import nxt.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

final class GetUnconfirmedTransactions {

    private GetUnconfirmedTransactions() {}

    /**
     * Process the GetUnconfirmedTransactions message and return the Transactions message.
     * The request contains a list of unconfirmed transactions to exclude.
     *
     * A maximum of 100 unconfirmed transactions will be returned.
     *
     * @param   peer                    Peer
     * @param   message                 Request message
     * @return                          Response message
     */
    static NetworkMessage processRequest(PeerImpl peer, NetworkMessage.GetUnconfirmedTransactionsMessage request) {
        List<Long> exclude = request.getExclusions();
        SortedSet<? extends Transaction> transactionSet = Nxt.getTransactionProcessor().getCachedUnconfirmedTransactions(exclude);
        List<Transaction> transactions = new ArrayList<>(Math.min(100, transactionSet.size()));
        for (Transaction transaction : transactionSet) {
            transactions.add(transaction);
            if (transactions.size() >= 100) {
                break;
            }
        }
        return new NetworkMessage.TransactionsMessage(request.getMessageId(), transactions);
    }
}
