/******************************************************************************
 * Copyright © 2013-2016 The Nxt Core Developers.                             *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * Nxt software, including this file, may be copied, modified, propagated,    *
 * or distributed except according to the terms contained in the LICENSE.txt  *
 * file.                                                                      *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

package nxt;

public abstract class Chain {

    public static Chain getChain(String name) {
        if ("FXT".equals(name)) {
            return FxtChain.FXT;
        } else {
            return ChildChain.getChain(name);
        }
    }

    private final String name;

    Chain(String name) {
        this.name = name.toUpperCase();
    }

    public final String getName() {
        return name;
    }

    public String getDbSchema() {
        return name;
    }

    public final String getSchemaTable(String table) {
        if (table.contains(".")) {
            throw new IllegalArgumentException("Schema already specified: " + table);
        }
        return getDbSchema() + "." + table.toUpperCase();
    }

    public abstract TransactionHome getTransactionHome();
}
