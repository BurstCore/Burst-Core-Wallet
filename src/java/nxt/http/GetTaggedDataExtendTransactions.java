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
import nxt.NxtException;
import nxt.blockchain.Appendix;
import nxt.blockchain.Blockchain;
import nxt.blockchain.ChildChain;
import nxt.taggeddata.TaggedDataExtendAttachment;
import nxt.util.Filter;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

public final class GetTaggedDataExtendTransactions extends APIServlet.APIRequestHandler {

    static final GetTaggedDataExtendTransactions instance = new GetTaggedDataExtendTransactions();

    private GetTaggedDataExtendTransactions() {
        super(new APITag[] {APITag.DATA}, "transactionFullHash");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {
        byte[] transactionFullHash = ParameterParser.getBytes(req, "transactionFullHash", true);
        ChildChain childChain = ParameterParser.getChildChain(req);
        List<byte[]> extendTransactions = childChain.getTaggedDataHome().getExtendTransactionIds(transactionFullHash);
        JSONObject response = new JSONObject();
        JSONArray jsonArray = new JSONArray();
        Blockchain blockchain = Nxt.getBlockchain();
        Filter<Appendix> filter = (appendix) -> ! (appendix instanceof TaggedDataExtendAttachment);
        extendTransactions.forEach(extendTransactionFullHash -> jsonArray.add(
                JSONData.transaction(blockchain.getTransactionByFullHash(childChain, extendTransactionFullHash), filter)));
        response.put("extendTransactions", jsonArray);
        return response;
    }

}
