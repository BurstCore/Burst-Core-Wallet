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
import nxt.crypto.Crypto;
import nxt.dbschema.Db;
import nxt.util.Convert;
import nxt.util.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class Genesis {

    static final byte[] generationSignature = Constants.isTestnet ?
            new byte[] {
                    -59, 37, -67, -124, 87, 37, -7, 45, 5, -5, -117, 93, 98, -103, 46, 18,
                    -77, 14, 98, -52, -23, -91, 3, 79, 37, -6, 23, -90, 62, 8, 82, -1
            } : new byte[32];

    static byte[] apply() {
        MessageDigest digest = Crypto.sha256();
        int count = 0;
        try (InputStreamReader is = new InputStreamReader(new DigestInputStream(
                ClassLoader.getSystemResourceAsStream("PUBLIC_KEY" + (Constants.isTestnet ? "-testnet.json" : ".json")), digest))) {
            JSONArray json = (JSONArray) JSONValue.parseWithException(is);
            Logger.logDebugMessage("Loading public keys");
            for (Object jsonPublicKey : json) {
                byte[] publicKey = Convert.parseHexString((String)jsonPublicKey);
                Account account = Account.addOrGetAccount(Account.getId(publicKey));
                account.apply(publicKey);
                if (count++ % 100 == 0) {
                    Db.db.commitTransaction();
                }
            }
            Logger.logDebugMessage("Loaded " + json.size() + " public keys");
        } catch (IOException|ParseException e) {
            throw new RuntimeException("Failed to process genesis recipients public keys", e);
        }
        List<Chain> chains = new ArrayList<>(ChildChain.getAll());
        chains.add(FxtChain.FXT);
        chains.sort(Comparator.comparingInt(Chain::getId));
        for (Chain chain : chains) {
            count = 0;
            try (InputStreamReader is = new InputStreamReader(new DigestInputStream(
                    ClassLoader.getSystemResourceAsStream(chain.getName() + (Constants.isTestnet ? "-testnet.json" : ".json")), digest))) {
                JSONObject chainBalances = (JSONObject) JSONValue.parseWithException(is);
                Logger.logDebugMessage("Loading genesis amounts for " + chain.getName());
                long total = 0;
                for (Map.Entry<String, Long> entry : ((Map<String, Long>)chainBalances).entrySet()) {
                    Account account = Account.addOrGetAccount(Long.parseUnsignedLong(entry.getKey()));
                    account.addToBalanceAndUnconfirmedBalance(chain, null, null, entry.getValue());
                    total += entry.getValue();
                    if (count++ % 100 == 0) {
                        Db.db.commitTransaction();
                    }
                }
                Logger.logDebugMessage("Total balance %f %s", (double)total / chain.ONE_COIN, chain.getName());
            } catch (IOException|ParseException e) {
                throw new RuntimeException("Failed to process genesis recipients accounts for " + chain.getName(), e);
            }
        }
        digest.update(Convert.toBytes(Constants.EPOCH_BEGINNING));
        return digest.digest();
    }

    private Genesis() {} // never

}
