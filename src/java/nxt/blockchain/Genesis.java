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
import nxt.crypto.Crypto;
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
                    -41, -41, -30, -100, -63, -37, 62, 1, 56, -127, 84, 112, -8, 1, 96, -10,
                    52, -33, -20, 94, -63, 58, -35, 110, -24, -46, -78, 38, -21, -82, -96, 42
            } : new byte[32];

    static byte[] apply() {
        MessageDigest digest = Crypto.sha256();
        try (InputStreamReader is = new InputStreamReader(new DigestInputStream(
                ClassLoader.getSystemResourceAsStream("PUBLIC_KEY" + (Constants.isTestnet ? "-testnet.json" : ".json")), digest))) {
            JSONArray json = (JSONArray) JSONValue.parseWithException(is);
            Logger.logDebugMessage("Loading public keys");
            json.forEach(jsonPublicKey -> {
                byte[] publicKey = Convert.parseHexString((String)jsonPublicKey);
                Account account = Account.addOrGetAccount(Account.getId(publicKey));
                account.apply(publicKey);
            });
            Logger.logDebugMessage("Loaded " + json.size() + " public keys");
        } catch (IOException|ParseException e) {
            throw new RuntimeException("Failed to process genesis recipients public keys", e);
        }
        List<Chain> chains = new ArrayList<>(ChildChain.getAll());
        chains.add(FxtChain.FXT);
        chains.sort(Comparator.comparingInt(Chain::getId));
        for (Chain chain : chains) {
            try (InputStreamReader is = new InputStreamReader(new DigestInputStream(
                    ClassLoader.getSystemResourceAsStream(chain.getName() + (Constants.isTestnet ? "-testnet.json" : ".json")), digest))) {
                JSONObject chainBalances = (JSONObject) JSONValue.parseWithException(is);
                Logger.logDebugMessage("Loading genesis amounts for " + chain.getName());
                long total = 0;
                for (Map.Entry<String, Long> entry : ((Map<String, Long>)chainBalances).entrySet()) {
                    Account account = Account.addOrGetAccount(Long.parseUnsignedLong(entry.getKey()));
                    account.addToBalanceAndUnconfirmedBalance(chain, null, 0, entry.getValue());
                    total += entry.getValue();
                }
                Logger.logDebugMessage("Total balance " + total + " " + chain.getName());
            } catch (IOException|ParseException e) {
                throw new RuntimeException("Failed to process genesis recipients accounts for " + chain.getName(), e);
            }
        }
        return digest.digest();
    }

    private Genesis() {} // never

}
