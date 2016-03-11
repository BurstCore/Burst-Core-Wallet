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
import nxt.NxtException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Iterator;
import java.util.List;

final class BlockInventory {

    /** Block cache */
    private static final ConcurrentHashMap<Long, Block> blockCache = new ConcurrentHashMap<>();

    private BlockInventory() {}

    /**
     * Process a BlockInventory message (there is no response message)
     *
     * @param   peer                    Peer
     * @param   request                 Request message
     * @return                          Response message
     */
    static NetworkMessage processRequest(PeerImpl peer, NetworkMessage.BlockInventoryMessage request) {
        final long invBlockId = request.getBlockId();
        long invPreviousBlockId = request.getPreviousBlockId();
        int invTimestamp = request.getTimestamp();
        //
        // Ignore the block if we already have it
        //
        if (blockCache.get(invBlockId) != null) {
            return null;
        }
        //
        // Accept the block if it is a continuation of the current chain or represents
        // a fork of 1 or 2 blocks.  Forks longer than 2 blocks will be handled by the
        // blockchain download processor.
        //
        Block invLastBlock = Nxt.getBlockchain().getLastBlock();
        Block invTipBlock = blockCache.get(invPreviousBlockId);
        if (invPreviousBlockId == invLastBlock.getId() ||
                (invPreviousBlockId == invLastBlock.getPreviousBlockId() &&
                        invTimestamp < invLastBlock.getTimestamp()) ||
                (invTipBlock != null && invTipBlock.getPreviousBlockId() == invLastBlock.getPreviousBlockId())) {
            Nxt.getBlockchainProcessor().suspendDownload(true);
            Peers.peersService.execute(() -> {
                //
                // Request the block
                //
                if (blockCache.get(invBlockId) != null) {
                    return;
                }
                NetworkMessage.BlocksMessage response = (NetworkMessage.BlocksMessage)peer.sendRequest(
                        new NetworkMessage.GetBlocksMessage(Collections.singletonList(invBlockId)));
                if (response == null || response.getBlockCount() == 0 || blockCache.get(invBlockId) != null) {
                    return;
                }
                //
                // Process the block
                //
                try {
                    Block block = response.getBlocks().get(0);
                    long previousBlockId = block.getPreviousBlockId();
                    Block lastBlock = Nxt.getBlockchain().getLastBlock();
                    Iterator<Block> it = blockCache.values().iterator();
                    while (it.hasNext()) {
                        Block cacheBlock = it.next();
                        if (cacheBlock.getId() != lastBlock.getPreviousBlockId() &&
                                cacheBlock.getPreviousBlockId() != lastBlock.getPreviousBlockId()) {
                            it.remove();
                        }
                    }
                    blockCache.put(block.getId(), block);
                    if (previousBlockId == lastBlock.getId() ||
                            (previousBlockId == lastBlock.getPreviousBlockId() &&
                                    block.getTimestamp() < lastBlock.getTimestamp())) {
                        Nxt.getBlockchainProcessor().processPeerBlock(block);
                    } else  {
                        Block tipBlock = blockCache.get(previousBlockId);
                        if (tipBlock != null && tipBlock.getPreviousBlockId() == lastBlock.getPreviousBlockId()) {
                            List<Block> blockList = new ArrayList<>(2);
                            blockList.add(tipBlock);
                            blockList.add(block);
                            Nxt.getBlockchainProcessor().processPeerBlocks(blockList);
                        }
                    }
                } catch (NxtException | RuntimeException e) {
                    peer.blacklist(e);
                }
            });
        } else if (!Nxt.getBlockchain().hasBlock(invBlockId)) {
            Nxt.getBlockchainProcessor().suspendDownload(false);
        }
        return null;
    }
}
