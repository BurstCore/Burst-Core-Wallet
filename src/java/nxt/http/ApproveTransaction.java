/*
 * Copyright © 2013-2016 The Nxt Core Developers.
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


import nxt.Constants;
import nxt.NxtException;
import nxt.account.Account;
import nxt.blockchain.Attachment;
import nxt.blockchain.ChainTransactionId;
import nxt.blockchain.ChildChain;
import nxt.util.Convert;
import nxt.voting.PhasingPollHome;
import nxt.voting.PhasingVoteCastingAttachment;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

import static nxt.http.JSONResponses.MISSING_TRANSACTION_FULL_HASH;
import static nxt.http.JSONResponses.TOO_MANY_PHASING_VOTES;
import static nxt.http.JSONResponses.UNKNOWN_CHAIN;
import static nxt.http.JSONResponses.UNKNOWN_PHASED_TRANSACTION;

public class ApproveTransaction extends CreateTransaction {
    static final ApproveTransaction instance = new ApproveTransaction();

    private ApproveTransaction() {
        super(new APITag[]{APITag.CREATE_TRANSACTION, APITag.PHASING}, "phasedTransaction", "phasedTransaction", "phasedTransaction",
                "revealedSecret", "revealedSecretIsText");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {
        String[] phasedTransactionValues = req.getParameterValues("phasedTransaction");

        if (phasedTransactionValues == null || phasedTransactionValues.length == 0) {
            return MISSING_TRANSACTION_FULL_HASH;
        }

        if (phasedTransactionValues.length > Constants.MAX_PHASING_VOTE_TRANSACTIONS) {
            return TOO_MANY_PHASING_VOTES;
        }

        List<ChainTransactionId> phasedTransactionIds = new ArrayList<>(phasedTransactionValues.length);
        for (String phasedTransactionValue : phasedTransactionValues) {
            String[] s = phasedTransactionValue.split(":");
            int chainId = Integer.parseInt(s[0]);
            ChildChain childChain = ChildChain.getChildChain(chainId);
            if (childChain == null) {
                return UNKNOWN_CHAIN;
            }
            byte[] hash = Convert.parseHexString(s[1]);
            PhasingPollHome.PhasingPoll phasingPoll = childChain.getPhasingPollHome().getPoll(hash);
            if (phasingPoll == null) {
                return UNKNOWN_PHASED_TRANSACTION;
            }
            phasedTransactionIds.add(new ChainTransactionId(chainId, hash));
        }

        byte[] secret;
        String secretValue = Convert.emptyToNull(req.getParameter("revealedSecret"));
        if (secretValue != null) {
            boolean isText = "true".equalsIgnoreCase(req.getParameter("revealedSecretIsText"));
            secret = isText ? Convert.toBytes(secretValue) : Convert.parseHexString(secretValue);
        } else {
            String secretText = Convert.emptyToNull(req.getParameter("revealedSecretText"));
            if (secretText != null) {
                secret = Convert.toBytes(secretText);
            } else {
                secret = Convert.EMPTY_BYTE;
            }
        }
        Account account = ParameterParser.getSenderAccount(req);
        Attachment attachment = new PhasingVoteCastingAttachment(phasedTransactionIds, secret);
        return createTransaction(req, account, attachment);
    }
}
