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

import nxt.AccountLedger.LedgerEvent;
import nxt.crypto.Crypto;
import nxt.util.Convert;
import nxt.util.Logger;
import nxt.util.MiningPlot;
import nxt.peer.Peer;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import fr.cryptohash.Shabal256;

final class BlockImpl implements Block {

    private final int version;
    private final int timestamp;
    private final long previousBlockId;
    private volatile byte[] generatorPublicKey;
    private final byte[] previousBlockHash;
    private final long totalAmountNQT;
    private final long totalFeeNQT;
    private final int payloadLength;
    private final byte[] generationSignature;
    private final byte[] payloadHash;
    private volatile List<TransactionImpl> blockTransactions;

    private byte[] blockSignature;
    private BigInteger cumulativeDifficulty = BigInteger.ZERO;
    private long baseTarget = Constants.INITIAL_BASE_TARGET;
    private volatile long nextBlockId;
    private int height = -1;
    private volatile long id;
    private volatile String stringId = null;
    private volatile long generatorId;
    private volatile byte[] bytes = null;

    // BURST-specific from here on
    private Long nonce;
    private BigInteger unscaledPOCTime = null;
    private final byte[] blockATs;
    private Peer downloadedFrom = null;

    BlockImpl(int version, int timestamp, long previousBlockId, long totalAmountNQT, long totalFeeNQT, int payloadLength, byte[] payloadHash,
              byte[] generatorPublicKey, byte[] generationSignature, byte[] previousBlockHash, List<TransactionImpl> transactions, String secretPhrase, Long nonce, byte[] blockATs) {
        this(version, timestamp, previousBlockId, totalAmountNQT, totalFeeNQT, payloadLength, payloadHash,
                generatorPublicKey, generationSignature, null, previousBlockHash, transactions, nonce, blockATs);
        blockSignature = Crypto.sign(bytes(), secretPhrase);
        bytes = null;
    }

    BlockImpl(int version, int timestamp, long previousBlockId, long totalAmountNQT, long totalFeeNQT, int payloadLength, byte[] payloadHash,
              byte[] generatorPublicKey, byte[] generationSignature, byte[] blockSignature, byte[] previousBlockHash, List<TransactionImpl> transactions, Long nonce, byte[] blockATs) {
        this.version = version;
        this.timestamp = timestamp;
        this.previousBlockId = previousBlockId;
        this.totalAmountNQT = totalAmountNQT;
        this.totalFeeNQT = totalFeeNQT;
        this.payloadLength = payloadLength;
        this.payloadHash = payloadHash;
        this.generatorPublicKey = generatorPublicKey;
        this.generationSignature = generationSignature;
        this.blockSignature = blockSignature;
        this.previousBlockHash = previousBlockHash;
        if (transactions != null) {
            this.blockTransactions = Collections.unmodifiableList(transactions);
        }
        this.nonce = nonce;
        this.blockATs = blockATs;
    }

    BlockImpl(int version, int timestamp, long previousBlockId, long totalAmountNQT, long totalFeeNQT, int payloadLength,
              byte[] payloadHash, long generatorId, byte[] generationSignature, byte[] blockSignature,
              byte[] previousBlockHash, BigInteger cumulativeDifficulty, long baseTarget, long nextBlockId, int height, long id,
              List<TransactionImpl> blockTransactions, Long nonce, byte[] blockATs) {
        this(version, timestamp, previousBlockId, totalAmountNQT, totalFeeNQT, payloadLength, payloadHash,
                null, generationSignature, blockSignature, previousBlockHash, null, nonce, blockATs);
        this.cumulativeDifficulty = cumulativeDifficulty;
        this.baseTarget = baseTarget;
        this.nextBlockId = nextBlockId;
        this.height = height;
        this.id = id;
        this.generatorId = generatorId;
        this.blockTransactions = blockTransactions;
    }

    @Override
    public int getVersion() {
        return version;
    }

    @Override
    public int getTimestamp() {
        return timestamp;
    }

    @Override
    public long getPreviousBlockId() {
        return previousBlockId;
    }

    @Override
    public byte[] getGeneratorPublicKey() {
        if (generatorPublicKey == null) {
            generatorPublicKey = Account.getPublicKey(generatorId);
        }
        return generatorPublicKey;
    }

