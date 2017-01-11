package nxt.http.coinexchange;

import nxt.BlockchainTest;
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
        // Want to buy 100 IGNIS worth of USD with a maximum price of 4 IGNIS per USD
        // Both amount and price are denominated in IGNIS
        APICall apiCall = new APICall.Builder("exchangeCoins").
                secretPhrase(ALICE.getSecretPhrase()).
                feeNQT(0).
                param("feeRateNQTPerFXT", IGNIS.ONE_COIN).
                param("chain", IGNIS.getId()).
                param("exchange", USD.getId()).
                param("amountNQT", 100 * IGNIS.ONE_COIN).
                param("priceNQT", 4 * IGNIS.ONE_COIN).
                build();
        JSONObject response = apiCall.invoke();
        Logger.logDebugMessage("exchangeCoins: " + response);
        generateBlock();

        // Want to buy 25 USD worth of IGNIS with a maximum price of 1/4 USD per IGNIS
        // Both amount and price are denominated in USD
        apiCall = new APICall.Builder("exchangeCoins").
                secretPhrase(BOB.getSecretPhrase()).
                feeNQT(0).
                param("feeRateNQTPerFXT", USD.ONE_COIN).
                param("chain", USD.getId()).
                param("exchange", IGNIS.getId()).
                param("amountNQT", 25 * USD.ONE_COIN).
                param("priceNQT", USD.ONE_COIN / 4).
                build();
        response = apiCall.invoke();
        Logger.logDebugMessage("exchangeCoins: " + response);
        generateBlock();

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
        Assert.assertEquals(4L, trade.get("chain"));
        Assert.assertEquals(2L, trade.get("exchange"));
        Assert.assertEquals("" + (100 * IGNIS.ONE_COIN), trade.get("amountNQT")); // IGNIS bought
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
        Assert.assertEquals(2L, trade.get("chain"));
        Assert.assertEquals(4L, trade.get("exchange"));
        Assert.assertEquals("" + (25 * USD.ONE_COIN), trade.get("amountNQT")); // IGNIS bought
        Assert.assertEquals("" + (4 * IGNIS.ONE_COIN), trade.get("priceNQT")); // Total USD paid?

        Assert.assertEquals(-100 * IGNIS.ONE_COIN - IGNIS.ONE_COIN / 10, ALICE.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(25 * USD.ONE_COIN, ALICE.getChainBalanceDiff(USD.getId()));
        Assert.assertEquals(100 * IGNIS.ONE_COIN, BOB.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(-25 * USD.ONE_COIN - USD.ONE_COIN / 10, BOB.getChainBalanceDiff(USD.getId()));
    }
}
