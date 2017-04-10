package nxt.http;

import nxt.Account;
import nxt.Attachment;
import nxt.Constants;
import nxt.Nxt;
import nxt.NxtException;
import nxt.util.Convert;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetRewardRecipient extends APIServlet.APIRequestHandler {
	
	static final GetRewardRecipient instance = new GetRewardRecipient();
	
	private GetRewardRecipient() {
		super(new APITag[] {APITag.ACCOUNTS, APITag.MINING, APITag.INFO}, "account");
	}
	
	@Override
	protected JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {
		JSONObject response = new JSONObject();
		
		Account account = ParameterParser.getAccount(req);
		Account.RewardRecipientAssignment assignment = account.getRewardRecipientAssignment();
		long height = Nxt.getBlockchain().getLastBlock().getHeight();
		if(account == null || assignment == null) {
			response.put("rewardRecipient", Long.toUnsignedString(account.getId()));
		}
		else if(assignment.getFromHeight() > height + 1) {
			response.put("rewardRecipient", Long.toUnsignedString(assignment.getPrevRecipientId()));
		}
		else {
			response.put("rewardRecipient", Long.toUnsignedString(assignment.getRecipientId()));
		}
		
		return response;
	}

}
