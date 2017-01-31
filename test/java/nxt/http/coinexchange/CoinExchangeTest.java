package nxt.http.coinexchange;

import nxt.BlockchainTest;
import nxt.Tester;
import nxt.blockchain.ChildChain;
import nxt.http.APICall;
import nxt.util.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import static nxt.blockchain.ChildChain.IGNIS;
import static nxt.blockchain.ChildChain.USD;

public class CoinExchangeTest extends BlockchainTest {

    @Test
    public void simpleExchange() {
        // Want to buy 25 USD with a maximum price of 4 IGNIS per USD
        // Convert the amount to IGNIS
        long displayUsdAmount = 25;
        long quantityQNT = displayUsdAmount * USD.ONE_COIN;
        long displayIgnisPerUsdPrice = 4;
        long priceNQT = displayIgnisPerUsdPrice * IGNIS.ONE_COIN;

        // Submit request to buy 25 USD with a maximum price of 4 IGNIS per USD
        // Quantity is denominated in USD and price is denominated in IGNIS per whole USD
        APICall apiCall = new APICall.Builder("exchangeCoins").
                secretPhrase(ALICE.getSecretPhrase()).
                param("feeRateNQTPerFXT", IGNIS.ONE_COIN).
                param("chain", IGNIS.getId()).
                param("exchange", USD.getId()).
                param("quantityQNT", quantityQNT).
                param("priceNQT", priceNQT).
                build();
        JSONObject response = apiCall.invoke();
        Logger.logDebugMessage("exchangeCoins: " + response);
        generateBlock();

        JSONObject transactionJSON = (JSONObject)response.get("transactionJSON");
        String orderId = Tester.responseToStringId(transactionJSON);
        apiCall = new APICall.Builder("getCoinExchangeOrder").
                param("order", orderId).
                build();
        response = apiCall.invoke();
        Assert.assertEquals(Long.toString(25 * USD.ONE_COIN), response.get("quantityQNT"));
        Assert.assertEquals(Long.toString(4 * IGNIS.ONE_COIN), response.get("bidNQT"));
        Assert.assertEquals(Long.toString((long)(1.0 / 4 * USD.ONE_COIN)), response.get("askNQT"));

        // Want to buy 110 IGNIS with a maximum price of 1/4 USD per IGNIS
        // Quantity is denominated in IGNIS price is denominated in USD per whole IGNIS
        apiCall = new APICall.Builder("exchangeCoins").
                secretPhrase(BOB.getSecretPhrase()).
                param("feeRateNQTPerFXT", USD.ONE_COIN).
                param("chain", USD.getId()).
                param("exchange", IGNIS.getId()).
                param("quantityQNT", 100 * IGNIS.ONE_COIN + 10 * IGNIS.ONE_COIN).
                param("priceNQT", USD.ONE_COIN / 4).
                build();
        response = apiCall.invoke();
        Logger.logDebugMessage("exchangeCoins: " + response);
        generateBlock();

        transactionJSON = (JSONObject)response.get("transactionJSON");
        orderId = Tester.responseToStringId(transactionJSON);
        apiCall = new APICall.Builder("getCoinExchangeOrder").
                param("order", orderId).
                build();
        response = apiCall.invoke();
        Assert.assertEquals(Long.toString(10 * IGNIS.ONE_COIN), response.get("quantityQNT")); // leftover after the exchange of 100
        Assert.assertEquals(Long.toString((long) (0.25 * USD.ONE_COIN)), response.get("bidNQT"));
        Assert.assertEquals(Long.toString(4 * IGNIS.ONE_COIN), response.get("askNQT"));

        // Now look at the resulting trades
        apiCall = new APICall.Builder("getCoinExchangeTrades").
                param("chain", ChildChain.USD.getId()).
                param("account", BOB.getRsAccount()).
                build();
        response = apiCall.invoke();
        Logger.logDebugMessage("GetCoinExchangeTrades: " + response);

        // Bob received 100 IGNIS and paid 0.25 USD per IGNIS
        JSONArray trades = (JSONArray) response.get("trades");
        JSONObject trade = (JSONObject) trades.get(0);
        Assert.assertEquals(USD.getId(), (int)(long)trade.get("chain"));
        Assert.assertEquals(IGNIS.getId(), (int)(long)trade.get("exchange"));
        Assert.assertEquals("" + (100 * IGNIS.ONE_COIN), trade.get("quantityQNT")); // IGNIS bought
        Assert.assertEquals("" + (long)(0.25 * USD.ONE_COIN), trade.get("priceNQT")); // USD per IGNIS price

        apiCall = new APICall.Builder("getCoinExchangeTrades").
                param("chain", IGNIS.getId()).
                param("account", ALICE.getRsAccount()).
                build();
        response = apiCall.invoke();
        Logger.logDebugMessage("GetCoinExchangeTrades: " + response);

        // Alice received 25 USD and paid 4 IGNIS per USD
        trades = (JSONArray) response.get("trades");
        trade = (JSONObject) trades.get(0);
        Assert.assertEquals(IGNIS.getId(), (int)(long)trade.get("chain"));
        Assert.assertEquals(USD.getId(), (int)(long)trade.get("exchange"));
        Assert.assertEquals("" + (25 * USD.ONE_COIN), trade.get("quantityQNT")); // USD bought
        Assert.assertEquals("" + (4 * IGNIS.ONE_COIN), trade.get("priceNQT")); // IGNIS per USD price

        Assert.assertEquals(-100 * IGNIS.ONE_COIN - IGNIS.ONE_COIN / 10, ALICE.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(25 * USD.ONE_COIN, ALICE.getChainBalanceDiff(USD.getId()));
        Assert.assertEquals(100 * IGNIS.ONE_COIN, BOB.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(-25 * USD.ONE_COIN - USD.ONE_COIN / 10, BOB.getChainBalanceDiff(USD.getId()));
    }

    @Test
    public void ronsSample() {
        long usdToBuy = 5 * USD.ONE_COIN;
        long ignisPerWholeUsd = (long) (0.75 * IGNIS.ONE_COIN);

        APICall apiCall = new APICall.Builder("exchangeCoins").
                secretPhrase(ALICE.getSecretPhrase()).
                param("feeRateNQTPerFXT", IGNIS.ONE_COIN).
                param("chain", IGNIS.getId()).
                param("exchange", USD.getId()).
                param("quantityQNT", usdToBuy).
                param("priceNQT", ignisPerWholeUsd).
                build();
        JSONObject response = apiCall.invoke();
        String aliceOrder = Tester.responseToStringId(response);
        generateBlock();

        long ignisToBuy = 5 * IGNIS.ONE_COIN;
        long usdPerWholeIgnis = (long) (1.35 * USD.ONE_COIN);

        apiCall = new APICall.Builder("exchangeCoins").
                secretPhrase(BOB.getSecretPhrase()).
                param("feeRateNQTPerFXT", USD.ONE_COIN).
                param("chain", USD.getId()).
                param("exchange", IGNIS.getId()).
                param("quantityQNT", ignisToBuy).
                param("priceNQT", usdPerWholeIgnis).
                build();
        response = apiCall.invoke();
        String bobOrder = Tester.responseToStringId(response);
        generateBlock();

        Assert.assertEquals((long)(-3.75 * IGNIS.ONE_COIN) - IGNIS.ONE_COIN / 10, ALICE.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(5 * USD.ONE_COIN, ALICE.getChainBalanceDiff(USD.getId()));
        Assert.assertEquals((long)(3.75 * IGNIS.ONE_COIN), BOB.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(-5 * USD.ONE_COIN - USD.ONE_COIN / 10, BOB.getChainBalanceDiff(USD.getId()));

        apiCall = new APICall.Builder("getCoinExchangeOrder").
                param("order", aliceOrder).
                build();
        response = apiCall.invoke();
        Assert.assertEquals(5L, response.get("errorCode"));

        apiCall = new APICall.Builder("getCoinExchangeOrder").
                param("order", bobOrder).
                build();
        response = apiCall.invoke();
        Assert.assertEquals((long)(1.25 * IGNIS.ONE_COIN), Long.parseLong((String) response.get("quantityQNT")));
        Assert.assertEquals((long)(1.35 * USD.ONE_COIN), Long.parseLong((String) response.get("bidNQT")));
        Assert.assertEquals((long)(0.74074074 * IGNIS.ONE_COIN), Long.parseLong((String) response.get("askNQT")));
    }

}
