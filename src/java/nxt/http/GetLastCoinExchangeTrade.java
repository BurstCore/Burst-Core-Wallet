/*
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

import nxt.NxtException;
import nxt.blockchain.Chain;
import nxt.ce.CoinExchange;
import nxt.ce.CoinExchange.Trade;
import nxt.db.DbIterator;

import static nxt.http.JSONResponses.NO_TRADES_FOUND;

import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetLastCoinExchangeTrade extends APIServlet.APIRequestHandler {

    static final GetLastCoinExchangeTrade instance = new GetLastCoinExchangeTrade();

    /**
     * <p>Search criteria:
     * <ul>
     * <li>account - Select trades for this account
     * <li>chain - Select trades exchanging coins for this chain
     * <li>exchange - Select trades requesting coins for this chain
     * </ul>
     * <p>All trades will be selected if no search criteria is specified.
     */
    private GetLastCoinExchangeTrade() {
        super(new APITag[] {APITag.CE}, "exchange", "account");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {
        Chain chain = ParameterParser.getChain(req, "chain", false);
        int chainId = chain != null ? chain.getId() : 0;
        Chain exchange = ParameterParser.getChain(req, "exchange", false);
        int exchangeId = exchange != null ? exchange.getId() : 0;
        long accountId = ParameterParser.getAccountId(req, "account", false);
        DbIterator<Trade> it = CoinExchange.getTrades(accountId, chainId, exchangeId, 0, 0);
        if (it.hasNext()) {
            return JSONData.coinExchangeTrade(it.next());
        } else {
            return NO_TRADES_FOUND;
        }
    }
}
