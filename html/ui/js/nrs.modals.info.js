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

/**
 * @depends {nrs.js}
 * @depends {nrs.modals.js}
 */
var NRS = (function(NRS, $) {
	var nrsModal = $("#nrs_modal");
    nrsModal.on("show.bs.modal", function() {
		if (NRS.fetchingModalData) {
			return;
		}

		NRS.fetchingModalData = true;

		NRS.sendRequest("getState", {
			"includeCounts": true,
            "adminPassword": NRS.getAdminPassword()
		}, function(state) {
			for (var key in state) {
				if (!state.hasOwnProperty(key)) {
					continue;
				}
				var el = $("#nrs_node_state_" + key);
				if (el.length) {
					if (key.indexOf("number") != -1) {
						el.html(NRS.formatAmount(state[key]));
					} else if (key.indexOf("Memory") != -1) {
						el.html(NRS.formatVolume(state[key]));
					} else if (key == "time") {
						el.html(NRS.formatTimestamp(state[key]));
					} else {
						el.html(NRS.escapeRespStr(state[key]));
					}
				}
			}

			$("#nrs_update_explanation").show();
			$("#nrs_modal_state").show();

			NRS.fetchingModalData = false;
		});
	});

	nrsModal.on("hide.bs.modal", function() {
		$("body").off("dragover.nrs, drop.nrs");

		$("#nrs_update_drop_zone, #nrs_update_result, #nrs_update_hashes, #nrs_update_hash_progress").hide();

		$(this).find("ul.nav li.active").removeClass("active");
		$("#nrs_modal_state_nav").addClass("active");

		$(".nrs_modal_content").hide();
	});

	nrsModal.find("ul.nav li").click(function(e) {
		e.preventDefault();

		var tab = $(this).data("tab");

		$(this).siblings().removeClass("active");
		$(this).addClass("active");

		$(".nrs_modal_content").hide();

		var content = $("#nrs_modal_" + tab);

		content.show();
	});

	return NRS;
}(NRS || {}, jQuery));