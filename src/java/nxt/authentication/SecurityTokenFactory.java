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

import nxt.Constants;

import java.nio.ByteBuffer;

/**
 * Generate an authentication security token for a permissioned blockchain
 */
public abstract class SecurityTokenFactory {

    private static final SecurityTokenFactory securityTokenFactory;
    static {
        if (Constants.isPermissioned) {
            try {
                Class<?> factoryClass = Class.forName("com.jelurida.blockchain.authentication.BlockchainSecurityTokenFactory");
                securityTokenFactory = (SecurityTokenFactory)factoryClass.newInstance();
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                throw new ExceptionInInitializerError(e);
            }
        } else {
            securityTokenFactory = null;
        }
    }

    /**
     * Get the security token factory
     *
     * @return                      Security token factory or null if no provider available
     */
    public static SecurityTokenFactory getSecurityTokenFactory() {
        return securityTokenFactory;
    }

    /**
     * Create a new security token
     *
     * @param   publicKey           Public key
     * @return                      Security token
     */
    public abstract SecurityToken getSecurityToken(byte[] publicKey);

    /**
     * Create a new security token
     *
     * @param   buffer              Byte buffer
     * @return                      Security token
     */
    public abstract SecurityToken getSecurityToken(ByteBuffer buffer);
}
