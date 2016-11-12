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

package nxt.messaging;

import nxt.Constants;
import nxt.NxtException;
import nxt.account.Account;
import nxt.account.AccountLedger;
import nxt.blockchain.Appendix;
import nxt.blockchain.Attachment;
import nxt.blockchain.ChildChain;
import nxt.blockchain.ChildTransactionImpl;
import nxt.blockchain.ChildTransactionType;
import nxt.blockchain.Fee;
import nxt.blockchain.Genesis;
import nxt.blockchain.Transaction;
import nxt.blockchain.TransactionImpl;
import nxt.blockchain.TransactionType;
import nxt.util.Convert;
import nxt.voting.PhasingPollHome;
import nxt.voting.PhasingVoteCastingAttachment;
import nxt.voting.PollCreationAttachment;
import nxt.voting.PollHome;
import nxt.voting.VoteCastingAttachment;
import nxt.voting.VoteWeighting;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public abstract class MessagingTransactionType extends ChildTransactionType {

    private MessagingTransactionType() {
    }

    @Override
    public final byte getType() {
        return ChildTransactionType.TYPE_MESSAGING;
    }

    @Override
    public final boolean applyAttachmentUnconfirmed(ChildTransactionImpl transaction, Account senderAccount) {
        return true;
    }

    @Override
    public final void undoAttachmentUnconfirmed(ChildTransactionImpl transaction, Account senderAccount) {
    }

    public final static TransactionType ARBITRARY_MESSAGE = new MessagingTransactionType() {

        @Override
        public final byte getSubtype() {
            return ChildTransactionType.SUBTYPE_MESSAGING_ARBITRARY_MESSAGE;
        }

        @Override
        public AccountLedger.LedgerEvent getLedgerEvent() {
            return AccountLedger.LedgerEvent.ARBITRARY_MESSAGE;
        }

        @Override
        public String getName() {
            return "ArbitraryMessage";
        }

        @Override
        public Attachment.EmptyAttachment parseAttachment(ByteBuffer buffer) throws NxtException.NotValidException {
            return MessageAttachment.INSTANCE;
        }

        @Override
        public Attachment.EmptyAttachment parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
            return MessageAttachment.INSTANCE;
        }

        @Override
        public void applyAttachment(ChildTransactionImpl transaction, Account senderAccount, Account recipientAccount) {
        }

        @Override
        public void validateAttachment(ChildTransactionImpl transaction) throws NxtException.ValidationException {
            Attachment attachment = transaction.getAttachment();
            if (transaction.getAmount() != 0) {
                throw new NxtException.NotValidException("Invalid arbitrary message: " + attachment.getJSONObject());
            }
            if (transaction.getRecipientId() == Genesis.CREATOR_ID) {
                throw new NxtException.NotValidException("Sending messages to Genesis not allowed.");
            }
        }

        @Override
        public boolean canHaveRecipient() {
            return true;
        }

        @Override
        public boolean mustHaveRecipient() {
            return false;
        }

        @Override
        public boolean isPhasingSafe() {
            return false;
        }

    };

    public static final TransactionType ALIAS_ASSIGNMENT = new MessagingTransactionType() {

        private final Fee ALIAS_FEE = new Fee.SizeBasedFee(2 * Constants.ONE_NXT, 2 * Constants.ONE_NXT, 32) {
            @Override
            public int getSize(TransactionImpl transaction, Appendix appendage) {
                AliasAssignmentAttachment attachment = (AliasAssignmentAttachment) transaction.getAttachment();
                return attachment.getAliasName().length() + attachment.getAliasURI().length();
            }
        };

        @Override
        public final byte getSubtype() {
            return ChildTransactionType.SUBTYPE_MESSAGING_ALIAS_ASSIGNMENT;
        }

        @Override
        public AccountLedger.LedgerEvent getLedgerEvent() {
            return AccountLedger.LedgerEvent.ALIAS_ASSIGNMENT;
        }

        @Override
        public String getName() {
            return "AliasAssignment";
        }

        @Override
        public Fee getBaselineFee(Transaction transaction) {
            return ALIAS_FEE;
        }

        @Override
        public AliasAssignmentAttachment parseAttachment(ByteBuffer buffer) throws NxtException.NotValidException {
            return new AliasAssignmentAttachment(buffer);
        }

        @Override
        public AliasAssignmentAttachment parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
            return new AliasAssignmentAttachment(attachmentData);
        }

        @Override
        public void applyAttachment(ChildTransactionImpl transaction, Account senderAccount, Account recipientAccount) {
            AliasAssignmentAttachment attachment = (AliasAssignmentAttachment) transaction.getAttachment();
            transaction.getChain().getAliasHome().addOrUpdateAlias(transaction, attachment);
        }

        @Override
        public boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            AliasAssignmentAttachment attachment = (AliasAssignmentAttachment) transaction.getAttachment();
            return isDuplicate(MessagingTransactionType.ALIAS_ASSIGNMENT, attachment.getAliasName().toLowerCase(), duplicates, true);
        }

        @Override
        public boolean isBlockDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            return ((ChildChain) transaction.getChain()).getAliasHome().getAlias(((AliasAssignmentAttachment) transaction.getAttachment()).getAliasName()) == null
                    && isDuplicate(MessagingTransactionType.ALIAS_ASSIGNMENT, "", duplicates, true);
        }

        @Override
        public void validateAttachment(ChildTransactionImpl transaction) throws NxtException.ValidationException {
            AliasAssignmentAttachment attachment = (AliasAssignmentAttachment) transaction.getAttachment();
            if (attachment.getAliasName().length() == 0
                    || attachment.getAliasName().length() > Constants.MAX_ALIAS_LENGTH
                    || attachment.getAliasURI().length() > Constants.MAX_ALIAS_URI_LENGTH) {
                throw new NxtException.NotValidException("Invalid alias assignment: " + attachment.getJSONObject());
            }
            String normalizedAlias = attachment.getAliasName().toLowerCase();
            for (int i = 0; i < normalizedAlias.length(); i++) {
                if (Constants.ALPHABET.indexOf(normalizedAlias.charAt(i)) < 0) {
                    throw new NxtException.NotValidException("Invalid alias name: " + normalizedAlias);
                }
            }
            AliasHome.Alias alias = transaction.getChain().getAliasHome().getAlias(normalizedAlias);
            if (alias != null && alias.getAccountId() != transaction.getSenderId()) {
                throw new NxtException.NotCurrentlyValidException("Alias already owned by another account: " + normalizedAlias);
            }
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

    public static final TransactionType ALIAS_SELL = new MessagingTransactionType() {

        @Override
        public final byte getSubtype() {
            return ChildTransactionType.SUBTYPE_MESSAGING_ALIAS_SELL;
        }

        @Override
        public AccountLedger.LedgerEvent getLedgerEvent() {
            return AccountLedger.LedgerEvent.ALIAS_SELL;
        }
        @Override
        public String getName() {
            return "AliasSell";
        }

        @Override
        public AliasSellAttachment parseAttachment(ByteBuffer buffer) throws NxtException.NotValidException {
            return new AliasSellAttachment(buffer);
        }

        @Override
        public AliasSellAttachment parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
            return new AliasSellAttachment(attachmentData);
        }

        @Override
        public void applyAttachment(ChildTransactionImpl transaction, Account senderAccount, Account recipientAccount) {
            AliasSellAttachment attachment = (AliasSellAttachment) transaction.getAttachment();
            transaction.getChain().getAliasHome().sellAlias(transaction, attachment);
        }

        @Override
        public boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            AliasSellAttachment attachment = (AliasSellAttachment) transaction.getAttachment();
            // not a bug, uniqueness is based on Messaging.ALIAS_ASSIGNMENT
            return isDuplicate(MessagingTransactionType.ALIAS_ASSIGNMENT, attachment.getAliasName().toLowerCase(), duplicates, true);
        }

        @Override
        public void validateAttachment(ChildTransactionImpl transaction) throws NxtException.ValidationException {
            if (transaction.getAmount() != 0) {
                throw new NxtException.NotValidException("Invalid sell alias transaction: " +
                        transaction.getJSONObject());
            }
            final AliasSellAttachment attachment =
                    (AliasSellAttachment) transaction.getAttachment();
            final String aliasName = attachment.getAliasName();
            if (aliasName == null || aliasName.length() == 0) {
                throw new NxtException.NotValidException("Missing alias name");
            }
            long priceNQT = attachment.getPriceNQT();
            if (priceNQT < 0 || priceNQT > Constants.MAX_BALANCE_NQT) {
                throw new NxtException.NotValidException("Invalid alias sell price: " + priceNQT);
            }
            if (priceNQT == 0) {
                if (Genesis.CREATOR_ID == transaction.getRecipientId()) {
                    throw new NxtException.NotValidException("Transferring aliases to Genesis account not allowed");
                } else if (transaction.getRecipientId() == 0) {
                    throw new NxtException.NotValidException("Missing alias transfer recipient");
                }
            }
            AliasHome.Alias alias = transaction.getChain().getAliasHome().getAlias(aliasName);
            if (alias == null) {
                throw new NxtException.NotCurrentlyValidException("No such alias: " + aliasName);
            } else if (alias.getAccountId() != transaction.getSenderId()) {
                throw new NxtException.NotCurrentlyValidException("Alias doesn't belong to sender: " + aliasName);
            }
            if (transaction.getRecipientId() == Genesis.CREATOR_ID) {
                throw new NxtException.NotValidException("Selling alias to Genesis not allowed");
            }
        }

        @Override
        public boolean canHaveRecipient() {
            return true;
        }

        @Override
        public boolean mustHaveRecipient() {
            return false;
        }

        @Override
        public boolean isPhasingSafe() {
            return false;
        }

    };

    public static final TransactionType ALIAS_BUY = new MessagingTransactionType() {

        @Override
        public final byte getSubtype() {
            return ChildTransactionType.SUBTYPE_MESSAGING_ALIAS_BUY;
        }

        @Override
        public AccountLedger.LedgerEvent getLedgerEvent() {
            return AccountLedger.LedgerEvent.ALIAS_BUY;
        }

        @Override
        public String getName() {
            return "AliasBuy";
        }

        @Override
        public AliasBuyAttachment parseAttachment(ByteBuffer buffer) throws NxtException.NotValidException {
            return new AliasBuyAttachment(buffer);
        }

        @Override
        public AliasBuyAttachment parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
            return new AliasBuyAttachment(attachmentData);
        }

        @Override
        public void applyAttachment(ChildTransactionImpl transaction, Account senderAccount, Account recipientAccount) {
            final AliasBuyAttachment attachment =
                    (AliasBuyAttachment) transaction.getAttachment();
            final String aliasName = attachment.getAliasName();
            transaction.getChain().getAliasHome().changeOwner(transaction.getSenderId(), aliasName);
        }

        @Override
        public boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            AliasBuyAttachment attachment = (AliasBuyAttachment) transaction.getAttachment();
            // not a bug, uniqueness is based on Messaging.ALIAS_ASSIGNMENT
            return isDuplicate(MessagingTransactionType.ALIAS_ASSIGNMENT, attachment.getAliasName().toLowerCase(), duplicates, true);
        }

        @Override
        public void validateAttachment(ChildTransactionImpl transaction) throws NxtException.ValidationException {
            AliasHome aliasHome = transaction.getChain().getAliasHome();
            final AliasBuyAttachment attachment =
                    (AliasBuyAttachment) transaction.getAttachment();
            final String aliasName = attachment.getAliasName();
            final AliasHome.Alias alias = aliasHome.getAlias(aliasName);
            if (alias == null) {
                throw new NxtException.NotCurrentlyValidException("No such alias: " + aliasName);
            } else if (alias.getAccountId() != transaction.getRecipientId()) {
                throw new NxtException.NotCurrentlyValidException("Alias is owned by account other than recipient: "
                        + Long.toUnsignedString(alias.getAccountId()));
            }
            AliasHome.Offer offer = aliasHome.getOffer(alias);
            if (offer == null) {
                throw new NxtException.NotCurrentlyValidException("Alias is not for sale: " + aliasName);
            }
            if (transaction.getAmount() < offer.getPriceNQT()) {
                String msg = "Price is too low for: " + aliasName + " ("
                        + transaction.getAmount() + " < " + offer.getPriceNQT() + ")";
                throw new NxtException.NotCurrentlyValidException(msg);
            }
            if (offer.getBuyerId() != 0 && offer.getBuyerId() != transaction.getSenderId()) {
                throw new NxtException.NotCurrentlyValidException("Wrong buyer for " + aliasName + ": "
                        + Long.toUnsignedString(transaction.getSenderId()) + " expected: "
                        + Long.toUnsignedString(offer.getBuyerId()));
            }
        }

        @Override
        public boolean canHaveRecipient() {
            return true;
        }

        @Override
        public boolean isPhasingSafe() {
            return false;
        }

    };

    public static final TransactionType ALIAS_DELETE = new MessagingTransactionType() {

        @Override
        public final byte getSubtype() {
            return ChildTransactionType.SUBTYPE_MESSAGING_ALIAS_DELETE;
        }

        @Override
        public AccountLedger.LedgerEvent getLedgerEvent() {
            return AccountLedger.LedgerEvent.ALIAS_DELETE;
        }

        @Override
        public String getName() {
            return "AliasDelete";
        }

        @Override
        public AliasDeleteAttachment parseAttachment(final ByteBuffer buffer) throws NxtException.NotValidException {
            return new AliasDeleteAttachment(buffer);
        }

        @Override
        public AliasDeleteAttachment parseAttachment(final JSONObject attachmentData) throws NxtException.NotValidException {
            return new AliasDeleteAttachment(attachmentData);
        }

        @Override
        public void applyAttachment(ChildTransactionImpl transaction, Account senderAccount, Account recipientAccount) {
            final AliasDeleteAttachment attachment =
                    (AliasDeleteAttachment) transaction.getAttachment();
            transaction.getChain().getAliasHome().deleteAlias(attachment.getAliasName());
        }

        @Override
        public boolean isDuplicate(final Transaction transaction, final Map<TransactionType, Map<String, Integer>> duplicates) {
            AliasDeleteAttachment attachment = (AliasDeleteAttachment) transaction.getAttachment();
            // not a bug, uniqueness is based on Messaging.ALIAS_ASSIGNMENT
            return isDuplicate(MessagingTransactionType.ALIAS_ASSIGNMENT, attachment.getAliasName().toLowerCase(), duplicates, true);
        }

        @Override
        public void validateAttachment(ChildTransactionImpl transaction) throws NxtException.ValidationException {
            final AliasDeleteAttachment attachment =
                    (AliasDeleteAttachment) transaction.getAttachment();
            final String aliasName = attachment.getAliasName();
            if (aliasName == null || aliasName.length() == 0) {
                throw new NxtException.NotValidException("Missing alias name");
            }
            final AliasHome.Alias alias = transaction.getChain().getAliasHome().getAlias(aliasName);
            if (alias == null) {
                throw new NxtException.NotCurrentlyValidException("No such alias: " + aliasName);
            } else if (alias.getAccountId() != transaction.getSenderId()) {
                throw new NxtException.NotCurrentlyValidException("Alias doesn't belong to sender: " + aliasName);
            }
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

    public final static TransactionType POLL_CREATION = new MessagingTransactionType() {

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
            return ChildTransactionType.SUBTYPE_MESSAGING_POLL_CREATION;
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
        public boolean isBlockDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            return isDuplicate(MessagingTransactionType.POLL_CREATION, getName(), duplicates, true);
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

    public final static TransactionType VOTE_CASTING = new MessagingTransactionType() {

        @Override
        public final byte getSubtype() {
            return ChildTransactionType.SUBTYPE_MESSAGING_VOTE_CASTING;
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
            return isDuplicate(MessagingTransactionType.VOTE_CASTING, key, duplicates, true);
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

    public static final TransactionType PHASING_VOTE_CASTING = new MessagingTransactionType() {

        private final Fee PHASING_VOTE_FEE = (transaction, appendage) -> {
            PhasingVoteCastingAttachment attachment = (PhasingVoteCastingAttachment) transaction.getAttachment();
            return attachment.getTransactionFullHashes().size() * Constants.ONE_NXT;
        };

        @Override
        public final byte getSubtype() {
            return ChildTransactionType.SUBTYPE_MESSAGING_PHASING_VOTE_CASTING;
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

            List<byte[]> hashes = attachment.getTransactionFullHashes();
            if (hashes.size() > Constants.MAX_PHASING_VOTE_TRANSACTIONS) {
                throw new NxtException.NotValidException("No more than " + Constants.MAX_PHASING_VOTE_TRANSACTIONS + " votes allowed for two-phased multi-voting");
            }

            long voterId = transaction.getSenderId();
            for (byte[] hash : hashes) {
                long phasedTransactionId = Convert.fullHashToId(hash);
                if (phasedTransactionId == 0) {
                    throw new NxtException.NotValidException("Invalid phased transactionFullHash " + Convert.toHexString(hash));
                }

                PhasingPollHome.PhasingPoll poll = transaction.getChain().getPhasingPollHome().getPoll(phasedTransactionId);
                if (poll == null) {
                    throw new NxtException.NotCurrentlyValidException("Invalid phased transaction " + Long.toUnsignedString(phasedTransactionId)
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
                        throw new NxtException.NotValidException("Phased transaction " + Long.toUnsignedString(phasedTransactionId) + " does not accept by-hash voting");
                    }
                    if (hashedSecret != null && !Arrays.equals(poll.getHashedSecret(), hashedSecret)) {
                        throw new NxtException.NotValidException("Phased transaction " + Long.toUnsignedString(phasedTransactionId) + " is using a different hashedSecret");
                    }
                    if (algorithm != 0 && algorithm != poll.getAlgorithm()) {
                        throw new NxtException.NotValidException("Phased transaction " + Long.toUnsignedString(phasedTransactionId) + " is using a different hashedSecretAlgorithm");
                    }
                    if (hashedSecret == null && ! poll.verifySecret(revealedSecret)) {
                        throw new NxtException.NotValidException("Revealed secret does not match phased transaction hashed secret");
                    }
                    hashedSecret = poll.getHashedSecret();
                    algorithm = poll.getAlgorithm();
                } else if (poll.getVoteWeighting().getVotingModel() == VoteWeighting.VotingModel.HASH) {
                    throw new NxtException.NotValidException("Phased transaction " + Long.toUnsignedString(phasedTransactionId) + " requires revealed secret for approval");
                }
                if (!Arrays.equals(poll.getFullHash(), hash)) {
                    throw new NxtException.NotCurrentlyValidException("Phased transaction hash does not match hash in voting transaction");
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
            List<byte[]> hashes = attachment.getTransactionFullHashes();
            for (byte[] hash : hashes) {
                transaction.getChain().getPhasingVoteHome().addVote(transaction, senderAccount, Convert.fullHashToId(hash));
            }
        }

        @Override
        public boolean isPhasingSafe() {
            return true;
        }

    };

    public static final MessagingTransactionType ACCOUNT_INFO = new MessagingTransactionType() {

        private final Fee ACCOUNT_INFO_FEE = new Fee.SizeBasedFee(Constants.ONE_NXT, 2 * Constants.ONE_NXT, 32) {
            @Override
            public int getSize(TransactionImpl transaction, Appendix appendage) {
                AccountInfoAttachment attachment = (AccountInfoAttachment) transaction.getAttachment();
                return attachment.getName().length() + attachment.getDescription().length();
            }
        };

        @Override
        public byte getSubtype() {
            return ChildTransactionType.SUBTYPE_MESSAGING_ACCOUNT_INFO;
        }

        @Override
        public AccountLedger.LedgerEvent getLedgerEvent() {
            return AccountLedger.LedgerEvent.ACCOUNT_INFO;
        }

        @Override
        public String getName() {
            return "AccountInfo";
        }

        @Override
        public Fee getBaselineFee(Transaction transaction) {
            return ACCOUNT_INFO_FEE;
        }

        @Override
        public AccountInfoAttachment parseAttachment(ByteBuffer buffer) throws NxtException.NotValidException {
            return new AccountInfoAttachment(buffer);
        }

        @Override
        public AccountInfoAttachment parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
            return new AccountInfoAttachment(attachmentData);
        }

        @Override
        public void validateAttachment(ChildTransactionImpl transaction) throws NxtException.ValidationException {
            AccountInfoAttachment attachment = (AccountInfoAttachment)transaction.getAttachment();
            if (attachment.getName().length() > Constants.MAX_ACCOUNT_NAME_LENGTH
                    || attachment.getDescription().length() > Constants.MAX_ACCOUNT_DESCRIPTION_LENGTH) {
                throw new NxtException.NotValidException("Invalid account info issuance: " + attachment.getJSONObject());
            }
        }

        @Override
        public void applyAttachment(ChildTransactionImpl transaction, Account senderAccount, Account recipientAccount) {
            AccountInfoAttachment attachment = (AccountInfoAttachment) transaction.getAttachment();
            senderAccount.setAccountInfo(attachment.getName(), attachment.getDescription());
        }

        @Override
        public boolean isBlockDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            return isDuplicate(MessagingTransactionType.ACCOUNT_INFO, getName(), duplicates, true);
        }

        @Override
        public boolean canHaveRecipient() {
            return false;
        }

        @Override
        public boolean isPhasingSafe() {
            return true;
        }

    };

    public static final MessagingTransactionType ACCOUNT_PROPERTY = new MessagingTransactionType() {

        private final Fee ACCOUNT_PROPERTY_FEE = new Fee.SizeBasedFee(Constants.ONE_NXT, Constants.ONE_NXT, 32) {
            @Override
            public int getSize(TransactionImpl transaction, Appendix appendage) {
                AccountPropertyAttachment attachment = (AccountPropertyAttachment) transaction.getAttachment();
                return attachment.getValue().length();
            }
        };

        @Override
        public byte getSubtype() {
            return ChildTransactionType.SUBTYPE_MESSAGING_ACCOUNT_PROPERTY;
        }

        @Override
        public AccountLedger.LedgerEvent getLedgerEvent() {
            return AccountLedger.LedgerEvent.ACCOUNT_PROPERTY;
        }

        @Override
        public String getName() {
            return "AccountProperty";
        }

        @Override
        public Fee getBaselineFee(Transaction transaction) {
            return ACCOUNT_PROPERTY_FEE;
        }

        @Override
        public AccountPropertyAttachment parseAttachment(ByteBuffer buffer) throws NxtException.NotValidException {
            return new AccountPropertyAttachment(buffer);
        }

        @Override
        public AccountPropertyAttachment parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
            return new AccountPropertyAttachment(attachmentData);
        }

        @Override
        public void validateAttachment(ChildTransactionImpl transaction) throws NxtException.ValidationException {
            AccountPropertyAttachment attachment = (AccountPropertyAttachment)transaction.getAttachment();
            if (attachment.getProperty().length() > Constants.MAX_ACCOUNT_PROPERTY_NAME_LENGTH
                    || attachment.getProperty().length() == 0
                    || attachment.getValue().length() > Constants.MAX_ACCOUNT_PROPERTY_VALUE_LENGTH) {
                throw new NxtException.NotValidException("Invalid account property: " + attachment.getJSONObject());
            }
            if (transaction.getAmount() != 0) {
                throw new NxtException.NotValidException("Account property transaction cannot be used to send NXT");
            }
            if (transaction.getRecipientId() == Genesis.CREATOR_ID) {
                throw new NxtException.NotValidException("Setting Genesis account properties not allowed");
            }
        }

        @Override
        public void applyAttachment(ChildTransactionImpl transaction, Account senderAccount, Account recipientAccount) {
            AccountPropertyAttachment attachment = (AccountPropertyAttachment) transaction.getAttachment();
            recipientAccount.setProperty(transaction, senderAccount, attachment.getProperty(), attachment.getValue());
        }

        @Override
        public boolean canHaveRecipient() {
            return true;
        }

        @Override
        public boolean isPhasingSafe() {
            return true;
        }

    };

    public static final MessagingTransactionType ACCOUNT_PROPERTY_DELETE = new MessagingTransactionType() {

        @Override
        public byte getSubtype() {
            return ChildTransactionType.SUBTYPE_MESSAGING_ACCOUNT_PROPERTY_DELETE;
        }

        @Override
        public AccountLedger.LedgerEvent getLedgerEvent() {
            return AccountLedger.LedgerEvent.ACCOUNT_PROPERTY_DELETE;
        }

        @Override
        public String getName() {
            return "AccountPropertyDelete";
        }

        @Override
        public AccountPropertyDeleteAttachment parseAttachment(ByteBuffer buffer) throws NxtException.NotValidException {
            return new AccountPropertyDeleteAttachment(buffer);
        }

        @Override
        public AccountPropertyDeleteAttachment parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
            return new AccountPropertyDeleteAttachment(attachmentData);
        }

        @Override
        public void validateAttachment(ChildTransactionImpl transaction) throws NxtException.ValidationException {
            AccountPropertyDeleteAttachment attachment = (AccountPropertyDeleteAttachment)transaction.getAttachment();
            Account.AccountProperty accountProperty = Account.getProperty(attachment.getPropertyId());
            if (accountProperty == null) {
                throw new NxtException.NotCurrentlyValidException("No such property " + Long.toUnsignedString(attachment.getPropertyId()));
            }
            if (accountProperty.getRecipientId() != transaction.getSenderId() && accountProperty.getSetterId() != transaction.getSenderId()) {
                throw new NxtException.NotValidException("Account " + Long.toUnsignedString(transaction.getSenderId())
                        + " cannot delete property " + Long.toUnsignedString(attachment.getPropertyId()));
            }
            if (accountProperty.getRecipientId() != transaction.getRecipientId()) {
                throw new NxtException.NotValidException("Account property " + Long.toUnsignedString(attachment.getPropertyId())
                        + " does not belong to " + Long.toUnsignedString(transaction.getRecipientId()));
            }
            if (transaction.getAmount() != 0) {
                throw new NxtException.NotValidException("Account property transaction cannot be used to send NXT");
            }
            if (transaction.getRecipientId() == Genesis.CREATOR_ID) {
                throw new NxtException.NotValidException("Deleting Genesis account properties not allowed");
            }
        }

        @Override
        public void applyAttachment(ChildTransactionImpl transaction, Account senderAccount, Account recipientAccount) {
            AccountPropertyDeleteAttachment attachment = (AccountPropertyDeleteAttachment) transaction.getAttachment();
            senderAccount.deleteProperty(attachment.getPropertyId());
        }

        @Override
        public boolean canHaveRecipient() {
            return true;
        }

        @Override
        public boolean isPhasingSafe() {
            return true;
        }

    };

}
