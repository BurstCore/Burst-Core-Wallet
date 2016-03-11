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

import nxt.util.Logger;

import java.nio.ByteBuffer;
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
        Logger.logInfoMessage(Thread.currentThread().getName() + " started");
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
                NetworkMessage message = null;
                NetworkMessage response;
                try {
                    message = NetworkMessage.getMessage(entry.getBytes());
                    if (message.downloadNotAllowed()) {
                        throw new IllegalStateException(Errors.DOWNLOADING);
                    }
                    response = message.processMessage(peer);
                    if (response == null) {
                        if (message.getMessageId() == 0) {
                            Logger.logErrorMessage("'" + message.getMessageName()
                                    + "' response message does not have a message identifier");
                        } else {
                            peer.completeRequest(message);
                        }
                    } else {
                        peer.sendMessage(response);
                    }
                } catch (Exception exc) {
                    if (message != null && message.requiresResponse()) {
                        response = new NetworkMessage.ErrorMessage(
                                message.getMessageId(),
                                exc.getMessage() != null ? exc.getMessage() : exc.toString());
                        peer.sendMessage(response);
                    }
                }
            }
        } catch (Throwable exc) {
            Logger.logErrorMessage("Message handler abnormally terminated", exc);
        }
        Logger.logInfoMessage(Thread.currentThread().getName() +  " terminated");
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
