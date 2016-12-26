/*
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at
 * the top-level directory of this distribution for the individual copyright
 * holder information and the developer policies on copyright and licensing.
 *
 * Unless otherwise agreed in a custom licensing agreement, no part of the
 * Nxt software, including this file, may be copied, modified, propagated,
 * or distributed except according to the terms contained in the LICENSE.txt
 * file.
 *
 * Removal or modification of this copyright notice is prohibited.
 */
package nxt.http;

import nxt.NxtException;
import nxt.peer.BundlerRate;
import nxt.peer.Peers;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

public final class GetBundlerRates extends APIServlet.APIRequestHandler {

    static final GetBundlerRates instance = new GetBundlerRates();

    private GetBundlerRates() {
        super(new APITag[]{APITag.FORGING});
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {
        JSONObject response = new JSONObject();
        JSONArray ratesJSON = new JSONArray();
        List<BundlerRate> rates = Peers.getBestBundlerRates();
        rates.forEach(rate -> {
            JSONObject rateJSON = new JSONObject();
            rateJSON.put("chain", rate.getChain().getId());
            rateJSON.put("minRateNQTPerFXT", String.valueOf(rate.getRate()));
            ratesJSON.add(rateJSON);
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
