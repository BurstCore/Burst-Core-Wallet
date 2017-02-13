/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2017 Jelurida IP B.V.
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
package nxt.peer;

import nxt.Nxt;
import nxt.blockchain.Block;
import nxt.blockchain.Blockchain;
import nxt.util.Logger;

final class GetBlock {

    private GetBlock() { }

    /**
     * Process the GetBlock message and return the BlocksMessage
     *
     * @param   peer                    Peer
     * @param   request                 Request message
     * @return                          Response message
     */
    static NetworkMessage processRequest(PeerImpl peer, NetworkMessage.GetBlockMessage request) {
        long blockId = request.getBlockId();
        byte[] excludedTransactions = request.getExcludedTransactions();
        NetworkMessage message;
        Blockchain blockchain = Nxt.getBlockchain();
        blockchain.readLock();
        try {
            Block block = blockchain.getBlock(blockId, true);
            message = new NetworkMessage.BlocksMessage(request.getMessageId(), block, excludedTransactions);
        } catch (Exception e) {
            Logger.logDebugMessage("Failed to get block " + Long.toUnsignedString(blockId), e);
            throw e;
        } finally {
            blockchain.readUnlock();
        }
        return message;
    }
}
