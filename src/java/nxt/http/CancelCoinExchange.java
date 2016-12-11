/*
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

import nxt.NxtException;
import nxt.account.Account;
import nxt.blockchain.Attachment;
import nxt.blockchain.Chain;
import nxt.blockchain.FxtChain;
import nxt.ce.OrderCancelAttachment;
import nxt.ce.OrderCancelFxtAttachment;

import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static nxt.http.JSONResponses.NOT_ENOUGH_FUNDS;

public final class CancelCoinExchange extends CreateTransaction {

    static final CancelCoinExchange instance = new CancelCoinExchange();

    private CancelCoinExchange() {
        super(new APITag[] {APITag.CE, APITag.CREATE_TRANSACTION}, "orderFullHash");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {
        byte[] orderHash = ParameterParser.getBytes(req, "orderFullHash", true);
        Chain chain = ParameterParser.getChain(req);
        Account account = ParameterParser.getSenderAccount(req);
        Attachment attachment = (chain.getId() == FxtChain.FXT.getId() ?
                new OrderCancelFxtAttachment(orderHash) : new OrderCancelAttachment(orderHash));
        try {
            return createTransaction(req, account, attachment);
        } catch (NxtException.InsufficientBalanceException e) {
            return NOT_ENOUGH_FUNDS;
        }
    }
}
