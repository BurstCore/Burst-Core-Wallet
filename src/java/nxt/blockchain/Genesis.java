/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016 Jelurida IP B.V.
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

package nxt.blockchain;

import nxt.Constants;
import nxt.account.Account;
import nxt.util.Convert;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Map;

public final class Genesis {

    private static final JSONObject genesisParameters;
    static {
        try (InputStream is = ClassLoader.getSystemResourceAsStream("genesis.json")) {
            genesisParameters = (JSONObject)JSONValue.parseWithException(new InputStreamReader(is));
        } catch (IOException|ParseException e) {
            throw new RuntimeException("Failed to load genesis parameters", e);
        }
    }

    //TODO: remove completely?
    public static final byte[] CREATOR_PUBLIC_KEY = Convert.parseHexString((String)genesisParameters.get("genesisPublicKey"));
    public static final long CREATOR_ID = Account.getId(CREATOR_PUBLIC_KEY);
    public static final byte[] GENESIS_BLOCK_SIGNATURE = Convert.parseHexString((String)genesisParameters.get(Constants.isTestnet
            ? "genesisTestnetBlockSignature" : "genesisBlockSignature"));

    public static final Map<String, Long> GENESIS_RECIPIENTS = Collections.unmodifiableMap((Map<String, Long>)genesisParameters.get("genesisRecipients"));

    public static final long EPOCH_BEGINNING;
    static {
        try {
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
            EPOCH_BEGINNING = dateFormat.parse((String) genesisParameters.get("epochBeginning")).getTime();
        } catch (java.text.ParseException e) {
            throw new RuntimeException("Invalid epoch beginning " + genesisParameters.get("epochBeginning"));
        }
    }

    private Genesis() {} // never

}
