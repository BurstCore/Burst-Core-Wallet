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
import nxt.util.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
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
                Map<Long, Long> genesisAmounts = new HashMap<>();
                long total = 0;
                JSONArray amountRatios = (JSONArray)inputJSON.get("genesisRatios");
                JSONArray amounts = new JSONArray();
                for (int i = 0; i < amountRatios.size(); i++) {
                    long amount = ((Long)amountRatios.get(i)).longValue();
                    total = Math.addExact(total, amount);
                }
                for (int i = 0; i < amountRatios.size(); i++) {
                    long amount = ((Long)amountRatios.get(i)).longValue();
                    amount = BigInteger.valueOf(Constants.MAX_BALANCE_NXT).multiply(BigInteger.valueOf(amount))
                            .divide(BigInteger.valueOf(total)).longValueExact();
                    amounts.add(amount);
                    genesisAmounts.put(genesisRecipients[i], amount);
                }
                JSONObject outputJSON = new JSONObject();
                outputJSON.put("genesisPublicKey", Convert.toHexString(creatorPublicKey));
                String epochBeginning = (String) inputJSON.get("epochBeginning");
                if (epochBeginning == null) {
                    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
                    epochBeginning = dateFormat.format(new Date());
                }
                outputJSON.put("epochBeginning", epochBeginning);
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
                System.out.println("Genesis block generated: " + output.getAbsolutePath());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
