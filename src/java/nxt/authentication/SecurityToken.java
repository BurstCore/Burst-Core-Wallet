/*
 * Copyright © 2017 Jelurida IP B.V.
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of the Nxt software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 *
 * Removal or modification of this copyright notice is prohibited.
 */
package nxt.authentication;

import java.nio.ByteBuffer;

/**
 * Security token
 */
public interface SecurityToken {

    /**
     * Get peer account identifier
     *
     * @return                  Peer account identifier
     */
    long getPeerAccountId();

    /**
     * Get peer public key
     *
     * @return                  Peer public key
     */
    byte[] getPeerPublicKey();

    /**
     * Get the session key
     *
     * @param   secretPhrase    Server credentials secret phrase
     * @return                  Session key or null if there is no session key
     */
    byte[] getSessionKey(String secretPhrase);

    /**
     * Set the session key
     *
     * @param   secretPhrase    Server credentials secret phrase
     * @param   sessionKey      Session key
     */
    void setSessionKey(String secretPhrase, byte[] sessionKey);

    /**
     * Get the serialized token length
     *
     * @return                  Serialized token length
     */
    int getLength();

    /**
     * Get the serialized token
     *
     * @return                  Serialized token
     */
    byte[] getBytes();

    /**
     * Add the serialized token to a buffer
     *
     * @param   buffer          Byte buffer
     * @return                  Byte buffer
     */
    default ByteBuffer getBytes(ByteBuffer buffer) {
        buffer.put(getBytes());
        return buffer;
    }
}