    @Override
    public byte[] getPreviousBlockHash() {
        return previousBlockHash;
    }

    @Override
    public long getTotalAmountNQT() {
        return totalAmountNQT;
    }

    @Override
    public long getTotalFeeNQT() {
        return totalFeeNQT;
    }

    @Override
    public int getPayloadLength() {
        return payloadLength;
    }

    @Override
    public byte[] getPayloadHash() {
        return payloadHash;
    }

    @Override
    public byte[] getGenerationSignature() {
        return generationSignature;
    }

    @Override
    public byte[] getBlockSignature() {
        return blockSignature;
    }

    @Override
    public List<TransactionImpl> getTransactions() {
        if (this.blockTransactions == null) {
            List<TransactionImpl> transactions = Collections.unmodifiableList(TransactionDb.findBlockTransactions(getId()));
            for (TransactionImpl transaction : transactions) {
                transaction.setBlock(this);
            }
            this.blockTransactions = transactions;
        }
        return this.blockTransactions;
    }

    @Override
    public long getBaseTarget() {
        return baseTarget;
    }

    @Override
    public BigInteger getCumulativeDifficulty() {
        return cumulativeDifficulty;
    }

    @Override
    public long getNextBlockId() {
        return nextBlockId;
    }

    void setNextBlockId(long nextBlockId) {
        this.nextBlockId = nextBlockId;
    }

    @Override
    public int getHeight() {
        if (height == -1) {
            throw new IllegalStateException("Block height not yet set");
        }
        return height;
    }

    @Override
    public long getId() {
        if (id == 0) {
            if (blockSignature == null) {
                throw new IllegalStateException("Block is not signed yet");
            }
            byte[] hash = Crypto.sha256().digest(bytes());
            BigInteger bigInteger = new BigInteger(1, new byte[] {hash[7], hash[6], hash[5], hash[4], hash[3], hash[2], hash[1], hash[0]});
            id = bigInteger.longValue();
            stringId = bigInteger.toString();
        }
        return id;
    }

    @Override
    public String getStringId() {
        if (stringId == null) {
            getId();
            if (stringId == null) {
                stringId = Long.toUnsignedString(id);
            }
        }
        return stringId;
    }

    @Override
    public long getGeneratorId() {
        if (generatorId == 0) {
            generatorId = Account.getId(getGeneratorPublicKey());
        }
        return generatorId;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof BlockImpl && this.getId() == ((BlockImpl)o).getId();
    }

    @Override
    public int hashCode() {
        return (int)(getId() ^ (getId() >>> 32));
    }

    @Override
    public JSONObject getJSONObject() {
        JSONObject json = new JSONObject();
        json.put("version", version);
        json.put("timestamp", timestamp);
        json.put("previousBlock", Long.toUnsignedString(previousBlockId));
        json.put("totalAmountNQT", totalAmountNQT);
        json.put("totalFeeNQT", totalFeeNQT);
        json.put("payloadLength", payloadLength);
        json.put("payloadHash", Convert.toHexString(payloadHash));
        json.put("generatorPublicKey", Convert.toHexString(getGeneratorPublicKey()));
        json.put("generationSignature", Convert.toHexString(generationSignature));
        if (version > 1) {
            json.put("previousBlockHash", Convert.toHexString(previousBlockHash));
        }
        json.put("blockSignature", Convert.toHexString(blockSignature));
        JSONArray transactionsData = new JSONArray();
        getTransactions().forEach(transaction -> transactionsData.add(transaction.getJSONObject()));
        json.put("transactions", transactionsData);
        if (nonce == null)
            throw new IllegalStateException("Can't convert Block to JSON due to null nonce");
        json.put("nonce", Long.toUnsignedString(nonce));
        json.put("blockATs", Convert.toHexString( blockATs ));
        return json;
    }

