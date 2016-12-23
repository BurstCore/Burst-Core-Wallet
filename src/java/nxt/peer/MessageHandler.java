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

import nxt.Constants;
import nxt.Nxt;
import nxt.util.Logger;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Peer message handler
 */
class MessageHandler implements Runnable {

    /** Message queue */
    private static final LinkedBlockingQueue<QueueEntry> messageQueue = new LinkedBlockingQueue<>();

    /** Shutdown started */
    private static volatile boolean messageShutdown = false;

    /**
     * Construct a message handler
     */
    MessageHandler() {
    }

    /**
     * Process a message
     *
     * @param   peer                    Peer
     * @param   bytes                   Message bytes
     */
    static void processMessage(PeerImpl peer, ByteBuffer bytes) {
        messageQueue.offer(new QueueEntry(peer, bytes));
    }

    /**
     * Shutdown the message handlers
     */
    static void shutdown() {
        if (!messageShutdown) {
            messageShutdown = true;
            messageQueue.offer(new QueueEntry(null, null));
        }
    }

    /**
     * Message handling thread
     */
    @Override
    public void run() {
        Logger.logDebugMessage(Thread.currentThread().getName() + " started");
        try {
            while (true) {
                QueueEntry entry = messageQueue.take();
                //
                // During shutdown, discard all pending messages until we reach the shutdown entry.
                // Requeue the shutdown entry to wake up the next message handler so that
                // it can then shutdown.
                //
                if (messageShutdown) {
                    if (entry.getPeer() == null) {
                        messageQueue.offer(entry);
                        break;
                    }
                    continue;
                }
                //
                // Process the message
                //
                PeerImpl peer = entry.getPeer();
                if (peer.getState() != Peer.State.CONNECTED) {
                    continue;
                }
                NetworkMessage message = null;
                NetworkMessage response;
                try {
                    message = NetworkMessage.getMessage(entry.getBytes());
                    if (message.isResponse()) {
                        if (message.getMessageId() == 0) {
                            Logger.logErrorMessage("'" + message.getMessageName()
                                    + "' response message does not have a message identifier");
                        } else {
                            peer.completeRequest(message);
                        }
                    } else {
                        if (message.downloadNotAllowed()) {
                            if (Nxt.getBlockchainProcessor().isDownloading()) {
                                throw new IllegalStateException(Errors.DOWNLOADING);
                            }
                            if (Constants.isLightClient) {
                                throw new IllegalStateException(Errors.LIGHT_CLIENT);
                            }
                        }
                        response = message.processMessage(peer);
                        if (message.requiresResponse()) {
                            if (response == null) {
                                Logger.logErrorMessage("No response for '" + message.getMessageName() + "' message");
                            } else {
                                peer.sendMessage(response);
                            }
                        }
                    }
                } catch (Exception exc) {
                    String errorMessage = (Peers.hideErrorDetails ? exc.getClass().getName() :
                            (exc.getMessage() != null ? exc.getMessage() : exc.toString()));
                    boolean severeError;
                    if (exc instanceof IllegalStateException) {
                        severeError = false;
                    } else {
                        severeError = true;
                        Logger.logDebugMessage("Unable to process message from " + peer.getHost() + ": " + errorMessage, exc);
                    }
                    if (message != null && message.requiresResponse()) {
                        response = new NetworkMessage.ErrorMessage(message.getMessageId(),
                                severeError, message.getMessageName(), errorMessage);
                        peer.sendMessage(response);
                    }
                }
                //
                // Restart reads from the peer if the pending messages have been cleared
                //
                if (peer.getState() == Peer.State.CONNECTED) {
                    int count = peer.decrementInputCount();
                    if (count == 0) {
                        NetworkHandler.KeyEvent event = peer.getKeyEvent();
                        if ((event.getKey().interestOps() & SelectionKey.OP_READ) == 0) {
                            event.update(SelectionKey.OP_READ, 0);
                            NetworkHandler.wakeup();
                        }
                    }
                }
            }
        } catch (Throwable exc) {
            Logger.logErrorMessage("Message handler abnormally terminated", exc);
        }
        Logger.logDebugMessage(Thread.currentThread().getName() +  " stopped");
    }

    /**
     * Message queue entry
     */
    private static class QueueEntry {

        /** Peer */
        private final PeerImpl peer;

        /** Message buffer */
        private final ByteBuffer bytes;

        /**
         * Construct a queue entry
         *
         * @param   peer                Peer
         * @param   bytes               Message bytes
         */
        private QueueEntry(PeerImpl peer, ByteBuffer bytes) {
            this.peer = peer;
            this.bytes = bytes;
        }

        /**
         * Get the peer
         *
         * @return                      Peer
         */
        private PeerImpl getPeer() {
            return peer;
        }

        /**
         * Get the message bytes
         */
        private ByteBuffer getBytes() {
            return bytes;
        }
    }
}
