package nxt.http.assetexchange;

import nxt.BlockchainTest;
import nxt.Nxt;
import nxt.Tester;
import nxt.blockchain.Chain;
import nxt.blockchain.ChildChain;
import nxt.http.APICall;
import org.json.simple.JSONObject;
import org.junit.Assert;
import org.junit.Test;

public class AssetExchangeTest extends BlockchainTest {

    private JSONObject issueAsset(Tester creator) {
        APICall apiCall = new APICall.Builder("issueAsset")
                .param("secretPhrase", creator.getSecretPhrase())
                .param("name", "asset1")
                .param("description", "asset testing")
                .param("quantityQNT", 100000)
                .param("decimals", 2)
                .param("feeNQT", 1000 * ChildChain.IGNIS.ONE_COIN)
                .param("deadline", 1440)
                .build();
        JSONObject response = apiCall.invoke();
        BlockchainTest.generateBlock();
        return response;
    }

    private JSONObject transfer(String assetId, Tester from, Tester to, long quantityQNT) {
        APICall apiCall = new APICall.Builder("transferAsset")
                .param("secretPhrase", from.getSecretPhrase())
                .param("recipient", to.getRsAccount())
                .param("asset", assetId)
                .param("description", "asset testing")
                .param("quantityQNT", quantityQNT)
                .param("feeNQT", ChildChain.IGNIS.ONE_COIN)
                .build();
        JSONObject response = apiCall.invoke();
        BlockchainTest.generateBlock();
        return response;
    }

    private JSONObject payDividened(String assetId, Tester assetIssuer,int height, long amountNQTPerShare, Chain chain) {
        APICall apiCall = new APICall.Builder("dividendPayment")
                .param("secretPhrase", assetIssuer.getSecretPhrase())
                .param("asset", assetId)
                .param("height", height)
                .param("amountNQTPerShare", amountNQTPerShare)
                .param("feeNQT", chain.ONE_COIN)
                .chain(chain.getId())
                .build();
        JSONObject response = apiCall.invoke();
        BlockchainTest.generateBlock();
        return response;
    }

    @Test
    public void ignisDividend() {
        JSONObject response = issueAsset(ALICE);
        String assetId = Tester.responseToStringId(response);
        transfer(assetId, ALICE, BOB, 300 * 100);
        transfer(assetId, ALICE, CHUCK, 200 * 100);
        transfer(assetId, ALICE, DAVE, 100 * 100);
        generateBlock();

        // Pay dividend in IGNIS, nice and round
        int chainId = ChildChain.IGNIS.getId();
        payDividened(assetId, ALICE, Nxt.getBlockchain().getHeight(), 1000000L, ChildChain.IGNIS);
        generateBlock();
        Assert.assertEquals(3 * ChildChain.IGNIS.ONE_COIN, BOB.getChainBalanceDiff(chainId));
        Assert.assertEquals(2 * ChildChain.IGNIS.ONE_COIN, CHUCK.getChainBalanceDiff(chainId));
        Assert.assertEquals(ChildChain.IGNIS.ONE_COIN, DAVE.getChainBalanceDiff(chainId));
    }

    @Test
    public void usdDividend() {
        JSONObject response = issueAsset(RIKER);
        String assetId = Tester.responseToStringId(response);
        transfer(assetId, RIKER, BOB, 300 * 100);
        transfer(assetId, RIKER, CHUCK, 200 * 100);
        transfer(assetId, RIKER, DAVE, 100 * 100);
        generateBlock();

        int chainId = ChildChain.USD.getId();
        payDividened(assetId, RIKER, Nxt.getBlockchain().getHeight(), 1L, ChildChain.USD);
        generateBlock();
        Assert.assertEquals(300, BOB.getChainBalanceDiff(chainId));
        Assert.assertEquals(200, CHUCK.getChainBalanceDiff(chainId));
        Assert.assertEquals(100, DAVE.getChainBalanceDiff(chainId));
    }

}
