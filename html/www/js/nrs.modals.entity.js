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
	$("body").on("click", ".show_entity_modal_action", function(event) {
		event.preventDefault();
		if (NRS.fetchingModalData) {
			return;
		}
		NRS.fetchingModalData = true;
        var entityId;
        var entityType;
        if (typeof $(this).data("id") == "object") {
            var dataObject = $(this).data("id");
            entityId = dataObject["id"];
            entityType = dataObject["type"];
        } else {
            entityId = $(this).data("id");
            entityType = $(this).data("type");
        }
        if ($(this).data("back") == "true") {
            NRS.modalStack.pop(); // The forward modal
            NRS.modalStack.pop(); // The current modal
        }
        NRS.showEntityDetailsModal(entityId, entityType);
	});

	NRS.showEntityDetailsModal = function(id, type) {
        try {
            NRS.setBackLink();
    		NRS.modalStack.push({ class: "show_entity_modal_action", key: "id", value: { id: id, type: type }});
            var requestType;
            var idParam;
            switch(type) {
                case 1:
                    requestType = "getAsset";
                    idParam = "asset";
                    break;
                case 2:
                    requestType = "getCurrency";
                    idParam = "currency";
                    break;
                case 4:
                    requestType = "getPoll";
                    idParam = "poll";
                    break;
                case 5:
                    requestType = "getAccountProperties";
                    idParam = "property";
                    break;
            }
            $("#entity_details_header").html($.t(idParam) + " " + id);
            data = {};
            data[idParam] = id;
            NRS.sendRequest(requestType, data, function(response) {
                var entity = $.extend({}, response);
                var detailsTable = $("#entity_details_table");
                detailsTable.find("tbody").empty().append(NRS.createInfoTable(entity));
                detailsTable.show();
                $("#entity_details_modal").modal("show");
            });
        } finally {
            NRS.fetchingModalData = false;
        }
	};

	return NRS;
}(NRS || {}, jQuery));