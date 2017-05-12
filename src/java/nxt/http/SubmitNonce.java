package nxt.http;

import java.math.BigInteger;
import java.nio.ByteBuffer;

import javax.servlet.http.HttpServletRequest;

import nxt.Account;
import nxt.Block;
import nxt.Generator;
import nxt.Nxt;
import nxt.crypto.Crypto;
import nxt.util.Convert;
import fr.cryptohash.Shabal256;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;


public final class SubmitNonce extends APIServlet.APIRequestHandler {
    static final SubmitNonce instance = new SubmitNonce();

    private SubmitNonce() {
        super(new APITag[] {APITag.MINING}, "secretPhrase", "nonce", "accountId");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {
        String secret = Convert.emptyToNull(req.getParameter("secretPhrase"));
        Long nonce = Convert.parseUnsignedLong(req.getParameter("nonce"));
        String accountIdParam = Convert.emptyToNull(req.getParameter("accountId"));

        JSONObject response = new JSONObject();

        if (secret == null) {
            response.put("result", "Missing secretPhrase");
            return response;
        }

        if (nonce == null) {
            response.put("result", "Missing nonce");
            return response;
        }

        byte[] secretPublicKey = Crypto.getPublicKey(secret);
        long secretAccountId = Account.getId(secretPublicKey);
        byte[] publicKey = null;
        
        if (accountIdParam != null) {
            // generator's account needs to exist, have a public key AND reward recipient set to secretAccountId
            long accountId = Convert.parseAccountId(accountIdParam);
            Account genAccount = null;

            if (accountId != 0)
                genAccount = Account.getAccount(accountId);

            if (genAccount != null) {
                Account.RewardRecipientAssignment assignment = genAccount.getRewardRecipientAssignment();
                long rewardId;

                if (assignment == null) {
                    rewardId = genAccount.getId();
                } else if (assignment.getFromHeight() > Nxt.getBlockchain().getHeight() + 1) {
                    rewardId = assignment.getPrevRecipientId();
                } else {
                    rewardId = assignment.getRecipientId();
                }

                if (rewardId != secretAccountId) {
                    response.put("result", "Passphrase does not match reward recipient");
                    return response;
                }
            }

            publicKey = Account.getPublicKey(accountId);

            if (genAccount == null || publicKey == null) {
                response.put("result", "Passthrough mining requires public key in blockchain");
                return response;
            }
        }

        BigInteger POCTime = null;
        if (publicKey == null) {
            POCTime = Generator.submitNonce(secret, nonce);
        } else {
            POCTime = Generator.submitNonce(secret, nonce, publicKey);
        }

        if (POCTime == null) {
            response.put("result", "Failed to submit nonce to Generator");
            return response;
        }

        response.put("result", "success");
        response.put("deadline", POCTime);

        return response;
    }

    @Override
    protected boolean requirePost() {
        return true;
    }
}
