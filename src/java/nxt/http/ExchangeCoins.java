/*
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

import nxt.NxtException;
import nxt.account.Account;
import nxt.blockchain.Attachment;
import nxt.blockchain.Chain;
import nxt.blockchain.FxtChain;
import nxt.ce.OrderIssueAttachment;
import nxt.ce.OrderIssueFxtAttachment;

import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static nxt.http.JSONResponses.NOT_ENOUGH_FUNDS;

public final class ExchangeCoins extends CreateTransaction {

    static final ExchangeCoins instance = new ExchangeCoins();

    private ExchangeCoins() {
        super(new APITag[] {APITag.CE, APITag.CREATE_TRANSACTION}, "exchange", "amountNQT", "priceNQT");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {
        Chain chain = ParameterParser.getChain(req, "chain", true);
        Chain exchange = ParameterParser.getChain(req, "exchange", true);
        if (chain == exchange) {
            return JSONResponses.incorrect("exchange", "exchange must specify a different chain");
        }
        long amountNQT = ParameterParser.getAmountNQT(req);
        long priceNQT = ParameterParser.getPriceNQT(req);
        // All ARDR exchange transactions must be on the Fxt chain, otherwise the transaction is on the child chain
        Account account = ParameterParser.getSenderAccount(req);
        Chain txChain = (exchange.getId() == FxtChain.FXT.getId() ? exchange : chain);
        Attachment attachment = (txChain.getId() == FxtChain.FXT.getId() ?
                new OrderIssueFxtAttachment(chain, exchange, amountNQT, priceNQT) :
                new OrderIssueAttachment(chain, exchange, amountNQT, priceNQT));
        try {
            return createTransaction(req, account, attachment, txChain);
        } catch (NxtException.InsufficientBalanceException e) {
            return NOT_ENOUGH_FUNDS;
        }
    }
}
