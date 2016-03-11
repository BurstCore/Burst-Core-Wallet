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

import nxt.Block;
import nxt.Nxt;

import java.util.List;

final class GetNextBlocks {

    private GetNextBlocks() {}

    /**
     * Process the GetNextBlocks message and return the Blocks message
     *
     * @param   peer                    Peer
     * @param   message                 Request message
     * @return                          Response message
     */
    static NetworkMessage processRequest(PeerImpl peer, NetworkMessage.GetNextBlocksMessage request) {
        long blockId = request.getBlockId();
        List<Long> blockIds = request.getBlockIds();
        int limit = (request.getLimit() != 0 ? request.getLimit() : 36);
        List<? extends Block> blocks;
        if (!blockIds.isEmpty()) {
            if (blockIds.size() > 36) {
                throw new IllegalArgumentException(Errors.TOO_MANY_BLOCKS_REQUESTED);
            }
            blocks = Nxt.getBlockchain().getBlocksAfter(blockId, blockIds);
        } else {
            if (limit > 36) {
                throw new IllegalArgumentException(Errors.TOO_MANY_BLOCKS_REQUESTED);
            }
            blocks = Nxt.getBlockchain().getBlocksAfter(blockId, limit);
        }
        return new NetworkMessage.BlocksMessage(request.getMessageId(), blocks);
    }
}
