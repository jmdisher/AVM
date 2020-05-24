package org.aion.avm.core;

import avm.Address;
import java.math.BigInteger;

import org.aion.types.AionAddress;
import org.aion.types.Transaction;
import org.aion.avm.core.blockchainruntime.EmptyCapabilities;
import org.aion.avm.core.dappreading.UserlibJarBuilder;
import org.aion.avm.core.testWallet.ByteArrayHelpers;
import org.aion.avm.core.testWallet.ByteArrayWrapper;
import org.aion.avm.core.testWallet.BytesKey;
import org.aion.avm.core.testWallet.CallEncoder;
import org.aion.avm.core.testWallet.Daylimit;
import org.aion.avm.core.testWallet.EventLogger;
import org.aion.avm.core.testWallet.Multiowned;
import org.aion.avm.core.testWallet.Operation;
import org.aion.avm.core.testWallet.RequireFailedException;
import org.aion.avm.core.testWallet.Wallet;
import org.aion.avm.core.util.Helpers;
import org.aion.avm.userlib.CodeAndArguments;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.kernel.*;
import org.aion.types.TransactionResult;
import org.junit.*;


/**
 * Our current thinking is that we will use a JUnit launcher for the proof-of-concept demonstration.  This is that entry-point.
 * See issue-124 for more of the background.
 */
public class PocWalletTest {

    // For now, we will just reuse the from, to, and block for each call (in the future, this will change).
    private static AionAddress from = TestingState.PREMINED_ADDRESS;
    private static long energyLimit = 10_000_000_000L;
    private static long energyPrice = 1;

    private static IExternalState externalState;
    private static AvmImpl avm;
    private static IExternalCapabilities externalCapabilities;

    @BeforeClass
    public static void setup() {
        TestingBlock block = new TestingBlock(new byte[32], 1, Helpers.randomAddress(), System.currentTimeMillis(), new byte[0]);
        externalState = new TestingState(block);
        externalCapabilities = new EmptyCapabilities() {
            @Override
            public byte[] blake2b(byte[] data) {
                // NOTE:  This test relies on calling blake2b but doesn't rely on the answer being correct so just return the input.
                return data;
            }
        };
        avm = CommonAvmFactory.buildAvmInstanceForConfiguration(externalCapabilities, new AvmConfiguration());
    }

    @AfterClass
    public static void tearDown() {
        avm.shutdown();
    }

    private byte[] buildTestWalletJar() {
        return UserlibJarBuilder.buildJarForMainAndClassesAndUserlib(Wallet.class
                , Multiowned.class
                , ByteArrayWrapper.class
                , Operation.class
                , ByteArrayHelpers.class
                , BytesKey.class
                , RequireFailedException.class
                , Daylimit.class
                , EventLogger.class
        );
    }

    /**
     * Tests that a deploy call will store the code for the Wallet JAR.
     * This means that it transformed it correctly and nothing was missing.
     */
    @Test
    public void testDeployWritesCode() {
        byte[] testWalletJar = buildTestWalletJar();
        byte[] testWalletArguments = new byte[0];

        Transaction createTransaction = AvmTransactionUtil.create(from, externalState.getNonce(from), BigInteger.ZERO, new CodeAndArguments(testWalletJar, testWalletArguments).encodeToBytes(), energyLimit, energyPrice);
        TransactionResult createResult = avm.run(externalState, new Transaction[] {createTransaction}, ExecutionType.ASSUME_MAINCHAIN, externalState.getBlockNumber()-1)[0].getResult();

        Assert.assertTrue(createResult.transactionStatus.isSuccess());
        Assert.assertNotNull(externalState.getTransformedCode(new AionAddress(createResult.copyOfTransactionOutput().get())));
    }

    /**
     * Tests that we can run init on the deployed code, albeit as a second transaction (since we haven't yet decided how to invoke init on deploy).
     */
    @Test
    public void testDeployAndCallInit() throws Exception {
        // Constructor args.
        AionAddress extra1 = Helpers.randomAddress();
        AionAddress extra2 = Helpers.randomAddress();
        int requiredVotes = 2;
        long dailyLimit = 5000;

        byte[] testWalletJar = buildTestWalletJar();
        byte[] testWalletArguments = new byte[0];
        Transaction createTransaction = AvmTransactionUtil.create(from, externalState.getNonce(from), BigInteger.ZERO, new CodeAndArguments(testWalletJar, testWalletArguments).encodeToBytes(), energyLimit, energyPrice);
        TransactionResult createResult = avm.run(externalState, new Transaction[] {createTransaction}, ExecutionType.ASSUME_MAINCHAIN, externalState.getBlockNumber()-1)[0].getResult();
        Assert.assertTrue(createResult.transactionStatus.isSuccess());

        // contract address is stored in return data
        AionAddress contractAddress = new AionAddress(createResult.copyOfTransactionOutput().get());

        byte[] initArgs = CallEncoder.init(new Address(extra1.toByteArray()), new Address(extra2.toByteArray()), requiredVotes, dailyLimit);
        Transaction initTransaction = AvmTransactionUtil.call(from, contractAddress, externalState.getNonce(from), BigInteger.ZERO, initArgs, energyLimit, energyPrice);
        TransactionResult initResult = avm.run(externalState, new Transaction[] {initTransaction}, ExecutionType.ASSUME_MAINCHAIN, externalState.getBlockNumber()-1)[0].getResult();
        Assert.assertTrue(initResult.transactionStatus.isSuccess());
    }

