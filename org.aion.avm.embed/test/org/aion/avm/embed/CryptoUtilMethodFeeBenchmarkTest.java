package org.aion.avm.embed;

import org.aion.avm.core.*;
import org.aion.types.AionAddress;
import org.aion.types.Transaction;
import org.aion.avm.core.dappreading.UserlibJarBuilder;
import org.aion.avm.tooling.ABIUtil;
import org.aion.avm.userlib.CodeAndArguments;
import org.aion.kernel.*;
import org.aion.types.TransactionResult;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;

/**
 * To more accurately benchmark the amount of time(resource) used for each crypto method calls,
 * we will use the following mechanism for each test:
 * - Make a few calls to method warm up the system.
 * - Make several individual calls to a single method and record the time in ns.
 *      - Average these times to get a relatively accurate setup time.
 * - Make large number of calls to a single method and benchmark the result.
 *      - Remove the setup time before averaging the time used for each call.
 *
 * Note:
 * - This test suite does explicitly cover correctness.
 * - All counters are set to 1 to avoid excess time used when running test over the whole AVM.
 * - In general, time/call will decrease logarithmically.
 *
 * Please be aware that these benchmark will have some inaccuracies, if you would like a even more
 * precise benchmark consider the following:
 * - using a profiler software such as visualvm
 * - record the times in DAppExecutor class, example [surrounding: "byte[] ret = dapp.callMain();"]
 *      long st, et;
 *      st = System.nanoTime();
 *      byte[] ret = dapp.callMain();
 *      et = System.nanoTime();
 *      System.out.println(et-st + "");
 *
 * Our summary of from the benchmark:
 * - the 3 hash functions have relatively similar speed (within 5% difference on average;
 * - edverify calls are much slower, can take anywhere between 50 to 100 times longer.
 * - length of the message has negligible effect on resources used to hash, but we should still
 * consider adding a little extra fees corresponding to msg length.
 */

public class CryptoUtilMethodFeeBenchmarkTest {
    // NOTE:  Output is ONLY produced if REPORT is set to true.
    private static final boolean REPORT = false;

    private long energyLimit = 100_000_000_000L;
    private long energyPrice = 1L;

    private AionAddress deployer = TestingState.PREMINED_ADDRESS;
    private AionAddress dappAddress;

    private TestingState kernel;
    private AvmImpl avm;

    private byte[] hashMessage = "benchmark testing".getBytes();
    private byte[] hashMessageLong = "long benchmark testing 0123456789abcdef 0123456789abcdef 0123456789abcdef 0123456789abcdef 0123456789abcdef".getBytes();
    private static final String ALPHA_NUMERIC_STRING = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private static final String blake2bMethodName = "callBlake2b";
    private static final String shaMethodName = "callSha";
    private static final String keccakMethodName = "callKeccak";
    private static final String edverifyMethodName = "callEdverify";

    /**
     * todo: when benchmarking, modify some of these values see more accurate results.
     * Suggest:
     * - WARMUP_COUNT >= 1000
     * - LOOP_COUNT >= 10000
     * - AVG_COUNT = 1
     * - LIST_OF_STRING_COUNT = 10000
     * - FACTOR = 100, must be a factor of LIST_OF_STRING_COUNT
     * - should focus on relative time of each method call as different systems will yield different numbers.
     */
    private static final int WARMUP_COUNT = 1;
    private static final int LOOP_COUNT = 2;
    private static final int AVG_COUNT = 1;
    private static final int LIST_OF_STRING_COUNT = 5;
    private static final int FACTOR = 1;

    @Before
    public void setup() {
        byte[] basicAppTestJar = UserlibJarBuilder.buildJarForMainAndClassesAndUserlib(CryptoUtilMethodFeeBenchmarkTestTargetClass.class);

        byte[] txData = new CodeAndArguments(basicAppTestJar, null).encodeToBytes();

        this.kernel = new TestingState();
        this.avm = CommonAvmFactory.buildAvmInstanceForConfiguration(new StandardCapabilities(), new AvmConfiguration());
        Transaction tx = AvmTransactionUtil.create(deployer, kernel.getNonce(deployer), BigInteger.ZERO, txData, energyLimit, energyPrice);
        dappAddress = new AionAddress(avm.run(this.kernel, new Transaction[] {tx}, ExecutionType.ASSUME_MAINCHAIN, 0)[0].getResult().copyOfTransactionOutput().get());
        Assert.assertNotNull(dappAddress);
    }

    @After
    public void tearDown() {
        this.avm.shutdown();
    }

    /**
     * For the basic hash api benchmarks (testBlake2b, testSha, testKeccak), they should be ran
     * individually 1 by 1. Running them in sequence WILL have some effect on the results.
     */
    @Test
    public void testBlake2b(){
        // warm up
        for (int i = 0; i < WARMUP_COUNT; i++) {
            getAvgCallTime(blake2bMethodName, AVG_COUNT, 1, hashMessage);
        }

        // call method and record result, measure average call time
        long recordSum = 0;
        for (int i = 0; i < LOOP_COUNT; i++){
            recordSum = recordSum + getCallTime(blake2bMethodName, 1, hashMessage);
        }
        report("Average time per api call for blake2b hashing: " + recordSum/LOOP_COUNT + "ns");
    }

