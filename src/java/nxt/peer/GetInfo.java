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

import nxt.Constants;
import nxt.Nxt;
import nxt.NxtException;
import nxt.authentication.Role;
import nxt.authentication.RoleMapperFactory;
import nxt.authentication.SecurityToken;
import nxt.blockchain.Transaction;
import nxt.crypto.Crypto;
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
        if (!peer.setVersion(version.trim())) {
            return null;
        }

        String platform = message.getApplicationPlatform();
        if (platform == null) {
            platform = "?";
        }
        peer.setPlatform(platform.trim());

        peer.setShareAddress(message.getShareAddress());

        peer.setApiPort(message.getApiPort());
        peer.setApiSSLPort(message.getSslPort());
        peer.setDisabledAPIs(message.getDisabledAPIs());
        peer.setApiServerIdleTimeout(message.getApiServerIdleTimeout());
        peer.setBlockchainState(message.getBlockchainState());

        long origServices = peer.getServices();
        peer.setServices(message.getServices());
        if (peer.getServices() != origServices) {
            Peers.notifyListeners(peer, Peers.Event.CHANGE_SERVICES);
        }
        //
        // Authenticate the peer (peer must have WRITER permission)
        //
        SecurityToken securityToken = null;
        if (Constants.isPermissioned) {
            securityToken = message.getSecurityToken();
            if (securityToken == null) {
                Logger.logDebugMessage("GetInfo: No security token provided by peer " + peer.getHost());
                peer.disconnectPeer();
                return null;
            }
            long peerId = securityToken.getPeerAccountId();
            if (!RoleMapperFactory.getRoleMapper().isUserInRole(peerId, Role.WRITER)) {
                Logger.logDebugMessage("GetInfo: Peer " + peer.getHost() + " does not have WRITER permission");
                peer.disconnectPeer();
                return null;
            }
        }
        //
        // Indicate the connection handshake is complete.  For an inbound connection, we need
        // to send our GetInfo message.  For an outbound connection, we have already sent our
        // GetInfo message.
        //
        if (peer.isInbound()) {
            if (securityToken != null) {
                byte[] sessionKey = new byte[32];
                Crypto.getSecureRandom().nextBytes(sessionKey);
                NetworkHandler.sendGetInfoMessage(peer, securityToken.getPeerPublicKey(), sessionKey);
                peer.setSessionKey(sessionKey);
            } else {
                NetworkHandler.sendGetInfoMessage(peer);
            }
        } else if (securityToken != null) {
            byte[] sessionKey = securityToken.getSessionKey(Peers.peerSecretPhrase, securityToken.getPeerPublicKey());
            if (sessionKey == null) {
                Logger.logDebugMessage("GetInfo: Peer " + peer.getHost() + " did not provide a session key");
                peer.disconnectPeer();
                return null;
            }
            peer.setSessionKey(sessionKey);
        }
        peer.handshakeComplete();
        //
        // Send our bundler rates
        //
        Peers.sendBundlerRates(peer);
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
                List<? extends Transaction> addedTransactions = Nxt.getTransactionProcessor().processPeerTransactions(transactions);
                TransactionsInventory.cacheTransactions(addedTransactions);
            } catch (NxtException.ValidationException | RuntimeException e) {
                peer.blacklist(e);
            }
        });
        return null;
    }
}
