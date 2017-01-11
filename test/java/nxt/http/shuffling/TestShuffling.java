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

package nxt.http.shuffling;

import nxt.BlockchainTest;
import nxt.Nxt;
import nxt.blockchain.ChildChain;
import nxt.shuffling.ShufflingStage;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import static nxt.http.shuffling.ShufflingUtil.ALICE_RECIPIENT;
import static nxt.http.shuffling.ShufflingUtil.BOB_RECIPIENT;
import static nxt.http.shuffling.ShufflingUtil.CHUCK_RECIPIENT;
import static nxt.http.shuffling.ShufflingUtil.DAVE_RECIPIENT;
import static nxt.http.shuffling.ShufflingUtil.broadcast;
import static nxt.http.shuffling.ShufflingUtil.cancel;
import static nxt.http.shuffling.ShufflingUtil.create;
import static nxt.http.shuffling.ShufflingUtil.createAssetShuffling;
import static nxt.http.shuffling.ShufflingUtil.createCurrencyShuffling;
import static nxt.http.shuffling.ShufflingUtil.defaultHoldingShufflingAmount;
import static nxt.http.shuffling.ShufflingUtil.defaultShufflingAmount;
import static nxt.http.shuffling.ShufflingUtil.getShuffling;
import static nxt.http.shuffling.ShufflingUtil.getShufflingParticipants;
import static nxt.http.shuffling.ShufflingUtil.process;
import static nxt.http.shuffling.ShufflingUtil.register;
import static nxt.http.shuffling.ShufflingUtil.shufflingAsset;
import static nxt.http.shuffling.ShufflingUtil.shufflingCurrency;
import static nxt.http.shuffling.ShufflingUtil.verify;


public class TestShuffling extends BlockchainTest {

    @Test
    public void successfulShuffling() {
        JSONObject shufflingCreate = create(ALICE);
        String shufflingId = (String)shufflingCreate.get("transaction");
        String shufflingFullHash = (String)shufflingCreate.get("fullHash");
        generateBlock();
        register(shufflingFullHash, BOB);
        generateBlock();
        register(shufflingFullHash, CHUCK);
        generateBlock();
        register(shufflingFullHash, DAVE);
        generateBlock();

        Assert.assertEquals(-ChildChain.IGNIS.ONE_COIN, ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-(defaultShufflingAmount + ChildChain.IGNIS.ONE_COIN), ALICE.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-ChildChain.IGNIS.ONE_COIN, BOB.getFxtBalanceDiff());
        Assert.assertEquals(-(defaultShufflingAmount + ChildChain.IGNIS.ONE_COIN), BOB.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-ChildChain.IGNIS.ONE_COIN, CHUCK.getFxtBalanceDiff());
        Assert.assertEquals(-(defaultShufflingAmount + ChildChain.IGNIS.ONE_COIN), CHUCK.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-ChildChain.IGNIS.ONE_COIN, DAVE.getFxtBalanceDiff());
        Assert.assertEquals(-(defaultShufflingAmount + ChildChain.IGNIS.ONE_COIN), DAVE.getFxtUnconfirmedBalanceDiff());

        JSONObject getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.PROCESSING.getCode(), getShufflingResponse.get("stage"));

