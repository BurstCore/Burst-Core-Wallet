/*
 * Copyright © 2013-2016 The Nxt Core Developers.
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

package nxt;

import nxt.crypto.Crypto;
import nxt.util.Convert;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class Genesis {

    public static final byte[] CREATOR_PUBLIC_KEY = Convert.parseHexString("5e8edf8cc3494942ef335b221273cb53deff0191dd957e477ddf1d979208bc2c");
    public static final long CREATOR_ID = Account.getId(CREATOR_PUBLIC_KEY);

    public static final Map<Long, Long> GENESIS_AMOUNTS;
    static {
        Map<Long,Long> map = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            map.put(Account.getId(Crypto.getPublicKey(String.valueOf(i))), Constants.MAX_BALANCE_NXT / 10);
        }
        GENESIS_AMOUNTS = Collections.unmodifiableMap(map);
    }

    public static final long EPOCH_BEGINNING;
    static {
        try {
            EPOCH_BEGINNING = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z").parse("2017-01-01 00:00:00 +0000").getTime();
        } catch (java.text.ParseException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private Genesis() {} // never

}
