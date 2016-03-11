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
import nxt.Block;

final class GetCumulativeDifficulty {

    private GetCumulativeDifficulty() {}

    /**
     * Process a GetCumulativeDifficulty message and return a CumulativeDifficulty message
     *
     * @param   peer                    Peer
     * @param   request                 Request message
     * @return                          Response message
     */
    static NetworkMessage processRequest(PeerImpl peer, NetworkMessage.GetCumulativeDifficultyMessage request) {
        Block lastBlock = Nxt.getBlockchain().getLastBlock();
        NetworkMessage.CumulativeDifficultyMessage response =
                new NetworkMessage.CumulativeDifficultyMessage(request.getMessageId(),
                        lastBlock.getCumulativeDifficulty(), lastBlock.getHeight());
        return response;
    }
}
