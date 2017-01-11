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

package nxt.http;

import nxt.BlockchainTest;
import nxt.blockchain.ChildChain;
import nxt.util.Logger;
import org.json.simple.JSONObject;
import org.junit.Assert;
import org.junit.Test;

public class SendMoneyTest extends BlockchainTest {

    @Test
    public void sendMoney() {
        JSONObject response = new APICall.Builder("sendMoney").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountNQT", 100 * ChildChain.IGNIS.ONE_COIN).
                param("feeNQT", ChildChain.IGNIS.ONE_COIN).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        // Forger
        Assert.assertEquals(0, FORGY.getFxtBalanceDiff());
        Assert.assertEquals(0, FORGY.getFxtUnconfirmedBalanceDiff());
        // Sender
        Assert.assertEquals(0, ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-100 * ChildChain.IGNIS.ONE_COIN - ChildChain.IGNIS.ONE_COIN, ALICE.getFxtUnconfirmedBalanceDiff());
        // Recipient
        Assert.assertEquals(0, BOB.getFxtBalanceDiff());
        Assert.assertEquals(0, BOB.getFxtUnconfirmedBalanceDiff());
        generateBlock();
        // Forger
        Assert.assertEquals(ChildChain.IGNIS.ONE_COIN, FORGY.getFxtBalanceDiff());
        Assert.assertEquals(ChildChain.IGNIS.ONE_COIN, FORGY.getFxtUnconfirmedBalanceDiff());
        // Sender
        Assert.assertEquals(-100 * ChildChain.IGNIS.ONE_COIN - ChildChain.IGNIS.ONE_COIN, ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-100 * ChildChain.IGNIS.ONE_COIN - ChildChain.IGNIS.ONE_COIN, ALICE.getFxtUnconfirmedBalanceDiff());
        // Recipient
        Assert.assertEquals(100 * ChildChain.IGNIS.ONE_COIN, BOB.getFxtBalanceDiff());
        Assert.assertEquals(100 * ChildChain.IGNIS.ONE_COIN, BOB.getFxtUnconfirmedBalanceDiff());
    }

    @Test
    public void sendTooMuchMoney() {
        JSONObject response = new APICall.Builder("sendMoney").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountNQT", ALICE.getInitialFxtBalance()).
                param("feeNQT", ChildChain.IGNIS.ONE_COIN).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        Assert.assertEquals((long)6, response.get("errorCode"));
    }

    @Test
    public void sendAndReturn() {
        JSONObject response = new APICall.Builder("sendMoney").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountNQT", 100 * ChildChain.IGNIS.ONE_COIN).
                param("feeNQT", ChildChain.IGNIS.ONE_COIN).
                build().invoke();
        Logger.logDebugMessage("sendMoney1: " + response);
        response = new APICall.Builder("sendMoney").
                param("secretPhrase", BOB.getSecretPhrase()).
                param("recipient", ALICE.getStrId()).
                param("amountNQT", 100 * ChildChain.IGNIS.ONE_COIN).
                param("feeNQT", ChildChain.IGNIS.ONE_COIN).
                build().invoke();
        Logger.logDebugMessage("sendMoney2: " + response);
        // Forger
        Assert.assertEquals(0, FORGY.getFxtBalanceDiff());
        Assert.assertEquals(0, FORGY.getFxtUnconfirmedBalanceDiff());
        // Sender
        Assert.assertEquals(0, ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-100 * ChildChain.IGNIS.ONE_COIN - ChildChain.IGNIS.ONE_COIN, ALICE.getFxtUnconfirmedBalanceDiff());
        // Recipient
        Assert.assertEquals(0, BOB.getFxtBalanceDiff());
        Assert.assertEquals(-100 * ChildChain.IGNIS.ONE_COIN - ChildChain.IGNIS.ONE_COIN, BOB.getFxtUnconfirmedBalanceDiff());
        generateBlock();
        // Forger
        Assert.assertEquals(2* ChildChain.IGNIS.ONE_COIN, FORGY.getFxtBalanceDiff());
        Assert.assertEquals(2* ChildChain.IGNIS.ONE_COIN, FORGY.getFxtUnconfirmedBalanceDiff());
        // Sender
        Assert.assertEquals(-ChildChain.IGNIS.ONE_COIN, ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-ChildChain.IGNIS.ONE_COIN, ALICE.getFxtUnconfirmedBalanceDiff());
        // Recipient
        Assert.assertEquals(-ChildChain.IGNIS.ONE_COIN, BOB.getFxtBalanceDiff());
        Assert.assertEquals(-ChildChain.IGNIS.ONE_COIN, BOB.getFxtUnconfirmedBalanceDiff());
    }

