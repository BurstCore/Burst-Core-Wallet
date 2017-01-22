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

var NRS = (function (NRS, $) {

    NRS.formatOrderPricePerWholeQNT = function (price, decimals, zeroPad) {
        price = NRS.calculateOrderPricePerWholeQNT(price, decimals, true);
        return NRS.format(price, false, zeroPad);
    };

    NRS.calculateOrderPricePerWholeQNT = function (price, decimals, returnAsObject) {
        if (typeof price != "object") {
            price = new BigInteger(String(price));
        }
        return NRS.convertToNXT(price.multiply(new BigInteger("" + Math.pow(10, decimals))), returnAsObject);
    };

    NRS.calculatePricePerWholeQNT = function (price, decimals) {
        price = String(price);

        if (decimals) {
            var toRemove = price.slice(-decimals);

            if (!/^[0]+$/.test(toRemove)) {
                throw $.t("error_invalid_input", { input: "price " + price + " decimals " + decimals });
            } else {
                return price.slice(0, -decimals);
            }
        } else {
            return price;
        }
    };

    function calculateOrderTotalImpl(quantityQNT, priceNQT) {
        if (typeof quantityQNT != "object") {
            quantityQNT = new BigInteger(String(quantityQNT));
        }
        if (typeof priceNQT != "object") {
            priceNQT = new BigInteger(String(priceNQT));
        }
        return quantityQNT.multiply(priceNQT);
    }

    NRS.calculateOrderTotalNQT = function (quantityQNT, priceNQT) {
        return calculateOrderTotalImpl(quantityQNT, priceNQT).toString();
    };

    NRS.calculateOrderTotal = function (quantityQNT, priceNQT) {
        return NRS.calculateOrderTotalDecimals(quantityQNT, priceNQT, NRS.getActiveChainDecimals());
    };

    NRS.calculateOrderTotalDecimals = function (quantityQNT, priceNQT, decimals) {
        return NRS.intToFloat(calculateOrderTotalImpl(quantityQNT, priceNQT), decimals);
    };

    NRS.calculatePercentage = function (a, b, rounding_mode) {
        if (String(b) == "0") {
            return "0";
        }
        if (rounding_mode != undefined) { // Rounding mode from Big.js
            Big.RM = rounding_mode;
        }
        a = new Big(String(a));
        b = new Big(String(b));

        var result = a.div(b).times(new Big("100")).toFixed(2);
        Big.RM = 1;
        return NRS.format(result.toString());
    };

    NRS.convertToNXT = function(amount, returnAsObject) {
        var decimals = NRS.getActiveChainDecimals();
        return NRS.intToFloat(amount, decimals, returnAsObject);
    };

    NRS.convertToFXT = function(amount, returnAsObject) {
        return NRS.intToFloat(amount, 8, returnAsObject);
    };

    NRS.convertToChainCoin = function(amount, chain, returnAsObject) {
        if (typeof chain == "number") {
            chain = NRS.getChain(chain);
        }
        return NRS.intToFloat(amount, chain.decimals, returnAsObject);
    };

    NRS.intToFloat = function (amount, decimals, returnAsObject) {
        if (typeof amount != "object") {
            amount = new BigInteger(String(amount));
        }
        var negative = "";
        if (amount.compareTo(BigInteger.ZERO) < 0) {
            amount = amount.abs();
            negative = "-";
        }
        var oneCoin = NRS.getOneCoin(decimals);
        var fractionalPart = amount.mod(new BigInteger(oneCoin)).toString();
        amount = amount.divide(new BigInteger(oneCoin));
        var mantissa = "";
        if (fractionalPart && fractionalPart != "0") {
            mantissa = ".";
            for (var i = fractionalPart.length; i < decimals; i++) {
                mantissa += "0";
            }
            mantissa += fractionalPart.replace(/0+$/, "");
        }
        amount = amount.toString();
        if (returnAsObject) {
            return {
                "negative": negative,
                "amount": amount,
                "mantissa": mantissa
            };
        } else {
            return negative + amount + mantissa;
        }
    };

    NRS.amountToPrecision = function (amount, decimals) {
        amount = String(amount);

        var parts = amount.split(".");

        //no fractional part
        if (parts.length == 1) {
            return parts[0];
        } else if (parts.length == 2) {
            var fraction = parts[1];
            fraction = fraction.replace(/0+$/, "");

            if (fraction.length > decimals) {
                fraction = fraction.substring(0, decimals);
            }

            return parts[0] + "." + fraction;
        } else {
            throw $.t("error_invalid_input", { input: "amount " + amount + " decimals " + decimals });
        }
    };

    NRS.convertToNQT = function(currency) {
        return NRS.floatToInt(currency, NRS.getActiveChainDecimals());
    };

    NRS.floatToInt = function(currency, decimals) {
        currency = String(currency);
        var parts = currency.split(".");
        var amount = parts[0];
        var fraction;
        if (parts.length == 1) {
            //no fractional part
            fraction = NRS.getOneCoin(decimals).substring(1);
        } else if (parts.length == 2) {
            if (parts[1].length <= decimals) {
                fraction = parts[1];
            } else {
                fraction = parts[1].substring(0, decimals);
            }
        } else {
            throw $.t("error_invalid_input", { input: "currency " + currency + " decimals " + decimals});
        }
        for (var i = fraction.length; i < decimals; i++) {
            fraction += "0";
        }
        var result = amount + "" + fraction;
        if (!/^\d+$/.test(result)) {
            //in case there's a comma or something else in there.. at this point there should only be numbers
            throw $.t("error_invalid_input", { input: "currency " + currency + " decimals " + decimals});
        }
        //remove leading zeroes
        result = result.replace(/^0+/, "");
        if (result === "") {
            result = "0";
        }
        return result;
    };

    NRS.convertToQNTf = function (quantity, decimals, returnAsObject) {
        quantity = String(quantity);

        if (quantity.length < decimals) {
            for (var i = quantity.length; i < decimals; i++) {
                quantity = "0" + quantity;
            }
        }

        var mantissa = "";

        if (decimals) {
            mantissa = "." + quantity.substring(quantity.length - decimals);
            quantity = quantity.substring(0, quantity.length - decimals);

            if (!quantity) {
                quantity = "0";
            }

            mantissa = mantissa.replace(/0+$/, "");

            if (mantissa == ".") {
                mantissa = "";
            }
        }

        if (returnAsObject) {
            return {
                "amount": quantity,
                "mantissa": mantissa
            };
        } else {
            return quantity + mantissa;
        }
    };

    NRS.convertToQNT = function (quantity, decimals) {
        quantity = String(quantity);

        var parts = quantity.split(".");

        var qnt = parts[0];

        //no fractional part
        var i;
        if (parts.length == 1) {
            if (decimals) {
                for (i = 0; i < decimals; i++) {
                    qnt += "0";
                }
            }
        } else if (parts.length == 2) {
            var fraction = parts[1];
            if (fraction.length > decimals) {
                throw $.t("error_fraction_decimals", {
                    "decimals": decimals
                });
            } else if (fraction.length < decimals) {
                for (i = fraction.length; i < decimals; i++) {
                    fraction += "0";
                }
            }
            qnt += fraction;
        } else {
            throw $.t("error_invalid_input", { input: "quantity " + quantity + " decimals " + decimals});
        }

        //in case there's a comma or something else in there.. at this point there should only be numbers
        if (!/^\d+$/.test(qnt)) {
            throw $.t("error_invalid_input_numbers", { input: "quantity " + quantity + " decimals " + decimals});
        }
        try {
            if (parseInt(qnt) === 0) {
                return "0";
            }
        } catch (e) {
        }

        //remove leading zeroes
        return qnt.replace(/^0+/, "");
    };

    NRS.format = function(params, no_escaping, zeroPad) {
        return NRS.formatDecimals(params, no_escaping, zeroPad, NRS.getActiveChainDecimals());
    };

    NRS.formatDecimals = function(params, no_escaping, zeroPad, decimals) {
        var amount;
        var mantissa;
        if (typeof params != "object") {
            amount = String(params);
            if (amount.indexOf(".") !== -1) {
                mantissa = amount.substr(amount.indexOf("."));
                amount = amount.replace(mantissa, "");
            } else {
                mantissa = "";
            }
            var negative = amount.charAt(0) == "-" ? "-" : "";
            if (negative) {
                amount = amount.substring(1);
            }
            params = {
                "amount": amount,
                "negative": negative,
                "mantissa": mantissa
            };
        }

        amount = String(params.amount);
        var digits = amount.split("").reverse();
        var formattedAmount = "";
        var locale = NRS.getLocale();
        var formattedMantissa = params.mantissa.replace(".", locale.decimal);
        if (zeroPad) {
            var mantissaLen = formattedMantissa.length;
            if (mantissaLen > 0) {
                formattedMantissa += NRS.getOneCoin(decimals).substr(1, zeroPad - mantissaLen + 1);
            } else {
                formattedMantissa += NRS.getOneCoin(decimals).substr(1, zeroPad);
                if (zeroPad != 0) {
                    formattedMantissa = locale.decimal + formattedMantissa;
                }
            }
        }
        for (var i = 0; i < digits.length; i++) {
            if (i > 0 && i % 3 == 0) {
                formattedAmount = locale.section + formattedAmount;
            }
            formattedAmount = digits[i] + formattedAmount;
        }

        var output = (params.negative ? params.negative : "") + formattedAmount + formattedMantissa;

        if (!no_escaping) {
            output = output.escapeHTML();
        }
        return output;
    };

    NRS.formatQuantity = function (quantity, decimals, no_escaping, zeroPad) {
        return NRS.format(NRS.convertToQNTf(quantity, decimals, true), no_escaping, zeroPad);
    };

    NRS.formatAmount = function (amount, round, no_escaping, zeroPad) {
        return NRS.formatAmountDecimals(amount, round, no_escaping, zeroPad, NRS.getActiveChainDecimals())
    };

    NRS.formatAmountDecimals = function (amount, round, no_escaping, zeroPad, decimals) {
        if (typeof amount == "undefined") {
            return "0";
        } else if (typeof amount == "string") {
            amount = new BigInteger(amount);
        }

        var negative = "";
        var mantissa = "";

        if (typeof amount == "object") {
            var params = NRS.intToFloat(amount, decimals, true);
            negative = params.negative;
            amount = params.amount;
            mantissa = params.mantissa;
        } else {
            //rounding only applies to non-nqt
            if (round) {
                amount = (Math.round(amount * 100) / 100);
            }

            if (amount < 0) {
                amount = Math.abs(amount);
                negative = "-";
            }

            amount = "" + amount;
            if (amount.indexOf(".") !== -1) {
                mantissa = amount.substr(amount.indexOf("."));
                amount = amount.replace(mantissa, "");
            } else {
                mantissa = "";
            }
        }
        if (NRS.settings) {
            var offset = 0;
            if (mantissa != "" && mantissa.substring(0, 1) == ".") {
                offset ++;
            }
            var maxLength = parseInt(NRS.settings.max_nxt_decimals) + offset;
            if (mantissa.length > maxLength) {
                mantissa = mantissa.substring(0, maxLength);
                if (mantissa.length == 1 && mantissa.substring(0, 1) == ".") {
                    mantissa = "";
                }
            }
        }

        return NRS.format({
            "negative": negative,
            "amount": amount,
            "mantissa": mantissa
        }, no_escaping, zeroPad);
    };

    NRS.getTransactionsAmountDecimals = function(transactions) {
        var decimals = {};
        decimals.amount = NRS.getNumberOfDecimals(transactions, "amountNQT", function (transaction) {
            return NRS.formatAmount(transaction.amountNQT);
        });
        decimals.fee = NRS.getNumberOfDecimals(transactions, "feeNQT", function (transaction) {
            return NRS.formatAmount(transaction.feeNQT);
        });
        return decimals;
    };

    NRS.getNumberOfDecimals = function(rows, key, callback) {
        var locale = NRS.getLocale();
        var decimals = 0;
        for (var i=0; i<rows.length; i++) {
            var val = rows[i][key];
            if (callback) {
                val = callback(rows[i]);
            }
            var tokens = val.split(locale.decimal);
            if (tokens.length != 2) {
                continue;
            }
            decimals = Math.max(decimals, tokens[1].length);
        }
        return decimals;
    };

    NRS.validateDecimals = function (maxFractionLength, charCode, val, e) {
        if (maxFractionLength) {
            //allow 1 single period character
            if (charCode == 110 || charCode == 190) {
                if (val.indexOf(".") != -1) {
                    e.preventDefault();
                    return false;
                } else {
                    return true;
                }
            }
        } else {
            //do not allow period
            if (charCode == 110 || charCode == 190 || charCode == 188) {
                $.growl($.t("error_fractions"), {
                    "type": "danger"
                });
                e.preventDefault();
                return false;
            }
        }
        if (charCode >= 96 && charCode <= 105) {
            // convert numeric keyboard code to normal ascii otherwise String.fromCharCode()
            // returns the wrong value
            charCode = charCode + 48 - 96;
        }
        var input = val + String.fromCharCode(charCode);

        var mantissa = input.match(/\.(\d*)$/);

        //only allow as many as there are decimals allowed..
        if (mantissa && mantissa[1].length > maxFractionLength) {
            var selectedText = NRS.getSelectedText();

            if (selectedText != val) {
                var errorMessage = $.t("error_decimals", {
                    "count": maxFractionLength
                });
                $.growl(errorMessage, {
                    "type": "danger"
                });

                e.preventDefault();
                return false;
            }
        }

        //numeric characters, left/right key, backspace, delete
        if (charCode == 8 || charCode == 37 || charCode == 39 || charCode == 46 || (charCode >= 48 && charCode <= 57 && !isNaN(String.fromCharCode(charCode)))) {
            return true;
        } else {
            //comma
            if (charCode == 188) {
                $.growl($.t("error_comma_not_allowed"), {
                    "type": "danger"
                });
            }
            e.preventDefault();
            return false;
        }
    };

    NRS.getOneCoin = function(decimals) {
        return String(NRS.constants.MAX_ONE_COIN).substring(0, decimals + 1);
    };

    return NRS;
}(NRS || {}, jQuery));