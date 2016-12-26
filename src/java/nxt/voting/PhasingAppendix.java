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

package nxt.voting;

import nxt.Constants;
import nxt.Nxt;
import nxt.NxtException;
import nxt.account.Account;
import nxt.account.AccountLedger;
import nxt.blockchain.Appendix;
import nxt.blockchain.Chain;
import nxt.blockchain.ChainTransactionId;
import nxt.blockchain.ChildChain;
import nxt.blockchain.ChildTransaction;
import nxt.blockchain.ChildTransactionImpl;
import nxt.blockchain.Fee;
import nxt.blockchain.Transaction;
import nxt.blockchain.TransactionImpl;
import nxt.blockchain.TransactionProcessor;
import nxt.blockchain.TransactionProcessorImpl;
import nxt.blockchain.TransactionType;
import nxt.util.Convert;
import nxt.util.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PhasingAppendix extends Appendix.AbstractAppendix {

    private static final String appendixName = "Phasing";

    private static final Fee PHASING_FEE = (transaction, appendage) -> {
        long fee = 0;
        PhasingAppendix phasing = (PhasingAppendix)appendage;
        if (!phasing.params.getVoteWeighting().isBalanceIndependent()) {
            fee += 20 * Constants.ONE_NXT;
        } else {
            fee += Constants.ONE_NXT;
        }
        if (phasing.hashedSecret.length > 0) {
            fee += (1 + (phasing.hashedSecret.length - 1) / 32) * Constants.ONE_NXT;
        }
        fee += Constants.ONE_NXT * phasing.linkedTransactionsIds.size();
        return fee;
    };

    public static PhasingAppendix parse(JSONObject attachmentData) {
        if (!Appendix.hasAppendix(appendixName, attachmentData)) {
            return null;
        }
        return new PhasingAppendix(attachmentData);
    }

    private final int finishHeight;
    private final PhasingParams params;
    private final List<ChainTransactionId> linkedTransactionsIds;
    private final byte[] hashedSecret;
    private final byte algorithm;

    public PhasingAppendix(ByteBuffer buffer) {
        super(buffer);
        finishHeight = buffer.getInt();
        params = new PhasingParams(buffer);
        byte size = buffer.get();
        if (size > 0) {
            linkedTransactionsIds = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                linkedTransactionsIds.add(ChainTransactionId.parse(buffer));
            }
        } else {
            linkedTransactionsIds = Collections.emptyList();
        }
        byte hashedSecretLength = buffer.get();
        if (hashedSecretLength > 0) {
            hashedSecret = new byte[hashedSecretLength];
            buffer.get(hashedSecret);
        } else {
            hashedSecret = Convert.EMPTY_BYTE;
        }
        algorithm = buffer.get();
    }

    private PhasingAppendix(JSONObject attachmentData) {
        super(attachmentData);
        finishHeight = ((Long) attachmentData.get("phasingFinishHeight")).intValue();
        params = new PhasingParams(attachmentData);
        JSONArray linkedTransactionsJson = (JSONArray) attachmentData.get("phasingLinkedTransactions");
        if (linkedTransactionsJson != null && linkedTransactionsJson.size() > 0) {
            linkedTransactionsIds = new ArrayList<>(linkedTransactionsJson.size());
            linkedTransactionsJson.forEach(json -> linkedTransactionsIds.add(ChainTransactionId.parse((JSONObject)json)));
        } else {
            linkedTransactionsIds = Collections.emptyList();
        }
        String hashedSecret = Convert.emptyToNull((String)attachmentData.get("phasingHashedSecret"));
        if (hashedSecret != null) {
            this.hashedSecret = Convert.parseHexString(hashedSecret);
            this.algorithm = ((Long) attachmentData.get("phasingHashedSecretAlgorithm")).byteValue();
        } else {
            this.hashedSecret = Convert.EMPTY_BYTE;
            this.algorithm = 0;
        }
    }

    public PhasingAppendix(int finishHeight, PhasingParams phasingParams, List<ChainTransactionId> linkedTransactionsIds, byte[] hashedSecret, byte algorithm) {
        this.finishHeight = finishHeight;
        this.params = phasingParams;
        this.linkedTransactionsIds = linkedTransactionsIds;
        this.hashedSecret = hashedSecret != null ? hashedSecret : Convert.EMPTY_BYTE;
        this.algorithm = algorithm;
    }

    @Override
    public String getAppendixName() {
        return appendixName;
    }

    @Override
    protected int getMySize() {
        return 4 + params.getMySize() + 1 + ChainTransactionId.BYTE_SIZE * linkedTransactionsIds.size() + 1 + hashedSecret.length + 1;
    }

    @Override
    protected void putMyBytes(ByteBuffer buffer) {
        buffer.putInt(finishHeight);
        params.putMyBytes(buffer);
        buffer.put((byte) linkedTransactionsIds.size());
        linkedTransactionsIds.forEach(linkedTransaction -> linkedTransaction.put(buffer));
        buffer.put((byte)hashedSecret.length);
        buffer.put(hashedSecret);
        buffer.put(algorithm);
    }

    @Override
    protected void putMyJSON(JSONObject json) {
        json.put("phasingFinishHeight", finishHeight);
        params.putMyJSON(json);
        if (linkedTransactionsIds.size() > 0) {
            JSONArray linkedTransactionsJson = new JSONArray();
            linkedTransactionsIds.forEach(linkedTransaction -> linkedTransactionsJson.add(linkedTransaction.getJSON()));
            json.put("phasingLinkedTransactions", linkedTransactionsJson);
        }
        if (hashedSecret.length > 0) {
            json.put("phasingHashedSecret", Convert.toHexString(hashedSecret));
            json.put("phasingHashedSecretAlgorithm", algorithm);
        }
    }

    @Override
    public void validate(Transaction transaction) throws NxtException.ValidationException {
        params.validate();
        int currentHeight = Nxt.getBlockchain().getHeight();
        if (params.getVoteWeighting().getVotingModel() == VoteWeighting.VotingModel.TRANSACTION) {
            if (linkedTransactionsIds.size() == 0 || linkedTransactionsIds.size() > Constants.MAX_PHASING_LINKED_TRANSACTIONS) {
                throw new NxtException.NotValidException("Invalid number of linkedFullHashes " + linkedTransactionsIds.size());
            }
            Set<ChainTransactionId> idSet = new HashSet<>(linkedTransactionsIds.size());
            for (ChainTransactionId linkedTransactionId : linkedTransactionsIds) {
                byte[] hash = linkedTransactionId.getFullHash();
                if (Convert.emptyToNull(hash) == null || hash.length != 32) {
                    throw new NxtException.NotValidException("Invalid linkedFullHash " + Convert.toHexString(hash));
                }
                if (!idSet.add(linkedTransactionId)) {
                    throw new NxtException.NotValidException("Duplicate linked transaction ids");
                }
                Chain chain = linkedTransactionId.getChain();
                if (chain == null) {
                    throw new NxtException.NotValidException("Invalid chain id " + linkedTransactionId.getChainId());
                }
                TransactionImpl linkedTransaction = chain.getTransactionHome().findTransaction(hash, currentHeight);
                if (linkedTransaction != null) {
                    if (transaction.getTimestamp() - linkedTransaction.getTimestamp() > Constants.MAX_REFERENCED_TRANSACTION_TIMESPAN) {
                        throw new NxtException.NotValidException("Linked transaction cannot be more than 60 days older than the phased transaction");
                    }
                    if (linkedTransaction instanceof ChildTransaction && ((ChildTransaction)linkedTransaction).getPhasing() != null) {
                        throw new NxtException.NotCurrentlyValidException("Cannot link to an already existing phased transaction");
                    }
                }
            }
            if (params.getQuorum() > linkedTransactionsIds.size()) {
                throw new NxtException.NotValidException("Quorum of " + params.getQuorum() + " cannot be achieved in by-transaction voting with "
                        + linkedTransactionsIds.size() + " linked full hashes only");
            }
        } else {
            if (linkedTransactionsIds.size() != 0) {
                throw new NxtException.NotValidException("LinkedFullHashes can only be used with VotingModel.TRANSACTION");
            }
        }

        if (params.getVoteWeighting().getVotingModel() == VoteWeighting.VotingModel.HASH) {
            if (params.getQuorum() != 1) {
                throw new NxtException.NotValidException("Quorum must be 1 for by-hash voting");
            }
            if (hashedSecret.length == 0 || hashedSecret.length > Byte.MAX_VALUE) {
                throw new NxtException.NotValidException("Invalid hashedSecret " + Convert.toHexString(hashedSecret));
            }
            if (PhasingPollHome.getHashFunction(algorithm) == null) {
                throw new NxtException.NotValidException("Invalid hashedSecretAlgorithm " + algorithm);
            }
        } else {
            if (hashedSecret.length != 0) {
                throw new NxtException.NotValidException("HashedSecret can only be used with VotingModel.HASH");
            }
            if (algorithm != 0) {
                throw new NxtException.NotValidException("HashedSecretAlgorithm can only be used with VotingModel.HASH");
            }
        }

        if (finishHeight <= currentHeight + (params.getVoteWeighting().acceptsVotes() ? 2 : 1)
                || finishHeight >= currentHeight + Constants.MAX_PHASING_DURATION) {
            throw new NxtException.NotCurrentlyValidException("Invalid finish height " + finishHeight);
        }
    }

    @Override
    public void validateAtFinish(Transaction transaction) throws NxtException.ValidationException {
        params.checkApprovable();
    }

    @Override
    public void apply(Transaction transaction, Account senderAccount, Account recipientAccount) {
        ((ChildChain) transaction.getChain()).getPhasingPollHome().addPoll(transaction, this);
    }

    @Override
    public boolean isPhasable() {
        return false;
    }

    @Override
    public Fee getBaselineFee(Transaction transaction) {
        return PHASING_FEE;
    }

    private void release(TransactionImpl transaction) {
        Account senderAccount = Account.getAccount(transaction.getSenderId());
        Account recipientAccount = transaction.getRecipientId() == 0 ? null : Account.getAccount(transaction.getRecipientId());
        transaction.getAppendages().forEach(appendage -> {
            if (appendage.isPhasable()) {
                appendage.apply(transaction, senderAccount, recipientAccount);
            }
        });
        TransactionProcessorImpl.getInstance().notifyListeners(Collections.singletonList(transaction), TransactionProcessor.Event.RELEASE_PHASED_TRANSACTION);
        Logger.logDebugMessage("Transaction " + transaction.getStringId() + " has been released");
    }

    public void reject(ChildTransactionImpl transaction) {
        Account senderAccount = Account.getAccount(transaction.getSenderId());
        transaction.getType().undoAttachmentUnconfirmed(transaction, senderAccount);
        transaction.getChain().getBalanceHome().getBalance(transaction.getSenderId())
                .addToUnconfirmedBalance(AccountLedger.LedgerEvent.REJECT_PHASED_TRANSACTION, AccountLedger.newEventId(transaction),
                                                 transaction.getAmount());
        TransactionProcessorImpl.getInstance()
                .notifyListeners(Collections.singletonList(transaction), TransactionProcessor.Event.REJECT_PHASED_TRANSACTION);
        Logger.logDebugMessage("Transaction " + transaction.getStringId() + " has been rejected");
    }

    public void countVotes(ChildTransactionImpl transaction) {
        if (PhasingPollHome.getResult(transaction) != null) {
            return;
        }
        PhasingPollHome phasingPollHome = transaction.getChain().getPhasingPollHome();
        PhasingPollHome.PhasingPoll poll = phasingPollHome.getPoll(transaction);
        long result = poll.countVotes();
        poll.finish(result);
        if (result >= poll.getQuorum()) {
            try {
                release(transaction);
            } catch (RuntimeException e) {
                Logger.logErrorMessage("Failed to release phased transaction " + transaction.getJSONObject().toJSONString(), e);
                reject(transaction);
            }
        } else {
            reject(transaction);
        }
    }

    public void tryCountVotes(ChildTransactionImpl transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
        PhasingPollHome.PhasingPoll poll = transaction.getChain().getPhasingPollHome().getPoll(transaction);
        long result = poll.countVotes();
        if (result >= poll.getQuorum()) {
            if (!transaction.attachmentIsDuplicate(duplicates, false)) {
                try {
                    release(transaction);
                    poll.finish(result);
                    Logger.logDebugMessage("Early finish of transaction " + transaction.getStringId() + " at height " + Nxt.getBlockchain().getHeight());
                } catch (RuntimeException e) {
                    Logger.logErrorMessage("Failed to release phased transaction " + transaction.getJSONObject().toJSONString(), e);
                }
            } else {
                Logger.logDebugMessage("At height " + Nxt.getBlockchain().getHeight() + " phased transaction " + transaction.getStringId()
                        + " is duplicate, cannot finish early");
            }
        } else {
            Logger.logDebugMessage("At height " + Nxt.getBlockchain().getHeight() + " phased transaction " + transaction.getStringId()
                    + " does not yet meet quorum, cannot finish early");
        }
    }

    public int getFinishHeight() {
        return finishHeight;
    }

    public long getQuorum() {
        return params.getQuorum();
    }

    public long[] getWhitelist() {
        return params.getWhitelist();
    }

    public VoteWeighting getVoteWeighting() {
        return params.getVoteWeighting();
    }

    public List<ChainTransactionId> getLinkedTransactionsIds() {
        return linkedTransactionsIds;
    }

    public byte[] getHashedSecret() {
        return hashedSecret;
    }

    public byte getAlgorithm() {
        return algorithm;
    }

    public PhasingParams getParams() {
        return params;
    }
}
