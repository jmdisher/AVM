package org.aion.avm.core;

import org.aion.kernel.TestingState;
import org.aion.types.AionAddress;
import org.aion.types.Transaction;
import org.aion.avm.core.blockchainruntime.EmptyCapabilities;
import org.aion.avm.core.dappreading.UserlibJarBuilder;
import org.aion.avm.core.testBlake2b.Blake2b;
import org.aion.avm.core.testBlake2b.Main;
import org.aion.avm.core.testWallet.ByteArrayHelpers;
import org.aion.avm.core.testWallet.ByteArrayWrapper;
import org.aion.avm.core.testWallet.BytesKey;
import org.aion.avm.core.testWallet.Daylimit;
import org.aion.avm.core.testWallet.EventLogger;
import org.aion.avm.core.testWallet.Multiowned;
import org.aion.avm.core.testWallet.Operation;
import org.aion.avm.core.testWallet.RequireFailedException;
import org.aion.avm.core.testWallet.Wallet;
import org.aion.avm.core.util.Helpers;
import org.aion.avm.userlib.CodeAndArguments;

import i.RuntimeAssertionError;
import org.aion.kernel.TestingBlock;

import java.math.BigInteger;

import org.aion.types.TransactionResult;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;


/**
 * This suite is really just for viewing the deployment costs of some of our various Dapp examples.
 * Nothing explicitly is actually verified by these 'tests'.
 * The purpose is more to give us an idea about how our deployment costs look for different Dapps.
 */
@Ignore
public class DeploymentCostTest {
    private static final long ENERGY_LIMIT = 100_000_000_000L;
    private static final long ENERGY_PRICE = 1L;
    private static final AionAddress DEPLOYER = TestingState.PREMINED_ADDRESS;

    private TestingState kernel;
    private AvmImpl avm;

    @Before
    public void setup() {
        TestingBlock block = new TestingBlock(new byte[32], 1, Helpers.randomAddress(), System.currentTimeMillis(), new byte[0]);
        this.kernel = new TestingState(block);
        this.avm = CommonAvmFactory.buildAvmInstanceForConfiguration(new EmptyCapabilities(), new AvmConfiguration());
    }

    @After
    public void tearDown() {
        this.avm.shutdown();
    }


    // NOTE: To add a new dApp to this test simply do the following:
    // 1. Create a Contract enum for it and put a String representation into contractsAsStrings
    // 2. Inside getDeploymentJarBytesForContract() create a new case for this enum and grab the
    //    jar bytes for all of the classes involved in creating the dApp.
    //    There are examples for dApps with & without clinit args.

    /**
     * The contracts/dApps we use in the deployment cost test.
     */
    private enum Contract {
        BLAKE2B,
        POC_WALLET,
        ;
    }

    /**
     * Verifies the DApp deployment costs don't change unexpectedly.
     */
    @Test
    public void testCostToDeployDapps() {
        TransactionResult blake2bResult = deployContract(Contract.BLAKE2B);
        Assert.assertEquals(4_607_980L, blake2bResult.energyUsed);
        
        TransactionResult walletResult = deployContract(Contract.POC_WALLET);
        Assert.assertEquals(3_985_228L, walletResult.energyUsed);
    }

    //<-----------------------------------------helpers-------------------------------------------->

    /**
     * Returns the bytes that are to be deployed for the given contract.
     *
     * @param contract The contract whose bytes are to be returned.
     * @return The deployment bytes of the specified contract.
     */
    private byte[] getDeploymentJarBytesForContract(Contract contract) {
        byte[] jarBytes = null;
        switch (contract) {
            case BLAKE2B:
                jarBytes = classesToJarBytes(
                    Main.class,
                    Blake2b.class);
                // Verify that this size doesn't unexpectedly change.
                Assert.assertEquals(68_224L, jarBytes.length);
                break;
            case POC_WALLET:
                jarBytes = classesToJarBytes(
                    Wallet.class,
                    Multiowned.class,
                    ByteArrayWrapper.class,
                    Operation.class,
                    ByteArrayHelpers.class,
                    BytesKey.class,
                    RequireFailedException.class,
                    Daylimit.class,
                    EventLogger.class);
                // Verify that this size doesn't unexpectedly change.
                Assert.assertEquals(58_692L, jarBytes.length);
                break;
            default: RuntimeAssertionError.unreachable("This should never be reached.");
        }

        return jarBytes;
    }

    private TransactionResult deployContract(Contract contract) {
        byte[] jar = getDeploymentJarBytesForContract(contract);

        //deploy in normal Mode
        Transaction create = AvmTransactionUtil.create(DEPLOYER, this.kernel.getNonce(DEPLOYER), BigInteger.ZERO, jar, ENERGY_LIMIT, ENERGY_PRICE);
        TransactionResult createResult = this.avm.run(this.kernel, new Transaction[] {create}, ExecutionType.ASSUME_MAINCHAIN, kernel.getBlockNumber() - 1)[0].getResult();
        Assert.assertTrue(createResult.transactionStatus.isSuccess());
        return createResult;
    }

    private byte[] classesToJarBytes(Class<?> main, Class<?>... others) {
        return new CodeAndArguments(UserlibJarBuilder.buildJarForMainAndClassesAndUserlib(main, others), null).encodeToBytes();
    }
}
