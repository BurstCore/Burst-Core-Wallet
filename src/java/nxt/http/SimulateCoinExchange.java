/*
 * Copyright © 2016-2017 Jelurida IP B.V.
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
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.MathContext;

public final class SimulateCoinExchange extends APIServlet.APIRequestHandler {

    static final SimulateCoinExchange instance = new SimulateCoinExchange();

    private SimulateCoinExchange() {
        super(new APITag[] {APITag.CE}, "exchange", "quantityQNT", "priceNQT");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {
        Chain chain = ParameterParser.getChain(req, "chain", true);
        Chain exchange = ParameterParser.getChain(req, "exchange", true);
        if (chain == exchange) {
            return JSONResponses.incorrect("exchange", "exchange must specify a different chain");
        }
        long quantityQNT = ParameterParser.getQuantityQNT(req);
        long priceNQT = ParameterParser.getPriceNQT(req);
        JSONObject response = new JSONObject();
        response.put("quantityQNT",  quantityQNT);
        response.put("bidNQT",  priceNQT);
        long askValue = CoinExchange.ASK_CONSTANT
                .divide(new BigDecimal(priceNQT).movePointLeft(chain.getDecimals()), MathContext.DECIMAL128)
                .movePointRight(exchange.getDecimals()).longValue();
        response.put("askNQT",  askValue);
        return response;
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

    @Override
    protected boolean requireBlockchain() {
        return false;
    }

}
