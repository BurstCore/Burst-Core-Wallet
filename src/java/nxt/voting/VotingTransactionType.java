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

package nxt.voting;

import nxt.Constants;
import nxt.NxtException;
import nxt.account.Account;
import nxt.account.AccountLedger;
import nxt.blockchain.Appendix;
import nxt.blockchain.ChainTransactionId;
import nxt.blockchain.ChildChain;
import nxt.blockchain.ChildTransactionImpl;
import nxt.blockchain.ChildTransactionType;
import nxt.blockchain.Fee;
import nxt.blockchain.Transaction;
import nxt.blockchain.TransactionImpl;
import nxt.blockchain.TransactionType;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public abstract class VotingTransactionType extends ChildTransactionType {

    private static final byte SUBTYPE_VOTING_POLL_CREATION = 0;
    private static final byte SUBTYPE_VOTING_VOTE_CASTING = 1;
    private static final byte SUBTYPE_VOTING_PHASING_VOTE_CASTING = 2;

    public static TransactionType findTransactionType(byte subtype) {
        switch (subtype) {
            case SUBTYPE_VOTING_POLL_CREATION:
                return VotingTransactionType.POLL_CREATION;
            case SUBTYPE_VOTING_VOTE_CASTING:
                return VotingTransactionType.VOTE_CASTING;
            case SUBTYPE_VOTING_PHASING_VOTE_CASTING:
                return VotingTransactionType.PHASING_VOTE_CASTING;
            default:
                return null;
        }
    }

    private VotingTransactionType() {
    }

    @Override
    public final byte getType() {
        return ChildTransactionType.TYPE_VOTING;
    }

    @Override
    public final boolean applyAttachmentUnconfirmed(ChildTransactionImpl transaction, Account senderAccount) {
        return true;
    }

    @Override
    public final void undoAttachmentUnconfirmed(ChildTransactionImpl transaction, Account senderAccount) {
    }

    public final static TransactionType POLL_CREATION = new VotingTransactionType() {

        private final Fee POLL_OPTIONS_FEE = new Fee.SizeBasedFee(10 * Constants.ONE_NXT, Constants.ONE_NXT, 1) {
            @Override
            public int getSize(TransactionImpl transaction, Appendix appendage) {
                int numOptions = ((PollCreationAttachment)appendage).getPollOptions().length;
                return numOptions <= 19 ? 0 : numOptions - 19;
            }
        };

        private final Fee POLL_SIZE_FEE = new Fee.SizeBasedFee(0, 2 * Constants.ONE_NXT, 32) {
            @Override
            public int getSize(TransactionImpl transaction, Appendix appendage) {
                PollCreationAttachment attachment = (PollCreationAttachment)appendage;
                int size = attachment.getPollName().length() + attachment.getPollDescription().length();
                for (String option : ((PollCreationAttachment)appendage).getPollOptions()) {
                    size += option.length();
                }
                return size <= 288 ? 0 : size - 288;
            }
        };

        private final Fee POLL_FEE = (transaction, appendage) ->
                POLL_OPTIONS_FEE.getFee(transaction, appendage) + POLL_SIZE_FEE.getFee(transaction, appendage);

        @Override
        public final byte getSubtype() {
            return SUBTYPE_VOTING_POLL_CREATION;
        }

        @Override
        public AccountLedger.LedgerEvent getLedgerEvent() {
            return AccountLedger.LedgerEvent.POLL_CREATION;
        }

        @Override
        public String getName() {
            return "PollCreation";
        }

        @Override
        public Fee getBaselineFee(Transaction transaction) {
            return POLL_FEE;
        }

        @Override
        public PollCreationAttachment parseAttachment(ByteBuffer buffer) throws NxtException.NotValidException {
            return new PollCreationAttachment(buffer);
        }

        @Override
        public PollCreationAttachment parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
            return new PollCreationAttachment(attachmentData);
        }

        @Override
        public void applyAttachment(ChildTransactionImpl transaction, Account senderAccount, Account recipientAccount) {
            PollCreationAttachment attachment = (PollCreationAttachment) transaction.getAttachment();
            transaction.getChain().getPollHome().addPoll(transaction, attachment);
        }

        @Override
        public void validateAttachment(ChildTransactionImpl transaction) throws NxtException.ValidationException {

            PollCreationAttachment attachment = (PollCreationAttachment) transaction.getAttachment();

            int optionsCount = attachment.getPollOptions().length;

            if (attachment.getPollName().length() > Constants.MAX_POLL_NAME_LENGTH
                    || attachment.getPollName().isEmpty()
                    || attachment.getPollDescription().length() > Constants.MAX_POLL_DESCRIPTION_LENGTH
                    || optionsCount > Constants.MAX_POLL_OPTION_COUNT
                    || optionsCount == 0) {
                throw new NxtException.NotValidException("Invalid poll attachment: " + attachment.getJSONObject());
            }

            if (attachment.getMinNumberOfOptions() < 1
                    || attachment.getMinNumberOfOptions() > optionsCount) {
                throw new NxtException.NotValidException("Invalid min number of options: " + attachment.getJSONObject());
            }

            if (attachment.getMaxNumberOfOptions() < 1
                    || attachment.getMaxNumberOfOptions() < attachment.getMinNumberOfOptions()
                    || attachment.getMaxNumberOfOptions() > optionsCount) {
                throw new NxtException.NotValidException("Invalid max number of options: " + attachment.getJSONObject());
            }

            for (int i = 0; i < optionsCount; i++) {
                if (attachment.getPollOptions()[i].length() > Constants.MAX_POLL_OPTION_LENGTH
                        || attachment.getPollOptions()[i].isEmpty()) {
                    throw new NxtException.NotValidException("Invalid poll options length: " + attachment.getJSONObject());
                }
            }

            if (attachment.getMinRangeValue() < Constants.MIN_VOTE_VALUE || attachment.getMaxRangeValue() > Constants.MAX_VOTE_VALUE
                    || attachment.getMaxRangeValue() < attachment.getMinRangeValue()) {
                throw new NxtException.NotValidException("Invalid range: " + attachment.getJSONObject());
            }

            if (attachment.getFinishHeight() <= attachment.getFinishValidationHeight(transaction) + 1
                    || attachment.getFinishHeight() >= attachment.getFinishValidationHeight(transaction) + Constants.MAX_POLL_DURATION) {
                throw new NxtException.NotCurrentlyValidException("Invalid finishing height" + attachment.getJSONObject());
            }

            if (! attachment.getVoteWeighting().acceptsVotes() || attachment.getVoteWeighting().getVotingModel() == VoteWeighting.VotingModel.HASH) {
                throw new NxtException.NotValidException("VotingModel " + attachment.getVoteWeighting().getVotingModel() + " not valid for regular polls");
            }

            attachment.getVoteWeighting().validate();

        }

        @Override
        protected void validateId(ChildTransactionImpl transaction) throws NxtException.NotCurrentlyValidException {
            if (transaction.getChain().getPollHome().getPoll(transaction.getId()) != null) {
                throw new NxtException.NotCurrentlyValidException("Duplicate poll id " + transaction.getStringId());
            }
        }

        @Override
        public boolean isBlockDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            return isDuplicate(POLL_CREATION, getName(), duplicates, true);
        }

        @Override
        public boolean canHaveRecipient() {
            return false;
        }

        @Override
        public boolean isPhasingSafe() {
            return false;
        }

    };

    public final static TransactionType VOTE_CASTING = new VotingTransactionType() {

        @Override
        public final byte getSubtype() {
            return SUBTYPE_VOTING_VOTE_CASTING;
        }

        @Override
        public AccountLedger.LedgerEvent getLedgerEvent() {
            return AccountLedger.LedgerEvent.VOTE_CASTING;
        }

        @Override
        public String getName() {
            return "VoteCasting";
        }

        @Override
        public VoteCastingAttachment parseAttachment(ByteBuffer buffer) throws NxtException.NotValidException {
            return new VoteCastingAttachment(buffer);
        }

        @Override
        public VoteCastingAttachment parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
            return new VoteCastingAttachment(attachmentData);
        }

        @Override
        public void applyAttachment(ChildTransactionImpl transaction, Account senderAccount, Account recipientAccount) {
            VoteCastingAttachment attachment = (VoteCastingAttachment) transaction.getAttachment();
            transaction.getChain().getVoteHome().addVote(transaction, attachment);
        }

        @Override
        public void validateAttachment(ChildTransactionImpl transaction) throws NxtException.ValidationException {

            VoteCastingAttachment attachment = (VoteCastingAttachment) transaction.getAttachment();
            if (attachment.getPollId() == 0 || attachment.getPollVote() == null
                    || attachment.getPollVote().length > Constants.MAX_POLL_OPTION_COUNT) {
                throw new NxtException.NotValidException("Invalid vote casting attachment: " + attachment.getJSONObject());
            }

            long pollId = attachment.getPollId();

            PollHome.Poll poll = transaction.getChain().getPollHome().getPoll(pollId);
            if (poll == null) {
                throw new NxtException.NotCurrentlyValidException("Invalid poll: " + Long.toUnsignedString(attachment.getPollId()));
            }

            if (transaction.getChain().getVoteHome().getVote(pollId, transaction.getSenderId()) != null) {
                throw new NxtException.NotCurrentlyValidException("Double voting attempt");
            }

            if (poll.getFinishHeight() <= attachment.getFinishValidationHeight(transaction)) {
                throw new NxtException.NotCurrentlyValidException("Voting for this poll finishes at " + poll.getFinishHeight());
            }

            byte[] votes = attachment.getPollVote();
            int positiveCount = 0;
            for (byte vote : votes) {
                if (vote != Constants.NO_VOTE_VALUE && (vote < poll.getMinRangeValue() || vote > poll.getMaxRangeValue())) {
                    throw new NxtException.NotValidException(String.format("Invalid vote %d, vote must be between %d and %d",
                            vote, poll.getMinRangeValue(), poll.getMaxRangeValue()));
                }
                if (vote != Constants.NO_VOTE_VALUE) {
                    positiveCount++;
                }
            }

            if (positiveCount < poll.getMinNumberOfOptions() || positiveCount > poll.getMaxNumberOfOptions()) {
                throw new NxtException.NotValidException(String.format("Invalid num of choices %d, number of choices must be between %d and %d",
                        positiveCount, poll.getMinNumberOfOptions(), poll.getMaxNumberOfOptions()));
            }
        }

        @Override
        public boolean isDuplicate(final Transaction transaction, final Map<TransactionType, Map<String, Integer>> duplicates) {
            VoteCastingAttachment attachment = (VoteCastingAttachment) transaction.getAttachment();
            String key = Long.toUnsignedString(attachment.getPollId()) + ":" + Long.toUnsignedString(transaction.getSenderId());
            return isDuplicate(VOTE_CASTING, key, duplicates, true);
        }

        @Override
        public boolean canHaveRecipient() {
            return false;
        }

        @Override
        public boolean isPhasingSafe() {
            return false;
        }

    };

    public static final TransactionType PHASING_VOTE_CASTING = new VotingTransactionType() {

        private final Fee PHASING_VOTE_FEE = (transaction, appendage) -> {
            PhasingVoteCastingAttachment attachment = (PhasingVoteCastingAttachment) transaction.getAttachment();
            return attachment.getPhasedTransactionsIds().size() * Constants.ONE_NXT;
        };

        @Override
        public final byte getSubtype() {
            return SUBTYPE_VOTING_PHASING_VOTE_CASTING;
        }

        @Override
        public AccountLedger.LedgerEvent getLedgerEvent() {
            return AccountLedger.LedgerEvent.PHASING_VOTE_CASTING;
        }

        @Override
        public String getName() {
            return "PhasingVoteCasting";
        }

        @Override
        public Fee getBaselineFee(Transaction transaction) {
            return PHASING_VOTE_FEE;
        }

        @Override
        public PhasingVoteCastingAttachment parseAttachment(ByteBuffer buffer) throws NxtException.NotValidException {
            return new PhasingVoteCastingAttachment(buffer);
        }

        @Override
        public PhasingVoteCastingAttachment parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
            return new PhasingVoteCastingAttachment(attachmentData);
        }

        @Override
        public boolean canHaveRecipient() {
            return false;
        }

        @Override
        public void validateAttachment(ChildTransactionImpl transaction) throws NxtException.ValidationException {

            PhasingVoteCastingAttachment attachment = (PhasingVoteCastingAttachment) transaction.getAttachment();
            byte[] revealedSecret = attachment.getRevealedSecret();
            if (revealedSecret.length > Constants.MAX_PHASING_REVEALED_SECRET_LENGTH) {
                throw new NxtException.NotValidException("Invalid revealed secret length " + revealedSecret.length);
            }
            byte[] hashedSecret = null;
            byte algorithm = 0;

            List<ChainTransactionId> phasedTransactionIds = attachment.getPhasedTransactionsIds();
            if (phasedTransactionIds.size() > Constants.MAX_PHASING_VOTE_TRANSACTIONS) {
                throw new NxtException.NotValidException("No more than " + Constants.MAX_PHASING_VOTE_TRANSACTIONS + " votes allowed for two-phased multi-voting");
            }
            long voterId = transaction.getSenderId();
            for (ChainTransactionId phasedTransactionId : phasedTransactionIds) {
                ChildChain childChain = phasedTransactionId.getChildChain();
                if (childChain == null) {
                    throw new NxtException.NotValidException("Invalid child chain id " + phasedTransactionId.getChainId());
                }
                String phasedTransactionStringId = Long.toUnsignedString(phasedTransactionId.getTransactionId());
                PhasingPollHome.PhasingPoll poll = childChain.getPhasingPollHome().getPoll(phasedTransactionId.getFullHash());
                if (poll == null) {
                    throw new NxtException.NotCurrentlyValidException("Invalid phased transaction " + phasedTransactionStringId
                            + ", or phasing is finished");
                }
                if (! poll.getVoteWeighting().acceptsVotes()) {
                    throw new NxtException.NotValidException("This phased transaction does not require or accept voting");
                }
                long[] whitelist = poll.getWhitelist();
                if (whitelist.length > 0 && Arrays.binarySearch(whitelist, voterId) < 0) {
                    throw new NxtException.NotValidException("Voter is not in the phased transaction whitelist");
                }
                if (revealedSecret.length > 0) {
                    if (poll.getVoteWeighting().getVotingModel() != VoteWeighting.VotingModel.HASH) {
                        throw new NxtException.NotValidException("Phased transaction " + phasedTransactionStringId + " does not accept by-hash voting");
                    }
                    if (hashedSecret != null && !Arrays.equals(poll.getHashedSecret(), hashedSecret)) {
                        throw new NxtException.NotValidException("Phased transaction " + phasedTransactionStringId + " is using a different hashedSecret");
                    }
                    if (algorithm != 0 && algorithm != poll.getAlgorithm()) {
                        throw new NxtException.NotValidException("Phased transaction " + phasedTransactionStringId + " is using a different hashedSecretAlgorithm");
                    }
                    if (hashedSecret == null && ! poll.verifySecret(revealedSecret)) {
                        throw new NxtException.NotValidException("Revealed secret does not match phased transaction hashed secret");
                    }
                    hashedSecret = poll.getHashedSecret();
                    algorithm = poll.getAlgorithm();
                } else if (poll.getVoteWeighting().getVotingModel() == VoteWeighting.VotingModel.HASH) {
                    throw new NxtException.NotValidException("Phased transaction " + phasedTransactionStringId + " requires revealed secret for approval");
                }
                if (poll.getFinishHeight() <= attachment.getFinishValidationHeight(transaction) + 1) {
                    throw new NxtException.NotCurrentlyValidException(String.format("Phased transaction finishes at height %d which is not after approval transaction height %d",
                            poll.getFinishHeight(), attachment.getFinishValidationHeight(transaction) + 1));
                }
            }
        }

        @Override
        public final void applyAttachment(ChildTransactionImpl transaction, Account senderAccount, Account recipientAccount) {
            PhasingVoteCastingAttachment attachment = (PhasingVoteCastingAttachment) transaction.getAttachment();
            List<ChainTransactionId> phasedTransactionIds = attachment.getPhasedTransactionsIds();
            phasedTransactionIds.forEach(phasedTransactionId ->
                    phasedTransactionId.getChildChain().getPhasingVoteHome().addVote(transaction, senderAccount, phasedTransactionId.getFullHash()));
        }

        @Override
        public boolean isPhasingSafe() {
            return true;
        }

    };

}
