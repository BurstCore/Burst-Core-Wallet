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
import nxt.blockchain.Attachment;
import nxt.blockchain.ChildChain;
import nxt.blockchain.Transaction;
import nxt.taggeddata.TaggedDataUploadAttachment;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;

import static nxt.http.JSONResponses.HASHES_MISMATCH;
import static nxt.http.JSONResponses.INCORRECT_TRANSACTION;
import static nxt.http.JSONResponses.UNKNOWN_TRANSACTION;

public final class VerifyTaggedData extends APIServlet.APIRequestHandler {

    static final VerifyTaggedData instance = new VerifyTaggedData();

    private VerifyTaggedData() {
        super("file", new APITag[]{APITag.DATA}, "transactionFullHash",
                "name", "description", "tags", "type", "channel", "isText", "filename", "data");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {

        byte[] transactionFullHash = ParameterParser.getBytes(req, "transactionFullHash", true);
        ChildChain childChain = ParameterParser.getChildChain(req);
        Transaction transaction = Nxt.getBlockchain().getTransactionByFullHash(childChain, transactionFullHash);
        if (transaction == null) {
            return UNKNOWN_TRANSACTION;
        }

        TaggedDataUploadAttachment taggedData = ParameterParser.getTaggedData(req);
        Attachment attachment = transaction.getAttachment();

        if (! (attachment instanceof TaggedDataUploadAttachment)) {
            return INCORRECT_TRANSACTION;
        }

        TaggedDataUploadAttachment myTaggedData = (TaggedDataUploadAttachment)attachment;
        if (!Arrays.equals(myTaggedData.getHash(), taggedData.getHash())) {
            return HASHES_MISMATCH;
        }

        JSONObject response = myTaggedData.getJSONObject();
        response.put("verify", true);
        return response;
    }

}
