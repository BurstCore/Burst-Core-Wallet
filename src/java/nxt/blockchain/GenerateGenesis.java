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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class GenerateGenesis {

    public static void main(String[] args) {
        try {
            Logger.setLevel(Logger.Level.ERROR);
            if (args.length != 2) {
                System.out.println("Usage: GenerateGenesis <input genesis json file> <output genesis json file>");
                System.exit(1);
            }
            File input = new File(args[0]);
            if (!input.exists()) {
                System.out.println("File not found: " + input.getAbsolutePath());
                System.exit(1);
            }
            File output = new File(args[1]);
            if (output.exists()) {
                System.out.println("File already exists: " + output.getAbsolutePath());
                System.exit(1);
            }
            try (BufferedReader reader = new BufferedReader(new FileReader(input));
                 BufferedWriter writer = new BufferedWriter(new FileWriter(output))) {
                JSONObject inputJSON = (JSONObject) JSONValue.parseWithException(reader);
                String creatorSecretPhrase = (String)inputJSON.get("genesisSecretPhrase");
                byte[] creatorPublicKey = Crypto.getPublicKey(creatorSecretPhrase);
                JSONArray recipientSecretPhrases = (JSONArray)inputJSON.get("genesisRecipientSecretPhrases");
                JSONArray recipientPublicKeys = (JSONArray)inputJSON.get("genesisRecipientPublicKeys");
                if (recipientPublicKeys == null) {
                    recipientPublicKeys = new JSONArray();
                }
                int recipientsCount = recipientSecretPhrases != null ? recipientSecretPhrases.size() : recipientPublicKeys.size();
                long[] genesisRecipients = new long[recipientsCount];
                byte[][] genesisPublicKeys = new byte[recipientsCount][];
                for (int i = 0; i < genesisRecipients.length; i++) {
                    if (recipientSecretPhrases != null) {
                        genesisPublicKeys[i] = Crypto.getPublicKey((String) recipientSecretPhrases.get(i));
                        recipientPublicKeys.add(Convert.toHexString(genesisPublicKeys[i]));
                    } else {
                        genesisPublicKeys[i] = Convert.parseHexString((String)recipientPublicKeys.get(i));
                    }
                    genesisRecipients[i] = Account.getId(genesisPublicKeys[i]);
                }
                Map<Long, Integer> genesisAmounts = new HashMap<>();
                JSONArray amounts = (JSONArray)inputJSON.get("genesisAmounts");
                for (int i = 0; i < amounts.size(); i++) {
                    int amount = ((Long)amounts.get(i)).intValue();
                    genesisAmounts.put(genesisRecipients[i], amount);
                }

                JSONObject outputJSON = new JSONObject();
                outputJSON.put("genesisPublicKey", Convert.toHexString(creatorPublicKey));
                outputJSON.put("epochBeginning", inputJSON.get("epochBeginning"));
                outputJSON.put("genesisAmounts", amounts);
                outputJSON.put("genesisRecipientPublicKeys", recipientPublicKeys);

                MessageDigest digest = Crypto.sha256();
                byte[] payloadHash = digest.digest();
                byte[] generationSignature = new byte[32];
                byte[] testnetGenerationSignature = new byte[32];
                Arrays.fill(testnetGenerationSignature, (byte)1);

                BlockImpl genesisBlock = new BlockImpl(-1, 0, 0, Constants.MAX_BALANCE_NQT, 0, 0, payloadHash,
                        creatorPublicKey, generationSignature, new byte[32], Collections.emptyList(), creatorSecretPhrase);
                outputJSON.put("genesisBlockSignature", Convert.toHexString(genesisBlock.getBlockSignature()));

                BlockImpl genesisTestnetBlock = new BlockImpl(-1, 0, 0, Constants.MAX_BALANCE_NQT, 0, 0, payloadHash,
                        creatorPublicKey, testnetGenerationSignature, new byte[32], Collections.emptyList(), creatorSecretPhrase);
                outputJSON.put("genesisTestnetBlockSignature", Convert.toHexString(genesisTestnetBlock.getBlockSignature()));

                writer.write(outputJSON.toJSONString());
                writer.newLine();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
