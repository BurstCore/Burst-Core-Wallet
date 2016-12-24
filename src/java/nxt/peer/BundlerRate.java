/*
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

import nxt.blockchain.ChildChain;

public final class BundlerRate {

    /**
     * Process a BundlerRate message (there is no response message)
     *
     * @param   peer                    Peer
     * @param   request                 Request message
     * @return                          Response message
     */
    static NetworkMessage processRequest(PeerImpl peer, NetworkMessage.BundlerRateMessage request) {
        Peers.updateBundlerRates(peer, request);
        return null;
    }

    /** Bundler chain */
    private final ChildChain chain;

    /** Bundler rate */
    private final long rate;

    /**
     * Create a new bundler rate
     *
     * @param   chain                   Child chain identifier
     * @param   rate                    Bundler rate
     */
    public BundlerRate(ChildChain chain, long rate) {
        this.chain = chain;
        this.rate = rate;
    }

    /**
     * Get the child chain
     *
     * @return                          Child chain
     */
    public ChildChain getChain() {
        return chain;
    }

    /**
     * Get the bundler rate
     *
     * @return                          Rate
     */
    public long getRate() {
        return rate;
    }
}
