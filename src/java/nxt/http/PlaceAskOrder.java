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

import nxt.NxtException;
import nxt.account.Account;
import nxt.ae.AskOrderPlacementAttachment;
import nxt.ae.Asset;
import nxt.blockchain.Attachment;
import nxt.blockchain.ChildChain;
import org.json.simple.JSONStreamAware;
import java.math.BigDecimal;
import java.math.MathContext;

import javax.servlet.http.HttpServletRequest;

import static nxt.http.JSONResponses.NO_COST_ORDER;
import static nxt.http.JSONResponses.NOT_ENOUGH_ASSETS;

public final class PlaceAskOrder extends CreateTransaction {

    static final PlaceAskOrder instance = new PlaceAskOrder();

    private PlaceAskOrder() {
        super(new APITag[] {APITag.AE, APITag.CREATE_TRANSACTION}, "asset", "quantityQNT", "priceNQT");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {

        Asset asset = ParameterParser.getAsset(req);
        long priceNQT = ParameterParser.getPriceNQT(req);
        long quantityQNT = ParameterParser.getQuantityQNT(req);
        Account account = ParameterParser.getSenderAccount(req);

        ChildChain childChain = ParameterParser.getChildChain(req);
        BigDecimal quantity = new BigDecimal(quantityQNT, MathContext.DECIMAL128)
                .movePointLeft(asset.getDecimals());
        BigDecimal price = new BigDecimal(priceNQT, MathContext.DECIMAL128)
                .movePointLeft(childChain.getDecimals());
        if (quantity.multiply(price).movePointRight(childChain.getDecimals()).longValue() == 0) {
            return NO_COST_ORDER;
        }

        Attachment attachment = new AskOrderPlacementAttachment(asset.getId(), quantityQNT, priceNQT);
        try {
            return createTransaction(req, account, attachment);
        } catch (NxtException.InsufficientBalanceException e) {
            return NOT_ENOUGH_ASSETS;
        }
    }

}
