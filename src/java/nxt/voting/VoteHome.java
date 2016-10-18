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

import nxt.blockchain.ChildChain;
import nxt.Nxt;
import nxt.blockchain.Transaction;
import nxt.db.DbClause;
import nxt.db.DbIterator;
import nxt.db.DbKey;
import nxt.db.EntityDbTable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class VoteHome {

    public static VoteHome forChain(ChildChain childChain) {
        if (childChain.getVoteHome() != null) {
            throw new IllegalStateException("already set");
        }
        return new VoteHome(childChain);
    }

    private final ChildChain childChain;
    private final PollHome pollHome;
    private final DbKey.LongKeyFactory<Vote> voteDbKeyFactory;
    private final EntityDbTable<Vote> voteTable;

    private VoteHome(ChildChain childChain) {
        this.childChain = childChain;
        this.pollHome = childChain.getPollHome();
        this.voteDbKeyFactory = new DbKey.LongKeyFactory<Vote>("id") {
            @Override
            public DbKey newKey(Vote vote) {
                return vote.dbKey;
            }
        };
        this.voteTable = new EntityDbTable<Vote>(childChain.getSchemaTable("vote"), voteDbKeyFactory) {
            @Override
            protected Vote load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
                return new Vote(rs, dbKey);
            }
            @Override
            protected void save(Connection con, Vote vote) throws SQLException {
                vote.save(con);
            }
            @Override
            public void trim(int height) {
                super.trim(height);
                try (Connection con = voteTable.getConnection();
                     DbIterator<PollHome.Poll> polls = pollHome.getPollsFinishingAtOrBefore(height, 0, Integer.MAX_VALUE);
                     PreparedStatement pstmt = con.prepareStatement("DELETE FROM vote WHERE poll_id = ?")) {
                    for (PollHome.Poll poll : polls) {
                        pstmt.setLong(1, poll.getId());
                        pstmt.executeUpdate();
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e.toString(), e);
                }
            }
        };
    }

    public int getCount() {
        return voteTable.getCount();
    }

    public Vote getVote(long id) {
        return voteTable.get(voteDbKeyFactory.newKey(id));
    }

    public DbIterator<Vote> getVotes(long pollId, int from, int to) {
        return voteTable.getManyBy(new DbClause.LongClause("poll_id", pollId), from, to);
    }

    public Vote getVote(long pollId, long voterId) {
        DbClause clause = new DbClause.LongClause("poll_id", pollId).and(new DbClause.LongClause("voter_id", voterId));
        return voteTable.getBy(clause);
    }

    public Vote addVote(Transaction transaction, VoteCastingAttachment attachment) {
        Vote vote = new Vote(transaction, attachment);
        voteTable.insert(vote);
        return vote;
    }


    public final class Vote {

        private final long id;
        private final DbKey dbKey;
        private final long pollId;
        private final long voterId;
        private final byte[] voteBytes;

        private Vote(Transaction transaction, VoteCastingAttachment attachment) {
            this.id = transaction.getId();
            this.dbKey = voteDbKeyFactory.newKey(this.id);
            this.pollId = attachment.getPollId();
            this.voterId = transaction.getSenderId();
            this.voteBytes = attachment.getPollVote();
        }

        private Vote(ResultSet rs, DbKey dbKey) throws SQLException {
            this.id = rs.getLong("id");
            this.dbKey = dbKey;
            this.pollId = rs.getLong("poll_id");
            this.voterId = rs.getLong("voter_id");
            this.voteBytes = rs.getBytes("vote_bytes");
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO vote (id, poll_id, voter_id, "
                    + "vote_bytes, height) VALUES (?, ?, ?, ?, ?)")) {
                int i = 0;
                pstmt.setLong(++i, this.id);
                pstmt.setLong(++i, this.pollId);
                pstmt.setLong(++i, this.voterId);
                pstmt.setBytes(++i, this.voteBytes);
                pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
                pstmt.executeUpdate();
            }
        }

        public long getId() {
            return id;
        }

        public long getPollId() {
            return pollId;
        }

        public long getVoterId() {
            return voterId;
        }

        public byte[] getVoteBytes() {
            return voteBytes;
        }
    }

}