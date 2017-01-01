/*
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
import nxt.account.Account;
import nxt.blockchain.ChildChain;
import nxt.crypto.Crypto;
import nxt.util.Logger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.util.Arrays;
import java.util.List;

public final class BundlerRate {

    /** Empty public key */
    private static final byte[] emptyPublicKey = new byte[32];

    /** Empty signature */
    private static final byte[] emptySignature = new byte[64];

    /**
     * Process a BundlerRate message (there is no response message)
     *
     * @param   peer                    Peer
     * @param   request                 Request message
     * @return                          Response message
     */
    static NetworkMessage processRequest(PeerImpl peer, NetworkMessage.BundlerRateMessage request) {
        List<BundlerRate> rates = request.getRates();
        //
        // Verify the bundler accounts
        //
        for (BundlerRate rate : rates) {
            byte[] accountPublicKey = Account.getPublicKey(rate.getAccountId());
            if (!Crypto.verify(rate.getSignature(), rate.getUnsignedBytes(), rate.getPublicKey()) ||
                        (accountPublicKey != null && !Arrays.equals(rate.getPublicKey(), accountPublicKey))) {
                Logger.logDebugMessage("Bundler rate for account "
                        + Long.toUnsignedString(rate.getAccountId()) + " failed signature verification");
                return null;
            }
        }
        //
        // Update the rates and relay the message
        //
        Peers.updateBundlerRates(peer, request);
        return null;
    }

    /** Bundler chain */
    private final ChildChain chain;

    /** Bundler rate */
    private final long rate;

    /** Bundler account */
    private final long accountId;

    /** Bundler public key */
    private final byte[] publicKey;

    /** Timestamp */
    private int timestamp;

    /** Signature */
    private final byte[] signature;

    /**
     * Create an unsigned bundler rate
     *
     * @param   chain                   Child chain
     * @param   rate                    Bundler rate
     */
    public BundlerRate(ChildChain chain, long rate) {
        this.chain = chain;
        this.publicKey = emptyPublicKey;
        this.accountId = 0;
        this.rate = rate;
        this.timestamp = 0;
        this.signature = emptySignature;
    }

    /**
     * Create a signed bundler rate
     *
     * @param   chain                   Child chain
     * @param   rate                    Bundler rate
     * @param   secretPhrase            Bundler account secret phrase
     */
    public BundlerRate(ChildChain chain, long rate, String secretPhrase) {
        this.chain = chain;
        this.publicKey = Crypto.getPublicKey(secretPhrase);
        this.accountId = Account.getId(publicKey);
        this.rate = rate;
        this.timestamp = Nxt.getEpochTime();
        this.signature = Crypto.sign(getUnsignedBytes(), secretPhrase);
    }

    /**
     * Create a signed bundler rate
     *
     * @param   buffer                      Encoded data
     * @throws  BufferUnderflowException    Encoded data is too short
     * @throws  NetworkException            Encoded data is not valid
     */
    public BundlerRate(ByteBuffer buffer) throws BufferUnderflowException, NetworkException {
        int chainId = buffer.getInt();
        this.chain = ChildChain.getChildChain(chainId);
        if (this.chain == null) {
            throw new NetworkException("Child chain '" + chainId + "' is not valid");
        }
        this.publicKey = new byte[32];
        buffer.get(this.publicKey);
        this.accountId = Account.getId(this.publicKey);
        this.rate = buffer.getLong();
        this.timestamp = buffer.getInt();
        this.signature = new byte[64];
        buffer.get(this.signature);
    }

    /**
     * Get the encoded length
     *
     * @return                          Encoded length
     */
    public int getLength() {
        return 4 + 32 + 8 + 4 + 64;
    }

    /**
     * Get our bytes
     *
     * @param   buffer                      Byte buffer
     * @throws  BufferOverflowException     Allocated buffer is too small
     */
    public synchronized void getBytes(ByteBuffer buffer) {
        buffer.putInt(chain.getId())
              .put(publicKey)
              .putLong(rate)
              .putInt(timestamp)
              .put(signature);
    }

    /**
     * Get the unsigned bytes
     *
     * @return                              Rate bytes
     */
    public byte[] getUnsignedBytes() {
        byte[] bytes = new byte[getLength() - 4 - 64];
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(chain.getId())
              .put(publicKey)
              .putLong(rate);
        return bytes;
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
     * Get the account identifier
     *
     * @return                          Account identifier
     */
    public long getAccountId() {
        return accountId;
    }

    /**
     * Get the account public key
     *
     * @return                          Account public key
     */
    public byte[] getPublicKey() {
        return publicKey;
    }

    /**
     * Get the bundler rate
     *
     * @return                          Rate
     */
    public long getRate() {
        return rate;
    }

    /**
     * Get the timestamp
     *
     * @return                          Timestamp
     */
    public synchronized int getTimestamp() {
        return timestamp;
    }

    /**
     * Set the timestamp
     *
     * @param   timestamp               New timestamp
     */
    public synchronized void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Get the signature
     *
     * @return                          Signature
     */
    public byte[] getSignature() {
        return signature;
    }

    /**
     * Get the hash code
     *
     * @return                          Hash code
     */
    @Override
    public int hashCode() {
        return Integer.hashCode(chain.getId()) ^ Long.hashCode(accountId) ^ Long.hashCode(rate) ^
                    Integer.hashCode(timestamp);
    }

    /**
     * Check if two bundler rates are equal
     *
     * @param   obj                     Bundler rate to compare
     * @return                          TRUE if the rates are equal
     */
    @Override
    public boolean equals(Object obj) {
        return ((obj instanceof BundlerRate) &&
                chain == ((BundlerRate)obj).chain &&
                accountId == ((BundlerRate)obj).accountId &&
                rate == ((BundlerRate)obj).rate &&
                timestamp == ((BundlerRate)obj).timestamp);
    }
}
