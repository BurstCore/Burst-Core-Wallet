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
import nxt.account.Account;
import nxt.blockchain.ChildChain;
import nxt.blockchain.Transaction;
import nxt.db.DbClause;
import nxt.db.DbIterator;
import nxt.db.DbKey;
import nxt.db.EntityDbTable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class PhasingVoteHome {

    public static PhasingVoteHome forChain(ChildChain childChain) {
        if (childChain.getPhasingVoteHome() != null) {
            throw new IllegalStateException("already set");
        }
        return new PhasingVoteHome(childChain);
    }

    private final ChildChain childChain;
    private final DbKey.LinkKeyFactory<PhasingVote> phasingVoteDbKeyFactory;
    private final EntityDbTable<PhasingVote> phasingVoteTable;

    private PhasingVoteHome(ChildChain childChain) {
        this.childChain = childChain;
        this.phasingVoteDbKeyFactory = new DbKey.LinkKeyFactory<PhasingVote>("transaction_id", "voter_id") {
            @Override
            public DbKey newKey(PhasingVote vote) {
                return vote.dbKey;
            }
        };
        this.phasingVoteTable = new EntityDbTable<PhasingVote>(childChain.getSchemaTable("phasing_vote"), phasingVoteDbKeyFactory) {
            @Override
            protected PhasingVote load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
                return new PhasingVote(rs, dbKey);
            }
            @Override
            protected void save(Connection con, PhasingVote vote) throws SQLException {
                vote.save(con);
            }
        };
    }

    public DbIterator<PhasingVote> getVotes(long phasedTransactionId, int from, int to) {
        return phasingVoteTable.getManyBy(new DbClause.LongClause("transaction_id", phasedTransactionId), from, to);
    }

    public PhasingVote getVote(long phasedTransactionId, long voterId) {
        return phasingVoteTable.get(phasingVoteDbKeyFactory.newKey(phasedTransactionId, voterId));
    }

    public long getVoteCount(long phasedTransactionId) {
        return phasingVoteTable.getCount(new DbClause.LongClause("transaction_id", phasedTransactionId));
    }

    public void addVote(Transaction transaction, Account voter, long phasedTransactionId) {
        PhasingVote phasingVote = phasingVoteTable.get(phasingVoteDbKeyFactory.newKey(phasedTransactionId, voter.getId()));
        if (phasingVote == null) {
            phasingVote = new PhasingVote(transaction, voter, phasedTransactionId);
            phasingVoteTable.insert(phasingVote);
        }
    }

    public final class PhasingVote {

        private final long phasedTransactionId;
        private final long voterId;
        private final DbKey dbKey;
        private long voteId;

        private PhasingVote(Transaction transaction, Account voter, long phasedTransactionId) {
            this.phasedTransactionId = phasedTransactionId;
            this.voterId = voter.getId();
            this.dbKey = phasingVoteDbKeyFactory.newKey(this.phasedTransactionId, this.voterId);
            this.voteId = transaction.getId();
        }

        private PhasingVote(ResultSet rs, DbKey dbKey) throws SQLException {
            this.phasedTransactionId = rs.getLong("transaction_id");
            this.voterId = rs.getLong("voter_id");
            this.dbKey = dbKey;
            this.voteId = rs.getLong("vote_id");
        }

        public long getPhasedTransactionId() {
            return phasedTransactionId;
        }

        public long getVoterId() {
            return voterId;
        }

        public long getVoteId() {
            return voteId;
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO phasing_vote (vote_id, transaction_id, "
                    + "voter_id, height) VALUES (?, ?, ?, ?)")) {
                int i = 0;
                pstmt.setLong(++i, this.voteId);
                pstmt.setLong(++i, this.phasedTransactionId);
                pstmt.setLong(++i, this.voterId);
                pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
                pstmt.executeUpdate();
            }
        }
    }

}