    @Test
    public void testSha(){
        // warm up
        for (int i = 0; i < WARMUP_COUNT; i++) {
            getAvgCallTime(shaMethodName, AVG_COUNT, 1, hashMessage);
        }

        // call method and record result, measure average call time
        long recordSum = 0;
        for (int i = 0; i < LOOP_COUNT; i++){
            recordSum = recordSum + getCallTime(shaMethodName, 1, hashMessage);
        }
        report("Average time per api call for sha hashing: " + recordSum/LOOP_COUNT + "ns");
    }

    @Test
    public void testKeccak(){
        // warm up
        for (int i = 0; i < WARMUP_COUNT; i++) {
            getAvgCallTime(keccakMethodName, AVG_COUNT, 1, hashMessage);
        }

        // call method and record result, measure average call time
        long recordSum = 0;
        for (int i = 0; i < LOOP_COUNT; i++){
            recordSum = recordSum + getCallTime(keccakMethodName, 1, hashMessage);
        }
        report("Average time per api call for keccak hashing: " + recordSum/LOOP_COUNT + "ns");
    }

    /**
     * Compare making looping calls from outside of dapp
     */
    @Test
    public void testAll3HashFunctionsAndCompare(){
        long blake2bSum = 0;
        long shaSum = 0;
        long keccakSum = 0;

        byte[] msg = hashMessageLong; // change this message to see variance in result

        for (int i = 0; i < LOOP_COUNT; i++){
            if (i < WARMUP_COUNT){
                getCallTime(blake2bMethodName, 1, msg);
                getCallTime(shaMethodName, 1, msg);
                getCallTime(keccakMethodName, 1, msg);
            } else {
                blake2bSum = blake2bSum + getCallTime(blake2bMethodName, 1, msg);
                shaSum = shaSum + getCallTime(shaMethodName, 1, msg);
                keccakSum = keccakSum + getCallTime(keccakMethodName, 1, msg);
            }
        }

        long blake2bTimePerCall = blake2bSum / (LOOP_COUNT - WARMUP_COUNT);
        long shaTimePerCall = shaSum / (LOOP_COUNT - WARMUP_COUNT);
        long keccakTimePerCall = keccakSum / (LOOP_COUNT - WARMUP_COUNT);

        report("blake2b avg: " + blake2bTimePerCall);
        report("sha avg: " + shaTimePerCall + ", is " + String.format("%.3f", (double)shaTimePerCall/blake2bTimePerCall) + " times speed comparing to blake2b");
        report("keccak avg: " + keccakTimePerCall + ", is " + String.format("%.3f", (double)keccakTimePerCall/blake2bTimePerCall) + " times speed comparing to blake2b");
    }

    /**
     * Compare making looping calls from within the dapp
     */
    @Test
    public void testAll3HashFunctionsAndCompare2(){
        //following values should be increased for a more accurate result
        // recommended values are 1000, 1000000
        int warmUp = WARMUP_COUNT;
        int loopCount = LOOP_COUNT;
        byte[] msg = hashMessageLong; // change this message to see variance in result

        // warm up blake2b, then make multiple calls within the dapp
        for (int i = 0; i < warmUp; i++) {
            getAvgCallTime(blake2bMethodName, AVG_COUNT, 1, hashMessage);
        }
        long blake2bTime = getCallTime(blake2bMethodName, loopCount, msg);
        report("blake2b avg: " + blake2bTime);

        // warm up sha, then make multiple calls within the dapp
        for (int i = 0; i < warmUp; i++) {
            getAvgCallTime(shaMethodName, AVG_COUNT, 1, hashMessage);
        }
        long shaSumTime = getCallTime(shaMethodName, loopCount, msg);
        report("sha avg: " + shaSumTime + ", is " + String.format("%.3f", (double)shaSumTime/blake2bTime) + " times speed comparing to blake2b");

        // warm up keccak, then make multiple calls within the dapp
        for (int i = 0; i < warmUp; i++) {
            getAvgCallTime(keccakMethodName, AVG_COUNT, 1, hashMessage);
        }
        long keccakTime = getCallTime(keccakMethodName, loopCount, msg);
        report("keccak avg: " + keccakTime + ", is " + String.format("%.3f", (double)keccakTime/blake2bTime) + " times speed comparing to blake2b");
    }

