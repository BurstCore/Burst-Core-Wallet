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
import nxt.account.Account;
import nxt.blockchain.ChildChain;
import nxt.blockchain.Transaction;
import nxt.taggeddata.TaggedDataExtendAttachment;
import nxt.taggeddata.TaggedDataHome;
import nxt.taggeddata.TaggedDataTransactionType;
import nxt.taggeddata.TaggedDataUploadAttachment;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static nxt.http.JSONResponses.UNKNOWN_TRANSACTION;

public final class ExtendTaggedData extends CreateTransaction {

    static final ExtendTaggedData instance = new ExtendTaggedData();

    private ExtendTaggedData() {
        super("file", new APITag[] {APITag.DATA, APITag.CREATE_TRANSACTION}, "transactionFullHash",
                "name", "description", "tags", "type", "channel", "isText", "filename", "data");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {

        Account account = ParameterParser.getSenderAccount(req);
        byte[] transactionFullHash = ParameterParser.getBytes(req, "transactionFullHash", true);
        ChildChain childChain = ParameterParser.getChildChain(req);
        TaggedDataHome.TaggedData taggedData = childChain.getTaggedDataHome().getData(transactionFullHash);
        if (taggedData == null) {
            Transaction transaction = Nxt.getBlockchain().getTransactionByFullHash(childChain, transactionFullHash);
            if (transaction == null || transaction.getType() != TaggedDataTransactionType.TAGGED_DATA_UPLOAD) {
                return UNKNOWN_TRANSACTION;
            }
            TaggedDataUploadAttachment taggedDataUpload = ParameterParser.getTaggedData(req);
            taggedData = childChain.getTaggedDataHome().new TaggedData(transaction, taggedDataUpload);
        }
        TaggedDataExtendAttachment taggedDataExtend = new TaggedDataExtendAttachment(childChain, taggedData);
        return createTransaction(req, account, taggedDataExtend);

    }

}