    static BlockImpl parseBlock(JSONObject blockData) throws NxtException.NotValidException, BlockchainProcessor.BlockOutOfOrderException {
        try {
            int version = ((Long) blockData.get("version")).intValue();
            int timestamp = ((Long) blockData.get("timestamp")).intValue();
            long previousBlock = Convert.parseUnsignedLong((String) blockData.get("previousBlock"));
            long totalAmountNQT = Convert.parseLong(blockData.get("totalAmountNQT"));
            long totalFeeNQT = Convert.parseLong(blockData.get("totalFeeNQT"));
            int payloadLength = ((Long) blockData.get("payloadLength")).intValue();
            byte[] payloadHash = Convert.parseHexString((String) blockData.get("payloadHash"));
            byte[] generatorPublicKey = Convert.parseHexString((String) blockData.get("generatorPublicKey"));
            byte[] generationSignature = Convert.parseHexString((String) blockData.get("generationSignature"));
            byte[] blockSignature = Convert.parseHexString((String) blockData.get("blockSignature"));
            byte[] previousBlockHash = version == 1 ? null : Convert.parseHexString((String) blockData.get("previousBlockHash"));
            Long nonce = Convert.parseUnsignedLong((String) blockData.get("nonce"));
            List<TransactionImpl> blockTransactions = new ArrayList<>();
            for (Object transactionData : (JSONArray) blockData.get("transactions")) {
                blockTransactions.add(TransactionImpl.parseTransaction((JSONObject) transactionData));
            }
            byte[] blockATs = Convert.parseHexString((String) blockData.get("blockATs"));
            BlockImpl block = new BlockImpl(version, timestamp, previousBlock, totalAmountNQT, totalFeeNQT, payloadLength, payloadHash, generatorPublicKey,
                    generationSignature, blockSignature, previousBlockHash, blockTransactions, nonce, blockATs);
            
            // We will have to defer this as the previous block might not be available yet and so can't check the signature
            /*
            if (!block.checkSignature()) {
                throw new NxtException.NotValidException("Invalid block signature");
            }
            */
            return block;
        } catch (NxtException.NotValidException|RuntimeException e) {
            Logger.logDebugMessage("Failed to parse block: " + blockData.toJSONString());
            throw e;
        }
    }

    @Override
    public byte[] getBytes() {
        return Arrays.copyOf(bytes(), bytes.length);
    }

