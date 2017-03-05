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

import java.util.EnumSet;

/**
 * Create a role mapper for a permissioned blockchain
 */
public class RoleMapperFactory {

    private RoleMapperFactory() {}

    private static final RoleMapper roleMapper;
    static {
        if (Constants.isPermissioned) {
            try {
                Class<?> roleMapperClass = Class.forName("com.jelurida.blockchain.authentication.BlockchainRoleMapper");
                roleMapper = (RoleMapper)roleMapperClass.newInstance();
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                throw new ExceptionInInitializerError(e);
            }
        } else {
            roleMapper = new NullRoleMapper();
        }
    }

    /**
     * Get the role mapper
     *
     * A null role mapper will be returned if the blockchain is not permissioned
     *
     * @return                      Role mapper
     */
    public static RoleMapper getRoleMapper() {
        return roleMapper;
    }

    /**
     * Dummy role mapper for a non-permissioned blockchain
     */
    public static class NullRoleMapper implements RoleMapper {

        @Override
        public EnumSet<Role> getUserRoles(String rsAccount) {
            return EnumSet.noneOf(Role.class);
        }

        @Override
        public boolean isValidRoleSetter(long setterId) {
            return false;
        }

        @Override
        public boolean isUserInRole(long accountId, Role role) {
            return false;
        }

        @Override
        public EnumSet<Role> parseRoles(String value) {
            return EnumSet.noneOf(Role.class);
        }
    }
}
