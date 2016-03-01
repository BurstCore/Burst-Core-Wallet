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
import nxt.util.Convert;

import java.util.List;

public final class Genesis {

    public static final String CREATOR_SECRET_PHRASE = Nxt.getStringProperty("nxt.genesisSecretPhrase");
    public static final byte[] CREATOR_PUBLIC_KEY = Crypto.getPublicKey(CREATOR_SECRET_PHRASE);
    public static final long CREATOR_ID = Account.getId(CREATOR_PUBLIC_KEY);

    public static final long[] GENESIS_RECIPIENTS;
    public static final byte[][] GENESIS_PUBLIC_KEYS;
    static {
        List<String> recipientPublicKeys = Nxt.getStringListProperty("nxt.genesisRecipientPublicKeys");
        GENESIS_RECIPIENTS = new long[recipientPublicKeys.size()];
        GENESIS_PUBLIC_KEYS = new byte[GENESIS_RECIPIENTS.length][];
        for (int i = 0; i < GENESIS_RECIPIENTS.length; i++) {
            GENESIS_PUBLIC_KEYS[i] = Convert.parseHexString(recipientPublicKeys.get(i));
            if (!Crypto.isCanonicalPublicKey(GENESIS_PUBLIC_KEYS[i])) {
                throw new RuntimeException("Invalid genesis recipient public key " + recipientPublicKeys.get(i));
            }
            GENESIS_RECIPIENTS[i] = Account.getId(GENESIS_PUBLIC_KEYS[i]);
            if (GENESIS_RECIPIENTS[i] == 0) {
                throw new RuntimeException("Invalid genesis recipient account " + GENESIS_RECIPIENTS[i]);
            }
        }
    }

    public static final int[] GENESIS_AMOUNTS;
    static {
        List<String> amounts = Nxt.getStringListProperty("nxt.genesisAmounts");
        GENESIS_AMOUNTS = new int[amounts.size()];
        long total = 0;
        for (int i = 0; i < GENESIS_AMOUNTS.length; i++) {
            GENESIS_AMOUNTS[i] = Integer.parseInt(amounts.get(i));
            if (GENESIS_AMOUNTS[i] <= 0) {
                throw new RuntimeException("Invalid genesis recipient amount " + amounts.get(i));
            }
            total = Math.addExact(total, GENESIS_AMOUNTS[i]);
        }
        if (total != Constants.MAX_BALANCE_NXT) {
            throw new RuntimeException("Genesis amount total is " + total + ", must be " + Constants.MAX_BALANCE_NXT);
        }
    }

    private Genesis() {} // never

}