        JSONObject getParticipantsResponse = getShufflingParticipants(shufflingId);
        JSONArray participants = (JSONArray)getParticipantsResponse.get("participants");
        Assert.assertEquals(4, participants.size());
        String shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(ALICE.getId()), shufflingAssignee);

        process(shufflingId, ALICE, ALICE_RECIPIENT);
        generateBlock();
        process(shufflingId, BOB, BOB_RECIPIENT);
        generateBlock();
        process(shufflingId, CHUCK, CHUCK_RECIPIENT);
        generateBlock();
        process(shufflingId, DAVE, DAVE_RECIPIENT);
        generateBlock();

        getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.VERIFICATION.getCode(), getShufflingResponse.get("stage"));
        String shufflingStateHash = (String)getShufflingResponse.get("shufflingStateHash");

        verify(shufflingId, ALICE, shufflingStateHash);
        verify(shufflingId, BOB, shufflingStateHash);
        verify(shufflingId, CHUCK, shufflingStateHash);
        generateBlock();
        getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.DONE.getCode(), getShufflingResponse.get("stage"));
        shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertNull(shufflingAssignee);

        Assert.assertEquals(-(defaultShufflingAmount + 12 * ChildChain.IGNIS.ONE_COIN), ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-(defaultShufflingAmount + 12 * ChildChain.IGNIS.ONE_COIN), ALICE.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-(defaultShufflingAmount + 12 * ChildChain.IGNIS.ONE_COIN), BOB.getFxtBalanceDiff());
        Assert.assertEquals(-(defaultShufflingAmount + 12 * ChildChain.IGNIS.ONE_COIN), BOB.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-(defaultShufflingAmount + 12 * ChildChain.IGNIS.ONE_COIN), CHUCK.getFxtBalanceDiff());
        Assert.assertEquals(-(defaultShufflingAmount + 12 * ChildChain.IGNIS.ONE_COIN), CHUCK.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-(defaultShufflingAmount + 12 * ChildChain.IGNIS.ONE_COIN), DAVE.getFxtBalanceDiff());
        Assert.assertEquals(-(defaultShufflingAmount + 12 * ChildChain.IGNIS.ONE_COIN), DAVE.getFxtUnconfirmedBalanceDiff());

        Assert.assertEquals(defaultShufflingAmount, ALICE_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(defaultShufflingAmount, ALICE_RECIPIENT.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(defaultShufflingAmount, BOB_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(defaultShufflingAmount, BOB_RECIPIENT.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(defaultShufflingAmount, CHUCK_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(defaultShufflingAmount, CHUCK_RECIPIENT.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(defaultShufflingAmount, DAVE_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(defaultShufflingAmount, DAVE_RECIPIENT.getFxtUnconfirmedBalanceDiff());

        Assert.assertEquals(48 * ChildChain.IGNIS.ONE_COIN, FORGY.getFxtBalanceDiff());
        Assert.assertEquals(48 * ChildChain.IGNIS.ONE_COIN, FORGY.getFxtUnconfirmedBalanceDiff());

    }

    @Test
    public void successfulAssetShuffling() {
        JSONObject shufflingCreate = createAssetShuffling(ALICE);
        String shufflingId = (String)shufflingCreate.get("transaction");
        String shufflingFullHash = (String)shufflingCreate.get("fullHash");
        generateBlock();
        register(shufflingFullHash, BOB);
        generateBlock();
        register(shufflingFullHash, CHUCK);
        generateBlock();
        register(shufflingFullHash, DAVE);
        generateBlock();

        JSONObject getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.PROCESSING.getCode(), getShufflingResponse.get("stage"));

        JSONObject getParticipantsResponse = getShufflingParticipants(shufflingId);
        JSONArray participants = (JSONArray)getParticipantsResponse.get("participants");
        Assert.assertEquals(4, participants.size());
        String shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(ALICE.getId()), shufflingAssignee);

        process(shufflingId, ALICE, ALICE_RECIPIENT);
        generateBlock();
        process(shufflingId, BOB, BOB_RECIPIENT);
        generateBlock();
        process(shufflingId, CHUCK, CHUCK_RECIPIENT);
        generateBlock();
        process(shufflingId, DAVE, DAVE_RECIPIENT);
        generateBlock();

        getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.VERIFICATION.getCode(), getShufflingResponse.get("stage"));
        String shufflingStateHash = (String)getShufflingResponse.get("shufflingStateHash");

        verify(shufflingId, ALICE, shufflingStateHash);
        verify(shufflingId, BOB, shufflingStateHash);
        verify(shufflingId, CHUCK, shufflingStateHash);
        generateBlock();
        getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.DONE.getCode(), getShufflingResponse.get("stage"));
        shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertNull(shufflingAssignee);

        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 12 * ChildChain.IGNIS.ONE_COIN), ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 12 * ChildChain.IGNIS.ONE_COIN), ALICE.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 12 * ChildChain.IGNIS.ONE_COIN), BOB.getFxtBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 12 * ChildChain.IGNIS.ONE_COIN), BOB.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 12 * ChildChain.IGNIS.ONE_COIN), CHUCK.getFxtBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 12 * ChildChain.IGNIS.ONE_COIN), CHUCK.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 12 * ChildChain.IGNIS.ONE_COIN), DAVE.getFxtBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 12 * ChildChain.IGNIS.ONE_COIN), DAVE.getFxtUnconfirmedBalanceDiff());

        Assert.assertEquals(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, ALICE_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, ALICE_RECIPIENT.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, BOB_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, BOB_RECIPIENT.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, CHUCK_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, CHUCK_RECIPIENT.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, DAVE_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, DAVE_RECIPIENT.getFxtUnconfirmedBalanceDiff());

        Assert.assertEquals(48 * ChildChain.IGNIS.ONE_COIN, FORGY.getFxtBalanceDiff());
        Assert.assertEquals(48 * ChildChain.IGNIS.ONE_COIN, FORGY.getFxtUnconfirmedBalanceDiff());

        Assert.assertEquals(defaultHoldingShufflingAmount, ALICE_RECIPIENT.getAssetQuantityDiff(shufflingAsset));
        Assert.assertEquals(defaultHoldingShufflingAmount, ALICE_RECIPIENT.getUnconfirmedAssetQuantityDiff(shufflingAsset));
        Assert.assertEquals(defaultHoldingShufflingAmount, BOB_RECIPIENT.getAssetQuantityDiff(shufflingAsset));
        Assert.assertEquals(defaultHoldingShufflingAmount, BOB_RECIPIENT.getUnconfirmedAssetQuantityDiff(shufflingAsset));
        Assert.assertEquals(defaultHoldingShufflingAmount, CHUCK_RECIPIENT.getAssetQuantityDiff(shufflingAsset));
        Assert.assertEquals(defaultHoldingShufflingAmount, CHUCK_RECIPIENT.getUnconfirmedAssetQuantityDiff(shufflingAsset));
        Assert.assertEquals(defaultHoldingShufflingAmount, DAVE_RECIPIENT.getAssetQuantityDiff(shufflingAsset));
        Assert.assertEquals(defaultHoldingShufflingAmount, DAVE_RECIPIENT.getUnconfirmedAssetQuantityDiff(shufflingAsset));

        Assert.assertEquals(-defaultHoldingShufflingAmount, ALICE.getAssetQuantityDiff(shufflingAsset));
        Assert.assertEquals(-defaultHoldingShufflingAmount, ALICE.getUnconfirmedAssetQuantityDiff(shufflingAsset));
        Assert.assertEquals(-defaultHoldingShufflingAmount, BOB.getAssetQuantityDiff(shufflingAsset));
        Assert.assertEquals(-defaultHoldingShufflingAmount, BOB.getUnconfirmedAssetQuantityDiff(shufflingAsset));
        Assert.assertEquals(-defaultHoldingShufflingAmount, CHUCK.getAssetQuantityDiff(shufflingAsset));
        Assert.assertEquals(-defaultHoldingShufflingAmount, CHUCK.getUnconfirmedAssetQuantityDiff(shufflingAsset));
        Assert.assertEquals(-defaultHoldingShufflingAmount, DAVE.getAssetQuantityDiff(shufflingAsset));
        Assert.assertEquals(-defaultHoldingShufflingAmount, DAVE.getUnconfirmedAssetQuantityDiff(shufflingAsset));

    }

    @Test
    public void successfulCurrencyShuffling() {
        JSONObject shufflingCreate = createCurrencyShuffling(ALICE);
        String shufflingId = (String)shufflingCreate.get("transaction");
        String shufflingFullHash = (String)shufflingCreate.get("fullHash");
        generateBlock();
        register(shufflingFullHash, BOB);
        generateBlock();
        register(shufflingFullHash, CHUCK);
        generateBlock();
        register(shufflingFullHash, DAVE);
        generateBlock();

        JSONObject getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.PROCESSING.getCode(), getShufflingResponse.get("stage"));

        JSONObject getParticipantsResponse = getShufflingParticipants(shufflingId);
        JSONArray participants = (JSONArray)getParticipantsResponse.get("participants");
        Assert.assertEquals(4, participants.size());
        String shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(ALICE.getId()), shufflingAssignee);

        process(shufflingId, ALICE, ALICE_RECIPIENT);
        generateBlock();
        process(shufflingId, BOB, BOB_RECIPIENT);
        generateBlock();
        process(shufflingId, CHUCK, CHUCK_RECIPIENT);
        generateBlock();
        process(shufflingId, DAVE, DAVE_RECIPIENT);
        generateBlock();

        getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.VERIFICATION.getCode(), getShufflingResponse.get("stage"));
        String shufflingStateHash = (String)getShufflingResponse.get("shufflingStateHash");

        verify(shufflingId, ALICE, shufflingStateHash);
        verify(shufflingId, BOB, shufflingStateHash);
        verify(shufflingId, CHUCK, shufflingStateHash);
        generateBlock();
        getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.DONE.getCode(), getShufflingResponse.get("stage"));
        shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertNull(shufflingAssignee);

        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 12 * ChildChain.IGNIS.ONE_COIN), ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 12 * ChildChain.IGNIS.ONE_COIN), ALICE.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 12 * ChildChain.IGNIS.ONE_COIN), BOB.getFxtBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 12 * ChildChain.IGNIS.ONE_COIN), BOB.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 12 * ChildChain.IGNIS.ONE_COIN), CHUCK.getFxtBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 12 * ChildChain.IGNIS.ONE_COIN), CHUCK.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 12 * ChildChain.IGNIS.ONE_COIN), DAVE.getFxtBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 12 * ChildChain.IGNIS.ONE_COIN), DAVE.getFxtUnconfirmedBalanceDiff());

        Assert.assertEquals(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, ALICE_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, ALICE_RECIPIENT.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, BOB_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, BOB_RECIPIENT.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, CHUCK_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, CHUCK_RECIPIENT.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, DAVE_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, DAVE_RECIPIENT.getFxtUnconfirmedBalanceDiff());

        Assert.assertEquals(48 * ChildChain.IGNIS.ONE_COIN, FORGY.getFxtBalanceDiff());
        Assert.assertEquals(48 * ChildChain.IGNIS.ONE_COIN, FORGY.getFxtUnconfirmedBalanceDiff());

        Assert.assertEquals(defaultHoldingShufflingAmount, ALICE_RECIPIENT.getCurrencyUnitsDiff(shufflingCurrency));
        Assert.assertEquals(defaultHoldingShufflingAmount, ALICE_RECIPIENT.getUnconfirmedCurrencyUnitsDiff(shufflingCurrency));
        Assert.assertEquals(defaultHoldingShufflingAmount, BOB_RECIPIENT.getCurrencyUnitsDiff(shufflingCurrency));
        Assert.assertEquals(defaultHoldingShufflingAmount, BOB_RECIPIENT.getUnconfirmedCurrencyUnitsDiff(shufflingCurrency));
        Assert.assertEquals(defaultHoldingShufflingAmount, CHUCK_RECIPIENT.getCurrencyUnitsDiff(shufflingCurrency));
        Assert.assertEquals(defaultHoldingShufflingAmount, CHUCK_RECIPIENT.getUnconfirmedCurrencyUnitsDiff(shufflingCurrency));
        Assert.assertEquals(defaultHoldingShufflingAmount, DAVE_RECIPIENT.getCurrencyUnitsDiff(shufflingCurrency));
        Assert.assertEquals(defaultHoldingShufflingAmount, DAVE_RECIPIENT.getUnconfirmedCurrencyUnitsDiff(shufflingCurrency));

        Assert.assertEquals(-defaultHoldingShufflingAmount, ALICE.getCurrencyUnitsDiff(shufflingCurrency));
        Assert.assertEquals(-defaultHoldingShufflingAmount, ALICE.getUnconfirmedCurrencyUnitsDiff(shufflingCurrency));
        Assert.assertEquals(-defaultHoldingShufflingAmount, BOB.getCurrencyUnitsDiff(shufflingCurrency));
        Assert.assertEquals(-defaultHoldingShufflingAmount, BOB.getUnconfirmedCurrencyUnitsDiff(shufflingCurrency));
        Assert.assertEquals(-defaultHoldingShufflingAmount, CHUCK.getCurrencyUnitsDiff(shufflingCurrency));
        Assert.assertEquals(-defaultHoldingShufflingAmount, CHUCK.getUnconfirmedCurrencyUnitsDiff(shufflingCurrency));
        Assert.assertEquals(-defaultHoldingShufflingAmount, DAVE.getCurrencyUnitsDiff(shufflingCurrency));
        Assert.assertEquals(-defaultHoldingShufflingAmount, DAVE.getUnconfirmedCurrencyUnitsDiff(shufflingCurrency));

    }

    @Test
    public void registrationNotFinished() {
        JSONObject shufflingCreate = create(ALICE);
        String shufflingId = (String)shufflingCreate.get("transaction");
        String shufflingFullHash = (String)shufflingCreate.get("fullHash");
        generateBlock();
        register(shufflingFullHash, BOB);
        for (int i = 0; i < 9; i++) {
            generateBlock();
        }

        JSONObject getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.CANCELLED.getCode(), getShufflingResponse.get("stage"));

        JSONObject getParticipantsResponse = getShufflingParticipants(shufflingId);
        JSONArray participants = (JSONArray)getParticipantsResponse.get("participants");
        Assert.assertEquals(2, participants.size());
        String shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertNull(shufflingAssignee);

        Assert.assertEquals(-ChildChain.IGNIS.ONE_COIN, ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-ChildChain.IGNIS.ONE_COIN, ALICE.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-ChildChain.IGNIS.ONE_COIN, BOB.getFxtBalanceDiff());
        Assert.assertEquals(-ChildChain.IGNIS.ONE_COIN, BOB.getFxtUnconfirmedBalanceDiff());

        Assert.assertNull(ALICE_RECIPIENT.getAccount());
        Assert.assertNull(BOB_RECIPIENT.getAccount());

        Assert.assertEquals(2 * ChildChain.IGNIS.ONE_COIN, FORGY.getFxtBalanceDiff());
        Assert.assertEquals(2 * ChildChain.IGNIS.ONE_COIN, FORGY.getFxtUnconfirmedBalanceDiff());

    }

    @Test
    public void registrationNotFinishedAsset() {
        JSONObject shufflingCreate = createAssetShuffling(ALICE);
        String shufflingId = (String)shufflingCreate.get("transaction");
        String shufflingFullHash = (String)shufflingCreate.get("fullHash");
        generateBlock();
        register(shufflingFullHash, BOB);
        for (int i = 0; i < 9; i++) {
            generateBlock();
        }

        JSONObject getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.CANCELLED.getCode(), getShufflingResponse.get("stage"));

        JSONObject getParticipantsResponse = getShufflingParticipants(shufflingId);
        JSONArray participants = (JSONArray)getParticipantsResponse.get("participants");
        Assert.assertEquals(2, participants.size());
        String shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertNull(shufflingAssignee);

        Assert.assertEquals(-ChildChain.IGNIS.ONE_COIN, ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-ChildChain.IGNIS.ONE_COIN, ALICE.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-ChildChain.IGNIS.ONE_COIN, BOB.getFxtBalanceDiff());
        Assert.assertEquals(-ChildChain.IGNIS.ONE_COIN, BOB.getFxtUnconfirmedBalanceDiff());

        Assert.assertNull(ALICE_RECIPIENT.getAccount());
        Assert.assertNull(BOB_RECIPIENT.getAccount());

        Assert.assertEquals(0, ALICE.getAssetQuantityDiff(shufflingAsset));
        Assert.assertEquals(0, ALICE.getUnconfirmedAssetQuantityDiff(shufflingAsset));
        Assert.assertEquals(0, BOB.getAssetQuantityDiff(shufflingAsset));
        Assert.assertEquals(0, BOB.getUnconfirmedAssetQuantityDiff(shufflingAsset));

        Assert.assertEquals(2 * ChildChain.IGNIS.ONE_COIN, FORGY.getFxtBalanceDiff());
        Assert.assertEquals(2 * ChildChain.IGNIS.ONE_COIN, FORGY.getFxtUnconfirmedBalanceDiff());

    }

    @Test
    public void processingNotStarted() {
        JSONObject shufflingCreate = create(ALICE);
        String shufflingId = (String)shufflingCreate.get("transaction");
        String shufflingFullHash = (String)shufflingCreate.get("fullHash");
        generateBlock();
        register(shufflingFullHash, BOB);
        generateBlock();
        register(shufflingFullHash, CHUCK);
        generateBlock();
        register(shufflingFullHash, DAVE);
        for (int i = 0; i < 10; i++) {
            generateBlock();
        }

        JSONObject getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.CANCELLED.getCode(), getShufflingResponse.get("stage"));

        JSONObject getParticipantsResponse = getShufflingParticipants(shufflingId);
        JSONArray participants = (JSONArray)getParticipantsResponse.get("participants");
        Assert.assertEquals(4, participants.size());
        String shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(ALICE.getStrId(), shufflingAssignee);

        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + ChildChain.IGNIS.ONE_COIN), ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + ChildChain.IGNIS.ONE_COIN), ALICE.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-ChildChain.IGNIS.ONE_COIN, BOB.getFxtBalanceDiff());
        Assert.assertEquals(-ChildChain.IGNIS.ONE_COIN, BOB.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-ChildChain.IGNIS.ONE_COIN, CHUCK.getFxtBalanceDiff());
        Assert.assertEquals(-ChildChain.IGNIS.ONE_COIN, CHUCK.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-ChildChain.IGNIS.ONE_COIN, DAVE.getFxtBalanceDiff());
        Assert.assertEquals(-ChildChain.IGNIS.ONE_COIN, DAVE.getFxtUnconfirmedBalanceDiff());

        Assert.assertNull(ALICE_RECIPIENT.getAccount());
        Assert.assertNull(BOB_RECIPIENT.getAccount());
        Assert.assertNull(CHUCK_RECIPIENT.getAccount());
        Assert.assertNull(DAVE_RECIPIENT.getAccount());

        Assert.assertEquals(4 * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtBalanceDiff());
        Assert.assertEquals(4 * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtUnconfirmedBalanceDiff());

    }

    @Test
    public void processingNotStartedCurrency() {
        JSONObject shufflingCreate = createCurrencyShuffling(ALICE);
        String shufflingId = (String)shufflingCreate.get("transaction");
        String shufflingFullHash = (String)shufflingCreate.get("fullHash");
        generateBlock();
        register(shufflingFullHash, BOB);
        generateBlock();
        register(shufflingFullHash, CHUCK);
        generateBlock();
        register(shufflingFullHash, DAVE);
        for (int i = 0; i < 10; i++) {
            generateBlock();
        }

        JSONObject getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.CANCELLED.getCode(), getShufflingResponse.get("stage"));

        JSONObject getParticipantsResponse = getShufflingParticipants(shufflingId);
        JSONArray participants = (JSONArray)getParticipantsResponse.get("participants");
        Assert.assertEquals(4, participants.size());
        String shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(ALICE.getStrId(), shufflingAssignee);

        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + ChildChain.IGNIS.ONE_COIN), ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + ChildChain.IGNIS.ONE_COIN), ALICE.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-ChildChain.IGNIS.ONE_COIN, BOB.getFxtBalanceDiff());
        Assert.assertEquals(-ChildChain.IGNIS.ONE_COIN, BOB.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-ChildChain.IGNIS.ONE_COIN, CHUCK.getFxtBalanceDiff());
        Assert.assertEquals(-ChildChain.IGNIS.ONE_COIN, CHUCK.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-ChildChain.IGNIS.ONE_COIN, DAVE.getFxtBalanceDiff());
        Assert.assertEquals(-ChildChain.IGNIS.ONE_COIN, DAVE.getFxtUnconfirmedBalanceDiff());

        Assert.assertNull(ALICE_RECIPIENT.getAccount());
        Assert.assertNull(BOB_RECIPIENT.getAccount());
        Assert.assertNull(CHUCK_RECIPIENT.getAccount());
        Assert.assertNull(DAVE_RECIPIENT.getAccount());

        Assert.assertEquals(0, ALICE.getCurrencyUnitsDiff(shufflingCurrency));
        Assert.assertEquals(0, ALICE.getUnconfirmedCurrencyUnitsDiff(shufflingCurrency));
        Assert.assertEquals(0, BOB.getCurrencyUnitsDiff(shufflingCurrency));
        Assert.assertEquals(0, BOB.getUnconfirmedCurrencyUnitsDiff(shufflingCurrency));
        Assert.assertEquals(0, CHUCK.getCurrencyUnitsDiff(shufflingCurrency));
        Assert.assertEquals(0, CHUCK.getUnconfirmedCurrencyUnitsDiff(shufflingCurrency));
        Assert.assertEquals(0, DAVE.getCurrencyUnitsDiff(shufflingCurrency));
        Assert.assertEquals(0, DAVE.getUnconfirmedCurrencyUnitsDiff(shufflingCurrency));

        Assert.assertEquals(4 * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtBalanceDiff());
        Assert.assertEquals(4 * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtUnconfirmedBalanceDiff());

    }

    @Test
    public void tooManyParticipants() {
        JSONObject shufflingCreate = create(ALICE, 3);
        String shufflingId = (String)shufflingCreate.get("transaction");
        String shufflingFullHash = (String)shufflingCreate.get("fullHash");
        generateBlock();
        register(shufflingFullHash, BOB);
        register(shufflingFullHash, CHUCK);
        register(shufflingFullHash, DAVE);
        register(shufflingFullHash, FORGY);
        for (int i = 0; i < 10; i++) {
            generateBlock();
        }
        Nxt.getTransactionProcessor().clearUnconfirmedTransactions();
        JSONObject getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.CANCELLED.getCode(), getShufflingResponse.get("stage"));

        JSONObject getParticipantsResponse = getShufflingParticipants(shufflingId);
        JSONArray participants = (JSONArray)getParticipantsResponse.get("participants");
        Assert.assertEquals(3, participants.size());
        String shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(ALICE.getStrId(), shufflingAssignee);

        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + ChildChain.IGNIS.ONE_COIN), ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + ChildChain.IGNIS.ONE_COIN), ALICE.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-ChildChain.IGNIS.ONE_COIN, BOB.getFxtBalanceDiff());
        Assert.assertEquals(-ChildChain.IGNIS.ONE_COIN, BOB.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-ChildChain.IGNIS.ONE_COIN, CHUCK.getFxtBalanceDiff());
        Assert.assertEquals(-ChildChain.IGNIS.ONE_COIN, CHUCK.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(0, DAVE.getFxtBalanceDiff());
        Assert.assertEquals(0, DAVE.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(3 * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtBalanceDiff());
        Assert.assertEquals(3 * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtUnconfirmedBalanceDiff());

        Assert.assertNull(ALICE_RECIPIENT.getAccount());
        Assert.assertNull(BOB_RECIPIENT.getAccount());
        Assert.assertNull(CHUCK_RECIPIENT.getAccount());
        Assert.assertNull(DAVE_RECIPIENT.getAccount());
    }

    @Test
    public void processingNotFinished() {
        JSONObject shufflingCreate = create(ALICE);
        String shufflingId = (String)shufflingCreate.get("transaction");
        String shufflingFullHash = (String)shufflingCreate.get("fullHash");
        generateBlock();
        register(shufflingFullHash, BOB);
        generateBlock();
        register(shufflingFullHash, CHUCK);
        generateBlock();
        register(shufflingFullHash, DAVE);
        generateBlock();

        JSONObject getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.PROCESSING.getCode(), getShufflingResponse.get("stage"));

        JSONObject getParticipantsResponse = getShufflingParticipants(shufflingId);
        JSONArray participants = (JSONArray)getParticipantsResponse.get("participants");
        Assert.assertEquals(4, participants.size());
        String shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(ALICE.getId()), shufflingAssignee);

        process(shufflingId, ALICE, ALICE_RECIPIENT);
        generateBlock();
        process(shufflingId, BOB, BOB_RECIPIENT);
        generateBlock();
        process(shufflingId, CHUCK, CHUCK_RECIPIENT);

        for (int i = 0; i < 10; i++) {
            generateBlock();
        }

        getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.CANCELLED.getCode(), getShufflingResponse.get("stage"));
        shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(DAVE.getId()), shufflingAssignee);

        Assert.assertEquals(-11 * ChildChain.IGNIS.ONE_COIN, ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-11 * ChildChain.IGNIS.ONE_COIN, ALICE.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-11 * ChildChain.IGNIS.ONE_COIN, BOB.getFxtBalanceDiff());
        Assert.assertEquals(-11 * ChildChain.IGNIS.ONE_COIN, BOB.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-11 * ChildChain.IGNIS.ONE_COIN, CHUCK.getFxtBalanceDiff());
        Assert.assertEquals(-11 * ChildChain.IGNIS.ONE_COIN, CHUCK.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + ChildChain.IGNIS.ONE_COIN), DAVE.getFxtBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + ChildChain.IGNIS.ONE_COIN), DAVE.getFxtUnconfirmedBalanceDiff());

        Assert.assertNull(ALICE_RECIPIENT.getAccount());
        Assert.assertNull(BOB_RECIPIENT.getAccount());
        Assert.assertNull(CHUCK_RECIPIENT.getAccount());
        Assert.assertNull(DAVE_RECIPIENT.getAccount());

        Assert.assertEquals(34 * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtBalanceDiff());
        Assert.assertEquals(34 * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtUnconfirmedBalanceDiff());

    }

    @Test
    public void verifyNotStarted() {
        JSONObject shufflingCreate = create(ALICE);
        String shufflingId = (String)shufflingCreate.get("transaction");
        String shufflingFullHash = (String)shufflingCreate.get("fullHash");
        generateBlock();
        register(shufflingFullHash, BOB);
        generateBlock();
        register(shufflingFullHash, CHUCK);
        generateBlock();
        register(shufflingFullHash, DAVE);
        generateBlock();

        JSONObject getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.PROCESSING.getCode(), getShufflingResponse.get("stage"));

        JSONObject getParticipantsResponse = getShufflingParticipants(shufflingId);
        JSONArray participants = (JSONArray)getParticipantsResponse.get("participants");
        Assert.assertEquals(4, participants.size());
        String shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(ALICE.getId()), shufflingAssignee);

        process(shufflingId, ALICE, ALICE_RECIPIENT);
        generateBlock();
        process(shufflingId, BOB, BOB_RECIPIENT);
        generateBlock();
        process(shufflingId, CHUCK, CHUCK_RECIPIENT);
        generateBlock();
        process(shufflingId, DAVE, DAVE_RECIPIENT);

        for (int i = 0; i < 14; i++) {
            generateBlock();
        }

        getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.CANCELLED.getCode(), getShufflingResponse.get("stage"));
        shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(ALICE.getId()), shufflingAssignee);

        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 11 * ChildChain.IGNIS.ONE_COIN), ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 11 * ChildChain.IGNIS.ONE_COIN), ALICE.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-11 * ChildChain.IGNIS.ONE_COIN, BOB.getFxtBalanceDiff());
        Assert.assertEquals(-11 * ChildChain.IGNIS.ONE_COIN, BOB.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-11 * ChildChain.IGNIS.ONE_COIN, CHUCK.getFxtBalanceDiff());
        Assert.assertEquals(-11 * ChildChain.IGNIS.ONE_COIN, CHUCK.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-12 * ChildChain.IGNIS.ONE_COIN, DAVE.getFxtBalanceDiff());
        Assert.assertEquals(-12 * ChildChain.IGNIS.ONE_COIN, DAVE.getFxtUnconfirmedBalanceDiff());

        Assert.assertNotNull(ALICE_RECIPIENT.getAccount());
        Assert.assertNotNull(BOB_RECIPIENT.getAccount());
        Assert.assertNotNull(CHUCK_RECIPIENT.getAccount());
        Assert.assertNotNull(DAVE_RECIPIENT.getAccount());

        Assert.assertEquals(0, ALICE_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(0, ALICE_RECIPIENT.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(0, BOB_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(0, BOB_RECIPIENT.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(0, CHUCK_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(0, CHUCK_RECIPIENT.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(0, DAVE_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(0, DAVE_RECIPIENT.getFxtUnconfirmedBalanceDiff());

        Assert.assertEquals(45 * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtBalanceDiff());
        Assert.assertEquals(45 * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtUnconfirmedBalanceDiff());

    }

    @Test
    public void verifyNotFinished() {
        JSONObject shufflingCreate = create(ALICE);
        String shufflingId = (String)shufflingCreate.get("transaction");
        String shufflingFullHash = (String)shufflingCreate.get("fullHash");
        generateBlock();
        register(shufflingFullHash, BOB);
        generateBlock();
        register(shufflingFullHash, CHUCK);
        generateBlock();
        register(shufflingFullHash, DAVE);
        generateBlock();

        JSONObject getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.PROCESSING.getCode(), getShufflingResponse.get("stage"));

        JSONObject getParticipantsResponse = getShufflingParticipants(shufflingId);
        JSONArray participants = (JSONArray)getParticipantsResponse.get("participants");
        Assert.assertEquals(4, participants.size());
        String shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(ALICE.getId()), shufflingAssignee);

        process(shufflingId, ALICE, ALICE_RECIPIENT);
        generateBlock();
        process(shufflingId, BOB, BOB_RECIPIENT);
        generateBlock();
        process(shufflingId, CHUCK, CHUCK_RECIPIENT);
        generateBlock();
        process(shufflingId, DAVE, DAVE_RECIPIENT);
        generateBlock();

        getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.VERIFICATION.getCode(), getShufflingResponse.get("stage"));
        String shufflingStateHash = (String)getShufflingResponse.get("shufflingStateHash");
        verify(shufflingId, ALICE, shufflingStateHash);
        verify(shufflingId, BOB, shufflingStateHash);
        for (int i = 0; i < 14; i++) {
            generateBlock();
        }
        getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.CANCELLED.getCode(), getShufflingResponse.get("stage"));
        shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(CHUCK.getId()), shufflingAssignee);

        Assert.assertEquals(-12 * ChildChain.IGNIS.ONE_COIN, ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-12 * ChildChain.IGNIS.ONE_COIN, ALICE.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-12 * ChildChain.IGNIS.ONE_COIN, BOB.getFxtBalanceDiff());
        Assert.assertEquals(-12 * ChildChain.IGNIS.ONE_COIN, BOB.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 11 * ChildChain.IGNIS.ONE_COIN), CHUCK.getFxtBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 11 * ChildChain.IGNIS.ONE_COIN), CHUCK.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-12 * ChildChain.IGNIS.ONE_COIN, DAVE.getFxtBalanceDiff());
        Assert.assertEquals(-12 * ChildChain.IGNIS.ONE_COIN, DAVE.getFxtUnconfirmedBalanceDiff());

        Assert.assertNotNull(ALICE_RECIPIENT.getAccount());
        Assert.assertNotNull(BOB_RECIPIENT.getAccount());
        Assert.assertNotNull(CHUCK_RECIPIENT.getAccount());
        Assert.assertNotNull(DAVE_RECIPIENT.getAccount());

        Assert.assertEquals(0, ALICE_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(0, ALICE_RECIPIENT.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(0, BOB_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(0, BOB_RECIPIENT.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(0, CHUCK_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(0, CHUCK_RECIPIENT.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(0, DAVE_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(0, DAVE_RECIPIENT.getFxtUnconfirmedBalanceDiff());

        Assert.assertEquals(47 * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtBalanceDiff());
        Assert.assertEquals(47 * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtUnconfirmedBalanceDiff());

    }

    @Test
    public void cancelAfterVerifyChuck() {
        JSONObject shufflingCreate = create(ALICE);
        String shufflingId = (String)shufflingCreate.get("transaction");
        String shufflingFullHash = (String)shufflingCreate.get("fullHash");
        generateBlock();
        register(shufflingFullHash, BOB);
        generateBlock();
        register(shufflingFullHash, CHUCK);
        generateBlock();
        register(shufflingFullHash, DAVE);
        generateBlock();

        JSONObject getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.PROCESSING.getCode(), getShufflingResponse.get("stage"));

        JSONObject getParticipantsResponse = getShufflingParticipants(shufflingId);
        JSONArray participants = (JSONArray)getParticipantsResponse.get("participants");
        Assert.assertEquals(4, participants.size());
        String shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(ALICE.getId()), shufflingAssignee);

        process(shufflingId, ALICE, ALICE_RECIPIENT);
        generateBlock();
        process(shufflingId, BOB, BOB_RECIPIENT);
        generateBlock();
        process(shufflingId, CHUCK, CHUCK_RECIPIENT);
        generateBlock();
        process(shufflingId, DAVE, DAVE_RECIPIENT);
        generateBlock();

        getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.VERIFICATION.getCode(), getShufflingResponse.get("stage"));
        String shufflingStateHash = (String)getShufflingResponse.get("shufflingStateHash");
        verify(shufflingId, ALICE, shufflingStateHash);
        verify(shufflingId, BOB, shufflingStateHash);
        cancel(shufflingId, CHUCK, shufflingStateHash, 0);
        generateBlock();
        getShufflingResponse = getShuffling(shufflingId);
        shufflingStateHash = (String)getShufflingResponse.get("shufflingStateHash");
        shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(CHUCK.getId()), shufflingAssignee);
        cancel(shufflingId, ALICE, shufflingStateHash, CHUCK.getId());
        cancel(shufflingId, BOB, shufflingStateHash, CHUCK.getId());
        for (int i = 0; i < 14; i++) {
            generateBlock();
        }
        getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.CANCELLED.getCode(), getShufflingResponse.get("stage"));
        shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(CHUCK.getId()), shufflingAssignee);

        Assert.assertEquals(-22 * ChildChain.IGNIS.ONE_COIN, ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-22 * ChildChain.IGNIS.ONE_COIN, ALICE.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-22 * ChildChain.IGNIS.ONE_COIN, BOB.getFxtBalanceDiff());
        Assert.assertEquals(-22 * ChildChain.IGNIS.ONE_COIN, BOB.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 21 * ChildChain.IGNIS.ONE_COIN), CHUCK.getFxtBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 21 * ChildChain.IGNIS.ONE_COIN), CHUCK.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-12 * ChildChain.IGNIS.ONE_COIN, DAVE.getFxtBalanceDiff());
        Assert.assertEquals(-12 * ChildChain.IGNIS.ONE_COIN, DAVE.getFxtUnconfirmedBalanceDiff());

        Assert.assertNotNull(ALICE_RECIPIENT.getAccount());
        Assert.assertNotNull(BOB_RECIPIENT.getAccount());
        Assert.assertNotNull(CHUCK_RECIPIENT.getAccount());
        Assert.assertNotNull(DAVE_RECIPIENT.getAccount());

        Assert.assertEquals(0, ALICE_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(0, ALICE_RECIPIENT.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(0, BOB_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(0, BOB_RECIPIENT.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(0, CHUCK_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(0, CHUCK_RECIPIENT.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(0, DAVE_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(0, DAVE_RECIPIENT.getFxtUnconfirmedBalanceDiff());

        Assert.assertEquals(77 * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtBalanceDiff());
        Assert.assertEquals(77 * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtUnconfirmedBalanceDiff());

    }

    @Test
    public void cancelAfterVerifyChuckInvalidKeys() {
        JSONObject shufflingCreate = create(ALICE);
        String shufflingId = (String)shufflingCreate.get("transaction");
        String shufflingFullHash = (String)shufflingCreate.get("fullHash");
        generateBlock();
        register(shufflingFullHash, BOB);
        generateBlock();
        register(shufflingFullHash, CHUCK);
        generateBlock();
        register(shufflingFullHash, DAVE);
        generateBlock();

        JSONObject getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.PROCESSING.getCode(), getShufflingResponse.get("stage"));

        JSONObject getParticipantsResponse = getShufflingParticipants(shufflingId);
        JSONArray participants = (JSONArray)getParticipantsResponse.get("participants");
        Assert.assertEquals(4, participants.size());
        String shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(ALICE.getId()), shufflingAssignee);

        process(shufflingId, ALICE, ALICE_RECIPIENT);
        generateBlock();
        process(shufflingId, BOB, BOB_RECIPIENT);
        generateBlock();
        process(shufflingId, CHUCK, CHUCK_RECIPIENT);
        generateBlock();
        process(shufflingId, DAVE, DAVE_RECIPIENT);
        generateBlock();

        getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.VERIFICATION.getCode(), getShufflingResponse.get("stage"));
        String shufflingStateHash = (String)getShufflingResponse.get("shufflingStateHash");
        verify(shufflingId, ALICE, shufflingStateHash);
        verify(shufflingId, BOB, shufflingStateHash);
        JSONObject cancelResponse = cancel(shufflingId, CHUCK, shufflingStateHash, 0, false);
        JSONObject transactionJSON = (JSONObject)cancelResponse.get("transactionJSON");
        JSONArray keySeeds = (JSONArray)((JSONObject)transactionJSON.get("attachment")).get("keySeeds");
        String s = (String)keySeeds.get(0);
        keySeeds.set(0, "0000000000" + s.substring(10));
        broadcast(transactionJSON, CHUCK);
        generateBlock();
        getShufflingResponse = getShuffling(shufflingId);
        shufflingStateHash = (String)getShufflingResponse.get("shufflingStateHash");
        shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(CHUCK.getId()), shufflingAssignee);
        cancel(shufflingId, ALICE, shufflingStateHash, CHUCK.getId());
        cancel(shufflingId, BOB, shufflingStateHash, CHUCK.getId());
        for (int i = 0; i < 14; i++) {
            generateBlock();
        }
        getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.CANCELLED.getCode(), getShufflingResponse.get("stage"));
        shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(CHUCK.getId()), shufflingAssignee);

        Assert.assertEquals(-22 * ChildChain.IGNIS.ONE_COIN, ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-22 * ChildChain.IGNIS.ONE_COIN, ALICE.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-22 * ChildChain.IGNIS.ONE_COIN, BOB.getFxtBalanceDiff());
        Assert.assertEquals(-22 * ChildChain.IGNIS.ONE_COIN, BOB.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 21 * ChildChain.IGNIS.ONE_COIN), CHUCK.getFxtBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 21 * ChildChain.IGNIS.ONE_COIN), CHUCK.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-12 * ChildChain.IGNIS.ONE_COIN, DAVE.getFxtBalanceDiff());
        Assert.assertEquals(-12 * ChildChain.IGNIS.ONE_COIN, DAVE.getFxtUnconfirmedBalanceDiff());

        Assert.assertNotNull(ALICE_RECIPIENT.getAccount());
        Assert.assertNotNull(BOB_RECIPIENT.getAccount());
        Assert.assertNotNull(CHUCK_RECIPIENT.getAccount());
        Assert.assertNotNull(DAVE_RECIPIENT.getAccount());

        Assert.assertEquals(0, ALICE_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(0, ALICE_RECIPIENT.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(0, BOB_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(0, BOB_RECIPIENT.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(0, CHUCK_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(0, CHUCK_RECIPIENT.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(0, DAVE_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(0, DAVE_RECIPIENT.getFxtUnconfirmedBalanceDiff());

        Assert.assertEquals(77 * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtBalanceDiff());
        Assert.assertEquals(77 * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtUnconfirmedBalanceDiff());

    }

    @Test
    public void cancelAfterVerifyChuckInvalidKeysAlice() {
        JSONObject shufflingCreate = create(ALICE);
        String shufflingId = (String)shufflingCreate.get("transaction");
        String shufflingFullHash = (String)shufflingCreate.get("fullHash");
        generateBlock();
        register(shufflingFullHash, BOB);
        generateBlock();
        register(shufflingFullHash, CHUCK);
        generateBlock();
        register(shufflingFullHash, DAVE);
        generateBlock();

        JSONObject getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.PROCESSING.getCode(), getShufflingResponse.get("stage"));

        JSONObject getParticipantsResponse = getShufflingParticipants(shufflingId);
        JSONArray participants = (JSONArray)getParticipantsResponse.get("participants");
        Assert.assertEquals(4, participants.size());
        String shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(ALICE.getId()), shufflingAssignee);

        process(shufflingId, ALICE, ALICE_RECIPIENT);
        generateBlock();
        process(shufflingId, BOB, BOB_RECIPIENT);
        generateBlock();
        process(shufflingId, CHUCK, CHUCK_RECIPIENT);
        generateBlock();
        process(shufflingId, DAVE, DAVE_RECIPIENT);
        generateBlock();

        getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.VERIFICATION.getCode(), getShufflingResponse.get("stage"));
        String shufflingStateHash = (String)getShufflingResponse.get("shufflingStateHash");
        verify(shufflingId, ALICE, shufflingStateHash);
        verify(shufflingId, BOB, shufflingStateHash);
        cancel(shufflingId, CHUCK, shufflingStateHash, 0);
        generateBlock();
        getShufflingResponse = getShuffling(shufflingId);
        shufflingStateHash = (String)getShufflingResponse.get("shufflingStateHash");
        shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(CHUCK.getId()), shufflingAssignee);
        JSONObject cancelResponse = cancel(shufflingId, ALICE, shufflingStateHash, CHUCK.getId(), false);
        JSONObject transactionJSON = (JSONObject)cancelResponse.get("transactionJSON");
        JSONArray keySeeds = (JSONArray)((JSONObject)transactionJSON.get("attachment")).get("keySeeds");
        String s = (String)keySeeds.get(0);
        keySeeds.set(0, "0000000000" + s.substring(10));
        broadcast(transactionJSON, ALICE);
        generateBlock();
        cancel(shufflingId, BOB, shufflingStateHash, CHUCK.getId());
        for (int i = 0; i < 14; i++) {
            generateBlock();
        }
        getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.CANCELLED.getCode(), getShufflingResponse.get("stage"));
        shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(ALICE.getId()), shufflingAssignee);

        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 22 * ChildChain.IGNIS.ONE_COIN), ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 22 * ChildChain.IGNIS.ONE_COIN), ALICE.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-22 * ChildChain.IGNIS.ONE_COIN, BOB.getFxtBalanceDiff());
        Assert.assertEquals(-22 * ChildChain.IGNIS.ONE_COIN, BOB.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-21 * ChildChain.IGNIS.ONE_COIN, CHUCK.getFxtBalanceDiff());
        Assert.assertEquals(-21 * ChildChain.IGNIS.ONE_COIN, CHUCK.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-12 * ChildChain.IGNIS.ONE_COIN, DAVE.getFxtBalanceDiff());
        Assert.assertEquals(-12 * ChildChain.IGNIS.ONE_COIN, DAVE.getFxtUnconfirmedBalanceDiff());

        Assert.assertNotNull(ALICE_RECIPIENT.getAccount());
        Assert.assertNotNull(BOB_RECIPIENT.getAccount());
        Assert.assertNotNull(CHUCK_RECIPIENT.getAccount());
        Assert.assertNotNull(DAVE_RECIPIENT.getAccount());

        Assert.assertEquals(0, ALICE_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(0, ALICE_RECIPIENT.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(0, BOB_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(0, BOB_RECIPIENT.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(0, CHUCK_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(0, CHUCK_RECIPIENT.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(0, DAVE_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(0, DAVE_RECIPIENT.getFxtUnconfirmedBalanceDiff());

        Assert.assertEquals(77 * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtBalanceDiff());
        Assert.assertEquals(77 * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtUnconfirmedBalanceDiff());

    }

    @Test
    public void cancelAfterVerifyChuckInvalidKeysAlice2() {
        JSONObject shufflingCreate = create(ALICE);
        String shufflingId = (String)shufflingCreate.get("transaction");
        String shufflingFullHash = (String)shufflingCreate.get("fullHash");
        generateBlock();
        register(shufflingFullHash, BOB);
        generateBlock();
        register(shufflingFullHash, CHUCK);
        generateBlock();
        register(shufflingFullHash, DAVE);
        generateBlock();

        JSONObject getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.PROCESSING.getCode(), getShufflingResponse.get("stage"));

        JSONObject getParticipantsResponse = getShufflingParticipants(shufflingId);
        JSONArray participants = (JSONArray)getParticipantsResponse.get("participants");
        Assert.assertEquals(4, participants.size());
        String shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(ALICE.getId()), shufflingAssignee);

        process(shufflingId, ALICE, ALICE_RECIPIENT);
        generateBlock();
        process(shufflingId, BOB, BOB_RECIPIENT);
        generateBlock();
        process(shufflingId, CHUCK, CHUCK_RECIPIENT);
        generateBlock();
        process(shufflingId, DAVE, DAVE_RECIPIENT);
        generateBlock();

        getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.VERIFICATION.getCode(), getShufflingResponse.get("stage"));
        String shufflingStateHash = (String)getShufflingResponse.get("shufflingStateHash");
        verify(shufflingId, ALICE, shufflingStateHash);
        verify(shufflingId, BOB, shufflingStateHash);
        cancel(shufflingId, CHUCK, shufflingStateHash, 0);
        generateBlock();
        getShufflingResponse = getShuffling(shufflingId);
        shufflingStateHash = (String)getShufflingResponse.get("shufflingStateHash");
        shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(CHUCK.getId()), shufflingAssignee);
        JSONObject cancelResponse = cancel(shufflingId, ALICE, shufflingStateHash, CHUCK.getId(), false);
        JSONObject transactionJSON = (JSONObject)cancelResponse.get("transactionJSON");
        JSONArray keySeeds = (JSONArray)((JSONObject)transactionJSON.get("attachment")).get("keySeeds");
        String s = (String)keySeeds.get(1);
        keySeeds.set(1, "0000000000" + s.substring(10));
        broadcast(transactionJSON, ALICE);
        generateBlock();
        cancel(shufflingId, BOB, shufflingStateHash, CHUCK.getId());
        for (int i = 0; i < 14; i++) {
            generateBlock();
        }
        getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.CANCELLED.getCode(), getShufflingResponse.get("stage"));
        shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(ALICE.getId()), shufflingAssignee);

        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 22 * ChildChain.IGNIS.ONE_COIN), ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 22 * ChildChain.IGNIS.ONE_COIN), ALICE.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-22 * ChildChain.IGNIS.ONE_COIN, BOB.getFxtBalanceDiff());
        Assert.assertEquals(-22 * ChildChain.IGNIS.ONE_COIN, BOB.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-21 * ChildChain.IGNIS.ONE_COIN, CHUCK.getFxtBalanceDiff());
        Assert.assertEquals(-21 * ChildChain.IGNIS.ONE_COIN, CHUCK.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-12 * ChildChain.IGNIS.ONE_COIN, DAVE.getFxtBalanceDiff());
        Assert.assertEquals(-12 * ChildChain.IGNIS.ONE_COIN, DAVE.getFxtUnconfirmedBalanceDiff());

        Assert.assertNotNull(ALICE_RECIPIENT.getAccount());
        Assert.assertNotNull(BOB_RECIPIENT.getAccount());
        Assert.assertNotNull(CHUCK_RECIPIENT.getAccount());
        Assert.assertNotNull(DAVE_RECIPIENT.getAccount());

        Assert.assertEquals(0, ALICE_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(0, ALICE_RECIPIENT.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(0, BOB_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(0, BOB_RECIPIENT.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(0, CHUCK_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(0, CHUCK_RECIPIENT.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(0, DAVE_RECIPIENT.getFxtBalanceDiff());
        Assert.assertEquals(0, DAVE_RECIPIENT.getFxtUnconfirmedBalanceDiff());

        Assert.assertEquals(77 * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtBalanceDiff());
        Assert.assertEquals(77 * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtUnconfirmedBalanceDiff());

    }

    @Test
    public void badProcessDataAlice() {
        JSONObject shufflingCreate = create(ALICE);
        String shufflingId = (String)shufflingCreate.get("transaction");
        String shufflingFullHash = (String)shufflingCreate.get("fullHash");
        generateBlock();
        register(shufflingFullHash, BOB);
        generateBlock();
        register(shufflingFullHash, CHUCK);
        generateBlock();
        register(shufflingFullHash, DAVE);
        generateBlock();

        JSONObject getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.PROCESSING.getCode(), getShufflingResponse.get("stage"));

        JSONObject getParticipantsResponse = getShufflingParticipants(shufflingId);
        JSONArray participants = (JSONArray)getParticipantsResponse.get("participants");
        Assert.assertEquals(4, participants.size());
        String shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(ALICE.getId()), shufflingAssignee);

        JSONObject processResponse = process(shufflingId, ALICE, ALICE_RECIPIENT, false);
        JSONObject transactionJSON = (JSONObject)processResponse.get("transactionJSON");
        JSONArray data = (JSONArray)((JSONObject)transactionJSON.get("attachment")).get("data");
        String s = (String)data.get(0);
        data.set(0, "8080808080" + s.substring(10));
        broadcast(transactionJSON, ALICE);
        generateBlock();
        getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.PROCESSING.getCode(), getShufflingResponse.get("stage"));
        process(shufflingId, BOB, BOB_RECIPIENT);
        generateBlock();
        getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.BLAME.getCode(), getShufflingResponse.get("stage"));
        shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(BOB.getId()), shufflingAssignee);
        for (int i = 0; i < 14; i++) {
            generateBlock();
        }
        getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.CANCELLED.getCode(), getShufflingResponse.get("stage"));
        shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(ALICE.getId()), shufflingAssignee);

        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 11 * ChildChain.IGNIS.ONE_COIN), ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 11 * ChildChain.IGNIS.ONE_COIN), ALICE.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-11 * ChildChain.IGNIS.ONE_COIN, BOB.getFxtBalanceDiff());
        Assert.assertEquals(-11 * ChildChain.IGNIS.ONE_COIN, BOB.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-1 * ChildChain.IGNIS.ONE_COIN, CHUCK.getFxtBalanceDiff());
        Assert.assertEquals(-1 * ChildChain.IGNIS.ONE_COIN, CHUCK.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-1 * ChildChain.IGNIS.ONE_COIN, DAVE.getFxtBalanceDiff());
        Assert.assertEquals(-1 * ChildChain.IGNIS.ONE_COIN, DAVE.getFxtUnconfirmedBalanceDiff());

        Assert.assertNull(ALICE_RECIPIENT.getAccount());
        Assert.assertNull(BOB_RECIPIENT.getAccount());
        Assert.assertNull(CHUCK_RECIPIENT.getAccount());
        Assert.assertNull(DAVE_RECIPIENT.getAccount());

        Assert.assertEquals(24 * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtBalanceDiff());
        Assert.assertEquals(24 * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtUnconfirmedBalanceDiff());

    }

    @Test
    public void modifiedProcessDataBob() {
        JSONObject shufflingCreate = create(ALICE);
        String shufflingId = (String)shufflingCreate.get("transaction");
        String shufflingFullHash = (String)shufflingCreate.get("fullHash");
        generateBlock();
        register(shufflingFullHash, BOB);
        generateBlock();
        register(shufflingFullHash, CHUCK);
        generateBlock();
        register(shufflingFullHash, DAVE);
        generateBlock();

        JSONObject getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.PROCESSING.getCode(), getShufflingResponse.get("stage"));

        JSONObject getParticipantsResponse = getShufflingParticipants(shufflingId);
        JSONArray participants = (JSONArray)getParticipantsResponse.get("participants");
        Assert.assertEquals(4, participants.size());
        String shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(ALICE.getId()), shufflingAssignee);

        process(shufflingId, ALICE, ALICE_RECIPIENT);
        generateBlock();
        JSONObject processResponse = process(shufflingId, BOB, BOB_RECIPIENT, false);
        JSONObject transactionJSON = (JSONObject)processResponse.get("transactionJSON");
        JSONArray data = (JSONArray)((JSONObject)transactionJSON.get("attachment")).get("data");
        String s = (String)data.get(0);
        data.set(0, "8080808080" + s.substring(10));
        broadcast(transactionJSON, BOB);
        generateBlock();
        getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.PROCESSING.getCode(), getShufflingResponse.get("stage"));
        process(shufflingId, CHUCK, CHUCK_RECIPIENT);
        generateBlock();
        getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.BLAME.getCode(), getShufflingResponse.get("stage"));
        shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(CHUCK.getId()), shufflingAssignee);
        String shufflingStateHash = (String)getShufflingResponse.get("shufflingStateHash");
        cancel(shufflingId, ALICE, shufflingStateHash, CHUCK.getId());
        JSONObject cancelResponse = cancel(shufflingId, BOB, shufflingStateHash, CHUCK.getId());
        boolean bobCancelFailed = cancelResponse.get("error") != null; // if he happened to modify his own piece
        for (int i = 0; i < 14; i++) {
            generateBlock();
        }

        getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.CANCELLED.getCode(), getShufflingResponse.get("stage"));
        shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(BOB.getId()), shufflingAssignee);

        Assert.assertEquals(-21 * ChildChain.IGNIS.ONE_COIN, ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-21 * ChildChain.IGNIS.ONE_COIN, ALICE.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + (bobCancelFailed ? 11 : 21) * ChildChain.IGNIS.ONE_COIN), BOB.getFxtBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + (bobCancelFailed ? 11 : 21) * ChildChain.IGNIS.ONE_COIN), BOB.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-11 * ChildChain.IGNIS.ONE_COIN, CHUCK.getFxtBalanceDiff());
        Assert.assertEquals(-11 * ChildChain.IGNIS.ONE_COIN, CHUCK.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-1 * ChildChain.IGNIS.ONE_COIN, DAVE.getFxtBalanceDiff());
        Assert.assertEquals(-1 * ChildChain.IGNIS.ONE_COIN, DAVE.getFxtUnconfirmedBalanceDiff());

        Assert.assertNull(ALICE_RECIPIENT.getAccount());
        Assert.assertNull(BOB_RECIPIENT.getAccount());
        Assert.assertNull(CHUCK_RECIPIENT.getAccount());
        Assert.assertNull(DAVE_RECIPIENT.getAccount());

        Assert.assertEquals((bobCancelFailed ? 44 : 54) * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtBalanceDiff());
        Assert.assertEquals((bobCancelFailed ? 44 : 54) * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtUnconfirmedBalanceDiff());

    }

    @Test
    public void modifiedProcessDataChuck() {
        JSONObject shufflingCreate = create(ALICE);
        String shufflingId = (String)shufflingCreate.get("transaction");
        String shufflingFullHash = (String)shufflingCreate.get("fullHash");
        generateBlock();
        register(shufflingFullHash, BOB);
        generateBlock();
        register(shufflingFullHash, CHUCK);
        generateBlock();
        register(shufflingFullHash, DAVE);
        generateBlock();

        JSONObject getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.PROCESSING.getCode(), getShufflingResponse.get("stage"));

        JSONObject getParticipantsResponse = getShufflingParticipants(shufflingId);
        JSONArray participants = (JSONArray)getParticipantsResponse.get("participants");
        Assert.assertEquals(4, participants.size());
        String shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(ALICE.getId()), shufflingAssignee);

        process(shufflingId, ALICE, ALICE_RECIPIENT);
        generateBlock();
        process(shufflingId, BOB, BOB_RECIPIENT);
        generateBlock();
        JSONObject processResponse = process(shufflingId, CHUCK, CHUCK_RECIPIENT, false);
        JSONObject transactionJSON = (JSONObject)processResponse.get("transactionJSON");
        JSONArray data = (JSONArray)((JSONObject)transactionJSON.get("attachment")).get("data");
        String s = (String)data.get(0);
        data.set(0, "8080808080" + s.substring(10));
        broadcast(transactionJSON, CHUCK);
        generateBlock();
        getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.PROCESSING.getCode(), getShufflingResponse.get("stage"));
        process(shufflingId, DAVE, DAVE_RECIPIENT);
        generateBlock();
        getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.BLAME.getCode(), getShufflingResponse.get("stage"));
        shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(DAVE.getId()), shufflingAssignee);
        String shufflingStateHash = (String)getShufflingResponse.get("shufflingStateHash");
        cancel(shufflingId, ALICE, shufflingStateHash, DAVE.getId());
        cancel(shufflingId, BOB, shufflingStateHash, DAVE.getId());
        JSONObject cancelResponse = cancel(shufflingId, CHUCK, shufflingStateHash, DAVE.getId());
        boolean chuckCancelFailed = cancelResponse.get("error") != null; // if he happened to modify his own piece
        for (int i = 0; i < 14; i++) {
            generateBlock();
        }

        getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.CANCELLED.getCode(), getShufflingResponse.get("stage"));
        shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(CHUCK.getId()), shufflingAssignee);

        Assert.assertEquals(-21 * ChildChain.IGNIS.ONE_COIN, ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-21 * ChildChain.IGNIS.ONE_COIN, ALICE.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-21 * ChildChain.IGNIS.ONE_COIN, BOB.getFxtBalanceDiff());
        Assert.assertEquals(-21 * ChildChain.IGNIS.ONE_COIN, BOB.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + (chuckCancelFailed ? 11 : 21) * ChildChain.IGNIS.ONE_COIN), CHUCK.getFxtBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + (chuckCancelFailed ? 11 : 21) * ChildChain.IGNIS.ONE_COIN), CHUCK.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-12 * ChildChain.IGNIS.ONE_COIN, DAVE.getFxtBalanceDiff());
        Assert.assertEquals(-12 * ChildChain.IGNIS.ONE_COIN, DAVE.getFxtUnconfirmedBalanceDiff());

        Assert.assertNull(ALICE_RECIPIENT.getAccount());
        Assert.assertNull(BOB_RECIPIENT.getAccount());
        Assert.assertNull(CHUCK_RECIPIENT.getAccount());
        Assert.assertNull(DAVE_RECIPIENT.getAccount());

        Assert.assertEquals((chuckCancelFailed ? 65 : 75) * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtBalanceDiff());
        Assert.assertEquals((chuckCancelFailed ? 65 : 75) * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtUnconfirmedBalanceDiff());

    }

    @Test
    public void modifiedRecipientKeysDave() {
        JSONObject shufflingCreate = create(ALICE);
        String shufflingId = (String)shufflingCreate.get("transaction");
        String shufflingFullHash = (String)shufflingCreate.get("fullHash");
        generateBlock();
        register(shufflingFullHash, BOB);
        generateBlock();
        register(shufflingFullHash, CHUCK);
        generateBlock();
        register(shufflingFullHash, DAVE);
        generateBlock();

        JSONObject getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.PROCESSING.getCode(), getShufflingResponse.get("stage"));

        JSONObject getParticipantsResponse = getShufflingParticipants(shufflingId);
        JSONArray participants = (JSONArray)getParticipantsResponse.get("participants");
        Assert.assertEquals(4, participants.size());
        String shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(ALICE.getId()), shufflingAssignee);

        process(shufflingId, ALICE, ALICE_RECIPIENT);
        generateBlock();
        process(shufflingId, BOB, BOB_RECIPIENT);
        generateBlock();
        process(shufflingId, CHUCK, CHUCK_RECIPIENT);
        generateBlock();
        JSONObject processResponse = process(shufflingId, DAVE, DAVE_RECIPIENT, false);
        JSONObject transactionJSON = (JSONObject)processResponse.get("transactionJSON");
        JSONArray data = (JSONArray)((JSONObject)transactionJSON.get("attachment")).get("recipientPublicKeys");
        String s = (String)data.get(0);
        if (!s.equals(DAVE_RECIPIENT.getPublicKeyStr())) {
            data.set(0, "0000000000" + s.substring(10));
        } else {
            s = (String)data.get(1);
            data.set(1, "0000000000" + s.substring(10));
        }
        broadcast(transactionJSON, DAVE);
        generateBlock();
        getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.VERIFICATION.getCode(), getShufflingResponse.get("stage"));
        String shufflingStateHash = (String)getShufflingResponse.get("shufflingStateHash");
        cancel(shufflingId, ALICE, shufflingStateHash, 0);
        generateBlock();
        getShufflingResponse = getShuffling(shufflingId);
        shufflingStateHash = (String)getShufflingResponse.get("shufflingStateHash");
        cancel(shufflingId, BOB, shufflingStateHash, ALICE.getId());
        cancel(shufflingId, CHUCK, shufflingStateHash, ALICE.getId());
        for (int i = 0; i < 14; i++) {
            generateBlock();
        }

        getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.CANCELLED.getCode(), getShufflingResponse.get("stage"));
        shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(DAVE.getId()), shufflingAssignee);

        Assert.assertEquals(-21 * ChildChain.IGNIS.ONE_COIN, ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-21 * ChildChain.IGNIS.ONE_COIN, ALICE.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-21 * ChildChain.IGNIS.ONE_COIN, BOB.getFxtBalanceDiff());
        Assert.assertEquals(-21 * ChildChain.IGNIS.ONE_COIN, BOB.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-21 * ChildChain.IGNIS.ONE_COIN, CHUCK.getFxtBalanceDiff());
        Assert.assertEquals(-21 * ChildChain.IGNIS.ONE_COIN, CHUCK.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 12 * ChildChain.IGNIS.ONE_COIN), DAVE.getFxtBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 12 * ChildChain.IGNIS.ONE_COIN), DAVE.getFxtUnconfirmedBalanceDiff());

        Assert.assertTrue(ALICE_RECIPIENT.getAccount() == null || ALICE_RECIPIENT.getFxtBalanceDiff() == 0);
        Assert.assertTrue(ALICE_RECIPIENT.getAccount() == null || ALICE_RECIPIENT.getFxtUnconfirmedBalanceDiff() == 0);
        Assert.assertTrue(BOB_RECIPIENT.getAccount() == null || BOB_RECIPIENT.getFxtBalanceDiff() == 0);
        Assert.assertTrue(BOB_RECIPIENT.getAccount() == null || BOB_RECIPIENT.getFxtUnconfirmedBalanceDiff() == 0);
        Assert.assertTrue(CHUCK_RECIPIENT.getAccount() == null || CHUCK_RECIPIENT.getFxtBalanceDiff() == 0);
        Assert.assertTrue(CHUCK_RECIPIENT.getAccount() == null || CHUCK_RECIPIENT.getFxtUnconfirmedBalanceDiff() == 0);
        Assert.assertTrue(DAVE_RECIPIENT.getAccount() == null || DAVE_RECIPIENT.getFxtBalanceDiff() == 0);
        Assert.assertTrue(DAVE_RECIPIENT.getAccount() == null || DAVE_RECIPIENT.getFxtUnconfirmedBalanceDiff() == 0);

        Assert.assertEquals(75 * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtBalanceDiff());
        Assert.assertEquals(75 * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtUnconfirmedBalanceDiff());

    }

    @Test
    public void duplicateRecipientKeysDave() {
        JSONObject shufflingCreate = create(ALICE);
        String shufflingId = (String)shufflingCreate.get("transaction");
        String shufflingFullHash = (String)shufflingCreate.get("fullHash");
        generateBlock();
        register(shufflingFullHash, BOB);
        generateBlock();
        register(shufflingFullHash, CHUCK);
        generateBlock();
        register(shufflingFullHash, DAVE);
        generateBlock();

        JSONObject getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.PROCESSING.getCode(), getShufflingResponse.get("stage"));

        JSONObject getParticipantsResponse = getShufflingParticipants(shufflingId);
        JSONArray participants = (JSONArray)getParticipantsResponse.get("participants");
        Assert.assertEquals(4, participants.size());
        String shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(ALICE.getId()), shufflingAssignee);

        process(shufflingId, ALICE, ALICE_RECIPIENT);
        generateBlock();
        process(shufflingId, BOB, BOB_RECIPIENT);
        generateBlock();
        process(shufflingId, CHUCK, CHUCK_RECIPIENT);
        generateBlock();
        JSONObject processResponse = process(shufflingId, DAVE, DAVE_RECIPIENT, false);
        JSONObject transactionJSON = (JSONObject)processResponse.get("transactionJSON");
        JSONArray data = (JSONArray)((JSONObject)transactionJSON.get("attachment")).get("recipientPublicKeys");
        String s = (String)data.get(0);
        data.set(1, s);
        JSONObject broadcastResponse = broadcast(transactionJSON, DAVE);
        Assert.assertTrue(broadcastResponse.get("error") != null);
        for (int i = 0; i < 14; i++) {
            generateBlock();
        }

        getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.CANCELLED.getCode(), getShufflingResponse.get("stage"));
        shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(DAVE.getId()), shufflingAssignee);

        Assert.assertEquals(-11 * ChildChain.IGNIS.ONE_COIN, ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-11 * ChildChain.IGNIS.ONE_COIN, ALICE.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-11 * ChildChain.IGNIS.ONE_COIN, BOB.getFxtBalanceDiff());
        Assert.assertEquals(-11 * ChildChain.IGNIS.ONE_COIN, BOB.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-11 * ChildChain.IGNIS.ONE_COIN, CHUCK.getFxtBalanceDiff());
        Assert.assertEquals(-11 * ChildChain.IGNIS.ONE_COIN, CHUCK.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + ChildChain.IGNIS.ONE_COIN), DAVE.getFxtBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + ChildChain.IGNIS.ONE_COIN), DAVE.getFxtUnconfirmedBalanceDiff());

        Assert.assertNull(ALICE_RECIPIENT.getAccount());
        Assert.assertNull(BOB_RECIPIENT.getAccount());
        Assert.assertNull(CHUCK_RECIPIENT.getAccount());
        Assert.assertNull(DAVE_RECIPIENT.getAccount());

        Assert.assertEquals(34 * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtBalanceDiff());
        Assert.assertEquals(34 * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtUnconfirmedBalanceDiff());

    }

    @Test
    public void duplicateProcessDataChuck() {
        JSONObject shufflingCreate = create(ALICE);
        String shufflingId = (String)shufflingCreate.get("transaction");
        String shufflingFullHash = (String)shufflingCreate.get("fullHash");
        generateBlock();
        register(shufflingFullHash, BOB);
        generateBlock();
        register(shufflingFullHash, CHUCK);
        generateBlock();
        register(shufflingFullHash, DAVE);
        generateBlock();

        JSONObject getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.PROCESSING.getCode(), getShufflingResponse.get("stage"));

        JSONObject getParticipantsResponse = getShufflingParticipants(shufflingId);
        JSONArray participants = (JSONArray)getParticipantsResponse.get("participants");
        Assert.assertEquals(4, participants.size());
        String shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(ALICE.getId()), shufflingAssignee);

        process(shufflingId, ALICE, ALICE_RECIPIENT);
        generateBlock();
        process(shufflingId, BOB, BOB_RECIPIENT);
        generateBlock();
        JSONObject processResponse = process(shufflingId, CHUCK, CHUCK_RECIPIENT, false);
        JSONObject transactionJSON = (JSONObject)processResponse.get("transactionJSON");
        JSONArray data = (JSONArray)((JSONObject)transactionJSON.get("attachment")).get("data");
        String s = (String)data.get(0);
        data.set(1, s);
        JSONObject broadcastResponse = broadcast(transactionJSON, CHUCK);
        Assert.assertTrue(broadcastResponse.get("error") != null);
        for (int i = 0; i < 14; i++) {
            generateBlock();
        }

        getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.CANCELLED.getCode(), getShufflingResponse.get("stage"));
        shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(CHUCK.getId()), shufflingAssignee);

        Assert.assertEquals(-11 * ChildChain.IGNIS.ONE_COIN, ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-11 * ChildChain.IGNIS.ONE_COIN, ALICE.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-11 * ChildChain.IGNIS.ONE_COIN, BOB.getFxtBalanceDiff());
        Assert.assertEquals(-11 * ChildChain.IGNIS.ONE_COIN, BOB.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + ChildChain.IGNIS.ONE_COIN), CHUCK.getFxtBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + ChildChain.IGNIS.ONE_COIN), CHUCK.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-1 * ChildChain.IGNIS.ONE_COIN, DAVE.getFxtBalanceDiff());
        Assert.assertEquals(-1 * ChildChain.IGNIS.ONE_COIN, DAVE.getFxtUnconfirmedBalanceDiff());

        Assert.assertNull(ALICE_RECIPIENT.getAccount());
        Assert.assertNull(BOB_RECIPIENT.getAccount());
        Assert.assertNull(CHUCK_RECIPIENT.getAccount());
        Assert.assertNull(DAVE_RECIPIENT.getAccount());

        Assert.assertEquals(24 * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtBalanceDiff());
        Assert.assertEquals(24 * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtUnconfirmedBalanceDiff());

    }

    @Test
    public void duplicateRecipientsBobChuck() {
        JSONObject shufflingCreate = create(ALICE);
        String shufflingId = (String)shufflingCreate.get("transaction");
        String shufflingFullHash = (String)shufflingCreate.get("fullHash");
        generateBlock();
        register(shufflingFullHash, BOB);
        generateBlock();
        register(shufflingFullHash, CHUCK);
        generateBlock();
        register(shufflingFullHash, DAVE);
        generateBlock();

        JSONObject getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.PROCESSING.getCode(), getShufflingResponse.get("stage"));

        JSONObject getParticipantsResponse = getShufflingParticipants(shufflingId);
        JSONArray participants = (JSONArray)getParticipantsResponse.get("participants");
        Assert.assertEquals(4, participants.size());
        String shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(ALICE.getId()), shufflingAssignee);

        process(shufflingId, ALICE, ALICE_RECIPIENT);
        generateBlock();
        process(shufflingId, BOB, BOB_RECIPIENT);
        generateBlock();
        process(shufflingId, CHUCK, BOB_RECIPIENT);
        generateBlock();
        process(shufflingId, DAVE, DAVE_RECIPIENT);
        generateBlock();

        getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.BLAME.getCode(), getShufflingResponse.get("stage"));

        shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(DAVE.getId()), shufflingAssignee);
        String shufflingStateHash = (String)getShufflingResponse.get("shufflingStateHash");

        cancel(shufflingId, ALICE, shufflingStateHash, DAVE.getId());
        cancel(shufflingId, BOB, shufflingStateHash, DAVE.getId());
        cancel(shufflingId, CHUCK, shufflingStateHash, DAVE.getId());
        for (int i = 0; i < 14; i++) {
            generateBlock();
        }
        getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.CANCELLED.getCode(), getShufflingResponse.get("stage"));
        shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(CHUCK.getId()), shufflingAssignee);

        Assert.assertEquals(-21 * ChildChain.IGNIS.ONE_COIN, ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-21 * ChildChain.IGNIS.ONE_COIN, ALICE.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-21 * ChildChain.IGNIS.ONE_COIN, BOB.getFxtBalanceDiff());
        Assert.assertEquals(-21 * ChildChain.IGNIS.ONE_COIN, BOB.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 21 * ChildChain.IGNIS.ONE_COIN), CHUCK.getFxtBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 21 * ChildChain.IGNIS.ONE_COIN), CHUCK.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-12 * ChildChain.IGNIS.ONE_COIN, DAVE.getFxtBalanceDiff());
        Assert.assertEquals(-12 * ChildChain.IGNIS.ONE_COIN, DAVE.getFxtUnconfirmedBalanceDiff());

        Assert.assertNull(ALICE_RECIPIENT.getAccount());
        Assert.assertNull(BOB_RECIPIENT.getAccount());
        Assert.assertNull(CHUCK_RECIPIENT.getAccount());
        Assert.assertNull(DAVE_RECIPIENT.getAccount());

        Assert.assertEquals(75 * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtBalanceDiff());
        Assert.assertEquals(75 * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtUnconfirmedBalanceDiff());

    }

    @Test
    public void duplicateRecipientsAliceBob() {
        JSONObject shufflingCreate = create(ALICE);
        String shufflingId = (String)shufflingCreate.get("transaction");
        String shufflingFullHash = (String)shufflingCreate.get("fullHash");
        generateBlock();
        register(shufflingFullHash, BOB);
        generateBlock();
        register(shufflingFullHash, CHUCK);
        generateBlock();
        register(shufflingFullHash, DAVE);
        generateBlock();

        JSONObject getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.PROCESSING.getCode(), getShufflingResponse.get("stage"));

        JSONObject getParticipantsResponse = getShufflingParticipants(shufflingId);
        JSONArray participants = (JSONArray)getParticipantsResponse.get("participants");
        Assert.assertEquals(4, participants.size());
        String shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(ALICE.getId()), shufflingAssignee);

        process(shufflingId, ALICE, ALICE_RECIPIENT);
        generateBlock();
        process(shufflingId, BOB, ALICE_RECIPIENT);
        generateBlock();
        process(shufflingId, CHUCK, CHUCK_RECIPIENT);
        generateBlock();
        process(shufflingId, DAVE, DAVE_RECIPIENT);
        generateBlock();

        getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.BLAME.getCode(), getShufflingResponse.get("stage"));

        shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(DAVE.getId()), shufflingAssignee);
        String shufflingStateHash = (String)getShufflingResponse.get("shufflingStateHash");

        cancel(shufflingId, ALICE, shufflingStateHash, DAVE.getId());
        cancel(shufflingId, BOB, shufflingStateHash, DAVE.getId());
        cancel(shufflingId, CHUCK, shufflingStateHash, DAVE.getId());
        for (int i = 0; i < 14; i++) {
            generateBlock();
        }
        getShufflingResponse = getShuffling(shufflingId);
        Assert.assertEquals((long) ShufflingStage.CANCELLED.getCode(), getShufflingResponse.get("stage"));
        shufflingAssignee = (String) getShufflingResponse.get("assignee");
        Assert.assertEquals(Long.toUnsignedString(BOB.getId()), shufflingAssignee);

        Assert.assertEquals(-21 * ChildChain.IGNIS.ONE_COIN, ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-21 * ChildChain.IGNIS.ONE_COIN, ALICE.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 21 * ChildChain.IGNIS.ONE_COIN), BOB.getFxtBalanceDiff());
        Assert.assertEquals(-(ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT + 21 * ChildChain.IGNIS.ONE_COIN), BOB.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-21 * ChildChain.IGNIS.ONE_COIN, CHUCK.getFxtBalanceDiff());
        Assert.assertEquals(-21 * ChildChain.IGNIS.ONE_COIN, CHUCK.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(-12 * ChildChain.IGNIS.ONE_COIN, DAVE.getFxtBalanceDiff());
        Assert.assertEquals(-12 * ChildChain.IGNIS.ONE_COIN, DAVE.getFxtUnconfirmedBalanceDiff());

        Assert.assertNull(ALICE_RECIPIENT.getAccount());
        Assert.assertNull(BOB_RECIPIENT.getAccount());
        Assert.assertNull(CHUCK_RECIPIENT.getAccount());
        Assert.assertNull(DAVE_RECIPIENT.getAccount());

        Assert.assertEquals(75 * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtBalanceDiff());
        Assert.assertEquals(75 * ChildChain.IGNIS.ONE_COIN + ChildChain.IGNIS.SHUFFLING_DEPOSIT_NQT, FORGY.getFxtUnconfirmedBalanceDiff());

    }

}
