/******************************************************************************
 * Copyright © 2013-2016 The Nxt Core Developers.                             *
 * Copyright © 2016-2017 Jelurida IP B.V.                                     *
 *                                                                            *
 * See the LICENSE.txt file at the top-level directory of this distribution   *
 * for licensing information.                                                 *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,*
 * no part of the Nxt software, including this file, may be copied, modified, *
 * propagated, or distributed except according to the terms contained in the  *
 * LICENSE.txt file.                                                          *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

/**
 * @depends {nrs.js}
 */
var NRS = (function (NRS, $, undefined) {

    var coins;
    var coinIds;
    var closedGroups;
    var coinSearch;
    var viewingCoin;
    var currentCoin;
    var coinTradeHistoryType;
    var currentCoinID;
    var selectedApprovalCoin;

    NRS.pages.coin_exchange = function() { alert("not implemented") };

    NRS.pages.coin_exchange_history = function () {
        NRS.sendRequest("getCoinExchangeTrades+", {
            "account": NRS.accountRS,
            "includeChainInfo": false,
            "firstIndex": NRS.pageNumber * NRS.itemsPerPage - NRS.itemsPerPage,
            "lastIndex": NRS.pageNumber * NRS.itemsPerPage
        }, function (response) {
            if (response.trades && response.trades.length) {
                if (response.trades.length > NRS.itemsPerPage) {
                    NRS.hasMorePages = true;
                    response.trades.pop();
                }
                var trades = response.trades;
                var exchangeDecimals = NRS.constants.CHAIN_PROPERTIES[trades[0].exchange].decimals;
                var amountDecimals = NRS.getNumberOfDecimals(trades, "amountNQT", function(val) {
                    return NRS.formatQuantity(val.quantityQNT, exchangeDecimals);
                });
                var chainDecimals = NRS.constants.CHAIN_PROPERTIES[NRS.getActiveChain()].decimals;
                var priceDecimals = NRS.getNumberOfDecimals(trades, "priceNQT", function(val) {
                    return NRS.formatOrderPricePerWholeQNT(val.priceNQT, chainDecimals);
                });
                var rows = "";
                for (var i = 0; i < trades.length; i++) {
                    var trade = trades[i];
                    trade.priceNQT = new BigInteger(trade.priceNQT);
                    trade.amountNQT = new BigInteger(trade.amountNQT);
                    rows += "<tr>" +
                        "<td>" + NRS.formatTimestamp(trade.timestamp) + "</td>" +
                        "<td>" + NRS.getTransactionLink(trade.orderFullHash) + "</td>" +
                        "<td>" + NRS.getTransactionLink(trade.matchFullHash, null, false, trade.exchange) + "</td>" +
                        "<td>" + NRS.getChainLink(trade.chain) + "</td>" +
                        "<td>" + NRS.getAccountLink(trade, "account") + "</td>" +
                        "<td class='asset_price numeric'>" + NRS.formatAmount(trade.priceNQT, false, false, priceDecimals) + "</td>" +
                        "<td class='numeric'>" + NRS.formatAmount(trade.amountNQT, false, false, amountDecimals) + "</td>" +
                        "</tr>";
                }
                NRS.dataLoaded(rows);
            } else {
                NRS.dataLoaded();
            }
        });
    };

    NRS.pages.open_coin_orders = function() {
        NRS.getOpenOrders();
    };

    NRS.getOpenOrders = function() {
        NRS.sendRequest("getCoinExchangeOrders", {
            "account": NRS.account,
            "firstIndex": 0,
            "lastIndex": 100
        }, function (response) {
            if (response["orders"] && response["orders"].length) {
                var orders = response["orders"];
                orders.sort(function (a, b) {
                    if (NRS.getChainName(a.exchange) > NRS.getChainName(b.exchange)) {
                        return 1;
                    } else if (NRS.getChainName(a.exchange) < NRS.getChainName(b.exchange)) {
                        return -1;
                    } else {
                        // todo sort same chain orders
                    }
                });

                var rows = "";
                for (var i = 0; i < orders.length; i++) {
                    var order = orders[i];
                    order.priceNQT = new BigInteger(order.priceNQT);
                    order.amountNQT = new BigInteger(order.amountNQT);
                    var decimals = NRS.getChainDecimals(order.chain);
                    rows += "<tr>" +
                        "<td>" + NRS.getTransactionLink(order.orderFullHash) + "</td>" +
                        "<td>" + NRS.getChainLink(order.exchange) + "</td>" +
                        "<td>" + NRS.formatQuantity(order.amountNQT, decimals) + "</td>" +
                        "<td>" + NRS.formatOrderPricePerWholeQNT(order.bidNQT, order.decimals) + "</td>" +
                        "<td>" + NRS.formatOrderPricePerWholeQNT(order.askNQT, order.decimals) + "</td>" +
                        "<td class='cancel'><a href='#' data-toggle='modal' data-target='#cancel_coin_order_modal' data-order='" + NRS.escapeRespStr(order.order) + "'>" + $.t("cancel") + "</a></td>" +
                    "</tr>";
                }
                NRS.dataLoaded(rows);
            } else {
                NRS.dataLoaded();
            }
        });
    };

    NRS.incoming.open_orders = function (transactions) {
        if (NRS.hasTransactionUpdates(transactions)) {
            NRS.loadPage("open_coin_orders");
        }
    };

    $("#cancel_coin_order_modal").on("show.bs.modal", function (e) {
        var $invoker = $(e.relatedTarget);
        var order = $invoker.data("order");
        $("#cancel_coin_order_order").val(order);
    });

    NRS.setup.coin_exchange = function () {
        var sidebarId = 'sidebar_coin_exchange';
        var options = {
            "id": sidebarId,
            "titleHTML": '<i class="fa fa-money"></i><span data-i18n="coin_exchange">Coin Exchange</span>',
            "page": 'coin_exchange_history',
            "desiredPosition": 30,
            "depends": { tags: [ NRS.constants.API_TAGS.CE ] }
        };
        NRS.addTreeviewSidebarMenuItem(options);
        options = {
            "titleHTML": '<span data-i18n="coin_exchange">Coin Exchange</span>',
            "type": 'PAGE',
            "page": 'coin_exchange'
        };
        NRS.appendMenuItemToTSMenuItem(sidebarId, options);
        options = {
            "titleHTML": '<span data-i18n="coin_exchange_history">Coin Exchange History</span></a>',
            "type": 'PAGE',
            "page": 'coin_exchange_history'
        };
        NRS.appendMenuItemToTSMenuItem(sidebarId, options);
        options = {
            "titleHTML": '<span data-i18n="open_coin_orders">Open Coin Orders</span>',
            "type": 'PAGE',
            "page": 'open_coin_orders'
        };
        NRS.appendMenuItemToTSMenuItem(sidebarId, options);
    };

    return NRS;
}(NRS || {}, jQuery));