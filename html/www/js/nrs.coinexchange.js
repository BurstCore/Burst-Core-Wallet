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
    var coinBalances;
    var closedGroups;
    var coinSearch;
    var viewingCoin;
    var currentCoin;
    var currentCoinID;
    var selectedApprovalCoin;

    NRS.resetCoinExchangeState = function () {
        coins = [];
        coinIds = [];
        closedGroups = [];
        coinSearch = false;
        viewingCoin = false; //viewing non-bookmarked coin
        currentCoin = {};
        currentCoinID = 0;
        selectedApprovalCoin = "";
    };
    NRS.resetCoinExchangeState();

    NRS.setClosedGroups = function(groups) {
        closedGroups = groups;
    };

    NRS.getCurrentCoin = function() {
        return currentCoin;
    };

    function loadCoinFromURL() {
        var page = NRS.getUrlParameter("page");
        var coin = NRS.getUrlParameter("coin");
        if (!page || page != "coin_exchange") {
            return;
        }
        if (!coin) {
            $.growl($.t("missing_coin_param"), {
                "type": "danger"
            });
            return;
        }
        page = page.escapeHTML();
        coin = coin.escapeHTML();
        var chain = NRS.getChain(coin);
        if (!chain) {
            $.growl($.t("invalid_coin_param", { coin: coin }), {
                "type": "danger"
            });
        } else {
            NRS.loadCoin(chain, false);
        }
    }

    NRS.pages.coin_exchange = function(callback) {
        $(".content.content-stretch:visible").width($(".page:visible").width());
        coins = [];
        coinIds = [];
        NRS.storageSelect("coins", null, function(error, coins) {
            // select already bookmarked coins
            $.each(coins, function (index, coin) {
                NRS.cacheCoin(coin);
            });

            var qs = [ "account=" + NRS.accountRS ];
            for (var chain in NRS.constants.CHAIN_PROPERTIES) {
                qs.push("chain=" + chain);
            }
            qs = qs.join("&");

            //check owned coin, see if any are not yet in bookmarked
            NRS.sendRequest("getBalances", { "querystring": qs }, function(response) {
                coinBalances = response.balances;
                $.each(coinBalances, function(chainId, balance) {
                    if (coinIds.indexOf(chainId) == -1 && balance.unconfirmedBalanceNQT > 0) {
                        NRS.saveCoinBookmark(NRS.getChain(chainId));
                        coinIds.push(chainId);
                    }
                });

                NRS.loadCoinExchangeSidebar(callback);
                loadCoinFromURL();
            })
        });
    };

    NRS.cacheCoin = function(coin) {
        if (coinIds.indexOf(coin.id) != -1) {
            return;
        }
        coinIds.push(coin.id);
        if (!coin.groupName) {
            coin.groupName = "";
        }

        var cachedCoin = {
            "id": String(coin.id),
            "name": String(coin.name),
            "decimals": parseInt(coin.decimals, 10),
            "ONE_COIN": coin.ONE_COIN,
            "groupName": String(coin.groupName).toLowerCase()
        };
        coins.push(cachedCoin);
    };

    NRS.forms.addCoinBookmark = function ($modal) {
        var data = NRS.getFormData($modal.find("form:first"));
        data.id = $.trim(data.id);
        if (!data.id) {
            return {
                "error": $.t("error_coin_id_required")
            };
        }
        if (!$.isNumeric(data.id)) {
            data.id = NRS.getChainIdByName(data.id);
        }
        if (data.id == NRS.getActiveChainId()) {
            return {
                "error": $.t("error_coin_id_same_as_chain")
            };
        }
        var chain = NRS.getChain(data.id);
        if (!chain) {
            return {
                "error": $.t("error_coin_id_invalid")
            };
        }
        NRS.saveCoinBookmark(chain, NRS.forms.addCoinBookmarkComplete);
    };

    $("#coin_exchange_bookmark_this_coin").on("click", function () {
        if (viewingCoin) {
            NRS.saveCoinBookmark(viewingCoin, function(newCoins) {
                viewingCoin = false;
                NRS.loadCoinExchangeSidebar(function () {
                    $("#coin_exchange_sidebar").find("a[data-coin=" + newCoins[0].id + "]").addClass("active").trigger("click");
                });
            });
        }
    });

    NRS.forms.addCoinBookmarkComplete = function (newCoins, submittedCoins) {
        coinSearch = false;
        var coinExchangeSidebar = $("#coin_exchange_sidebar");
        if (newCoins.length == 0) {
            NRS.closeModal();
            $.growl($.t("error_coin_already_bookmarked", {
                "count": submittedCoins.length
            }), {
                "type": "danger"
            });
            coinExchangeSidebar.find("a.active").removeClass("active");
            coinExchangeSidebar.find("a[data-coin=" + submittedCoins[0].id + "]").addClass("active").trigger("click");
        } else {
            NRS.closeModal();
            var message = $.t("success_coin_bookmarked", {
                "count": newCoins.length
            });
            $.growl(message, {
                "type": "success"
            });
            NRS.loadCoinExchangeSidebar(function () {
                coinExchangeSidebar.find("a.active").removeClass("active");
                coinExchangeSidebar.find("a[data-coin=" + newCoins[0].id + "]").addClass("active").trigger("click");
            });
        }
    };

    NRS.saveCoinBookmark = function(coin, callback) {
        var newCoin = {
            "id": String(coin.id),
            "name": String(coin.name),
            "decimals": parseInt(coin.decimals, 10),
            "ONE_COIN": coin.ONE_COIN,
            "groupName": ""
        };

        var newCoinIds = [];
        var newCoins = [];
        newCoins.push(newCoin);
        newCoinIds.push({
            "id": String(coin.id)
        });

        NRS.storageSelect("coins", newCoinIds, function (error, existingCoins) {
            var existingIds = [];
            if (existingCoins.length) {
                $.each(existingCoins, function (index, coin) {
                    existingIds.push(coin.id);
                });

                newCoins = $.grep(newCoins, function(v) {
                    return (existingIds.indexOf(v.id) === -1);
                });
            }

            if (newCoins.length == 0) {
                if (callback) {
                    callback([], coins);
                }
            } else {
                NRS.storageInsert("coins", "id", newCoins, function () {
                    $.each(newCoins, function (key, coin) {
                        coinIds.push(coin.id);
                        coins.push(coin);
                    });

                    if (callback) {
                        //for some reason we need to wait a little or DB won't be able to fetch inserted record yet..
                        setTimeout(function () {
                            callback(newCoins, coins);
                        }, 50);
                    }
                });
            }
        });
    };

    NRS.positionCoinSidebar = function () {
        var coinExchangeSidebar = $("#coin_exchange_sidebar");
        coinExchangeSidebar.parent().css("position", "relative");
        coinExchangeSidebar.parent().css("padding-bottom", "5px");
        coinExchangeSidebar.height($(window).height() - 120);
    };

    //called on opening the coin exchange page and automatic refresh
    NRS.loadCoinExchangeSidebar = function (callback) {
        var coinExchangePage = $("#coin_exchange_page");
        var coinExchangeSidebarContent = $("#coin_exchange_sidebar_content");
        if (!coins.length) {
            NRS.pageLoaded(callback);
            coinExchangeSidebarContent.empty();
            if (!viewingCoin) {
                $("#no_coin_selected, #loading_coin_data, #no_coin_search_results, #coin_details").hide();
                $("#no_coins_available").show();
            }
            coinExchangePage.addClass("no_coins");
            return;
        }

        var rows = "";
        coinExchangePage.removeClass("no_coins");
        NRS.positionCoinSidebar();
        coins.sort(function (a, b) {
            if (!a.groupName && !b.groupName) {
                if (a.name > b.name) {
                    return 1;
                } else if (a.name < b.name) {
                    return -1;
                } else {
                    return 0;
                }
            } else if (!a.groupName) {
                return 1;
            } else if (!b.groupName) {
                return -1;
            } else if (a.groupName > b.groupName) {
                return 1;
            } else if (a.groupName < b.groupName) {
                return -1;
            } else {
                if (a.name > b.name) {
                    return 1;
                } else if (a.name < b.name) {
                    return -1;
                } else {
                    return 0;
                }
            }
        });

        var lastGroup = "";
        var ungrouped = true;
        var isClosedGroup = false;
        var isSearch = (coinSearch !== false);
        var searchResults = 0;

        for (var i = 0; i < coins.length; i++) {
            var coin = coins[i];
            if (coin.id == NRS.getActiveChainId()) {
                continue; // Cannot exchange a coin with itself
            }
            if (isSearch) {
                if (coinSearch.indexOf(coin.id) == -1) {
                    continue;
                } else {
                    searchResults++;
                }
            }

            if (coin.groupName.toLowerCase() != lastGroup) {
                var to_check = (coin.groupName ? coin.groupName : "undefined");
                isClosedGroup = closedGroups.indexOf(to_check) != -1;
                if (coin.groupName) {
                    ungrouped = false;
                    rows += "<a href='#' class='list-group-item list-group-item-header" + (coin.groupName == "Ignore List" ? " no-context" : "") + "'";
                    rows += (coin.groupName != "Ignore List" ? " data-context='coin_exchange_sidebar_group_context' " : "data-context=''");
                    rows += " data-groupname='" + NRS.escapeRespStr(coin.groupName) + "' data-closed='" + isClosedGroup + "'>";
                    rows += "<h4 class='list-group-item-heading'>" + NRS.unescapeRespStr(coin.groupName).toUpperCase().escapeHTML() + "</h4>";
                    rows += "<i class='fa fa-angle-" + (isClosedGroup ? "right" : "down") + " group_icon'></i></h4></a>";
                } else {
                    ungrouped = true;
                    rows += "<a href='#' class='list-group-item list-group-item-header no-context' data-closed='" + isClosedGroup + "'>";
                    rows += "<h4 class='list-group-item-heading'>UNGROUPED <i class='fa pull-right fa-angle-" + (isClosedGroup ? "right" : "down") + "'></i></h4>";
                    rows += "</a>";
                }
                lastGroup = coin.groupName.toLowerCase();
            }

            var ownsCoin = false;
            var ownsCoinNQT = 0;

            if (coinBalances) {
                $.each(coinBalances, function(chainId, coinBalance) {
                    if (chainId == coin.id && coinBalance.unconfirmedBalanceNQT != "0") {
                        ownsCoin = true;
                        ownsCoinNQT = coinBalance.unconfirmedBalanceNQT;
                        return false;
                    }
                });
            }

            rows += "<a href='#' class='list-group-item list-group-item-" + (ungrouped ? "ungrouped" : "grouped") + (ownsCoin ? " owns_coin" : " not_owns_coin") + "' ";
            rows += "data-cache='" + i + "' ";
            rows += "data-coin='" + NRS.escapeRespStr(coin.id) + "'" + (!ungrouped ? " data-groupname='" + NRS.escapeRespStr(coin.groupName) + "'" : "");
            rows += (isClosedGroup ? " style='display:none'" : "") + " data-closed='" + isClosedGroup + "'>";
            rows += "<h4 class='list-group-item-heading'>" + NRS.escapeRespStr(coin.name) + "</h4>";
            rows += "<p class='list-group-item-text'><span>" + $.t('amount') + "</span>: " + NRS.formatQuantity(ownsCoinNQT, coin.decimals) + "</p>";
            rows += "</a>";
        }

        var exchangeSidebar = $("#coin_exchange_sidebar");
        var active = exchangeSidebar.find("a.active");
        if (active.length) {
            active = active.data("coin");
        } else {
            active = false;
        }

        coinExchangeSidebarContent.empty().append(rows);
        var coinExchangeSidebarSearch = $("#coin_exchange_sidebar_search");
        coinExchangeSidebarSearch.show();

        if (isSearch) {
            if (active && coinSearch.indexOf(active) != -1) {
                //check if currently selected coin is in search results, if so keep it at that
                exchangeSidebar.find("a[data-coin=" + active + "]").addClass("active");
            } else if (coinSearch.length == 1) {
                //if there is only 1 search result, click it
                exchangeSidebar.find("a[data-coin=" + coinSearch[0] + "]").addClass("active").trigger("click");
            }
        } else if (active) {
            exchangeSidebar.find("a[data-coin=" + active + "]").addClass("active");
        }

        if (isSearch || coins.length >= 10) {
            coinExchangeSidebarSearch.show();
        } else {
            coinExchangeSidebarSearch.hide();
        }
        if (NRS.getUrlParameter("page") && NRS.getUrlParameter("page") == "coin_exchange" && NRS.getUrlParameter("coin")) {

        } else {
            if (isSearch && coinSearch.length == 0) {
                $("#no_coin_search_results").show();
                $("#coin_details, #no_coin_selected, #no_coins_available").hide();
            } else if (!exchangeSidebar.find("a.active").length) {
                $("#no_coin_selected").show();
                $("#coin_details, #no_coins_available, #no_coin_search_results").hide();
            } else if (active) {
                $("#no_coins_available, #no_coin_selected, #no_coin_search_results").hide();
            }

            if (viewingCoin) {
                $("#coin_exchange_bookmark_this_coin").show();
            } else {
                $("#coin_exchange_bookmark_this_coin").hide();
            }
        }
        NRS.pageLoaded(callback);
    };

    NRS.incoming.coin_exchange = function () {
        var coinExchangeSidebar = $("#coin_exchange_sidebar");
        if (!viewingCoin) {
            //refresh active coin
            var $active = coinExchangeSidebar.find("a.active");

            if ($active.length) {
                $active.trigger("click", [{
                    "refresh": true
                }]);
            }
        } else {
            NRS.loadCoin(viewingCoin, true);
        }

        //update coins owned (colored) - not implemented
        coinExchangeSidebar.find("a.list-group-item.owns_coin").removeClass("owns_coin").addClass("not_owns_coin");
    };

    $("#coin_exchange_sidebar").on("click", "a", function (e, data) {
        e.preventDefault();
        currentCoinID = String($(this).data("coin")).escapeHTML();

        //refresh is true if data is refreshed automatically by the system (when a new block arrives)
        var refresh = (data && data.refresh);

        //clicked on a group
        if (!currentCoinID) {
            var group = $(this).data("groupname");
            var closed = $(this).data("closed");

            var $links;
            if (!group) {
                $links = $("#coin_exchange_sidebar").find("a.list-group-item-ungrouped");
            } else {
                $links = $("#coin_exchange_sidebar").find("a.list-group-item-grouped[data-groupname='" + group.escapeHTML() + "']");
            }
            if (!group) {
                group = "undefined";
            }
            if (closed) {
                var pos = closedGroups.indexOf(group);
                if (pos >= 0) {
                    closedGroups.splice(pos);
                }
                $(this).data("closed", "");
                $(this).find("i").removeClass("fa-angle-right").addClass("fa-angle-down");
                $links.show();
            } else {
                closedGroups.push(group);
                $(this).data("closed", true);
                $(this).find("i").removeClass("fa-angle-down").addClass("fa-angle-right");
                $links.hide();
            }
            NRS.storageUpdate("data", {
                "contents": closedGroups.join("#")
            }, [{
                "id": "closed_groups"
            }]);
            return;
        }

        NRS.storageSelect("coins", [{
            "id": currentCoinID
        }], function (error, coin) {
            if (coin && coin.length && coin[0].id == currentCoinID) {
                NRS.loadCoin(coin[0], refresh);
            }
        });
    });

    NRS.loadCoin = function (coin, refresh) {
        var coinId = coin.id;
        currentCoin = coin;
        NRS.currentSubPage = coinId;

        if (!refresh) {
            var coinExchangeSidebar = $("#coin_exchange_sidebar");
            coinExchangeSidebar.find("a.active").removeClass("active");
            coinExchangeSidebar.find("a[data-coin=" + coinId + "]").addClass("active");
            $("#no_coin_selected, #loading_coin_data, #no_coins_available, #no_coin_search_results").hide();
            //noinspection JSValidateTypes
            $("#coin_details").show().parent().animate({
                "scrollTop": 0
            }, 0);
            $("#coin_quantity").html(NRS.formatQuantity("100000000000000000", coin.decimals));
            $("#coin_link").html(NRS.getChainLink(coinId));
            $("#coin_decimals").html(NRS.escapeRespStr(coin.decimals));
            $("#coin_name").html(NRS.escapeRespStr(coin.name));
            $(".coin_name").html(NRS.escapeRespStr(coin.name));
            $("#buy_coin_button").data("coin", coinId);
            $("#buy_coin_with_nxt").html($.t("buy_coin_with_nxt", {
                base: NRS.escapeRespStr(coin.name), counter: NRS.getActiveChainName()
            }));
            $("#buy_coin_price").val("");
            $("#buy_coin_quantity, #buy_coin_total").val("0");

            var coinExchangeAskOrdersTable = $("#coin_exchange_ask_orders_table");
            var coinExchangeTradeHistoryTable = $("#coin_exchange_trade_history_table");
            coinExchangeAskOrdersTable.find("tbody").empty();
            coinExchangeTradeHistoryTable.find("tbody").empty();
            coinExchangeAskOrdersTable.parent().addClass("data-loading").removeClass("data-empty");
            coinExchangeTradeHistoryTable.parent().addClass("data-loading").removeClass("data-empty");

            $(".data-loading img.loading").hide();

            setTimeout(function () {
                $(".data-loading img.loading").fadeIn(200);
            }, 200);

            if (coin.viewingCoin) {
                $("#coin_exchange_bookmark_this_coin").show();
                viewingCoin = coin;
            } else {
                $("#coin_exchange_bookmark_this_coin").hide();
                viewingCoin = false;
            }
        }

        if (NRS.accountInfo.unconfirmedBalanceNQT == "0") {
            $("#your_coin_balance").html("0");
            $("#buy_automatic_price").addClass("zero").removeClass("nonzero");
        } else {
            $("#your_coin_balance").html(NRS.formatAmount(NRS.accountInfo.unconfirmedBalanceNQT));
            $("#buy_automatic_price").addClass("nonzero").removeClass("zero");
        }

        NRS.loadCoinOrders(coinId, refresh);
        NRS.getCoinTradeHistory(coinId, "everyone", refresh);
    };

    function processOrders(orders, coinId, refresh) {
        var askOrdersTable = $("#coin_exchange_ask_orders_table");
        $("#coin_exchange_counter_coin").html(NRS.getActiveChainName());
        $("#coin_exchange_base_coin").html(NRS.getChain(coinId).name);
        if (orders.length) {
            var order;
            $("#sell_orders_count").html("(" + orders.length + (orders.length == 50 ? "+" : "") + ")");
            var rows = "";
            var sum = new BigInteger(String("0"));
            for (var i = 0; i < orders.length; i++) {
                order = orders[i];
                sum = sum.add(new BigInteger(order.exchangeQNT));
                if (i == 0 && !refresh) {
                    $("#buy_coin_price").val(NRS.formatQuantity(order.askNQT, NRS.getActiveChainDecimals()));
                }
                var statusIcon = NRS.getTransactionStatusIcon(order);
                var decimals = NRS.getChain(order.chain).decimals;
                var chain = (order.chain == 1 || order.exchange == 1) ? 1 : order.chain;
                rows += "<tr data-transaction='" + NRS.escapeRespStr(order.order) + "' data-amount='" + String(order.quantityQNT).escapeHTML() + "' data-price='" + String(order.askNQT).escapeHTML() + "'>" +
                    "<td>" + NRS.getTransactionLink(order.orderFullHash, statusIcon, true, chain) + "</td>" +
                    "<td>" + NRS.getAccountLink(order, "account") + "</td>" +
                    "<td class='numeric'>" + NRS.formatQuantity(order.quantityQNT, NRS.getActiveChainDecimals()) + "</td>" +
                    "<td class='numeric'>" + NRS.formatQuantity(order.askNQT, NRS.getActiveChainDecimals()) + "</td>" +
                    "<td class='numeric'>" + NRS.formatQuantity(order.exchangeQNT, decimals) + "</td>" +
                    "<td class='numeric'>" + NRS.formatQuantity(sum, decimals) + "</td>" +
                    "</tr>";
            }
            askOrdersTable.find("tbody").empty().append(rows);
        } else {
            askOrdersTable.find("tbody").empty();
            if (!refresh) {
                $("#buy_coin_price").val("0");
            }
            $("#sell_orders_count").html("");
        }
        NRS.dataLoadFinished(askOrdersTable, !refresh);
    }

    NRS.loadCoinOrders = function (coinId, refresh) {
        var params = {
            "chain": coinId,
            "exchange": NRS.getActiveChainId(),
            "firstIndex": 0,
            "lastIndex": 25
        };
        async.parallel([
                function(callback) {
                    params["showExpectedCancellations"] = "true";
                    NRS.sendRequest("getCoinExchangeOrders+", params, function(response) {
                        var orders = response["orders"];
                        if (!orders) {
                            orders = [];
                        }
                        callback(null, orders);
                    })
                },
                function(callback) {
                    NRS.sendRequest("getExpectedCoinExchangeOrders+", params, function(response) {
                        var orders = response["orders"];
                        if (!orders) {
                            orders = [];
                        }
                        callback(null, orders);
                    })
                }
            ],
            // invoked when both the requests above has completed
            // the results array contains both order lists
            function(err, results) {
                if (err) {
                    NRS.logConsole(err);
                    return;
                }
                var orders = results[0].concat(results[1]);
                orders.sort(function (a, b) {
                    return a.askNQT - b.askNQT;
                });
                processOrders(orders, coinId, refresh);
            });
    };

    NRS.getCoinTradeHistory = function(coinId, type, refresh) {
        var params = {
            "chain": NRS.getActiveChainId(),
            "exchange": coinId,
            "firstIndex": 0,
            "lastIndex": 50
        };

        if (type == "you") {
            params["account"] = NRS.accountRS;
        }

        NRS.sendRequest("getCoinExchangeTrades+", params, function(response) {
            var exchangeTradeHistoryTable = $("#coin_exchange_trade_history_table");
            if (response.trades && response.trades.length) {
                var trades = response.trades;
                var rows = "";
                for (var i = 0; i < trades.length; i++) {
                    var trade = trades[i];
                    var total = NRS.multiply(trade.quantityQNT, NRS.floatToInt(trade.exchangeRate, 8));
                    var isParentChain = trade.chain == 1 || trade.exchange == 1;
                    rows += "<tr>" +
                        "<td>" + NRS.formatTimestamp(trade.timestamp) + "</td>" +
                        "<td>" + NRS.getChainLink(trade.exchange) + "</td>" +
                        "<td>" + NRS.getTransactionLink(trade.orderFullHash, false, false, isParentChain ? "1" : trade.chain) + "</td>" +
                        "<td>" + NRS.getTransactionLink(trade.matchFullHash, false, false, isParentChain ? "1" : trade.exchange) + "</td>" +
                        "<td>" + NRS.getAccountLink(trade, "account") + "</td>" +
                        "<td class='numeric'>" + NRS.formatQuantity(trade.quantityQNT, NRS.getChain(trade.exchange).decimals) + "</td>" +
                        "<td class='coin_price numeric'>" + String(trade.exchangeRate).escapeHTML() + "</td>" +
                        "<td class='numeric'>" + NRS.formatQuantity(total, NRS.getChain(trade.exchange).decimals + 8) + "</td>" +
                    "</tr>";
                }
                exchangeTradeHistoryTable.find("tbody").empty().append(rows);
                NRS.dataLoadFinished(exchangeTradeHistoryTable, !refresh);
            } else {
                exchangeTradeHistoryTable.find("tbody").empty();
                NRS.dataLoadFinished(exchangeTradeHistoryTable, !refresh);
            }
        });
    };

    $("#coin_exchange_trade_history_type").find(".btn").click(function (e) {
        e.preventDefault();
        var type = $(this).data("type");
        NRS.getCoinTradeHistory(currentCoin.id, type, true);
    });

    var coinExchangeSearch = $("#coin_exchange_search");
    coinExchangeSearch.on("submit", function (e) {
        e.preventDefault();
        $("#coin_exchange_search").find("input[name=q]").trigger("input");
    });

    coinExchangeSearch.find("input[name=q]").on("input", function () {
        var input = $.trim($(this).val());
        if (!input) {
            coinSearch = false;
            NRS.loadCoinExchangeSidebar();
            $("#coin_exchange_clear_search").hide();
        } else {
            coinSearch = [];
            $.each(coins, function (key, coin) {
                if (coin.id == input || coin.name.indexOf(input) !== -1) {
                    coinSearch.push(coin.id);
                }
            });

            NRS.loadCoinExchangeSidebar();
            $("#coin_exchange_clear_search").show();
            $("#coin_exchange_show_type").hide();
        }
    });

    $("#coin_exchange_clear_search").on("click", function () {
        var coinExchangeSearch = $("#coin_exchange_search");
        coinExchangeSearch.find("input[name=q]").val("");
        coinExchangeSearch.trigger("submit");
    });

    $("#buy_coin_box").find(".box-header").click(function (e) {
        e.preventDefault();
        //Find the box parent
        var box = $(this).parents(".box").first();
        //Find the body and the footer
        var bf = box.find(".box-body, .box-footer");
        if (!box.hasClass("collapsed-box")) {
            box.addClass("collapsed-box");
            $(this).find(".btn i.fa").removeClass("fa-minus").addClass("fa-plus");
            bf.slideUp();
        } else {
            box.removeClass("collapsed-box");
            bf.slideDown();
            $(this).find(".btn i.fa").removeClass("fa-plus").addClass("fa-minus");
        }
    });

    $("#coin_exchange_ask_orders_table").find("tbody").on("click", "td", function (e) {
        var $target = $(e.target);
        var targetClass = $target.prop("class");
        if ($target.prop("tagName").toLowerCase() == "a" || (targetClass && targetClass.indexOf("fa") == 0)) {
            return;
        }

        var $tr = $target.closest("tr");
        try {
            var priceNQT = new BigInteger(String($tr.data("price")));
            var amountNQT = new BigInteger(String($tr.data("amount")));
            var totalNQT = new BigInteger(NRS.multiply(amountNQT, priceNQT));

            $("#buy_coin_price").val(NRS.convertToQNTf(priceNQT, NRS.getActiveChainDecimals()));
            $("#buy_coin_amount").val(NRS.convertToQNTf(amountNQT, currentCoin.decimals));
            $("#buy_coin_total").val(NRS.convertToNXT(totalNQT));
        } catch (err) {
            return;
        }

        try {
            var balanceNQT = new BigInteger(NRS.accountInfo.unconfirmedBalanceNQT);
        } catch (err) {
            return;
        }

        if (totalNQT.compareTo(balanceNQT) > 0) {
            $("#buy_coin_total").css({
                "background": "#ED4348",
                "color": "white"
            });
        } else {
            $("#buy_coin_total").css({
                "background": "",
                "color": ""
            });
        }

        var box = $("#buy_coin_box");
        if (box.hasClass("collapsed-box")) {
            box.removeClass("collapsed-box");
            box.find(".box-body").slideDown();
            box.find(".box-header").find(".btn i.fa").removeClass("fa-plus").addClass("fa-minus");
        }
    });

    $("#buy_automatic_price").on("click", function () {
        try {
            var coinPrice = $("#buy_coin_price");
            var price = new Big(NRS.convertToNQT(String(coinPrice.val())));            var balanceNQT = new Big(NRS.accountInfo.unconfirmedBalanceNQT);
            var maxQuantity = new Big(NRS.convertToQNTf(currentCoin.amountNQT, currentCoin.decimals));
            if (balanceNQT.cmp(new Big("0")) <= 0) {
                return;
            }

            if (price.cmp(new Big("0")) <= 0) {
                //get minimum price if no offers exist, based on coin decimals..
                price = new Big("" + Math.pow(10, currentCoin.decimals));
                coinPrice.val(NRS.convertToNXT(price.toString()));
            }

            var quantity = new Big(NRS.amountToPrecision(balanceNQT.div(price).toString(), currentCoin.decimals));
            var total = quantity.times(price);

            //proposed quantity is bigger than available quantity
            if (quantity.cmp(maxQuantity) == 1) {
                quantity = maxQuantity;
                total = quantity.times(price);
            }

            $("#buy_coin_quantity").val(quantity.toString());
            var coinTotal = $("#buy_coin_total");
            coinTotal.val(NRS.convertToNXT(total.toString()));
            coinTotal.css({
                "background": "",
                "color": ""
            });
        } catch (err) {
            NRS.logConsole(err.message);
        }
    });

    var buyCoinFields = $("#buy_coin_quantity, #buy_coin_price");
    buyCoinFields.keydown(function (e) {
        var charCode = !e.charCode ? e.which : e.charCode;
        if (NRS.isControlKey(charCode) || e.ctrlKey || e.metaKey) {
            return;
        }
        var isQuantityField = /_quantity/i.test($(this).attr("id"));
        var decimals = currentCoin.decimals;
        var maxFractionLength = (isQuantityField ? decimals : NRS.getActiveChainDecimals() + decimals);
        NRS.validateDecimals(maxFractionLength, charCode, $(this).val(), e);
    });

    buyCoinFields.keyup(function () {
        try {
            var quantityQNT = new BigInteger(NRS.convertToQNT(String($("#buy_coin_quantity").val()), currentCoin.decimals));
            var priceNQT = new BigInteger(NRS.convertToQNT(String($("#buy_coin_price").val()), currentCoin.decimals));

            if (priceNQT.toString() == "0" || quantityQNT.toString() == "0") {
                $("#buy_coin_total").val("0");
            } else {
                var totalCurrentCoin = quantityQNT.multiply(priceNQT);
                NRS.logConsole("totalCurrentCoin " + totalCurrentCoin);
                var total = NRS.intToFloat(totalCurrentCoin, currentCoin.decimals + currentCoin.decimals);
                $("#buy_coin_total").val(total.toString());
            }
            NRS.logConsole("quantityQNT " + quantityQNT + " priceNQT " + priceNQT + " total " + total);
        } catch (err) {
            NRS.logConsole("buy error " + err.message);
            $("#buy_coin_total").val("0");
        }
    });

    $("#coin_order_modal").on("show.bs.modal", function (e) {
        var $invoker = $(e.relatedTarget);
        var coinId = $invoker.data("coin");
        $("#coin_order_modal_button").html("Buy coin").data("resetText", "Buy Coin");
        $(".coin_order_modal_type").html("Buy");

        var quantity = String($("#buy_coin_quantity").val());
        var price = String($("#buy_coin_price").val());
        try {
            var quantityQNT = NRS.convertToQNT(quantity, currentCoin.decimals);
            var priceNQT = NRS.convertToQNT(price, NRS.getActiveChainDecimals());
            var totalNXT = NRS.formatQuantity(NRS.multiply(quantityQNT, priceNQT), currentCoin.decimals + NRS.getActiveChainDecimals());
        } catch (err) {
            $.growl($.t("error_invalid_input", { input: "quantity " + quantity + " price " + price + " decimals " + currentCoin.decimals }), {
                "type": "danger"
            });
            return e.preventDefault();
        }

        if (priceNQT.toString() == "0" || quantityQNT.toString() == "0") {
            $.growl($.t("error_amount_price_required"), {
                "type": "danger"
            });
            return e.preventDefault();
        }

        var description;
        var tooltipTitle;
        var coinName = $("#coin_name");
        description = $.t("buy_coin_order_description", {
            "amount": NRS.formatQuantity(quantityQNT, currentCoin.decimals, true),
            "base": coinName.html().escapeHTML(),
            "price": NRS.formatAmountDecimals(priceNQT, false, true, false, NRS.getActiveChainDecimals()),
            "counter": NRS.getActiveChainName()
        });
        tooltipTitle = $.t("buy_coin_order_description_help", {
            "price": NRS.formatAmountDecimals(priceNQT, false, true, false, NRS.getActiveChainDecimals()),
            "base": coinName.html().escapeHTML(),
            "total": totalNXT,
            "counter": NRS.getActiveChainName()
        });

        $("#coin_order_description").html(description);
        $("#coin_order_total").html(totalNXT + " " + NRS.getActiveChainName());

        var coinOrderTotalTooltip = $("#coin_order_total_tooltip");
        if (quantity != "1") {
            coinOrderTotalTooltip.show();
            coinOrderTotalTooltip.popover("destroy");
            coinOrderTotalTooltip.data("content", tooltipTitle);
            coinOrderTotalTooltip.popover({
                "content": tooltipTitle,
                "trigger": "hover"
            });
        } else {
            coinOrderTotalTooltip.hide();
        }

        $("#coin_order_coin").val(coinId);
        $("#coin_order_quantity").val(quantityQNT);
        $("#coin_order_price").val(priceNQT);
    });

    $("#coin_exchange_sidebar_group_context").on("click", "a", function (e) {
        e.preventDefault();
        var groupName = NRS.selectedContext.data("groupname");
        var option = $(this).data("option");
        if (option == "change_group_name") {
            $("#coin_exchange_change_group_name_old_display").html(groupName.escapeHTML());
            $("#coin_exchange_change_group_name_old").val(groupName);
            $("#coin_exchange_change_group_name_new").val("");
            $("#coin_exchange_change_group_name_modal").modal("show");
        }
    });

    NRS.forms.coinExchangeChangeGroupName = function () {
        var oldGroupName = $("#coin_exchange_change_group_name_old").val();
        var newGroupName = $("#coin_exchange_change_group_name_new").val();
        if (!newGroupName.match(/^[a-z0-9 ]+$/i)) {
            return {
                "error": $.t("error_group_name")
            };
        }

        NRS.storageUpdate("coins", {
            "groupName": newGroupName
        }, [{
            "groupName": oldGroupName
        }], function () {
            setTimeout(function () {
                NRS.loadPage("coin_exchange");
                $.growl($.t("success_group_name_update"), {
                    "type": "success"
                });
            }, 50);
        });

        return {
            "stop": true
        };
    };

    $("#coin_exchange_sidebar_context").on("click", "a", function (e) {
        e.preventDefault();
        var coinId = String(NRS.selectedContext.data("coin"));
        var option = $(this).data("option");
        NRS.closeContextMenu();
        if (option == "add_to_group") {
            $("#coin_exchange_group_coin").val(coinId);
            NRS.storageSelect("coins", [{
                "id": coinId
            }], function (error, coin) {
                coin = coin[0];
                $("#coin_exchange_group_title").html(NRS.escapeRespStr(coin.name));
                NRS.storageSelect("coins", [], function (error, coins) {
                    var groupNames = [];
                    $.each(coins, function (index, coin) {
                        if (coin.groupName && $.inArray(coin.groupName, groupNames) == -1) {
                            groupNames.push(coin.groupName);
                        }
                    });
                    groupNames.sort(function (a, b) {
                        if (a.toLowerCase() > b.toLowerCase()) {
                            return 1;
                        } else if (a.toLowerCase() < b.toLowerCase()) {
                            return -1;
                        } else {
                            return 0;
                        }
                    });

                    var groupSelect = $("#coin_exchange_group_group");
                    groupSelect.empty();
                    $.each(groupNames, function (index, groupName) {
                        var selectedAttr = (coin.groupName && coin.groupName.toLowerCase() == groupName.toLowerCase() ? "selected='selected'" : "");
                        groupSelect.append("<option value='" + groupName.escapeHTML() + "' " + selectedAttr + ">" + groupName.escapeHTML() + "</option>");
                    });
                    var selectedAttr = (!coin.groupName ? "selected='selected'" : "");
                    groupSelect.append("<option value='0' " + selectedAttr + ">None</option>");
                    groupSelect.append("<option value='-1'>New group</option>");
                    $("#coin_exchange_group_modal").modal("show");
                });
            });
        } else if (option == "remove_from_group") {
            NRS.storageUpdate("coins", {
                "groupName": ""
            }, [{
                "id": coinId
            }], function () {
                setTimeout(function () {
                    NRS.loadPage("coin_exchange");
                    $.growl($.t("success_coin_group_removal"), {
                        "type": "success"
                    });
                }, 50);
            });
        } else if (option == "remove_from_bookmarks") {
            NRS.storageDelete("coins", [{
                "id": coinId
            }], function () {
                setTimeout(function () {
                    NRS.loadPage("coin_exchange");
                    $.growl($.t("success_coin_bookmark_removal"), {
                        "type": "success"
                    });
                }, 50);
            });
        }
    });

    NRS.forms.exchangeCoins = function () {
        return {
            "requestType": "exchangeCoins",
            "successMessage": $.t("success_buy_order_coin"),
            "errorMessage": $.t("error_order_coin")
        };
    };

    $("#coin_exchange_group_group").on("change", function () {
        var value = $(this).val();
        if (value == -1) {
            $("#coin_exchange_group_new_group_div").show();
        } else {
            $("#coin_exchange_group_new_group_div").hide();
        }
    });

    NRS.forms.coinExchangeGroup = function () {
        var coinId = $("#coin_exchange_group_coin").val();
        var groupName = $("#coin_exchange_group_group").val();
        if (groupName == 0) {
            groupName = "";
        } else if (groupName == -1) {
            groupName = $("#coin_exchange_group_new_group").val();
        }

        NRS.storageUpdate("coins", {
            "groupName": groupName
        }, [{
            "id": coinId
        }], function () {
            setTimeout(function () {
                NRS.loadPage("coin_exchange");
                if (!groupName) {
                    $.growl($.t("success_coin_group_removal"), {
                        "type": "success"
                    });
                } else {
                    $.growl($.t("success_coin_group_add"), {
                        "type": "success"
                    });
                }
            }, 50);
        });

        return {
            "stop": true
        };
    };

    $("#coin_exchange_group_modal").on("hidden.bs.modal", function () {
        $("#coin_exchange_group_new_group_div").val("").hide();
    });

    $("body").on("click", "a[data-goto-coin]", function (e) {
        e.preventDefault();
        var $visible_modal = $(".modal.in");
        if ($visible_modal.length) {
            $visible_modal.modal("hide");
        }
        viewingCoin = true;
        NRS.goToCoin($(this).data("goto-coin"));
    });

    NRS.goToCoin = function (coin) {
        coinSearch = false;
        $("#coin_exchange_sidebar_search").find("input[name=q]").val("");
        $("#coin_exchange_clear_search").hide();
        $("#coin_exchange_sidebar").find("a.list-group-item.active").removeClass("active");
        $("#no_coin_selected, #coin_details, #no_coins_available, #no_coin_search_results").hide();
        $("#loading_coin_data").show();
        $("ul.sidebar-menu a[data-page=coin_exchange]").last().trigger("click", [{
            callback: function () {
                var coinLink = $("#coin_exchange_sidebar").find("a[data-coin=" + coin + "]");
                if (coinLink.length) {
                    coinLink.click();
                } else {
                    var chain = $.extend({}, NRS.getChain(coin));
                    NRS.loadCoinExchangeSidebar(function() {
                        chain.groupName = "";
                        chain.viewingCoin = true;
                        NRS.loadCoin(chain);
                    });
                }
            }
        }]);
    };

    NRS.pages.coin_exchange_history = function () {
        NRS.sendRequest("getCoinExchangeTrades+", {
            "account": NRS.accountRS,
            "includeChainInfo": false,
            "firstIndex": NRS.pageNumber * NRS.itemsPerPage - NRS.itemsPerPage,
            "lastIndex": NRS.pageNumber * NRS.itemsPerPage
        }, function (response) {
            if (response.trades && response.trades.length > 0) {
                if (response.trades.length > NRS.itemsPerPage) {
                    NRS.hasMorePages = true;
                    response.trades.pop();
                }
                var trades = response.trades;
                var exchangeDecimals = NRS.getChain(trades[0].exchange).decimals;
                var amountDecimals = NRS.getNumberOfDecimals(trades, "quantityQNT", function(val) {
                    return NRS.formatQuantity(val.quantityQNT, exchangeDecimals);
                });
                var rows = "";
                for (var i = 0; i < trades.length; i++) {
                    var trade = trades[i];
                    var isArdorTrade = NRS.isParentChain() || trade.exchange == 1;
                    var orderChain = isArdorTrade ? 1 : NRS.getActiveChainId();
                    var matchChain = isArdorTrade ? 1 : trade.exchange;
                    trade.priceNQT = new BigInteger(trade.priceNQT);
                    trade.amountNQT = new BigInteger(trade.amountNQT);
                    rows += "<tr>" +
                        "<td>" + NRS.formatTimestamp(trade.timestamp) + "</td>" +
                        "<td>" + NRS.getTransactionLink(trade.orderFullHash, null, false, orderChain) + "</td>" +
                        "<td>" + NRS.getTransactionLink(trade.matchFullHash, null, false, matchChain) + "</td>" +
                        "<td>" + NRS.getChainLink(trade.exchange) + "</td>" +
                        "<td>" + NRS.getAccountLink(trade, "account") + "</td>" +
                        "<td class='coin_price numeric'>" + String(trade.exchangeRate).escapeHTML() + "</td>" +
                        "<td class='numeric'>" + NRS.formatQuantity(trade.quantityQNT, NRS.getChain(trade.exchange).decimals, false, amountDecimals) + "</td>" +
                        "</tr>";
                }
                NRS.dataLoaded(rows);
            } else {
                NRS.dataLoaded();
            }
        });
    };

    NRS.pages.open_coin_orders = function() {
        NRS.getOpenCoinOrders();
    };

    NRS.getOpenCoinOrders = function() {
        NRS.sendRequest("getCoinExchangeOrders", {
            "firstIndex": 0,
            "lastIndex": 100
        }, function (response) {
            if (response["orders"] && response["orders"].length) {
                var orders = response["orders"];
                orders.sort(function (a, b) {
                    return a.bidNQT - b.bidNQT;
                });

                var rows = "";
                for (var i = 0; i < orders.length; i++) {
                    var order = orders[i];
                    var decimals = NRS.getChainDecimals(order.chain);
                    var exchangeDecimals = NRS.getChainDecimals(order.exchange);
                    var chain = (order.chain == 1 || order.exchange == 1) ? 1 : order.chain;
                    rows += "<tr>" +
                        "<td>" + NRS.getTransactionLink(order.orderFullHash, false, false, chain) + "</td>" +
                        "<td>" + NRS.getChainLink(order.exchange) + "</td>" +
                        "<td>" + NRS.getAccountLink(order, "account") + "</td>" +
                        "<td>" + NRS.formatQuantity(order.quantityQNT, exchangeDecimals) + "</td>" +
                        "<td>" + NRS.formatQuantity(order.bidNQT, decimals) + "</td>" +
                        "<td>" + NRS.formatQuantity(order.exchangeQNT, decimals) + "</td>" +
                        "<td>" + NRS.formatQuantity(order.askNQT, exchangeDecimals) + "</td>" +
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