    byte[] bytes() {
        if (bytes == null) {
            ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + 8 + 4 + (version < 3 ? (4 + 4) : (8 + 8)) + 4 + 32 + 32 + (32 + 32) + 8 + (blockATs != null ? blockATs.length : 0) + (blockSignature != null ? 64 : 0));
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.putInt(version);
            buffer.putInt(timestamp);
            buffer.putLong(previousBlockId);
            buffer.putInt(getTransactions().size());
            if (version < 3) {
                buffer.putInt((int) (totalAmountNQT / Constants.ONE_NXT));
                buffer.putInt((int) (totalFeeNQT / Constants.ONE_NXT));
            } else {
                buffer.putLong(totalAmountNQT);
                buffer.putLong(totalFeeNQT);
            }
            buffer.putInt(payloadLength);
            buffer.put(payloadHash);
            buffer.put(getGeneratorPublicKey());
            buffer.put(generationSignature);
            if (version > 1) {
                buffer.put(previousBlockHash);
            }
            if (nonce == null)
                throw new IllegalStateException("Can't serialize block due to null nonce");
            buffer.putLong(nonce);
            if(blockATs != null)
                buffer.put(blockATs);
            if (blockSignature != null) {
                buffer.put(blockSignature);
            }
            bytes = buffer.array();
        }
        return bytes;
    }

    boolean verifyBlockSignature() throws BlockchainProcessor.BlockOutOfOrderException {
        return checkSignature() && Account.setOrVerify(getGeneratorId(), getGeneratorPublicKey());
    }

    private volatile boolean hasValidSignature = false;

    private boolean checkSignature() throws BlockchainProcessor.BlockOutOfOrderException {
        if (! hasValidSignature) {
            try {
                
                BlockImpl previousBlock = BlockchainImpl.getInstance().getBlock(getPreviousBlockId());
                if (previousBlock == null) {
                    throw new BlockchainProcessor.BlockOutOfOrderException("Can't verify signature because previous block is missing", this);
                }
                
                byte[] generatorPublicKey = getGeneratorPublicKey();
                byte[] publicKey = generatorPublicKey;
                
                if (previousBlock.getHeight() + 1 >= Constants.BURST_REWARD_RECIPIENT_ASSIGNMENT_START_BLOCK) {
                    Account genAccount = Account.getAccount(generatorPublicKey); 
                    Account.RewardRecipientAssignment rewardAssignment = genAccount == null ? null : genAccount.getRewardRecipientAssignment();
                    
                    if (rewardAssignment != null) {
                        if (previousBlock.getHeight() + 1 >= rewardAssignment.getFromHeight()) {
                            publicKey = Account.getPublicKey(rewardAssignment.getRecipientId());
                        } else {
                            publicKey = Account.getPublicKey(rewardAssignment.getPrevRecipientId());
                        }
                    }
                }
                
                byte[] data = Arrays.copyOf(bytes(), bytes.length - 64);
                hasValidSignature = blockSignature != null && Crypto.verify(blockSignature, data, publicKey, version >= 3);

            } catch (RuntimeException e) {

                Logger.logMessage("Error verifying block signature", e);
                return false;

            }
            
        }
        
        return hasValidSignature;
    }

    boolean verifyGenerationSignature() throws BlockchainProcessor.BlockOutOfOrderException, BlockchainProcessor.BlockNotAcceptedException {

        try {

            BlockImpl previousBlock = BlockchainImpl.getInstance().getBlock(getPreviousBlockId());
            if (previousBlock == null) {
                throw new BlockchainProcessor.BlockOutOfOrderException("Can't verify signature because previous block is missing", this);
            }

            // In case the verifier-threads are not done with this yet - do it now
            synchronized(this) {
                if(this.unscaledPOCTime == null)
                    try {
                        preVerify();
                    } catch (IllegalStateException e) {
                        // probably height not set but we can't carry on
                        throw e;
                    }
            }

            byte[] correctGenerationSignature = Generator.calculateGenerationSignature(previousBlock.getGenerationSignature(), previousBlock.getGeneratorId());
            if (!Arrays.equals(generationSignature, correctGenerationSignature))
                return false;

            int elapsedTime = timestamp - previousBlock.timestamp;
            BigInteger POCTime = this.unscaledPOCTime.divide(BigInteger.valueOf(previousBlock.getBaseTarget()));

            return BigInteger.valueOf(elapsedTime).compareTo(POCTime) > 0;

        } catch (RuntimeException e) {

            Logger.logMessage("Error verifying block generation signature", e);
            return false;

        }

    }

    void apply() {
        Account generatorAccount = Account.addOrGetAccount(getGeneratorId());
        generatorAccount.apply(getGeneratorPublicKey());
        long totalBackFees = 0;
        if (this.height > Constants.SHUFFLING_BLOCK) {
            long[] backFees = new long[3];
            for (TransactionImpl transaction : getTransactions()) {
                long[] fees = transaction.getBackFees();
                for (int i = 0; i < fees.length; i++) {
                    backFees[i] += fees[i];
                }
            }
            for (int i = 0; i < backFees.length; i++) {
                if (backFees[i] == 0) {
                    break;
                }
                totalBackFees += backFees[i];
                Account previousGeneratorAccount = Account.getAccount(BlockDb.findBlockAtHeight(this.height - i - 1).getGeneratorId());
                Logger.logDebugMessage("Back fees %f BURST to forger at height %d", ((double)backFees[i])/Constants.ONE_NXT, this.height - i - 1);
                previousGeneratorAccount.addToBalanceAndUnconfirmedBalanceNQT(LedgerEvent.BLOCK_GENERATED, getId(), backFees[i]);
                previousGeneratorAccount.addToForgedBalanceNQT(backFees[i]);
            }
        }
        if (totalBackFees != 0) {
            Logger.logDebugMessage("Fee reduced by %f BURST at height %d", ((double)totalBackFees)/Constants.ONE_NXT, this.height);
        }
        generatorAccount.addToBalanceAndUnconfirmedBalanceNQT(LedgerEvent.BLOCK_GENERATED, getId(), totalFeeNQT - totalBackFees);
        generatorAccount.addToForgedBalanceNQT(totalFeeNQT - totalBackFees);
        
        // BURST-specific block reward
        if (height < Constants.BURST_REWARD_RECIPIENT_ASSIGNMENT_START_BLOCK) {
            generatorAccount.addToBalanceAndUnconfirmedBalanceNQT(LedgerEvent.BLOCK_GENERATED, getId(), getBlockReward());
            generatorAccount.addToForgedBalanceNQT(getBlockReward());
        } else {
            Account rewardAccount;
            Account.RewardRecipientAssignment rewardAssignment = generatorAccount.getRewardRecipientAssignment();
            if (rewardAssignment == null) {
                rewardAccount = generatorAccount;
            } else if (height >= rewardAssignment.getFromHeight()) {
                rewardAccount = Account.getAccount(rewardAssignment.getRecipientId());
            } else {
                rewardAccount = Account.getAccount(rewardAssignment.getPrevRecipientId());
            }
            rewardAccount.addToBalanceAndUnconfirmedBalanceNQT(LedgerEvent.BLOCK_GENERATED, getId(), getBlockReward());
            rewardAccount.addToForgedBalanceNQT(getBlockReward());
        }
    }

    void setPrevious(BlockImpl block) {
        if (block != null) {
            if (block.getId() != getPreviousBlockId()) {
                // shouldn't happen as previous id is already verified, but just in case
                throw new IllegalStateException("Previous block id doesn't match");
            }
            this.height = block.getHeight() + 1;
            this.calculateBaseTarget(block);
        } else {
            this.height = 0;
        }
        short index = 0;
        for (TransactionImpl transaction : getTransactions()) {
            transaction.setBlock(this);
            transaction.setIndex(index++);
        }
    }

    void loadTransactions() {
        for (TransactionImpl transaction : getTransactions()) {
            transaction.bytes();
            transaction.getAppendages();
        }
    }

    private void calculateBaseTarget(BlockImpl previousBlock) {
        if (this.getId() == Genesis.GENESIS_BLOCK_ID && previousBlockId == 0) {
            baseTarget = Constants.INITIAL_BASE_TARGET;
            cumulativeDifficulty = BigInteger.ZERO;
        } else if (this.height < 4) {
            baseTarget = Constants.INITIAL_BASE_TARGET;
            cumulativeDifficulty = previousBlock.cumulativeDifficulty.add(Convert.two64.divide(BigInteger.valueOf(Constants.INITIAL_BASE_TARGET)));
        } else if (this.height < Constants.BURST_DIFF_ADJUST_CHANGE_BLOCK) {
            Block itBlock = previousBlock;
            BigInteger avgBaseTarget = BigInteger.valueOf(itBlock.getBaseTarget());

            do {
                itBlock = Nxt.getBlockchain().getBlock(itBlock.getPreviousBlockId());
                avgBaseTarget = avgBaseTarget.add(BigInteger.valueOf(itBlock.getBaseTarget()));
            } while (itBlock.getHeight() > this.height - 4);

            avgBaseTarget = avgBaseTarget.divide(BigInteger.valueOf(4));
            long difTime = this.timestamp - itBlock.getTimestamp();

            long curBaseTarget = avgBaseTarget.longValue();
            long newBaseTarget = BigInteger.valueOf(curBaseTarget)
                    .multiply(BigInteger.valueOf(difTime))
                    .divide(BigInteger.valueOf(240 * 4)).longValue();

            if (newBaseTarget < 0 || newBaseTarget > Constants.MAX_BASE_TARGET)
                newBaseTarget = Constants.MAX_BASE_TARGET;

            if (newBaseTarget < (curBaseTarget * 9 / 10))
                newBaseTarget = curBaseTarget * 9 / 10;

            if (newBaseTarget == 0)
                newBaseTarget = 1;

            long twofoldCurBaseTarget = curBaseTarget * 11 / 10;
            
            if (twofoldCurBaseTarget < 0)
                twofoldCurBaseTarget = Constants.MAX_BASE_TARGET;

            if (newBaseTarget > twofoldCurBaseTarget)
                newBaseTarget = twofoldCurBaseTarget;

            baseTarget = newBaseTarget;
            cumulativeDifficulty = previousBlock.cumulativeDifficulty.add(Convert.two64.divide(BigInteger.valueOf(baseTarget)));
        } else {
            Block itBlock = previousBlock;
            BigInteger avgBaseTarget = BigInteger.valueOf(itBlock.getBaseTarget());
            int blockCounter = 1;

            do {
                itBlock = Nxt.getBlockchain().getBlock(itBlock.getPreviousBlockId());
                blockCounter++;
                avgBaseTarget = (avgBaseTarget.multiply(BigInteger.valueOf(blockCounter))
                        .add(BigInteger.valueOf(itBlock.getBaseTarget())))
                        .divide(BigInteger.valueOf(blockCounter + 1));
            } while (blockCounter < 24);

            long difTime = this.timestamp - itBlock.getTimestamp();
            long targetTimespan = 24 * 4 * 60;

            if (difTime < targetTimespan / 2)
                difTime = targetTimespan / 2;

            if (difTime > targetTimespan * 2)
                difTime = targetTimespan * 2;

            long curBaseTarget = previousBlock.getBaseTarget();
            long newBaseTarget = avgBaseTarget
                    .multiply(BigInteger.valueOf(difTime))
                    .divide(BigInteger.valueOf(targetTimespan)).longValue();

            if (newBaseTarget < 0 || newBaseTarget > Constants.MAX_BASE_TARGET)
                newBaseTarget = Constants.MAX_BASE_TARGET;

            if (newBaseTarget == 0)
                newBaseTarget = 1;

            if (newBaseTarget < curBaseTarget * 8 / 10)
                newBaseTarget = curBaseTarget * 8 / 10;

            if (newBaseTarget > curBaseTarget * 12 / 10)
                newBaseTarget = curBaseTarget * 12 / 10;

            baseTarget = newBaseTarget;
            cumulativeDifficulty = previousBlock.cumulativeDifficulty.add(Convert.two64.divide(BigInteger.valueOf(baseTarget)));
        }
    }

    // BURST-specific from here on

    @Override
    public boolean isVerified() {
        return unscaledPOCTime != null;
    }

    @Override
    public void preVerify() throws BlockchainProcessor.BlockNotAcceptedException {
        preVerify(null);
    }

    public void preVerify(byte[] scoopData) throws BlockchainProcessor.BlockNotAcceptedException {
        synchronized(this) {
            // Just in case its already verified
            if (this.unscaledPOCTime != null)
                return;
    
            for(TransactionImpl transaction : getTransactions()) {
                if (!transaction.verifySignature()) {
                    Logger.logMessage("Bad transaction signature during block pre-verification for tx: " + Long.toUnsignedString(transaction.getId()) + " at block height: " + getHeight());
                    throw new BlockchainProcessor.TransactionNotAcceptedException("Invalid signature for tx: " + Long.toUnsignedString(transaction.getId()) + "at block height: " + getHeight(), transaction);
                }
            }

            // only set POC time, marking block as verified, if all the transactions have verified
            try {
                if (scoopData == null) {
                    this.unscaledPOCTime = Generator.calculateUnscaledPOCTime(getGeneratorId(), nonce.longValue(), generationSignature, getScoopNum());
                } else {
                    this.unscaledPOCTime = Generator.calculateUnscaledPOCTime(getGeneratorId(), nonce.longValue(), generationSignature, scoopData);
                }
            } catch (RuntimeException e) {
                Logger.logMessage("Error pre-verifying block generation signature", e);
                throw e;
            }
        }
    }

    @Override
    public Peer getPeer() {
        return downloadedFrom;
    }

    @Override
    public void setPeer(Peer peer) {
        downloadedFrom = peer;
    }

    @Override
    public byte[] getBlockHash() {
        return Crypto.sha256().digest(bytes());
    }

    @Override
    public Long getNonce() {
        return nonce;
    }

    @Override
    public int getScoopNum() {
        return Generator.calculateScoopNum(generationSignature, getHeight());
    }

    @Override
    public byte[] getBlockATs() {
        return blockATs;
    }

    @Override
    public long getBlockReward() {
        if (this.height == 0 || this.height >= 1944000) {
            return 0;
        }
        int month = this.height / 10800;
        long reward = BigInteger.valueOf(10000)
                .multiply(BigInteger.valueOf(95).pow(month))
                .divide(BigInteger.valueOf(100).pow(month)).longValue() * Constants.ONE_NXT;
        
        return reward;
    }

    @Override
    public void setHeight(int height) {
        this.height = height;
    }

}
