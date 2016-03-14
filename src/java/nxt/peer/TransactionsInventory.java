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
import nxt.NxtException;
import nxt.Transaction;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Iterator;
import java.util.List;

final class TransactionsInventory {

    /** Transaction cache */
    private static final ConcurrentHashMap<Long, Integer> transactionCache = new ConcurrentHashMap<>();

    private TransactionsInventory() {}

    /**
     * Process a TransactionsInventory message (there is no response message)
     *
     * @param   peer                    Peer
     * @param   request                 Request message
     * @return                          Response message
     */
    static NetworkMessage processRequest(PeerImpl peer, NetworkMessage.TransactionsInventoryMessage request) {
        List<Long> transactionIds = request.getTransactionIds();
        List<Integer> timestamps = request.getTimestamps();
        Peers.peersService.execute(() -> {
            //
            // Request transactions that are not already in our cache
            //
            List<Long> requestIds = new ArrayList<>(Math.min(100, transactionIds.size()));
            for (int i=0; i<transactionIds.size(); i++) {
                Long transactionId = transactionIds.get(i);
                if (transactionCache.get(transactionId) == null) {
                    requestIds.add(transactionId);
                    transactionCache.put(transactionId, timestamps.get(i));
                    if (requestIds.size() >= 100) {
                        break;
                    }
                }
            }
            if (requestIds.isEmpty()) {
                return;
            }
            //
            // Remove old transactions from our cache.  The transaction processor tracks
            // unconfirmed transactions, so our cache is just to reduce needless transaction
            // broadcasts.
            //
            int now = Nxt.getEpochTime();
            Iterator<Integer> it = transactionCache.values().iterator();
            while (it.hasNext()) {
                Integer timestamp = it.next();
                if (now - timestamp > 10*60) {
                    it.remove();
                }
            }
            //
            // Request the transactions
            //
            try {
                List<Peer> connectedPeers = Peers.getConnectedPeers();
                if (connectedPeers.isEmpty()) {
                        return;
                }
                int index = connectedPeers.indexOf(peer);
                if (index < 0) {
                    index = 0;
                }
                int startIndex = index;
                while (!requestIds.isEmpty()) {
                    Peer feederPeer = connectedPeers.get(index);
                    NetworkMessage.TransactionsMessage response = (NetworkMessage.TransactionsMessage)feederPeer.sendRequest(
                            new NetworkMessage.GetTransactionsMessage(requestIds));
                    if (response == null || response.getTransactionCount() == 0) {
                        index = (index < connectedPeers.size()-1 ? index+1 : 0);
                        if (index == startIndex) {
                            break;
                        }
                        continue;
                    }
                    try {
                        List<Transaction> transactions = response.getTransactions();
                        transactions.forEach(tx -> requestIds.remove(tx.getId()));
                        Nxt.getTransactionProcessor().processPeerTransactions(transactions);
                    } catch (RuntimeException | NxtException.ValidationException e) {
                        feederPeer.blacklist(e);
                    }
                }
            } finally {
                requestIds.forEach(id -> transactionCache.remove(id));
            }
        });
        return null;
    }
}
