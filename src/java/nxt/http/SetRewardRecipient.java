package nxt.http;

import nxt.Account;
import nxt.Attachment;
import nxt.NxtException;
import nxt.db.DbIterator;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class SetRewardRecipient extends CreateTransaction {

    static final SetRewardRecipient instance = new SetRewardRecipient();

    private SetRewardRecipient() {
        super(new APITag[] {APITag.ACCOUNTS, APITag.MINING, APITag.CREATE_TRANSACTION}, "recipient");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {
        Account account = ParameterParser.getSenderAccount(req);
        long recipientId = ParameterParser.getAccountId(req, "recipient", true);

        if (Account.getPublicKey(recipientId) == null) {
            JSONObject response = new JSONObject();
            response.put("errorCode", 8);
            response.put("errorDescription", "recipient account does not have public key");
            return response;
        }

        Attachment attachment = new Attachment.BurstMiningRewardRecipientAssignment();
        return createTransaction(req, account, recipientId, 0, attachment);
    }

}
