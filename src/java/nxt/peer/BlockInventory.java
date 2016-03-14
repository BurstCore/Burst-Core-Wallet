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
import nxt.util.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

final class BlockInventory {

    /** Block cache */
    private static final ConcurrentHashMap<Long, Block> blockCache = new ConcurrentHashMap<>();

    /** Pending blocks */
    private static final Set<Long> pendingBlocks = Collections.synchronizedSet(new HashSet<>());

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
        // Ignore the block if we already have it or are in the process of getting it
        //
        if (blockCache.get(invBlockId) != null || pendingBlocks.contains(invBlockId)) {
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
            Logger.logDebugMessage("Suspending blockchain download - blockchain synchronized");
            Nxt.getBlockchainProcessor().suspendDownload(true);
            pendingBlocks.add(invBlockId);
            Peers.peersService.execute(() -> {
                Peer feederPeer = null;
                try {
                    //
                    // Request the block, starting with the peer that sent the BlocksInventory message
                    //
                    List<Peer> connectedPeers = Peers.getConnectedPeers();
                    if (connectedPeers.isEmpty()) {
                        return;
                    }
                    int index = connectedPeers.indexOf(peer);
                    if (index < 0) {
                        index = 0;
                    }
                    int startIndex = index;
                    List<Long> blockIdList = Collections.singletonList(invBlockId);
                    NetworkMessage.BlocksMessage response;
                    while (true) {
                        feederPeer = connectedPeers.get(index);
                        response = (NetworkMessage.BlocksMessage)feederPeer.sendRequest(
                                        new NetworkMessage.GetBlocksMessage(blockIdList));
                        if (blockCache.get(invBlockId) != null) {
                            return;
                        }
                        if (response == null || response.getBlockCount() == 0) {
                            index = (index < connectedPeers.size()-1 ? index+1 : 0);
                            if (index == startIndex) {
                                return;
                            }
                            continue;
                        }
                        break;
                    }
                    //
                    // Process the block
                    //
                    Block block = response.getBlocks().get(0);
                    long previousBlockId = block.getPreviousBlockId();
                    Block lastBlock = Nxt.getBlockchain().getLastBlock();
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
                    int now = Nxt.getEpochTime();
                    Iterator<Block> it = blockCache.values().iterator();
                    while (it.hasNext()) {
                        Block cacheBlock = it.next();
                        if (cacheBlock.getTimestamp() < now - 10*60) {
                            it.remove();
                        }
                    }
                } catch (NxtException | RuntimeException e) {
                    if (feederPeer != null) {
                        feederPeer.blacklist(e);
                    }
                } finally {
                    pendingBlocks.remove(invBlockId);
                }
            });
        } else if (!Nxt.getBlockchain().hasBlock(invBlockId)) {
            Logger.logDebugMessage("Resuming blockchain download - fork resolution required");
            Nxt.getBlockchainProcessor().suspendDownload(false);
        }
        return null;
    }
}
