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

    NRS.pages.coin_exchange = function() { alert("not implemented") };
    NRS.pages.coin_exchange_history = function() { alert("not implemented") };
    NRS.pages.open_coin_orders = function() { alert("not implemented") };

    NRS.setup.coin_exchange = function () {
        var sidebarId = 'sidebar_coin_exchange';
        var options = {
            "id": sidebarId,
            "titleHTML": '<i class="fa fa-money"></i><span data-i18n="coin_exchange">Coin Exchange</span>',
            "page": 'coin_exchange',
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