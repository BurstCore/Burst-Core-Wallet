package nxt.http;

import nxt.Account;
import nxt.NxtException;
import nxt.db.DbIterator;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetAccountsWithRewardRecipient extends APIServlet.APIRequestHandler {

    static final GetAccountsWithRewardRecipient instance = new GetAccountsWithRewardRecipient();

    private GetAccountsWithRewardRecipient() {
        super(new APITag[] {APITag.ACCOUNTS, APITag.MINING, APITag.INFO}, "account");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {
        JSONObject response = new JSONObject();

        Account targetAccount = ParameterParser.getAccount(req);
        JSONArray accounts = new JSONArray();
        DbIterator<Account.RewardRecipientAssignment> assignments = Account.getAccountsWithRewardRecipient(targetAccount.getId());

        while(assignments.hasNext()) {
            Account.RewardRecipientAssignment assignment = assignments.next();
            accounts.add(Long.toUnsignedString(assignment.accountId));
        }
        if(targetAccount.getRewardRecipientAssignment() == null) {
            accounts.add(Long.toUnsignedString(targetAccount.getId()));
        }

        response.put("accounts", accounts);

        return response;
    }

}
