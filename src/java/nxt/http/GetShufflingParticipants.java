/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016 Jelurida IP B.V.
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
import nxt.blockchain.ChildChain;
import nxt.db.DbIterator;
import nxt.shuffling.ShufflingParticipantHome;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetShufflingParticipants extends APIServlet.APIRequestHandler {

    static final GetShufflingParticipants instance = new GetShufflingParticipants();

    private GetShufflingParticipants() {
        super(new APITag[] {APITag.SHUFFLING}, "shuffling");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {
        long shufflingId = ParameterParser.getUnsignedLong(req, "shuffling", true);
        ChildChain childChain = ParameterParser.getChildChain(req);
        JSONObject response = new JSONObject();
        JSONArray participantsJSONArray = new JSONArray();
        response.put("participants", participantsJSONArray);
        try (DbIterator<ShufflingParticipantHome.ShufflingParticipant> participants = childChain.getShufflingParticipantHome().getParticipants(shufflingId)) {
            for (ShufflingParticipantHome.ShufflingParticipant participant : participants) {
                participantsJSONArray.add(JSONData.participant(participant));
            }
        }
        return response;
    }

}
