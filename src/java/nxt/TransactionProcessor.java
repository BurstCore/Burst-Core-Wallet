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

import nxt.db.DbIterator;
import nxt.util.Observable;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.Collection;
import java.util.List;
import java.util.SortedSet;

public interface TransactionProcessor extends Observable<List<? extends Transaction>,TransactionProcessor.Event> {

    enum Event {
        REMOVED_UNCONFIRMED_TRANSACTIONS,
        ADDED_UNCONFIRMED_TRANSACTIONS,
        ADDED_CONFIRMED_TRANSACTIONS,
        RELEASE_PHASED_TRANSACTION,
        REJECT_PHASED_TRANSACTION
    }

    DbIterator<? extends Transaction> getAllUnconfirmedTransactions();

    DbIterator<? extends Transaction> getAllUnconfirmedTransactions(String sort);

    DbIterator<UnconfirmedTransaction> getUnconfirmedFxtTransactions();

    Transaction getUnconfirmedTransaction(long transactionId);

    Transaction[] getAllWaitingTransactions();

    Transaction[] getAllBroadcastedTransactions();

    void clearUnconfirmedTransactions();

    void requeueAllUnconfirmedTransactions();

    void rebroadcastAllUnconfirmedTransactions();

    void broadcast(Transaction transaction) throws NxtException.ValidationException;

    void processPeerTransactions(JSONObject request) throws NxtException.ValidationException;

    void processLater(Collection<? extends Transaction> transactions);

    SortedSet<? extends Transaction> getCachedUnconfirmedTransactions(List<String> exclude);

    List<Transaction> restorePrunableData(JSONArray transactions) throws NxtException.NotValidException;
}
