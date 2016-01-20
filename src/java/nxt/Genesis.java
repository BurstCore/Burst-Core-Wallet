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

import nxt.crypto.Crypto;

public final class Genesis {

    public static final String CREATOR_SECRET_PHRASE = "12345678";
    public static final byte[] CREATOR_PUBLIC_KEY = Crypto.getPublicKey(CREATOR_SECRET_PHRASE);
    public static final long CREATOR_ID = Account.getId(CREATOR_PUBLIC_KEY);

    public static final long[] GENESIS_RECIPIENTS = {
            Long.parseUnsignedLong("2330184721294966748"),
            Long.parseUnsignedLong("2752264569666051083"),
            Long.parseUnsignedLong("2766217825807328998"),
            Long.parseUnsignedLong("3705364957971254799")
    };


    public static final int[] GENESIS_AMOUNTS = {
            400000000,
            300000000,
            200000000,
            100000000
    };

    private Genesis() {} // never

}