    @Test
    public void testAll3HashFunctionsLargeInput() {
        int loopCount = LOOP_COUNT;
        int messageSize = 100000;

        for (int i = 0; i < WARMUP_COUNT; i++) {
            getAvgCallTime(blake2bMethodName, AVG_COUNT, 1, hashMessage);
        }
        double blake2bAvg = getAvgCallTime("blake2bLargeInput", AVG_COUNT, loopCount, messageSize);
        report("blake2b avg for " + messageSize + " bytes input:" + blake2bAvg);

        for (int i = 0; i < WARMUP_COUNT; i++) {
            getAvgCallTime(shaMethodName, AVG_COUNT, 1, hashMessage);
        }

        double sha256Sum = getAvgCallTime("shaLargeInput", AVG_COUNT, loopCount, messageSize);

        report("sha256 avg for " + messageSize + " bytes input:" + sha256Sum);

        for (int i = 0; i < WARMUP_COUNT; i++) {
            getAvgCallTime(keccakMethodName, AVG_COUNT, 1, hashMessage);
        }

        double keccakSum = getAvgCallTime("keccakLargeInput", AVG_COUNT, loopCount, messageSize);
        report("keccak avg for " + messageSize + " bytes input:"  + keccakSum);
    }

    /**
     * Compare edverify to blake2b
     */
    @Test
    public void testEdverifyComparisionToBlake2bInDepth(){
        //following values should be increased for a more accurate result
        int warmUp = WARMUP_COUNT;
        int loopCount = LOOP_COUNT;
        byte[] msg = hashMessageLong; // change this message to see variance in result

        // warm up blake2b, then make multiple calls within the dapp
        for (int i = 0; i < warmUp; i++) {
            getAvgCallTime(blake2bMethodName, AVG_COUNT, 1, hashMessage);
        }
        long blake2bTime = getCallTime(blake2bMethodName, loopCount, msg);
        report("blake2b avg: " + blake2bTime);

        // warm up blake2b, then make multiple calls within the dapp
        for (int i = 0; i < warmUp; i++) {
            getAvgCallTime(edverifyMethodName, AVG_COUNT, 1, hashMessage);
        }
        long edverifyTime = getCallTime(edverifyMethodName, loopCount, msg);
        report("edverify avg: " + edverifyTime + ", which is " + String.format("%.3f", (double)edverifyTime/blake2bTime) + " times speed comparing to blake2b");
    }

    /**
     * benchmark how message length can affect time it takes to hash for each algorithm
     */
    @Test
    public void testBlake2bMessageLength(){
        String[] listOfMessage = generateListOfStrings(LIST_OF_STRING_COUNT);
        for (int i = 0; i < LIST_OF_STRING_COUNT; i = i + FACTOR){
            long time = getCallTime(blake2bMethodName, 1, listOfMessage[i].getBytes());
            report("Signing using blake2b: msg length = " + i+1 + " time = " + time);
        }
    }

    @Test
    public void testShaMessageLength(){
        String[] listOfMessage = generateListOfStrings(LIST_OF_STRING_COUNT);
        for (int i = 0; i < LIST_OF_STRING_COUNT; i = i + FACTOR){
            long time = getCallTime(shaMethodName, 1, listOfMessage[i].getBytes());
            report("Signing using sha: msg length = " + i+1 + " time = " + time);
        }
    }

    @Test
    public void testKeccakMessageLength(){
        String[] listOfMessage = generateListOfStrings(LIST_OF_STRING_COUNT);
        for (int i = 0; i < LIST_OF_STRING_COUNT; i = i + FACTOR){
            long time = getCallTime(keccakMethodName,  1, listOfMessage[i].getBytes());
            report("Signing using keccak: msg length = " + i+1 + " time = " + time);
        }
    }


    /**
     * Helper methods for benchmark
     */

    private double getAvgCallTime(String methodName, int loopCount, Object... arguments) {
        long sum = 0;
        for (int i = 0; i < loopCount; i++) {
            sum = sum + getCallTime(methodName, arguments);
        }
        return sum / loopCount;
    }

    private long getCallTime(String methodName, Object... arguments) {
        long st;
        long et;
        Transaction tx = setupTransaction(methodName, arguments);

        st = System.nanoTime();
        TransactionResult result = avm.run(this.kernel, new Transaction[]{tx}, ExecutionType.ASSUME_MAINCHAIN, 0)[0].getResult();
        et = System.nanoTime();

        Assert.assertTrue(result.transactionStatus.isSuccess());

        return et - st;
    }

    private Transaction setupTransaction(String methodName, java.lang.Object... arguments){
        byte[] txData = ABIUtil.encodeMethodArguments(methodName, arguments);
        return AvmTransactionUtil.call(deployer, dappAddress, kernel.getNonce(deployer), BigInteger.ZERO, txData, energyLimit, energyPrice);
    }

    private String[] generateListOfStrings(int count){
        String[] listOfString = new String[count];
        for (int i = 0; i < count; i = i + FACTOR){
            listOfString[i] = generateString(i+1);
        }
        return listOfString;
    }

    private String generateString(int count) {
        StringBuilder builder = new StringBuilder();
        while (count-- != 0) {
            double n = (Math.random()*ALPHA_NUMERIC_STRING.length());
            builder.append(ALPHA_NUMERIC_STRING.charAt((int)n));
        }
        return builder.toString();
    }

    private static void report(String output) {
        if (REPORT) {
            System.out.println(output);
        }
    }
}
