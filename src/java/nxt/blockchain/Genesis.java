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

package nxt.blockchain;

import nxt.Constants;
import nxt.account.Account;
import nxt.util.Convert;
import nxt.util.Logger;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Map;

public final class Genesis {

    public static final byte[] CREATOR_PUBLIC_KEY;
    public static final long CREATOR_ID;
    public static final byte[] GENESIS_BLOCK_SIGNATURE;

    public static final long EPOCH_BEGINNING;

    static {
        try (InputStream is = ClassLoader.getSystemResourceAsStream("genesis.json")) {
            JSONObject genesisParameters = (JSONObject)JSONValue.parseWithException(new InputStreamReader(is));
            CREATOR_PUBLIC_KEY = Convert.parseHexString((String)genesisParameters.get("genesisPublicKey"));
            CREATOR_ID = Account.getId(CREATOR_PUBLIC_KEY);
            GENESIS_BLOCK_SIGNATURE = Convert.parseHexString((String)genesisParameters.get(Constants.isTestnet
                    ? "genesisTestnetBlockSignature" : "genesisBlockSignature"));
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
            EPOCH_BEGINNING = dateFormat.parse((String) genesisParameters.get("epochBeginning")).getTime();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load genesis parameters", e);
        }
    }

    static void apply() {
        Account.addOrGetAccount(Genesis.CREATOR_ID).apply(Genesis.CREATOR_PUBLIC_KEY);
        try (InputStream is = ClassLoader.getSystemResourceAsStream("genesis.json")) {
            JSONObject genesisParameters = (JSONObject) JSONValue.parseWithException(new InputStreamReader(is));
            Logger.logDebugMessage("Loading genesis amounts for ARDR");
            long total = 0;
            for (Map.Entry<String, Long> entry : ((Map<String, Long>)genesisParameters.get("genesisRecipients")).entrySet()) {
                byte[] recipientPublicKey = Convert.parseHexString(entry.getKey());
                Account recipient = Account.addOrGetAccount(Account.getId(recipientPublicKey));
                recipient.apply(recipientPublicKey);
                recipient.addToBalanceAndUnconfirmedBalance(FxtChain.FXT, null, null, entry.getValue());
                total += entry.getValue();
            }
            Logger.logDebugMessage("Total balance " + total + " ARDR");
        } catch (IOException|ParseException e) {
            throw new RuntimeException("Failed to process genesis recipients accounts", e);
        }
        for (ChildChain childChain : ChildChain.getAll()) {
            try (InputStream is = ClassLoader.getSystemResourceAsStream(childChain.getName() + ".json")) {
                JSONObject childChainParameters = (JSONObject) JSONValue.parseWithException(new InputStreamReader(is));
                Logger.logDebugMessage("Loading genesis amounts for " + childChain.getName());
                long total = 0;
                for (Map.Entry<String, Long> entry : ((Map<String, Long>)childChainParameters.get("genesisRecipients")).entrySet()) {
                    byte[] recipientPublicKey = Convert.parseHexString(entry.getKey());
                    Account recipient = Account.addOrGetAccount(Account.getId(recipientPublicKey));
                    recipient.apply(recipientPublicKey);
                    recipient.addToBalanceAndUnconfirmedBalance(childChain, null, null, entry.getValue());
                    total += entry.getValue();
                }
                Logger.logDebugMessage("Total balance " + total + " " + childChain.getName());
            } catch (IOException|ParseException e) {
                throw new RuntimeException("Failed to process child chain recipients accounts for " + childChain.getName(), e);
            }
        }
    }

    private Genesis() {} // never

}
