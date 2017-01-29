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
 * @depends {nrs.modals.js}
 */
var NRS = (function(NRS, $) {

    NRS.forms.dividendPayment = function($modal) {
        var data = NRS.getFormData($modal.find("form:first"));
        if (data.holdingType == "0") {
            data.holding = NRS.getActiveChainId();
        }
        data.asset = NRS.getCurrentAsset().asset;
        if (!data.amountNXTPerShare) {
            return {
                "error": $.t("error_amount_per_share_required")
            }
        } else {
            var decimals = data.dividend_payment_decimals ? data.dividend_payment_decimals : NRS.getActiveChainDecimals();
            data.amountNQTPerShare = NRS.floatToInt(data.amountNXTPerShare, decimals);
        }
        if (!/^\d+$/.test(data.height)) {
            return {
                "error": $.t("error_invalid_dividend_height")
            }
        }
        delete data.amountNXTPerShare;
        return {
            "data": data
        };
    };

    var dividendPaymentModal = $("#dividend_payment_modal");
    dividendPaymentModal.on("show.bs.modal", function() {
        var context = {
            labelText: "Currency",
            labelI18n: "currency",
            inputCodeName: "dividend_payment_ms_code",
            inputIdName: "holding",
            inputDecimalsName: "dividend_payment_decimals",
            helpI18n: "add_currency_modal_help"
        };
        NRS.initModalUIElement($(this), '.dividend_payment_holding_currency', 'add_currency_modal_ui_element', context);

        context = {
            labelText: "Asset",
            labelI18n: "asset",
            inputIdName: "holding",
            inputDecimalsName: "dividend_payment_decimals",
            helpI18n: "add_asset_modal_help"
        };
        NRS.initModalUIElement($(this), '.dividend_payment_holding_asset', 'add_asset_modal_ui_element', context);

        // Activating context help popovers - from some reason this code is activated
        // after the same event in nrs.modals.js which doesn't happen for create pool thus it's necessary
        // to explicitly enable the popover here. strange ...
        $(function () {
            $("[data-toggle='popover']").popover({
                "html": true
            });
        });
    });

    dividendPaymentModal.on("hidden.bs.modal", function() {
        $(this).find(".dividend_payment_info").first().hide();
    });

    $("#dividend_payment_amount_per_share").keydown(function(e) {
        var decimals = NRS.getCurrentAsset().decimals;
        var charCode = !e.charCode ? e.which : e.charCode;
        if (NRS.isControlKey(charCode) || e.ctrlKey || e.metaKey) {
            return;
        }
        return NRS.validateDecimals(8-decimals, charCode, $(this).val(), e);
   	});

    $("#dividend_payment_amount_per_share, #dividend_payment_height").on("blur", function() {
        var $modal = $(this).closest(".modal");
        var amountNXTPerShare = $modal.find("#dividend_payment_amount_per_share").val();
        var height = $modal.find("#dividend_payment_height").val();
        var $callout = $modal.find(".dividend_payment_info").first();
        var classes = "callout-info callout-danger callout-warning";
        if (amountNXTPerShare && /^\d+$/.test(height)) {
            NRS.getAssetAccounts(NRS.getCurrentAsset().asset, height,
                function (response) {
                    var accountAssets = response.accountAssets;
                    var qualifiedDividendRecipients = accountAssets.filter(
                        function(accountAsset) {
                            return accountAsset.accountRS !== NRS.getCurrentAsset().accountRS;
                        });
                    var totalQuantityQNT = new BigInteger("0");
                    qualifiedDividendRecipients.forEach(
                        function (accountAsset) {
                            totalQuantityQNT = totalQuantityQNT.add(new BigInteger(accountAsset.quantityQNT));
                        }
                    );
                    var priceNQT = new BigInteger(NRS.convertToNQT(amountNXTPerShare));
                    var totalNQT = totalQuantityQNT.multiply(priceNQT);

                    $callout.html($.t("dividend_payment_info_preview_success", {
                        "amountNXT": NRS.intToFloat(totalNQT, NRS.getCurrentAsset().decimals + NRS.getActiveChainDecimals()),
                        "coin": NRS.getActiveChainName(),
                        "totalQuantity": NRS.formatQuantity(totalQuantityQNT, NRS.getCurrentAsset().decimals),
                        "recipientCount": qualifiedDividendRecipients.length
                    }));
                    $callout.removeClass(classes).addClass("callout-info").show();
                },
                function (response) {
                    var displayString;
                    if (response.errorCode == 4 || response.errorCode == 8) {
                        displayString = $.t("error_invalid_dividend_height");
                    } else {
                        displayString = $.t("dividend_payment_info_preview_error", {"errorCode": response.errorCode});
                    }
                    $callout.html(displayString);
                    $callout.removeClass(classes).addClass("callout-warning").show();
                }
            );
        }
    });

    $('#dividend_payment_holding_type').change(function () {
        var holdingType = $("#dividend_payment_holding_type");
        if(holdingType.val() == "0") {
            $("#dividend_payment_asset_id_group").css("display", "none");
            $("#dividend_payment_ms_currency_group").css("display", "none");
        } if(holdingType.val() == "1") {
            $("#dividend_payment_asset_id_group").css("display", "inline");
            $("#dividend_payment_ms_currency_group").css("display", "none");
        } else if(holdingType.val() == "2") {
            $("#dividend_payment_asset_id_group").css("display", "none");
            $("#dividend_payment_ms_currency_group").css("display", "inline");
        }
    });

    return NRS;
}(NRS || {}, jQuery));
