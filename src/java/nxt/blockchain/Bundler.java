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
import nxt.Nxt;
import nxt.NxtException;
import nxt.account.Account;
import nxt.crypto.Crypto;
import nxt.db.DbIterator;
import nxt.db.FilteringIterator;
import nxt.util.Logger;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class Bundler {

    private static final Map<ChildChain, Map<Long, Bundler>> bundlers = new ConcurrentHashMap<>();
    private static final TransactionProcessorImpl transactionProcessor = TransactionProcessorImpl.getInstance();

    public static Bundler getBundler(ChildChain childChain, long accountId) {
        Map<Long, Bundler> childChainBundlers = bundlers.computeIfAbsent(childChain, k -> new ConcurrentHashMap<>());
        return childChainBundlers.get(accountId);
    }

    public static synchronized Bundler addOrChangeBundler(ChildChain childChain, String secretPhrase, long minRateNQTPerFXT, long totalFeesLimitFQT) {
        return new Bundler(childChain, secretPhrase, minRateNQTPerFXT, totalFeesLimitFQT);
    }

    public static List<Bundler> getAllBundlers() {
        List<Bundler> allBundlers = new ArrayList<>();
        bundlers.values().forEach(childChainBundlers -> allBundlers.addAll(childChainBundlers.values()));
        return allBundlers;
    }

    public static List<Bundler> getChildChainBundlers(ChildChain childChain) {
        Map<Long, Bundler> childChainBundlers = bundlers.computeIfAbsent(childChain, k -> new ConcurrentHashMap<>());
        return new ArrayList<>(childChainBundlers.values());
    }

    public static List<Bundler> getAccountBundlers(long accountId) {
        List<Bundler> accountBundlers = new ArrayList<>();
        bundlers.values().forEach(childChainBundlers -> {
            Bundler bundler = childChainBundlers.get(accountId);
            if (bundler != null) {
                accountBundlers.add(bundler);
            }
        });
        return accountBundlers;
    }

    public static Bundler stopBundler(ChildChain childChain, long accountId) {
        Map<Long, Bundler> childChainBundlers = bundlers.computeIfAbsent(childChain, k -> new ConcurrentHashMap<>());
        return childChainBundlers.remove(accountId);
    }

    public static void stopAllBundlers() {
        bundlers.clear();
    }

    public static void init() {}

    static {
        transactionProcessor.addListener(transactions -> bundlers.values().forEach(chainBundlers -> chainBundlers.values().forEach(bundler -> {
            boolean hasChildChainTransactions = false;
            for (Transaction transaction : transactions) {
                if (transaction.getChain() == bundler.childChain) {
                    hasChildChainTransactions = true;
                    break;
                }
            }
            if (hasChildChainTransactions) {
                List<ChildBlockFxtTransaction> childBlockFxtTransactions = bundler.bundle();
                childBlockFxtTransactions.forEach(childBlockFxtTransaction -> {
                    try {
                        transactionProcessor.broadcast(childBlockFxtTransaction);
                    } catch (NxtException.ValidationException e) {
                        Logger.logErrorMessage(e.getMessage(), e);
                    }
                });
            }
        })), TransactionProcessor.Event.ADDED_UNCONFIRMED_TRANSACTIONS);
    }

    private final ChildChain childChain;
    private final String secretPhrase;
    private final byte[] publicKey;
    private final long accountId;
    private final long minRateNQTPerFXT;
    private final long totalFeesLimitFQT;
    private volatile long currentTotalFeesFQT;

    private Bundler(ChildChain childChain, String secretPhrase, long minRateNQTPerFXT, long totalFeesLimitFQT) {
        this.childChain = childChain;
        this.secretPhrase = secretPhrase;
        this.publicKey = Crypto.getPublicKey(secretPhrase);
        this.accountId = Account.getId(publicKey);
        this.minRateNQTPerFXT = minRateNQTPerFXT;
        this.totalFeesLimitFQT = totalFeesLimitFQT;
        Map<Long, Bundler> chainBundlers = bundlers.get(childChain);
        if (chainBundlers == null) {
            chainBundlers = new ConcurrentHashMap<>();
            bundlers.put(childChain, chainBundlers);
        }
        chainBundlers.put(accountId, this);
    }

    public final ChildChain getChildChain() {
        return childChain;
    }

    public final byte[] getPublicKey() {
        return publicKey;
    }

    public final long getAccountId() {
        return accountId;
    }

    public final long getMinRateNQTPerFXT() {
        return minRateNQTPerFXT;
    }

    public final long getTotalFeesLimitFQT() {
        return totalFeesLimitFQT;
    }

    public final long getCurrentTotalFeesFQT() {
        return currentTotalFeesFQT;
    }

    private List<ChildBlockFxtTransaction> bundle() {
        int blockchainHeight = Nxt.getBlockchain().getHeight();
        List<ChildBlockFxtTransaction> childBlockFxtTransactions = new ArrayList<>();
        try (FilteringIterator<UnconfirmedTransaction> unconfirmedTransactions = new FilteringIterator<>(
                TransactionProcessorImpl.getInstance().getUnconfirmedChildTransactions(childChain),
                transaction -> transaction.getTransaction().hasAllReferencedTransactions(transaction.getTimestamp(), 0))) {
            List<ChildTransaction> childTransactions = new ArrayList<>();
            Set<ChildTransaction> childTransactionSet = new HashSet<>();
            long totalMinFeeFQT = 0;
            long[] backFees = new long[3];
            while (unconfirmedTransactions.hasNext()) {
                ChildTransactionImpl childTransaction = (ChildTransactionImpl) unconfirmedTransactions.next().getTransaction();
                long minChildFeeFQT = childTransaction.getMinimumFeeFQT(blockchainHeight);
                long childFee = childTransaction.getFee();
                if (BigInteger.valueOf(childFee).multiply(BigInteger.valueOf(Constants.ONE_NXT))
                        .compareTo(BigInteger.valueOf(minRateNQTPerFXT).multiply(BigInteger.valueOf(minChildFeeFQT))) < 0) {
                    continue;
                }
                if (currentTotalFeesFQT + totalMinFeeFQT + minChildFeeFQT > totalFeesLimitFQT) {
                    Logger.logDebugMessage("Bundler " + Long.toUnsignedString(accountId) + " will exceed total fees limit, not bundling");
                    continue;
                }
                childTransactions.add(childTransaction);
                childTransactionSet.add(childTransaction);
                totalMinFeeFQT += minChildFeeFQT;
                long[] childMinBackFees = childTransaction.getMinimumBackFeesFQT(blockchainHeight, minChildFeeFQT);
                for (int i = 0; i < childMinBackFees.length; i++) {
                    backFees[i] += childMinBackFees[i];
                }
                //TODO: need to check block size limits in addition to transaction count
                if (childTransactions.size() == Constants.MAX_NUMBER_OF_TRANSACTIONS) {
                    if (!hasChildBlockFxtTransaction(childTransactionSet)) {
                        try {
                            ChildBlockFxtTransaction childBlockFxtTransaction = bundle(childTransactions, totalMinFeeFQT, backFees);
                            currentTotalFeesFQT += childBlockFxtTransaction.getFee();
                            childBlockFxtTransactions.add(childBlockFxtTransaction);
                        } catch (NxtException.ValidationException e) {
                            Logger.logInfoMessage(e.getMessage(), e);
                        }
                    }
                    childTransactions = new ArrayList<>();
                    childTransactionSet.clear();
                    totalMinFeeFQT = 0;
                    backFees = new long[3];
                }
            }
        }
        return childBlockFxtTransactions;
    }

    private ChildBlockFxtTransaction bundle(List<ChildTransaction> childTransactions, long feeFQT, long[] backFees) throws NxtException.ValidationException {
        byte[][] childTransactionFullHashes = new byte[childTransactions.size()][];
        for (int i = 0; i < childTransactionFullHashes.length; i++) {
            childTransactionFullHashes[i] = childTransactions.get(i).getFullHash();
        }
        FxtTransaction.Builder builder = Nxt.newTransactionBuilder(publicKey, 0, feeFQT, (short)10,
                new ChildBlockAttachment(childChain, childTransactionFullHashes, backFees));
        ChildBlockFxtTransaction childBlockFxtTransaction = (ChildBlockFxtTransaction)builder.build(secretPhrase);
        childBlockFxtTransaction.validate();
        Logger.logDebugMessage("Created ChildBlockFxtTransaction: " + childBlockFxtTransaction.getJSONObject().toJSONString());
        return childBlockFxtTransaction;
    }

    private boolean hasChildBlockFxtTransaction(Set<ChildTransaction> childTransactions) {
        try (DbIterator<UnconfirmedTransaction> unconfirmedTransactions = transactionProcessor.getUnconfirmedFxtTransactions()) {
            while (unconfirmedTransactions.hasNext()) {
                FxtTransaction fxtTransaction = (FxtTransaction)unconfirmedTransactions.next().getTransaction();
                if (fxtTransaction.getType() == ChildBlockFxtTransactionType.INSTANCE && fxtTransaction.getSenderId() == accountId) {
                    try {
                        fxtTransaction.validate();
                    } catch (NxtException.ValidationException e) {
                        continue; // skip not currently valid
                    }
                    List<? extends ChildTransaction> bundledChildTransactions = fxtTransaction.getChildTransactions();
                    if (childTransactions.size() == bundledChildTransactions.size() && childTransactions.containsAll(bundledChildTransactions)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

}
