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
import nxt.util.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;

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

                JSONArray genesisSignatures = new JSONArray();
                List<TransactionImpl> transactions = new ArrayList<>();
                for (int i = 0; i < genesisRecipients.length; i++) {
                    TransactionImpl transaction = new TransactionImpl.BuilderImpl((byte) 0, creatorPublicKey,
                            genesisAmounts.get(genesisRecipients[i]) * Constants.ONE_NXT, 0, (short) 0,
                            Attachment.ORDINARY_PAYMENT)
                            .timestamp(0)
                            .recipientId(genesisRecipients[i])
                            .height(0)
                            .ecBlockHeight(0)
                            .ecBlockId(0)
                            .appendix(new Appendix.PublicKeyAnnouncement(genesisPublicKeys[i]))
                            .build(creatorSecretPhrase);
                    transactions.add(transaction);
                    genesisSignatures.add(Convert.toHexString(transaction.getSignature()));
                }
                outputJSON.put("genesisSignatures", genesisSignatures);

                Collections.sort(transactions, Comparator.comparingLong(Transaction::getId));
                MessageDigest digest = Crypto.sha256();
                for (TransactionImpl transaction : transactions) {
                    digest.update(transaction.bytes());
                }
                byte[] payloadHash = digest.digest();
                byte[] generationSignature = new byte[32];
                byte[] testnetGenerationSignature = new byte[32];
                Arrays.fill(testnetGenerationSignature, (byte)1);

                BlockImpl genesisBlock = new BlockImpl(-1, 0, 0, Constants.MAX_BALANCE_NQT, 0, transactions.size() * 160, payloadHash,
                        creatorPublicKey, generationSignature, new byte[32], transactions, creatorSecretPhrase);
                outputJSON.put("genesisBlockSignature", Convert.toHexString(genesisBlock.getBlockSignature()));

                BlockImpl genesisTestnetBlock = new BlockImpl(-1, 0, 0, Constants.MAX_BALANCE_NQT, 0, transactions.size() * 160, payloadHash,
                        creatorPublicKey, testnetGenerationSignature, new byte[32], transactions, creatorSecretPhrase);
                outputJSON.put("genesisTestnetBlockSignature", Convert.toHexString(genesisTestnetBlock.getBlockSignature()));

                writer.write(outputJSON.toJSONString());
                writer.newLine();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
