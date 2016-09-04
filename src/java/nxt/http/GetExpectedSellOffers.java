/******************************************************************************
 * Copyright © 2013-2016 The Nxt Core Developers.                             *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * Nxt software, including this file, may be copied, modified, propagated,    *
 * or distributed except according to the terms contained in the LICENSE.txt  *
 * file.                                                                      *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

package nxt.http;

import nxt.Attachment;
import nxt.MonetarySystem;
import nxt.Nxt;
import nxt.Transaction;
import nxt.util.Filter;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class GetExpectedSellOffers extends APIServlet.APIRequestHandler {

    static final GetExpectedSellOffers instance = new GetExpectedSellOffers();

    private GetExpectedSellOffers() {
        super(new APITag[] {APITag.MS}, "currency", "account", "sortByRate");
    }

    private final Comparator<Transaction> rateComparator = (o1, o2) -> {
        Attachment.MonetarySystemPublishExchangeOffer a1 = (Attachment.MonetarySystemPublishExchangeOffer)o1.getAttachment();
        Attachment.MonetarySystemPublishExchangeOffer a2 = (Attachment.MonetarySystemPublishExchangeOffer)o2.getAttachment();
        return Long.compare(a1.getSellRateNQT(), a2.getSellRateNQT());
    };

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {

        long currencyId = ParameterParser.getUnsignedLong(req, "currency", false);
        long accountId = ParameterParser.getAccountId(req, "account", false);
        boolean sortByRate = "true".equalsIgnoreCase(req.getParameter("sortByRate"));

        Filter<Transaction> filter = transaction -> {
            if (transaction.getType() != MonetarySystem.PUBLISH_EXCHANGE_OFFER) {
                return false;
            }
            if (accountId != 0 && transaction.getSenderId() != accountId) {
                return false;
            }
            Attachment.MonetarySystemPublishExchangeOffer attachment = (Attachment.MonetarySystemPublishExchangeOffer)transaction.getAttachment();
            return currencyId == 0 || attachment.getCurrencyId() == currencyId;
        };

        List<? extends Transaction> transactions = Nxt.getBlockchain().getExpectedTransactions(filter);
        if (sortByRate) {
            Collections.sort(transactions, rateComparator);
        }

        JSONObject response = new JSONObject();
        JSONArray offerData = new JSONArray();
        transactions.forEach(transaction -> offerData.add(JSONData.expectedSellOffer(transaction)));
        response.put("offers", offerData);
        return response;
    }

}
