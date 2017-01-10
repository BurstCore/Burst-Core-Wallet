package nxt.http.coinexchange;

import nxt.BlockchainTest;
import nxt.blockchain.ChildChain;
import nxt.http.APICall;
import nxt.util.Logger;
import org.json.simple.JSONObject;
import org.junit.Test;

import static nxt.blockchain.ChildChain.USD;

public class CoinExchangeTest extends BlockchainTest {

    @Test
    public void simpleExchange() {
        APICall apiCall = new APICall.Builder("exchangeCoins").
                secretPhrase(ALICE.getSecretPhrase()).
                feeNQT(ChildChain.IGNIS.ONE_COIN).
                param("chain", ChildChain.IGNIS.getId()).
                param("exchange", ChildChain.USD.getId()).
                param("amountNQT", 100 * USD.ONE_COIN).
                param("priceNQT", 2 * ChildChain.IGNIS.ONE_COIN).
                build();
        JSONObject response = apiCall.invoke();
        Logger.logDebugMessage("exchangeCoins: " + response);
        generateBlock();

        apiCall = new APICall.Builder("exchangeCoins").
                secretPhrase(BOB.getSecretPhrase()).
                feeNQT(ChildChain.USD.ONE_COIN).
                param("chain", ChildChain.USD.getId()).
                param("exchange", ChildChain.IGNIS.getId()).
                param("amountNQT", 200 * USD.ONE_COIN).
                param("priceNQT", ChildChain.USD.ONE_COIN / 2).
                build();
        response = apiCall.invoke();
        Logger.logDebugMessage("exchangeCoins: " + response);
        generateBlock();

        apiCall = new APICall.Builder("getCoinExchangeTrades").
                param("chain", ChildChain.USD.getId()).
                build();
        response = apiCall.invoke();
        Logger.logDebugMessage("GetCoinExchangeTrades: " + response);

        // TODO check the exchange properties
    }
}
