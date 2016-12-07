/*
 * Copyright 2013-2016 The Nxt Core Developers.
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at
 * the top-level directory of this distribution for the individual copyright
 * holder information and the developer policies on copyright and licensing.
 *
 * Unless otherwise agreed in a custom licensing agreement, no part of the
 * Nxt software, including this file, may be copied, modified, propagated,
 * or distributed except according to the terms contained in the LICENSE.txt
 * file.
 *
 * Removal or modification of this copyright notice is prohibited.
 */
package nxt.peer;

import nxt.Nxt;
import nxt.blockchain.Block;

import java.util.ArrayList;
import java.util.List;

final class GetBlocks {

    private GetBlocks() { }

    /**
     * Process the GetBlocks message and return the Blocks message
     *
     * @param   peer                    Peer
     * @param   request                 Request message
     * @return                          Response message
     */
    static NetworkMessage processRequest(PeerImpl peer, NetworkMessage.GetBlocksMessage request) {
        List<Long> blockIds = request.getBlockIds();
        //TODO: need to handle excluded transactions
        List<Block> blocks = new ArrayList<>(blockIds.size());
        for (Long blockId : blockIds) {
            Block block = Nxt.getBlockchain().getBlock(blockId, true);
            if (block != null) {
                blocks.add(block);
            }
        }
        return new NetworkMessage.BlocksMessage(request.getMessageId(), blocks);
    }
}