    @Test
    public void signAndBroadcastBytes() {
        JSONObject response = new APICall.Builder("sendMoney").
                param("publicKey", ALICE.getPublicKeyStr()).
                param("recipient", BOB.getStrId()).
                param("amountNQT", 100 * ChildChain.IGNIS.ONE_COIN).
                param("feeNQT", ChildChain.IGNIS.ONE_COIN).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        generateBlock();
        // No change transaction not broadcast
        Assert.assertEquals(0, ALICE.getFxtBalanceDiff());
        Assert.assertEquals(0, ALICE.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(0, BOB.getFxtBalanceDiff());
        Assert.assertEquals(0, BOB.getFxtUnconfirmedBalanceDiff());

        response = new APICall.Builder("signTransaction").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("unsignedTransactionBytes", (String)response.get("unsignedTransactionBytes")).
                build().invoke();
        Logger.logDebugMessage("signTransaction: " + response);

        response = new APICall.Builder("broadcastTransaction").
                param("transactionBytes", (String)response.get("transactionBytes")).
                build().invoke();
        Logger.logDebugMessage("broadcastTransaction: " + response);
        generateBlock();

        // Sender
        Assert.assertEquals(-100 * ChildChain.IGNIS.ONE_COIN - ChildChain.IGNIS.ONE_COIN, ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-100 * ChildChain.IGNIS.ONE_COIN - ChildChain.IGNIS.ONE_COIN, ALICE.getFxtUnconfirmedBalanceDiff());
        // Recipient
        Assert.assertEquals(100 * ChildChain.IGNIS.ONE_COIN, BOB.getFxtBalanceDiff());
        Assert.assertEquals(100 * ChildChain.IGNIS.ONE_COIN, BOB.getFxtUnconfirmedBalanceDiff());
    }

    @Test
    public void signAndBroadcastJSON() {
        JSONObject response = new APICall.Builder("sendMoney").
                param("publicKey", ALICE.getPublicKeyStr()).
                param("recipient", BOB.getStrId()).
                param("amountNQT", 100 * ChildChain.IGNIS.ONE_COIN).
                param("feeNQT", ChildChain.IGNIS.ONE_COIN).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        generateBlock();
        // No change transaction not broadcast
        Assert.assertEquals(0, ALICE.getFxtBalanceDiff());
        Assert.assertEquals(0, ALICE.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(0, BOB.getFxtBalanceDiff());
        Assert.assertEquals(0, BOB.getFxtUnconfirmedBalanceDiff());

        response = new APICall.Builder("signTransaction").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("unsignedTransactionJSON", response.get("transactionJSON").toString()).
                build().invoke();
        Logger.logDebugMessage("signTransaction: " + response);

        response = new APICall.Builder("broadcastTransaction").
                param("transactionBytes", (String)response.get("transactionBytes")).
                build().invoke();
        Logger.logDebugMessage("broadcastTransaction: " + response);
        generateBlock();

        // Sender
        Assert.assertEquals(-100 * ChildChain.IGNIS.ONE_COIN - ChildChain.IGNIS.ONE_COIN, ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-100 * ChildChain.IGNIS.ONE_COIN - ChildChain.IGNIS.ONE_COIN, ALICE.getFxtUnconfirmedBalanceDiff());
        // Recipient
        Assert.assertEquals(100 * ChildChain.IGNIS.ONE_COIN, BOB.getFxtBalanceDiff());
        Assert.assertEquals(100 * ChildChain.IGNIS.ONE_COIN, BOB.getFxtUnconfirmedBalanceDiff());
    }
}
