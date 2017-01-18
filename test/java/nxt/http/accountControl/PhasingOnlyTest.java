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

package nxt.http.accountControl;

import nxt.BlockchainTest;
import nxt.Nxt;
import nxt.Tester;
import nxt.blockchain.ChildChain;
import nxt.http.APICall;
import nxt.http.APICall.Builder;
import nxt.util.Convert;
import nxt.util.Logger;
import nxt.voting.PhasingParams;
import nxt.voting.VoteWeighting.MinBalanceModel;
import nxt.voting.VoteWeighting.VotingModel;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class PhasingOnlyTest extends BlockchainTest {
    @Test
    public void testSetAndGet() throws Exception {
        
        assertNoPhasingOnlyControl();

        setPhasingOnlyControl(VotingModel.ACCOUNT, null, 1L, null, null, new long[] {BOB.getId()},
                10 * ChildChain.IGNIS.ONE_COIN, 5, 1440);

        assertPhasingOnly(new PhasingParams(VotingModel.ACCOUNT.getCode(), 0L, 1L, 0L, (byte)0, new long[] {BOB.getId()}),
                "{2=" + 10 * ChildChain.IGNIS.ONE_COIN + "}", 5, 1440);
    }
    
    @Test
    public void testAccountVoting() throws Exception {
        //all transactions must be approved either by BOB or CHUCK
        setPhasingOnlyControl(VotingModel.ACCOUNT, null, 1L, null, null, new long[] {BOB.getId(), CHUCK.getId()}, 0, 0, 0);

        Builder builder = new ACTestUtils.Builder("sendMoney", ALICE.getSecretPhrase())
            .recipient(BOB.getId())
            .param("amountNQT", ChildChain.IGNIS.ONE_COIN);
        
        //no phasing - block
        ACTestUtils.assertTransactionBlocked(builder);
        
      //correct phasing
        setTransactionPhasingParams(builder, 20, VotingModel.ACCOUNT, null, 1L, null, null, new long[] {BOB.getId(), CHUCK.getId()});
        ACTestUtils.assertTransactionSuccess(builder);
        
      //subset of the voters should also be blocked
        setTransactionPhasingParams(builder, 20, VotingModel.ACCOUNT, null, 1L, null, null, new long[] {BOB.getId()});
        ACTestUtils.assertTransactionBlocked(builder);
        
      //incorrect quorum - even if more restrictive, should also be blocked
        setTransactionPhasingParams(builder, 20, VotingModel.ACCOUNT, null, 2L, null, null, new long[] {BOB.getId(), CHUCK.getId()});
        ACTestUtils.assertTransactionBlocked(builder);
        
        //remove the phasing control
        builder = new ACTestUtils.Builder("setPhasingOnlyControl", ALICE.getSecretPhrase());
        
        setControlPhasingParams(builder, VotingModel.NONE, null, null, null, null, null, 0, 0, 0);
        
        setTransactionPhasingParams(builder, 3, VotingModel.ACCOUNT, null, 1L, null, null, new long[] {BOB.getId(), CHUCK.getId()});
        
        JSONObject removePhasingOnlyJSON = ACTestUtils.assertTransactionSuccess(builder);
        generateBlock();
        
        assertPhasingOnly(new PhasingParams(VotingModel.ACCOUNT.getCode(), 0L, 1L, 0L, (byte)0, new long[] {BOB.getId(), CHUCK.getId()}), "{}", 0, 0);

        //approve the remove
        builder = new ACTestUtils.Builder("approveTransaction", BOB.getSecretPhrase())
                .param("phasedTransaction", getPhasedTransaction(removePhasingOnlyJSON));
        ACTestUtils.assertTransactionSuccess(builder);
        
        generateBlock();

        assertNoPhasingOnlyControl();
    }

    @Test
    public void testExtraRestrictions() throws Exception {
        //all transactions must be approved either by BOB or CHUCK, total fees 5 NXT, min duration 4, max duration 100
        setPhasingOnlyControl(VotingModel.ACCOUNT, null, 1L, null, null, new long[] {BOB.getId(), CHUCK.getId()},
                5 * ChildChain.IGNIS.ONE_COIN, 4, 100);

        Builder builder = new ACTestUtils.Builder("sendMoney", ALICE.getSecretPhrase())
                .recipient(BOB.getId())
                .param("amountNQT", ChildChain.IGNIS.ONE_COIN)
                .feeNQT(7 * ChildChain.IGNIS.ONE_COIN);
        // fee too high
        setTransactionPhasingParams(builder, 20, VotingModel.ACCOUNT, null, 1L, null, null, new long[] {BOB.getId(), CHUCK.getId()});
        ACTestUtils.assertTransactionBlocked(builder);

        // fee at the limit
        builder.feeNQT(5 * ChildChain.IGNIS.ONE_COIN);
        JSONObject response = ACTestUtils.assertTransactionSuccess(builder);

        generateBlock();

        // not yet approved, another transaction at max fee should fail
        ACTestUtils.assertTransactionBlocked(builder);

        //approve
        Builder approveBuilder = new ACTestUtils.Builder("approveTransaction", BOB.getSecretPhrase())
                .param("phasedTransaction", getPhasedTransaction(response));
        ACTestUtils.assertTransactionSuccess(approveBuilder);
        generateBlock();

        //now can submit next transaction
        response = ACTestUtils.assertTransactionSuccess(builder);
        String fullHash = (String) response.get("fullHash");
        generateBlock();

        //approve
        approveBuilder.param("transactionFullHash", fullHash);
        ACTestUtils.assertTransactionSuccess(approveBuilder);
        generateBlock();

        //too long or too short periods should fail
        builder.param("phasingFinishHeight", Nxt.getBlockchain().getHeight() + 200);
        ACTestUtils.assertTransactionBlocked(builder);
        builder.param("phasingFinishHeight", Nxt.getBlockchain().getHeight() + 3);
        ACTestUtils.assertTransactionBlocked(builder);
        builder.param("phasingFinishHeight", Nxt.getBlockchain().getHeight() + 4);
        ACTestUtils.assertTransactionSuccess(builder);

    }

    @Test
    public void testRejectingPendingTransaction() throws Exception {

        Builder builder = new ACTestUtils.Builder("sendMoney", ALICE.getSecretPhrase())
            .recipient(BOB.getId())
            .param("amountNQT", ChildChain.IGNIS.ONE_COIN);
    
        setTransactionPhasingParams(builder, 4, VotingModel.ACCOUNT, null, 1L, null, null, new long[] {BOB.getId(), CHUCK.getId()});
        JSONObject sendMoneyJSON = ACTestUtils.assertTransactionSuccess(builder);
        generateBlock();
        
        builder = new ACTestUtils.Builder("setPhasingOnlyControl", ALICE.getSecretPhrase());
        
        setControlPhasingParams(builder, VotingModel.ACCOUNT, null, 1L, null, null, new long[] {DAVE.getId()}, 0, 0, 0);
        
        ACTestUtils.assertTransactionSuccess(builder);
        
        generateBlock();
        
        long balanceBeforeTransactionRejection = ACTestUtils.getAccountBalance(ALICE.getId(), "unconfirmedBalanceNQT");

        //approve the pending transaction
        builder = new ACTestUtils.Builder("approveTransaction", BOB.getSecretPhrase())
                .param("phasedTransaction", getPhasedTransaction(sendMoneyJSON));
        ACTestUtils.assertTransactionSuccess(builder);
        
        generateBlock();

        //the sendMoney finish height
        generateBlock();

        //Assert the unconfirmed balance is recovered
        Assert.assertEquals(balanceBeforeTransactionRejection + ChildChain.IGNIS.ONE_COIN,
                ACTestUtils.getAccountBalance(ALICE.getId(), "unconfirmedBalanceNQT"));
    }

    @Test
    public void testBalanceVoting() {
        setPhasingOnlyControl(VotingModel.COIN, "2", 100 * ChildChain.IGNIS.ONE_COIN, null, null, null, 0, 0, 0);

        Builder builder = new ACTestUtils.Builder("sendMoney", ALICE.getSecretPhrase())
            .recipient(BOB.getId())
            .param("amountNQT", ChildChain.IGNIS.ONE_COIN);

        //no phasing - block
        ACTestUtils.assertTransactionBlocked(builder);

        setTransactionPhasingParams(builder, 20, VotingModel.COIN, "2", 100 * ChildChain.IGNIS.ONE_COIN, null, null, new long[] {DAVE.getId()});
        ACTestUtils.assertTransactionBlocked(builder);

        setTransactionPhasingParams(builder, 20, VotingModel.ACCOUNT, null, 1L, null, null, new long[] {BOB.getId(), CHUCK.getId()});
        ACTestUtils.assertTransactionBlocked(builder);

        setTransactionPhasingParams(builder, 20, VotingModel.COIN, "2", 100 * ChildChain.IGNIS.ONE_COIN + 1, null, null, null);
        ACTestUtils.assertTransactionBlocked(builder);

        builder = new ACTestUtils.Builder("sendMoney", ALICE.getSecretPhrase())
            .recipient(BOB.getId())
            .param("amountNQT", ChildChain.IGNIS.ONE_COIN);

        setTransactionPhasingParams(builder, 20, VotingModel.COIN, "2", 100 * ChildChain.IGNIS.ONE_COIN, null, null, null);
        ACTestUtils.assertTransactionSuccess(builder);
    }

    @Test
    public void testAssetVoting() {
        Builder builder = new ACTestUtils.AssetBuilder(ALICE.getSecretPhrase(), "TestAsset");
        String assetId = Tester.responseToStringId(ACTestUtils.assertTransactionSuccess(builder));
        generateBlock();

        builder = new ACTestUtils.AssetBuilder(ALICE.getSecretPhrase(), "TestAsset2");
        String asset2Id = Tester.responseToStringId(ACTestUtils.assertTransactionSuccess(builder));
        generateBlock();

        setPhasingOnlyControl(VotingModel.ASSET, assetId, 100L, null, null, null, 0, 0, 0);

        builder = new ACTestUtils.Builder("sendMoney", ALICE.getSecretPhrase())
            .recipient(BOB.getId())
            .param("amountNQT", ChildChain.IGNIS.ONE_COIN);
        ACTestUtils.assertTransactionBlocked(builder);

        setTransactionPhasingParams(builder, 20, VotingModel.ASSET, asset2Id, 100L, null, null, null);
        ACTestUtils.assertTransactionBlocked(builder);

        setTransactionPhasingParams(builder, 20, VotingModel.ASSET, assetId, 100L, null, null, null);
        ACTestUtils.assertTransactionSuccess(builder);
    }

    @Test
    public void testCurrencyVoting() {
        Builder builder = new ACTestUtils.CurrencyBuilder().naming("testa", "TESTA", "Test AC");
        String currencyId = Tester.responseToStringId(ACTestUtils.assertTransactionSuccess(builder));
        generateBlock();

        builder = new ACTestUtils.CurrencyBuilder().naming("testb", "TESTB", "Test AC");
        String currency2Id = Tester.responseToStringId(ACTestUtils.assertTransactionSuccess(builder));
        generateBlock();

        setPhasingOnlyControl(VotingModel.CURRENCY, currencyId, 100L, null, null, null, 0, 0, 0);

        builder = new ACTestUtils.Builder("sendMoney", ALICE.getSecretPhrase())
            .recipient(BOB.getId())
            .param("amountNQT", ChildChain.IGNIS.ONE_COIN);
        ACTestUtils.assertTransactionBlocked(builder);

        setTransactionPhasingParams(builder, 20, VotingModel.CURRENCY, currency2Id, 100L, null, null, null);
        ACTestUtils.assertTransactionBlocked(builder);

        setTransactionPhasingParams(builder, 20, VotingModel.CURRENCY, currencyId, 100L, null, null, null);
        ACTestUtils.assertTransactionSuccess(builder);
    }

    private void assertNoPhasingOnlyControl() {
        Builder builder = new APICall.Builder("getPhasingOnlyControl")
            .param("account", Long.toUnsignedString(ALICE.getId()));

        JSONObject response = builder.build().invoke();
        Assert.assertTrue(response.isEmpty());
    }


    private void assertPhasingOnly(PhasingParams expected, String maxFees, int minDuration, int maxDuration) {
        Builder builder = new APICall.Builder("getPhasingOnlyControl")
            .param("account", Long.toUnsignedString(ALICE.getId()));

        JSONObject response = builder.build().invoke();
        Logger.logMessage("getPhasingOnlyControl response: " + response.toJSONString());
        Assert.assertEquals(expected.getVoteWeighting().getVotingModel().getCode(), ((Long) response.get("votingModel")).byteValue());
        Assert.assertEquals(expected.getQuorum(), Convert.parseLong(response.get("quorum")));
        Assert.assertEquals(expected.getWhitelist().length, ((JSONArray) response.get("whitelist")).size());
        Assert.assertEquals(expected.getVoteWeighting().getHoldingId(), Convert.parseUnsignedLong((String)response.get("holding")));
        Assert.assertEquals(expected.getVoteWeighting().getMinBalance(), Convert.parseLong(response.get("minBalance")));
        Assert.assertEquals(expected.getVoteWeighting().getMinBalanceModel().getCode(), ((Long) response.get("minBalanceModel")).byteValue());
        Assert.assertEquals(maxFees, response.get("maxFees"));
        Assert.assertEquals(minDuration, ((Long)response.get("minDuration")).shortValue());
        Assert.assertEquals(maxDuration, ((Long)response.get("maxDuration")).shortValue());
    }

    private void setPhasingOnlyControl(VotingModel votingModel, String holdingId, Long quorum,
                                       Long minBalance, MinBalanceModel minBalanceModel, long[] whitelist,
                                       long maxFees, int minDuration, int maxDuration) {

        Builder builder = new ACTestUtils.Builder("setPhasingOnlyControl", ALICE.getSecretPhrase());

        setControlPhasingParams(builder, votingModel, holdingId, quorum,
                minBalance, minBalanceModel, whitelist, maxFees, minDuration, maxDuration);

        APICall apiCall = builder.build();
        JSONObject response = apiCall.invoke();
        Logger.logMessage("setPhasingOnlyControl response: " + response.toJSONString());

        String result = (String) response.get("fullHash");
        Assert.assertNotNull(result);

        generateBlock();
    }

    private void setControlPhasingParams(Builder builder,
                                         VotingModel votingModel, String holdingId, Long quorum,
                                         Long minBalance, MinBalanceModel minBalanceModel, long[] whitelist,
                                         long maxFees, int minDuration, int maxDuration) {
        if (votingModel != null) {
            builder.param("controlVotingModel", votingModel.getCode());
        }

        if (holdingId != null) {
            builder.param("controlHolding", holdingId);
        }

        if (quorum != null) {
            builder.param("controlQuorum", quorum);
        }

        if (minBalance != null) {
            builder.param("controlMinBalance", minBalance);
        }

        if (minBalanceModel != null) {
            builder.param("controlMinBalanceModel", minBalanceModel.getCode());
        }

        if (whitelist != null) {
            builder.param("controlWhitelisted", Arrays.stream(whitelist).mapToObj(Long::toUnsignedString).toArray(String[]::new));
        }

        if (maxFees > 0) {
            builder.param("controlMaxFees", ChildChain.IGNIS.getId() + ":" + maxFees);
        }

        if (minDuration > 0) {
            builder.param("controlMinDuration", minDuration);
        }

        if (maxDuration > 0) {
            builder.param("controlMaxDuration", maxDuration);
        }

    }

    private void setTransactionPhasingParams(Builder builder, int finishAfter, VotingModel votingModel, String holdingId, Long quorum,
            Long minBalance, MinBalanceModel minBalanceModel, long[] whitelist) {

        builder.param("phased", "true");

        builder.param("phasingVotingModel", votingModel.getCode());

        builder.param("phasingFinishHeight", Nxt.getBlockchain().getHeight() + finishAfter);

        builder.param("phasingHolding", holdingId);

        if (quorum != null) {
            builder.param("phasingQuorum", quorum);
        }

        if (minBalance != null) {
            builder.param("phasingMinBalance", minBalance);
        }

        if (minBalanceModel != null) {
            builder.param("phasingMinBalanceModel", minBalanceModel.getCode());
        }

        if (whitelist != null) {
            builder.param("phasingWhitelisted", Arrays.stream(whitelist).mapToObj(Long::toUnsignedString).toArray(String[]::new));
        }
    }

    private String getPhasedTransaction(JSONObject sendMoneyJSON) {
        return ((JSONObject)sendMoneyJSON.get("transactionJSON")).get("chain") + ":" + sendMoneyJSON.get("fullHash");
    }
}
