package nxt.http.assetexchange;

import nxt.BlockchainTest;
import nxt.Nxt;
import nxt.Tester;
import nxt.account.HoldingType;
import nxt.blockchain.Chain;
import nxt.blockchain.ChildChain;
import nxt.http.APICall;
import nxt.http.monetarysystem.TestCurrencyIssuance;
import org.json.simple.JSONObject;
import org.junit.Assert;
import org.junit.Test;

public class AssetExchangeTest extends BlockchainTest {

    private JSONObject issueAsset(Tester creator, String name) {
        APICall apiCall = new APICall.Builder("issueAsset")
                .param("secretPhrase", creator.getSecretPhrase())
                .param("name", name)
                .param("description", "asset testing")
                .param("quantityQNT", 10000000)
                .param("decimals", 4)
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

    private JSONObject payDividened(String assetId, Tester assetIssuer, int height, long amountNQTPerShare, Chain chain, byte holdingType, String holding) {
        APICall apiCall = new APICall.Builder("dividendPayment")
                .param("secretPhrase", assetIssuer.getSecretPhrase())
                .param("asset", assetId)
                .param("height", height)
                .param("holdingType", holdingType)
                .param("holding", holding)
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
        JSONObject response = issueAsset(ALICE, "divSender");
        String assetId = Tester.responseToStringId(response);
        transfer(assetId, ALICE, BOB, 300 * 10000);
        transfer(assetId, ALICE, CHUCK, 200 * 10000);
        transfer(assetId, ALICE, DAVE, 100 * 10000);
        generateBlock();

        // Pay dividend in IGNIS, nice and round
        int chainId = ChildChain.IGNIS.getId();
        payDividened(assetId, ALICE, Nxt.getBlockchain().getHeight(), 1000000L, ChildChain.IGNIS, HoldingType.COIN.getCode(), "");
        generateBlock();
        Assert.assertEquals(3 * ChildChain.IGNIS.ONE_COIN, BOB.getChainBalanceDiff(chainId));
        Assert.assertEquals(2 * ChildChain.IGNIS.ONE_COIN, CHUCK.getChainBalanceDiff(chainId));
        Assert.assertEquals(ChildChain.IGNIS.ONE_COIN, DAVE.getChainBalanceDiff(chainId));
    }

    @Test
    public void usdDividend() {
        JSONObject response = issueAsset(RIKER, "divSender");
        String assetId = Tester.responseToStringId(response);
        transfer(assetId, RIKER, BOB, 5555555);
        transfer(assetId, RIKER, CHUCK, 2222222);
        transfer(assetId, RIKER, DAVE, 1111111);
        generateBlock();

        int chainId = ChildChain.USD.getId();
        payDividened(assetId, RIKER, Nxt.getBlockchain().getHeight(), 1L, ChildChain.USD, HoldingType.COIN.getCode(), "");
        generateBlock();
        Assert.assertEquals(-555-222-111 - ChildChain.USD.ONE_COIN, RIKER.getChainBalanceDiff(chainId));
        Assert.assertEquals(555, BOB.getChainBalanceDiff(chainId));
        Assert.assertEquals(222, CHUCK.getChainBalanceDiff(chainId));
        Assert.assertEquals(111, DAVE.getChainBalanceDiff(chainId));
    }

    @Test
    public void assetDividend() {
        JSONObject senderAsset = issueAsset(RIKER, "divSender");
        String assetId = Tester.responseToStringId(senderAsset);
        transfer(assetId, RIKER, BOB, 5555555);
        transfer(assetId, RIKER, CHUCK, 2222222);
        transfer(assetId, RIKER, DAVE, 1111111);
        JSONObject receiverAsset = issueAsset(RIKER, "divRecv");
        String receiverId = (Tester.responseToStringId(receiverAsset));
        generateBlock();

        payDividened(assetId, RIKER, Nxt.getBlockchain().getHeight(), 1L, ChildChain.USD, HoldingType.ASSET.getCode(), receiverId);
        generateBlock();
        Assert.assertEquals(10000000-555-222-111, RIKER.getAssetQuantityDiff(Long.parseUnsignedLong(receiverId)));
        Assert.assertEquals(555, BOB.getAssetQuantityDiff(Long.parseUnsignedLong(receiverId)));
        Assert.assertEquals(222, CHUCK.getAssetQuantityDiff(Long.parseUnsignedLong(receiverId)));
        Assert.assertEquals(111, DAVE.getAssetQuantityDiff(Long.parseUnsignedLong(receiverId)));
    }

    @Test
    public void currencyDividend() {
        JSONObject senderAsset = issueAsset(ALICE, "divSender");
        String assetId = Tester.responseToStringId(senderAsset);
        transfer(assetId, ALICE, BOB, 5555555);
        transfer(assetId, ALICE, CHUCK, 2222222);
        transfer(assetId, ALICE, DAVE, 1111111);
        String currencyId = new TestCurrencyIssuance().issueCurrencyImpl();
        generateBlock();

        payDividened(assetId, ALICE, Nxt.getBlockchain().getHeight(), 1L, ChildChain.USD, HoldingType.CURRENCY.getCode(), currencyId);
        generateBlock();
        Assert.assertEquals(100000-555-222-111, ALICE.getCurrencyUnitsDiff(Long.parseUnsignedLong(currencyId)));
        Assert.assertEquals(555, BOB.getCurrencyUnitsDiff(Long.parseUnsignedLong(currencyId)));
        Assert.assertEquals(222, CHUCK.getCurrencyUnitsDiff(Long.parseUnsignedLong(currencyId)));
        Assert.assertEquals(111, DAVE.getCurrencyUnitsDiff(Long.parseUnsignedLong(currencyId)));
    }

}
