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

import java.util.List;

final class GetPeers {

    private GetPeers() {}

    /**
     * Process the GetPeers message and return the AddPeers message
     *
     * @param   peer                    Peer
     * @param   message                 Request message
     * @return                          Response message
     */
    static NetworkMessage processRequest(PeerImpl peer, NetworkMessage.GetPeersMessage request) {
        List<Peer> peerList = Peers.getPeers(p -> !p.isBlacklisted()
                        && p.getState() == Peer.State.CONNECTED
                        && p.getAnnouncedAddress() != null
                        && p.shareAddress()
                        && !p.getAnnouncedAddress().equals(peer.getAnnouncedAddress()),
                    NetworkMessage.MAX_LIST_SIZE);
        if (!peerList.isEmpty()) {
            peer.sendMessage(new NetworkMessage.AddPeersMessage(peerList));
        }
        return null;
    }
}
