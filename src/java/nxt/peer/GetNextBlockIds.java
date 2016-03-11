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

package nxt.peer;

import nxt.Nxt;

import java.util.List;

final class GetNextBlockIds {

    private GetNextBlockIds() {}

    /**
     * Process the GetNextBlockIds message and return the BlockIds message
     *
     * @param   peer                    Peer
     * @param   message                 Request message
     * @return                          Response message
     */
    static NetworkMessage processRequest(PeerImpl peer, NetworkMessage.GetNextBlockIdsMessage request) {
        long blockId = request.getBlockId();
        int limit = request.getLimit();
        if (limit > 1440) {
            throw new IllegalArgumentException(Errors.TOO_MANY_BLOCKS_REQUESTED);
        }
        List<Long> ids = Nxt.getBlockchain().getBlockIdsAfter(blockId, limit > 0 ? limit : 1440);
        return new NetworkMessage.BlockIdsMessage(request.getMessageId(), ids);
    }
}
