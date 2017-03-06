/*
 * Copyright © 2017 Jelurida IP B.V.
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of the Nxt software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 *
 * Removal or modification of this copyright notice is prohibited.
 */
package nxt.http;

import nxt.Constants;
import nxt.NxtException;
import nxt.blockchain.ChildChain;
import nxt.peer.BundlerRate;
import nxt.peer.Peers;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

public class GetAllBundlerRates extends APIServlet.APIRequestHandler {

    static final GetAllBundlerRates instance = new GetAllBundlerRates();

    private GetAllBundlerRates() {
        super(new APITag[]{APITag.FORGING}, "minBundlerBalanceFXT");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {
        long minBalance = ParameterParser.getLong(req, "minBundlerBalanceFXT",
                0, Constants.MAX_BALANCE_FXT, false);
        JSONObject response = new JSONObject();
        JSONArray ratesJSON = new JSONArray();
        List<BundlerRate> rates = Peers.getAllBundlerRates(minBalance);
        ChildChain.getAll().forEach(childChain -> {
            JSONObject childResponseJSON = new JSONObject();
            childResponseJSON.put("chain", childChain.getId());
            JSONArray childRatesJSON = new JSONArray();
            rates.forEach(rate -> {
                if (rate.getChain() == childChain) {
                    JSONObject childRateJSON = new JSONObject();
                    childRateJSON.put("minRateNQTPerFXT", String.valueOf(rate.getRate()));
                    childRateJSON.put("currentFeeLimitFQT", String.valueOf(rate.getFeeLimit()));
                    childRatesJSON.add(childRateJSON);
                }
            });
            childResponseJSON.put("rates", childRatesJSON);
            ratesJSON.add(childResponseJSON);
        });
        response.put("rates", ratesJSON);
        return response;
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

    @Override
    protected boolean isChainSpecific() {
        return false;
    }

    @Override
    protected boolean requireBlockchain() {
        return false;
    }
}
