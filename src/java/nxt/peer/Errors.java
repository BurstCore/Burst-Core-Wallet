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

package nxt.peer;

final class Errors {

    final static String BLACKLISTED = "Your peer is blacklisted";
    final static String SEQUENCE_ERROR = "Peer request received before 'getInfo' request";
    final static String TOO_MANY_BLOCKS_REQUESTED = "Too many blocks requested";
    final static String TOO_MANY_TRANSACTIONS_REQUESTED = "Too many transactions request";
    final static String DOWNLOADING = "Blockchain download in progress";

    private Errors() {} // never
}
