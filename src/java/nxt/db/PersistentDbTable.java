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

package nxt.db;

public abstract class PersistentDbTable<T> extends EntityDbTable<T> {

    protected PersistentDbTable(String schemaTable, DbKey.Factory<T> dbKeyFactory) {
        super(schemaTable, dbKeyFactory, false, null);
    }

    protected PersistentDbTable(String schemaTable, DbKey.Factory<T> dbKeyFactory, String fullTextSearchColumns) {
        super(schemaTable, dbKeyFactory, false, fullTextSearchColumns);
    }

    PersistentDbTable(String schemaTable, DbKey.Factory<T> dbKeyFactory, boolean multiversion, String fullTextSearchColumns) {
        super(schemaTable, dbKeyFactory, multiversion, fullTextSearchColumns);
    }

    @Override
    public void rollback(int height) {
    }

    @Override
    public final void truncate() {
    }

    @Override
    public final boolean isPersistent() {
        return true;
    }

}
