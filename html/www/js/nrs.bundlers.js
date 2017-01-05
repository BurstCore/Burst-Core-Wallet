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
var NRS = (function(NRS, $) {

    function isErrorResponse(response) {
        return response.errorCode || response.errorDescription || response.errorMessage || response.error;
    }

    function getErrorMessage(response) {
        return response.errorDescription || response.errorMessage || response.error;
    } 

    NRS.jsondata = NRS.jsondata||{};

    NRS.jsondata.bundlers = function (response) {
        return {
            accountFormatted: NRS.getAccountLink(response, "bundler"),
            chainFormatted: NRS.getChainLink(response.chain),
            totalFeesLimitFQT: NRS.formatAmount(response.totalFeesLimitFQT),
            currentTotalFeesFQT: NRS.formatAmount(response.currentTotalFeesFQT),
            minRateNQTPerFXT: NRS.formatAmount(response.minRateNQTPerFXT),
            overpayFQTPerFXT: NRS.formatAmount(response.overpayFQTPerFXT)
        };
    };

    NRS.incoming.bundlers = function() {
        NRS.loadPage("bundlers");
    };

    NRS.pages.bundlers = function () {
        NRS.hasMorePages = false;
        var view = NRS.simpleview.get('bundlers_page', {
            errorMessage: null,
            isLoading: true,
            isEmpty: false,
            bundlers: []
        });
        var params = {
            "chain": NRS.getActiveChain(),
            "adminPassword": NRS.getAdminPassword(),
            "firstIndex": NRS.pageNumber * NRS.itemsPerPage - NRS.itemsPerPage,
            "lastIndex": NRS.pageNumber * NRS.itemsPerPage
        };
        NRS.sendRequest("getBundlers", params,
            function (response) {
                if (isErrorResponse(response)) {
                    view.render({
                        errorMessage: getErrorMessage(response),
                        isLoading: false,
                        isEmpty: false
                    });
                    return;
                }
                if (response.bundlers.length > NRS.itemsPerPage) {
                    NRS.hasMorePages = true;
                    response.bundlers.pop();
                }
                view.bundlers.length = 0;
                response.bundlers.forEach(
                    function (bundlerJson) {
                        view.bundlers.push(NRS.jsondata.bundlers(bundlerJson))
                    }
                );
                view.render({
                    isLoading: false,
                    isEmpty: view.bundlers.length == 0
                });
                NRS.pageLoaded();
            }
        )
    };

    NRS.forms.startBundlerComplete = function() {
        $.growl($.t("bundler_started"));
        NRS.loadPage("bundlers");
    };

    $("#stop_bundler_modal").on("show.bs.modal", function(e) {
        var $invoker = $(e.relatedTarget);
        var account = $invoker.data("account");
        if (account) {
            $("#stop_bundler_account").val(account);
        }
        if (NRS.getAdminPassword()) {
            $("#stop_bundler_admin_password").val(NRS.getAdminPassword());
        }
    });

    NRS.forms.stopBundlerComplete = function() {
        $.growl($.t("bundler_stopped"));
        NRS.loadPage("bundlers");
    };

    return NRS;

}(NRS || {}, jQuery));