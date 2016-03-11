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

import nxt.util.Logger;

final class GetInfo {

    private GetInfo() {}

    /**
     * Process the GetInfo message and send our GetInfo message in response
     *
     * @param   peer                    Peer
     * @param   message                 GetInfo message
     * @return                          Always null since the response message is sent asynchronously
     */
    static NetworkMessage processRequest(PeerImpl peer, NetworkMessage.GetInfoMessage message) {
        if (!Peers.ignorePeerAnnouncedAddress) {
            String announcedAddress = message.getAnnouncedAddress();
            if (announcedAddress != null) {
                announcedAddress = announcedAddress.toLowerCase().trim();
                if (!peer.verifyAnnouncedAddress(announcedAddress)) {
                    Logger.logDebugMessage("GetInfo: ignoring invalid announced address for " + peer.getHost());
                    String oldAnnouncedAddress = peer.getAnnouncedAddress();
                    if (!peer.verifyAnnouncedAddress(oldAnnouncedAddress)) {
                        Logger.logDebugMessage("GetInfo: old announced address for " + peer.getHost() + " no longer valid");
                        Peers.changePeerAnnouncedAddress(peer, null);
                    }
                    peer.disconnectPeer();
                    return null;
                }
                if (!announcedAddress.equals(peer.getAnnouncedAddress())) {
                    Logger.logDebugMessage("GetInfo: peer " + peer.getHost() + " changed announced address from " + peer.getAnnouncedAddress() + " to " + announcedAddress);
                    Peers.changePeerAnnouncedAddress(peer, announcedAddress);
                }
            } else if (!peer.getHost().equals(peer.getAnnouncedAddress())) {
                Peers.changePeerAnnouncedAddress(peer, null);
            }
        }
        String application = message.getApplicationName();
        if (application == null) {
            application = "?";
        }
        peer.setApplication(application.trim());

        String version = message.getApplicationVersion();
        if (version == null) {
            version = "?";
        }
        peer.setVersion(version.trim());

        String platform = message.getApplicationPlatform();
        if (platform == null) {
            platform = "?";
        }
        peer.setPlatform(platform.trim());

        peer.setShareAddress(message.getShareAddress());

        peer.setApiPort(message.getApiPort());
        peer.setApiSSLPort(message.getSslPort());

        long origServices = peer.getServices();
        peer.setServices(message.getServices());
        if (peer.getServices() != origServices) {
            Peers.notifyListeners(peer, Peers.Event.CHANGE_SERVICES);
        }

        NetworkHandler.sendGetInfoMessage(peer);
        return null;
    }
}
