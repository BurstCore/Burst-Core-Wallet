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
import nxt.util.Listener;
import nxt.util.Listeners;
import nxt.util.Logger;
import nxt.util.ThreadPool;
import nxt.util.MiningPlot;
import fr.cryptohash.Shabal256;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class Generator {

    public enum Event {
        GENERATION_DEADLINE, START_FORGING, STOP_FORGING
    }

    private static final Listeners<Generator,Event> listeners = new Listeners<>();

    private static long lastBlockId;

    private static volatile GeneratorState generatorState = null;

    private static final Runnable generateBlocksThread = new Runnable() {

        private volatile boolean logged;

        @Override
        public void run() {

            try {
                // don't try to forge if we've just started up and still scanning blockchain
                if (Nxt.getBlockchainProcessor().isScanning()) {
                    return;
                }

                long currentHeight = Nxt.getBlockchain().getHeight();

                try {
                    BlockchainImpl.getInstance().updateLock();
                    try {
                        Block lastBlock = Nxt.getBlockchain().getLastBlock();

                        // don't try to forge if we have no blocks or blockchain isn't up to date enough for this code version
                        if (lastBlock == null || lastBlock.getHeight() < Constants.LAST_KNOWN_BLOCK) {
                            return;
                        }

                        if (lastBlock.getId() != lastBlockId) {
                            // last block's Id has changed so our generation state is invalid - discard it
                            generatorState = null;
                            lastBlockId = lastBlock.getId();
                            return;
                        }

                        if (generatorState == null) {
                            // no usable nonce submitted so far - nothing to do
                            return;
                        }

                        int elapsedTime = Nxt.getEpochTime() - lastBlock.getTimestamp();
                        if (BigInteger.valueOf(elapsedTime).compareTo(generatorState.getPOCTime()) <= 0)
                            return; // too soon

                        // OK to attempt forge
                        generatorState.forge(lastBlock);
                        
                        // success - we should be on new block so old state now invalid
                        generatorState = null;
                    } finally {
                        BlockchainImpl.getInstance().updateUnlock();
                    }
                } catch (Exception e) {
                    Logger.logMessage("Error in block generation thread", e);
                }
            } catch (Throwable t) {
                Logger.logErrorMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
                t.printStackTrace();
                System.exit(1);
            }
        }

    };

    static {
        if (!Constants.isLightClient) {
            ThreadPool.scheduleThread("GenerateBlocks", generateBlocksThread, 500, TimeUnit.MILLISECONDS);
        }
    }

    static void init() {}

    public static boolean addListener(Listener<Generator> listener, Event eventType) {
        return listeners.addListener(listener, eventType);
    }

    public static boolean removeListener(Listener<Generator> listener, Event eventType) {
        return listeners.removeListener(listener, eventType);
    }

    public static BigInteger calculatePrescaledPOCTime(long accountId, long nonce, byte[] genSig, byte[] scoopData) {
        Shabal256 md = new Shabal256();
        md.update(genSig);
        md.update(scoopData);
        byte[] hash = md.digest();
        return new BigInteger(1, new byte[] {hash[7], hash[6], hash[5], hash[4], hash[3], hash[2], hash[1], hash[0]});
    }

    public static BigInteger calculatePrescaledPOCTime(long accountId, long nonce, byte[] genSig, int scoop) {
        MiningPlot plot = new MiningPlot(accountId, nonce);
        byte[] scoopData = plot.getScoop(scoop);

        return calculatePrescaledPOCTime(accountId, nonce, genSig, scoopData);
    }

    public static BigInteger calculatePOCTime(long accountId, long nonce, byte[] genSig, int scoop, long baseTarget) {
        BigInteger prescaledPOCTime = calculatePrescaledPOCTime(accountId, nonce, genSig, scoop);
        
        return prescaledPOCTime.divide(BigInteger.valueOf(baseTarget));
    }

    public static BigInteger calculatePOCTime(long accountId, long nonce, byte[] genSig, byte[] scoopData, long baseTarget) {
        BigInteger prescaledPOCTime = calculatePrescaledPOCTime(accountId, nonce, genSig, scoopData);
        
        return prescaledPOCTime.divide(BigInteger.valueOf(baseTarget));
    }

    public static BigInteger submitNonce(String secretPhrase, long nonce, byte[] publicKey) {
        byte[] publicKeyHash = Crypto.sha256().digest(publicKey);
        long accountId = Convert.fullHashToId(publicKeyHash);

        GeneratorState newState = new GeneratorState(secretPhrase, nonce, publicKey, accountId);
        BigInteger newPOCTime = newState.getPOCTime();
        
        synchronized (generatorState) {
            if (generatorState != null || newPOCTime.compareTo(generatorState.getPOCTime()) < 0)
                generatorState = newState;
        }

        return newPOCTime;
    }

    public static BigInteger submitNonce(String secretPhrase, long nonce) {
        byte[] publicKey = Crypto.getPublicKey(secretPhrase);
        return submitNonce(secretPhrase, nonce, publicKey);
    }

    public static byte[] calculateGenerationSignature(byte[] lastGenSig, long lastGenId) {
        ByteBuffer gensigbuf = ByteBuffer.allocate(32 + 8);
        gensigbuf.put(lastGenSig);
        gensigbuf.putLong(lastGenId);

        Shabal256 md = new Shabal256();
        md.update(gensigbuf.array());
        return md.digest();
    }

    public static int calculateScoopNum(byte[] generationSignature, long height) {
        ByteBuffer posbuf = ByteBuffer.allocate(32 + 8);
        posbuf.put(generationSignature);
        posbuf.putLong(height);

        Shabal256 md = new Shabal256();
        md.update(posbuf.array());
        BigInteger hashnum = new BigInteger(1, md.digest());
        return hashnum.mod(BigInteger.valueOf(MiningPlot.SCOOPS_PER_PLOT)).intValue();
    }
    
    private static class GeneratorState {

        private final long accountId;
        private final String secretPhrase;
        private final byte[] publicKey;
        private final BigInteger POCTime;
        private final long nonce;

        public GeneratorState(String secretPhrase, long nonce, byte[] publicKey, long accountId) {
            this.secretPhrase = secretPhrase;
            this.accountId = accountId;
            // need to store publicKey in addition to accountId, because the account may not have had its publicKey set yet
            this.publicKey = publicKey;
            this.nonce = nonce;

            Block lastBlock = Nxt.getBlockchain().getLastBlock();
            byte[] lastGenSig = lastBlock.getGenerationSignature();
            long lastGeneratorId = lastBlock.getGeneratorId();
            byte[] newGenSig = calculateGenerationSignature(lastGenSig, lastGeneratorId);
            int scoopNum = calculateScoopNum(newGenSig, lastBlock.getHeight() + 1);

            POCTime = calculatePOCTime(accountId, nonce, newGenSig, scoopNum, lastBlock.getBaseTarget());
        }

        public byte[] getPublicKey() {
            return publicKey;
        }

        public Long getAccountId() {
            return accountId;
        }

        public BigInteger getPOCTime() {
            return POCTime;
        }

        public void forge(Block lastBlock) throws BlockchainProcessor.BlockNotAcceptedException {
            int start = Nxt.getEpochTime();
            while (true) {
                try {
                    BlockchainProcessorImpl.getInstance().generateBlock(secretPhrase, publicKey, nonce);
                    return;
                } catch (BlockchainProcessor.TransactionNotAcceptedException e) {
                    // the bad transaction has been expunged, try again
                    if (Nxt.getEpochTime() - start > 10) { // give up after trying for 10 s
                        throw e;
                    }
                }
            }
        }

    }

}
