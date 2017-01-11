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

package nxt;

import nxt.blockchain.*;
import nxt.http.APICall;
import nxt.util.Convert;
import nxt.util.Logger;
import nxt.util.Time;
import org.json.simple.JSONObject;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;

import java.util.Properties;

public abstract class BlockchainTest extends AbstractBlockchainTest {

    protected static Tester FORGY;
    protected static Tester ALICE;
    protected static Tester BOB;
    protected static Tester CHUCK;
    protected static Tester DAVE;

    protected static int baseHeight;

    private static String forgerSecretPhrase = "aSykrgKGZNlSVOMDxkZZgbTvQqJPGtsBggb";
    private static final String forgerAccountId = "NXT-9KZM-KNYY-QBXZ-5TD8V";

    private static final String aliceSecretPhrase = "hope peace happen touch easy pretend worthless talk them indeed wheel state";
    private static final String bobSecretPhrase2 = "rshw9abtpsa2";
    private static final String chuckSecretPhrase = "eOdBVLMgySFvyiTy8xMuRXDTr45oTzB7L5J";
    private static final String daveSecretPhrase = "t9G2ymCmDsQij7VtYinqrbGCOAtDDA3WiNr";

    private static boolean isNxtInitialized = false;
    private static boolean needShutdownAfterClass = false;

    public static void initNxt() {
        if (!isNxtInitialized) {
            Properties properties = ManualForgingTest.newTestProperties();
            properties.setProperty("nxt.isTestnet", "true");
            properties.setProperty("nxt.isOffline", "true");
            properties.setProperty("nxt.enableFakeForging", "true");
            properties.setProperty("nxt.fakeForgingAccount", forgerAccountId);
            properties.setProperty("nxt.timeMultiplier", "1");
            properties.setProperty("nxt.testnetGuaranteedBalanceConfirmations", "1");
            properties.setProperty("nxt.testnetLeasingDelay", "1");
            properties.setProperty("nxt.disableProcessTransactionsThread", "true");
            properties.setProperty("nxt.deleteFinishedShufflings", "false");
            AbstractForgingTest.init(properties);
            isNxtInitialized = true;
        }
    }
    
    @BeforeClass
    public static void init() {
        needShutdownAfterClass = !isNxtInitialized;
        initNxt();
        
        Nxt.setTime(new Time.CounterTime(Nxt.getEpochTime()));
        baseHeight = blockchain.getHeight();
        Logger.logMessage("baseHeight: " + baseHeight);
        FORGY = new Tester(forgerSecretPhrase);
        ALICE = new Tester(aliceSecretPhrase);
        BOB = new Tester(bobSecretPhrase2);
        CHUCK = new Tester(chuckSecretPhrase);
        DAVE = new Tester(daveSecretPhrase);

        startBundlers();
    }

    private static void startBundlers() {
        for (Chain chain : ChildChain.getAll()) {
            long factor = Convert.decimalMultiplier(FxtChain.getChain(1).getDecimals() - chain.getDecimals());
            JSONObject response = new APICall.Builder("startBundler").
                    secretPhrase(FORGY.getSecretPhrase()).
                    param("chain", chain.getId()).
                    param("minRateNQTPerFXT", chain.ONE_COIN / factor).
                    param("totalFeesLimitFQT", 1000 * chain.ONE_COIN * factor). // allow 1000 default fee transactions per class
                    param("overpayFQTPerFXT", 0).
                    build().invoke();
            Logger.logDebugMessage("startBundler: " + response);
        }
    }

    @AfterClass
    public static void shutdown() {
        if (needShutdownAfterClass) {
            Nxt.shutdown();
        }
    }

    @After
    public void destroy() {
        TransactionProcessorImpl.getInstance().clearUnconfirmedTransactions();
        blockchainProcessor.popOffTo(baseHeight);
        shutdown();
    }

    public static void generateBlock() {
        try {
            blockchainProcessor.generateBlock(forgerSecretPhrase, Nxt.getEpochTime());
        } catch (BlockchainProcessor.BlockNotAcceptedException e) {
            e.printStackTrace();
            Assert.fail();
        }
    }

    protected static void generateBlocks(int howMany) {
        for (int i = 0; i < howMany; i++) {
            generateBlock();
        }
    }
}