    /**
     * Tests that inner classes work properly within the serialization system (since their constructors need to be marked accessible).
     */
    @Test
    public void testExecuteWithInnerClasses() throws Exception {
        // Constructor args.
        AionAddress extra1 = Helpers.randomAddress();
        AionAddress extra2 = Helpers.randomAddress();
        int requiredVotes = 2;
        long dailyLimit = 5000;

        // Deploy.
        AionAddress contractAddress = new AionAddress(deployTestWallet());

        // Run the init.
        runInit(contractAddress, new Address(extra1.toByteArray()), new Address(extra2.toByteArray()), requiredVotes, dailyLimit);

        // Call "execute" with something above the daily limit so we will create the "Transaction" inner class instance.
        AionAddress to = Helpers.randomAddress();
        byte[] data = Helpers.randomBytes(AionAddress.LENGTH);
        byte[] execArgs = CallEncoder.execute(new Address(to.toByteArray()), dailyLimit + 1, data);
        Transaction executeTransaction = AvmTransactionUtil.call(from, contractAddress, externalState.getNonce(from), BigInteger.ZERO, execArgs, energyLimit, energyPrice);
        TransactionResult executeResult = avm.run(externalState, new Transaction[] {executeTransaction}, ExecutionType.ASSUME_MAINCHAIN, externalState.getBlockNumber()-1)[0].getResult();
        Assert.assertTrue(executeResult.transactionStatus.isSuccess());
        byte[] toConfirm = new ABIDecoder(executeResult.copyOfTransactionOutput().get()).decodeOneByteArray();

        // Now, confirm as one of the other owners to observe we can instantiate the Transaction instance, from storage.
        externalState.adjustBalance(extra1, BigInteger.valueOf(1_000_000_000_000L));
        byte[] confirmArgs = CallEncoder.confirm(toConfirm);
        Transaction confirmTransaction = AvmTransactionUtil.call(extra1, contractAddress, externalState.getNonce(extra1), BigInteger.ZERO, confirmArgs, energyLimit, energyPrice);
        TransactionResult confirmResult = avm.run(externalState, new Transaction[] {confirmTransaction}, ExecutionType.ASSUME_MAINCHAIN, externalState.getBlockNumber()-1)[0].getResult();
        Assert.assertTrue(confirmResult.transactionStatus.isSuccess()); // transfer to non-existing accounts
    }


    private void runInit(AionAddress contractAddress, Address extra1, Address extra2, int requiredVotes, long dailyLimit) throws Exception {
        byte[] initArgs = CallEncoder.init(extra1, extra2, requiredVotes, dailyLimit);
        Transaction initTransaction = AvmTransactionUtil.call(from, contractAddress, externalState.getNonce(from), BigInteger.ZERO, initArgs, energyLimit, energyPrice);
        TransactionResult initResult = avm.run(externalState, new Transaction[] {initTransaction}, ExecutionType.ASSUME_MAINCHAIN, externalState.getBlockNumber()-1)[0].getResult();
        Assert.assertTrue(initResult.transactionStatus.isSuccess());
    }

    private byte[] deployTestWallet() {
        byte[] testWalletJar = buildTestWalletJar();
        byte[] testWalletArguments = new byte[0];

        Transaction createTransaction = AvmTransactionUtil.create(from, externalState.getNonce(from), BigInteger.ZERO, new CodeAndArguments(testWalletJar, testWalletArguments).encodeToBytes(), energyLimit, energyPrice);
        TransactionResult createResult = avm.run(externalState, new Transaction[] {createTransaction}, ExecutionType.ASSUME_MAINCHAIN, externalState.getBlockNumber()-1)[0].getResult();
        Assert.assertTrue(createResult.transactionStatus.isSuccess());

        // contract address is stored in return data
        byte[] contractAddress = createResult.copyOfTransactionOutput().get();
        return contractAddress;
    }
}
