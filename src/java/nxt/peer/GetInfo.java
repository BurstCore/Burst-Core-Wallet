/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016 Jelurida IP B.V.
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
import nxt.NxtException;
import nxt.Transaction;
import nxt.util.Logger;

import java.util.Collections;
import java.util.List;

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
        //
        // Process the peer information
        //
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

        peer.setDisabledAPIs(message.getDisabledAPIs());
        peer.setApiServerIdleTimeout(message.getApiServerIdleTimeout());
        /*
        //TODO: implement separately
        peerImpl.setBlockchainState(request.get("blockchainState"));
		*/

        //
        // Indicate the connection handshake is complete.  For an inbound connection, we need
        // to send our GetInfo message.  For an outbound connection, we have already sent our
        // GetInfo message.
        //
        peer.handshakeComplete();
        if (peer.isInbound()) {
            NetworkHandler.sendGetInfoMessage(peer);
        }
        //
        // Get the unconfirmed transactions.  This is done when a connection is established
        // to synchronize the unconfirmed transaction pools of both peers.
        //
        Peers.peersService.execute(() -> {
            List<Long> unconfirmed = Nxt.getTransactionProcessor().getAllUnconfirmedTransactionIds();
            Collections.sort(unconfirmed);
            NetworkMessage.TransactionsMessage response = (NetworkMessage.TransactionsMessage)peer.sendRequest(
                    new NetworkMessage.GetUnconfirmedTransactionsMessage(unconfirmed));
            if (response == null || response.getTransactionCount() == 0) {
                return;
            }
            try {
                List<Transaction> transactions = response.getTransactions();
                Nxt.getTransactionProcessor().processPeerTransactions(transactions);
            } catch (NxtException.ValidationException | RuntimeException e) {
                peer.blacklist(e);
            }
        });
        return null;
    }
}
