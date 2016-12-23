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

import nxt.Nxt;
import nxt.blockchain.ChainTransactionId;
import nxt.blockchain.ChildChain;
import nxt.blockchain.ChildTransaction;
import nxt.blockchain.Transaction;
import nxt.crypto.HashFunction;
import nxt.db.DbClause;
import nxt.db.DbIterator;
import nxt.db.DbKey;
import nxt.db.DbUtils;
import nxt.db.DerivedDbTable;
import nxt.db.EntityDbTable;
import nxt.db.ValuesDbTable;
import nxt.util.Convert;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class PhasingPollHome {

    public static final Set<HashFunction> acceptedHashFunctions =
            Collections.unmodifiableSet(EnumSet.of(HashFunction.SHA256, HashFunction.RIPEMD160, HashFunction.RIPEMD160_SHA256));

    public static HashFunction getHashFunction(byte code) {
        try {
            HashFunction hashFunction = HashFunction.getHashFunction(code);
            if (acceptedHashFunctions.contains(hashFunction)) {
                return hashFunction;
            }
        } catch (IllegalArgumentException ignore) {
        }
        return null;
    }

    public static PhasingPollHome forChain(ChildChain childChain) {
        if (childChain.getPhasingPollHome() != null) {
            throw new IllegalStateException("already set");
        }
        return new PhasingPollHome(childChain);
    }

    private static final DerivedDbTable phasingPollFinishTable = new DerivedDbTable("public.phasing_poll_finish") {};

    private static final Comparator<ChildTransaction> finishingTransactionsComparator =
            Comparator.comparingInt(ChildTransaction::getHeight) //TODO: also sort by transaction.index?
                    .thenComparingLong(ChildTransaction::getId);

    public static List<? extends ChildTransaction> getFinishingTransactions(int height) {
        List<ChildTransaction> childTransactions = new ArrayList<>();
        try (Connection con = phasingPollFinishTable.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT full_hash, chain_id FROM phasing_poll_finish "
             + "WHERE finish_height = ?")) {
            pstmt.setInt(1, height);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    ChildChain childChain = ChildChain.getChildChain(rs.getInt("chain_id"));
                    Transaction childTransaction = childChain.getTransactionHome().findTransactionByFullHash(rs.getBytes("full_hash"));
                    childTransactions.add((ChildTransaction)childTransaction);
                }
                childTransactions.sort(finishingTransactionsComparator);
                return childTransactions;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    //The transaction may still have finished early, this only checks for finish_height not yet reached
    public static boolean hasUnfinishedPhasedTransaction(long transactionId) {
        try (Connection con = phasingPollFinishTable.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT transaction_id FROM phasing_poll_finish "
                     + "WHERE transaction_id = ? AND finish_height > ?")) {
            pstmt.setLong(1, transactionId);
            pstmt.setInt(2, Nxt.getBlockchain().getHeight());
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    private static final DbKey.HashKeyFactory<PhasingPoll> linkedTransactionDbKeyFactory = new DbKey.HashKeyFactory<PhasingPoll>("transaction_full_hash", "transaction_id") {
        @Override
        public DbKey newKey(PhasingPoll poll) {
            return poll.dbKey == null ? newKey(poll.hash, poll.id) : poll.dbKey;
        }
    };

    private static final ValuesDbTable<PhasingPoll, ChainTransactionId> linkedTransactionTable = new ValuesDbTable<PhasingPoll, ChainTransactionId>
            ("public.phasing_poll_linked_transaction", linkedTransactionDbKeyFactory) {
        @Override
        protected ChainTransactionId load(Connection con, ResultSet rs) throws SQLException {
            return new ChainTransactionId(rs.getInt("linked_chain_id"), rs.getBytes("linked_full_hash"));
        }
        @Override
        protected void save(Connection con, PhasingPoll poll, ChainTransactionId linkedTransaction) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO phasing_poll_linked_transaction (chain_id, transaction_id, transaction_full_hash, "
                    + "linked_chain_id, linked_full_hash, linked_transaction_id, height) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                int i = 0;
                pstmt.setInt(++i, poll.getPhasingPollHome().childChain.getId());
                pstmt.setLong(++i, poll.getId());
                pstmt.setBytes(++i, poll.getFullHash());
                pstmt.setInt(++i, linkedTransaction.getChainId());
                pstmt.setBytes(++i, linkedTransaction.getFullHash());
                pstmt.setLong(++i, linkedTransaction.getTransactionId());
                pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
                pstmt.executeUpdate();
            }
        }
    };

    public static List<? extends ChildTransaction> getLinkedPhasedTransactions(byte[] linkedTransactionFullHash) {
        try (Connection con = linkedTransactionTable.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT chain_id, transaction_full_hash FROM phasing_poll_linked_transaction " +
                     "WHERE linked_transaction_id = ? AND linked_full_hash = ?")) {
            int i = 0;
            pstmt.setLong(++i, Convert.fullHashToId(linkedTransactionFullHash));
            pstmt.setBytes(++i, linkedTransactionFullHash);
            List<ChildTransaction> transactions = new ArrayList<>();
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    ChildChain childChain = ChildChain.getChildChain(rs.getInt("chain_id"));
                    transactions.add((ChildTransaction)childChain.getTransactionHome().findTransactionByFullHash(rs.getBytes("transaction_full_hash")));
                }
            }
            return transactions;
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }


    private final ChildChain childChain;
    private final PhasingVoteHome phasingVoteHome;
    private final DbKey.HashKeyFactory<PhasingPoll> phasingPollDbKeyFactory;
    private final EntityDbTable<PhasingPoll> phasingPollTable;
    private final DbKey.HashKeyFactory<PhasingPoll> votersDbKeyFactory;
    private final ValuesDbTable<PhasingPoll, Long> votersTable;
    private final DbKey.HashKeyFactory<PhasingPollResult> resultDbKeyFactory;
    private final EntityDbTable<PhasingPollResult> resultTable;

    private PhasingPollHome(ChildChain childChain) {
        this.childChain = childChain;
        this.phasingVoteHome = childChain.getPhasingVoteHome();
        this.phasingPollDbKeyFactory = new DbKey.HashKeyFactory<PhasingPoll>("full_hash", "id") {
            @Override
            public DbKey newKey(PhasingPoll poll) {
                return poll.dbKey;
            }
        };
        this.phasingPollTable = new EntityDbTable<PhasingPoll>(childChain.getSchemaTable("phasing_poll"), phasingPollDbKeyFactory) {
            @Override
            protected PhasingPoll load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
                return new PhasingPoll(rs, dbKey);
            }
            @Override
            protected void save(Connection con, PhasingPoll poll) throws SQLException {
                poll.save(con);
            }
            @Override
            public void trim(int height) {
                super.trim(height);
                //TODO: investigate if old phasing_poll_result records can also be deleted
                try (Connection con = getConnection();
                     DbIterator<PhasingPoll> pollsToTrim = phasingPollTable.getManyBy(new DbClause.IntClause("finish_height", DbClause.Op.LT, height), 0, -1);
                     PreparedStatement pstmt1 = con.prepareStatement("DELETE FROM phasing_poll WHERE id = ? AND full_hash = ?");
                     PreparedStatement pstmt2 = con.prepareStatement("DELETE FROM phasing_poll_voter WHERE transaction_id = ? AND transaction_full_hash = ?");
                     PreparedStatement pstmt3 = con.prepareStatement("DELETE FROM phasing_vote WHERE transaction_id = ? AND transaction_full_hash = ?");
                     PreparedStatement pstmt4 = con.prepareStatement("DELETE FROM phasing_poll_linked_transaction WHERE transaction_id = ? AND transaction_full_hash = ?");
                     PreparedStatement pstmt5 = con.prepareStatement("DELETE FROM phasing_poll_finish WHERE transaction_id = ? AND full_hash = ?")) {
                    while (pollsToTrim.hasNext()) {
                        PhasingPoll poll = pollsToTrim.next();
                        long id = poll.getId();
                        byte[] hash = poll.getFullHash();
                        pstmt1.setLong(1, id);
                        pstmt1.setBytes(2, hash);
                        pstmt1.executeUpdate();
                        pstmt2.setLong(1, id);
                        pstmt2.setBytes(2, hash);
                        pstmt2.executeUpdate();
                        pstmt3.setLong(1, id);
                        pstmt3.setBytes(2, hash);
                        pstmt3.executeUpdate();
                        pstmt4.setLong(1, id);
                        pstmt4.setBytes(2, hash);
                        pstmt4.executeUpdate();
                        pstmt5.setLong(1, id);
                        pstmt5.setBytes(2, hash);
                        pstmt5.executeUpdate();
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e.toString(), e);
                }
            }
        };
        this.votersDbKeyFactory = new DbKey.HashKeyFactory<PhasingPoll>("transaction_full_hash", "transaction_id") {
            @Override
            public DbKey newKey(PhasingPoll poll) {
                return poll.dbKey == null ? newKey(poll.hash, poll.id) : poll.dbKey;
            }
        };
        this.votersTable = new ValuesDbTable<PhasingPoll, Long>(childChain.getSchemaTable("phasing_poll_voter"), votersDbKeyFactory) {
            @Override
            protected Long load(Connection con, ResultSet rs) throws SQLException {
                return rs.getLong("voter_id");
            }
            @Override
            protected void save(Connection con, PhasingPoll poll, Long accountId) throws SQLException {
                try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO phasing_poll_voter (transaction_id, transaction_full_hash, "
                        + "voter_id, height) VALUES (?, ?, ?, ?)")) {
                    int i = 0;
                    pstmt.setLong(++i, poll.getId());
                    pstmt.setBytes(++i, poll.getFullHash());
                    pstmt.setLong(++i, accountId);
                    pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
                    pstmt.executeUpdate();
                }
            }
        };
        this.resultDbKeyFactory = new DbKey.HashKeyFactory<PhasingPollResult>("full_hash", "id") {
            @Override
            public DbKey newKey(PhasingPollResult phasingPollResult) {
                return phasingPollResult.dbKey;
            }
        };
        this.resultTable = new EntityDbTable<PhasingPollResult>(childChain.getSchemaTable("phasing_poll_result"), resultDbKeyFactory) {
            @Override
            protected PhasingPollResult load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
                return new PhasingPollResult(rs, dbKey);
            }
            @Override
            protected void save(Connection con, PhasingPollResult phasingPollResult) throws SQLException {
                phasingPollResult.save(con);
            }
        };
    }

    public final class PhasingPollResult {

        private final long id;
        private final byte[] hash;
        private final DbKey dbKey;
        private final long result;
        private final boolean approved;
        private final int height;

        private PhasingPollResult(PhasingPoll poll, long result) {
            this.id = poll.getId();
            this.hash = poll.getFullHash();
            this.dbKey = resultDbKeyFactory.newKey(this.hash, this.id);
            this.result = result;
            this.approved = result >= poll.getQuorum();
            this.height = Nxt.getBlockchain().getHeight();
        }

        private PhasingPollResult(ResultSet rs, DbKey dbKey) throws SQLException {
            this.id = rs.getLong("id");
            this.hash = rs.getBytes("full_hash");
            this.dbKey = dbKey;
            this.result = rs.getLong("result");
            this.approved = rs.getBoolean("approved");
            this.height = rs.getInt("height");
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO phasing_poll_result (id, full_hash, "
                    + "result, approved, height) VALUES (?, ?, ?, ?, ?)")) {
                int i = 0;
                pstmt.setLong(++i, id);
                pstmt.setBytes(++i, hash);
                pstmt.setLong(++i, result);
                pstmt.setBoolean(++i, approved);
                pstmt.setInt(++i, height);
                pstmt.executeUpdate();
            }
        }

        public long getId() {
            return id;
        }

        public byte[] getFullHash() {
            return hash;
        }

        public long getResult() {
            return result;
        }

        public boolean isApproved() {
            return approved;
        }

        public int getHeight() {
            return height;
        }

        public ChildChain getChildChain() {
            return childChain;
        }

    }


    public PhasingPollResult getResult(byte[] fullHash) {
        return resultTable.get(resultDbKeyFactory.newKey(fullHash));
    }

    public PhasingPoll getPoll(byte[] fullHash) {
        return phasingPollTable.get(phasingPollDbKeyFactory.newKey(fullHash));
    }

    public DbIterator<? extends ChildTransaction> getVoterPhasedTransactions(long voterId, int from, int to) {
        Connection con = null;
        try {
            con = phasingPollTable.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT transaction.* "
                    + "FROM transaction, phasing_poll_voter, phasing_poll "
                    + "LEFT JOIN phasing_poll_result ON phasing_poll.id = phasing_poll_result.id "
                    + "AND phasing_poll.full_hash = phasing_poll_result.full_hash "
                    + "WHERE transaction.id = phasing_poll.id AND "
                    + "transaction.full_hash = phasing_poll.full_hash AND"
                    + "phasing_poll.finish_height > ? AND "
                    + "phasing_poll.id = phasing_poll_voter.transaction_id "
                    + "AND phasing_poll.full_hash = phasing_poll_voter.transaction_full_hash "
                    + "AND phasing_poll_voter.voter_id = ? "
                    + "AND phasing_poll_result.id IS NULL "
                    + "AND phasing_poll_result.full_hash IS NULL "
                    + "ORDER BY transaction.height DESC, transaction.transaction_index DESC "
                    + DbUtils.limitsClause(from, to));
            int i = 0;
            pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
            pstmt.setLong(++i, voterId);
            DbUtils.setLimits(++i, pstmt, from, to);

            return Nxt.getBlockchain().getTransactions(childChain, con, pstmt);
        } catch (SQLException e) {
            DbUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    public DbIterator<? extends ChildTransaction> getHoldingPhasedTransactions(long holdingId, VoteWeighting.VotingModel votingModel,
                                                                    long accountId, boolean withoutWhitelist, int from, int to) {
        Connection con = null;
        try {
            con = phasingPollTable.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT transaction.* " +
                    "FROM transaction, phasing_poll " +
                    "WHERE phasing_poll.holding_id = ? " +
                    "AND phasing_poll.voting_model = ? " +
                    "AND phasing_poll.id = transaction.id " +
                    "AND phasing_poll.full_hash = transaction.full_hash " +
                    "AND phasing_poll.finish_height > ? " +
                    (accountId != 0 ? "AND phasing_poll.account_id = ? " : "") +
                    (withoutWhitelist ? "AND phasing_poll.whitelist_size = 0 " : "") +
                    "ORDER BY transaction.height DESC, transaction.transaction_index DESC " +
                    DbUtils.limitsClause(from, to));
            int i = 0;
            pstmt.setLong(++i, holdingId);
            pstmt.setByte(++i, votingModel.getCode());
            pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
            if (accountId != 0) {
                pstmt.setLong(++i, accountId);
            }
            DbUtils.setLimits(++i, pstmt, from, to);

            return Nxt.getBlockchain().getTransactions(childChain, con, pstmt);
        } catch (SQLException e) {
            DbUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    public DbIterator<? extends ChildTransaction> getAccountPhasedTransactions(long accountId, int from, int to) {
        Connection con = null;
        try {
            con = phasingPollTable.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT transaction.* FROM transaction, phasing_poll " +
                    " LEFT JOIN phasing_poll_result ON phasing_poll.id = phasing_poll_result.id " +
                    " AND phasing_poll.full_hash = phasing_poll_result.full_hash " +
                    " WHERE phasing_poll.id = transaction.id AND (transaction.sender_id = ? OR transaction.recipient_id = ?) " +
                    " AND phasing_poll.full_hash = transaction.full_hash " +
                    " AND phasing_poll_result.id IS NULL AND phasing_poll_result.full_hash IS NULL " +
                    " AND phasing_poll.finish_height > ? ORDER BY transaction.height DESC, transaction.transaction_index DESC " +
                    DbUtils.limitsClause(from, to));
            int i = 0;
            pstmt.setLong(++i, accountId);
            pstmt.setLong(++i, accountId);
            pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
            DbUtils.setLimits(++i, pstmt, from, to);

            return Nxt.getBlockchain().getTransactions(childChain, con, pstmt);
        } catch (SQLException e) {
            DbUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    public int getAccountPhasedTransactionCount(long accountId) {
        try (Connection con = phasingPollTable.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT COUNT(*) FROM transaction, phasing_poll " +
                     " LEFT JOIN phasing_poll_result ON phasing_poll.id = phasing_poll_result.id " +
                     " AND phasing_poll.full_hash = phasing_poll_result.full_hash " +
                     " WHERE phasing_poll.id = transaction.id AND (transaction.sender_id = ? OR transaction.recipient_id = ?) " +
                     " AND phasing_poll.full_hash = transaction.full_hash " +
                     " AND phasing_poll_result.id IS NULL AND phasing_poll_result.full_hash IS NULL " +
                     " AND phasing_poll.finish_height > ?")) {
            int i = 0;
            pstmt.setLong(++i, accountId);
            pstmt.setLong(++i, accountId);
            pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
            try (ResultSet rs = pstmt.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public long getSenderPhasedTransactionFees(long accountId) {
        try (Connection con = phasingPollTable.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT SUM(transaction.fee) AS fees FROM transaction, phasing_poll " +
                     " LEFT JOIN phasing_poll_result ON phasing_poll.id = phasing_poll_result.id AND phasing_poll.full_hash = phasing_poll_result.full_hash " +
                     " WHERE phasing_poll.id = transaction.id AND transaction.sender_id = ? " +
                     " AND phasing_poll.full_hash = transaction.full_hash " +
                     " AND phasing_poll_result.id IS NULL AND phasing_poll_result.full_hash IS NULL " +
                     " AND phasing_poll.finish_height > ? ORDER BY transaction.height DESC, transaction.transaction_index DESC ")) {
            int i = 0;
            pstmt.setLong(++i, accountId);
            pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
            try (ResultSet rs = pstmt.executeQuery()) {
                rs.next();
                return rs.getLong("fees");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }


    void addPoll(Transaction transaction, PhasingAppendix appendix) {
        PhasingPoll poll = new PhasingPoll(transaction, appendix);
        phasingPollTable.insert(poll);
        long[] voters = poll.whitelist;
        if (voters.length > 0) {
            votersTable.insert(poll, Convert.toList(voters));
        }
        if (appendix.getLinkedTransactionsIds().size() > 0) {
            linkedTransactionTable.insert(poll, appendix.getLinkedTransactionsIds());
        }
    }


    public final class PhasingPoll extends AbstractPoll {

        private final byte[] hash;
        private final DbKey dbKey;
        private final long[] whitelist;
        private final long quorum;
        private final byte[] hashedSecret;
        private final byte algorithm;

        private PhasingPoll(Transaction transaction, PhasingAppendix appendix) {
            super(transaction.getId(), transaction.getSenderId(), appendix.getFinishHeight(), appendix.getVoteWeighting());
            this.hash = transaction.getFullHash();
            this.dbKey = phasingPollDbKeyFactory.newKey(this.hash, this.id);
            this.quorum = appendix.getQuorum();
            this.whitelist = appendix.getWhitelist();
            this.hashedSecret = appendix.getHashedSecret();
            this.algorithm = appendix.getAlgorithm();
        }

        private PhasingPoll(ResultSet rs, DbKey dbKey) throws SQLException {
            super(rs);
            this.hash = rs.getBytes("full_hash");
            this.dbKey = dbKey;
            this.quorum = rs.getLong("quorum");
            this.whitelist = rs.getByte("whitelist_size") == 0 ? Convert.EMPTY_LONG : Convert.toArray(votersTable.get(votersDbKeyFactory.newKey(this)));
            this.hashedSecret = rs.getBytes("hashed_secret");
            this.algorithm = rs.getByte("algorithm");
        }

        public ChildChain getChildChain() {
            return childChain;
        }

        void finish(long result) {
            PhasingPollResult phasingPollResult = new PhasingPollResult(this, result);
            resultTable.insert(phasingPollResult);
        }

        public long[] getWhitelist() {
            return whitelist;
        }

        public long getQuorum() {
            return quorum;
        }

        public byte[] getFullHash() {
            return hash;
        }

        public List<ChainTransactionId> getLinkedTransactions() {
            return linkedTransactionTable.get(linkedTransactionDbKeyFactory.newKey(this));
        }

        public byte[] getHashedSecret() {
            return hashedSecret;
        }

        public byte getAlgorithm() {
            return algorithm;
        }

        public PhasingPollHome getPhasingPollHome() {
            return PhasingPollHome.this;
        }

        boolean verifySecret(byte[] revealedSecret) {
            HashFunction hashFunction = getHashFunction(algorithm);
            return hashFunction != null && Arrays.equals(hashedSecret, hashFunction.hash(revealedSecret));
        }

        public long countVotes() {
            if (voteWeighting.getVotingModel() == VoteWeighting.VotingModel.NONE) {
                return 0;
            }
            int height = Math.min(this.finishHeight, Nxt.getBlockchain().getHeight());
            if (voteWeighting.getVotingModel() == VoteWeighting.VotingModel.TRANSACTION) {
                int count = 0;
                for (ChainTransactionId linkedTransaction : getLinkedTransactions()) {
                    if (linkedTransaction.getChain().getTransactionHome().hasTransactionByFullHash(linkedTransaction.getFullHash(), height)) {
                        count += 1;
                    }
                }
                return count;
            }
            if (voteWeighting.isBalanceIndependent()) {
                return phasingVoteHome.getVoteCount(this.hash);
            }
            VoteWeighting.VotingModel votingModel = voteWeighting.getVotingModel();
            long cumulativeWeight = 0;
            try (DbIterator<PhasingVoteHome.PhasingVote> votes = phasingVoteHome.getVotes(this.hash, 0, Integer.MAX_VALUE)) {
                for (PhasingVoteHome.PhasingVote vote : votes) {
                    cumulativeWeight += votingModel.calcWeight(voteWeighting, vote.getVoterId(), height);
                }
            }
            return cumulativeWeight;
        }

        public boolean allowEarlyFinish() {
            return voteWeighting.isBalanceIndependent() && (whitelist.length > 0 || voteWeighting.getVotingModel() != VoteWeighting.VotingModel.ACCOUNT);
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO phasing_poll (id, full_hash, account_id, "
                    + "finish_height, whitelist_size, voting_model, quorum, min_balance, holding_id, "
                    + "min_balance_model, hashed_secret, algorithm, height) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                int i = 0;
                pstmt.setLong(++i, id);
                pstmt.setBytes(++i, hash);
                pstmt.setLong(++i, accountId);
                pstmt.setInt(++i, finishHeight);
                pstmt.setByte(++i, (byte) whitelist.length);
                pstmt.setByte(++i, voteWeighting.getVotingModel().getCode());
                DbUtils.setLongZeroToNull(pstmt, ++i, quorum);
                DbUtils.setLongZeroToNull(pstmt, ++i, voteWeighting.getMinBalance());
                DbUtils.setLongZeroToNull(pstmt, ++i, voteWeighting.getHoldingId());
                pstmt.setByte(++i, voteWeighting.getMinBalanceModel().getCode());
                DbUtils.setBytes(pstmt, ++i, hashedSecret);
                pstmt.setByte(++i, algorithm);
                pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
                pstmt.executeUpdate();
            }
            try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO phasing_poll_finish (transaction_id, full_hash, chain_id, finish_height, height) "
                    + "VALUES (?, ?, ?, ?)")) {
                int i = 0;
                pstmt.setLong(++i, id);
                pstmt.setBytes(++i, hash);
                pstmt.setInt(++i, childChain.getId());
                pstmt.setInt(++i, finishHeight);
                pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
                pstmt.executeUpdate();
            }
        }
    }

}