/*
 * Copyright 2013-2016 The Nxt Core Developers.
 *
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

import java.io.IOException;

/**
 * Signals that a peer network communication exception has occurred
 */
class NetworkException extends IOException {

    /**
     * Construct a NetworkException with the specified detail message.
     *
     * @param   message                 Detail message which is saved for later retrieval
     */
    NetworkException(String message) {
        super(message);
    }

    /**
     * Construct a NetworkException with the specified detail message and cause.
     *
     * @param   message                 Detail message which is saved for later retrieval
     * @param   cause                   Cause which is saved for later retrieval
     */
    NetworkException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Construct a NetworkException with the specified cause and a
     * detail message of cause.toString().
     *
     * @param   cause                   Cause which is saved for later retrieval
     */
    NetworkException(Throwable cause) {
        super(cause);
    }
}
