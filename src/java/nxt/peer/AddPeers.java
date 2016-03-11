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

final class AddPeers  {

    private AddPeers() {}

    /**
     * Process an AddPeers message (there is no response message)
     *
     * @param   peer                    Peer
     * @param   msg                     Request message
     * @return                          Response message
     */
    static NetworkMessage processRequest(PeerImpl peer, NetworkMessage.AddPeersMessage msg) {
        List<String> addresses = msg.getAnnouncedAddresses();
        List<Long> services = msg.getServices();
        if (!addresses.isEmpty() && Peers.shouldGetMorePeers() && !Peers.hasTooManyKnownPeers()) {
            Peers.peersService.execute(() -> {
                for (int i=0; i<addresses.size(); i++) {
                    PeerImpl newPeer = (PeerImpl)Peers.findOrCreatePeer(addresses.get(i), true);
                    if (newPeer != null) {
                        if (Peers.addPeer(newPeer)) {
                            newPeer.setServices(services.get(i));
                        }
                    }
                    if (Peers.hasTooManyKnownPeers()) {
                        break;
                    }
                }
            });
        }
        return null;
    }
}
