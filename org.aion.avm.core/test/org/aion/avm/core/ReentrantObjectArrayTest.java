package org.aion.avm.core;

import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import org.aion.kernel.TestingState;
import org.aion.types.AionAddress;
import org.aion.types.Transaction;
import org.aion.avm.core.blockchainruntime.EmptyCapabilities;
import org.aion.avm.core.dappreading.UserlibJarBuilder;
import org.aion.avm.core.util.Helpers;
import org.aion.avm.userlib.CodeAndArguments;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;
import org.aion.kernel.TestingBlock;
import org.aion.types.TransactionResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


public class ReentrantObjectArrayTest {
    // transaction
    private long energyLimit = 10_000_000L;
    private long energyPrice = 1L;

    // kernel & vm
    private TestingState kernel;
    private AvmImpl avm;

    private AionAddress deployer = TestingState.PREMINED_ADDRESS;
    private AionAddress dappAddress;

    @Before
    public void setup() {
        TestingBlock block = new TestingBlock(new byte[32], 1, Helpers.randomAddress(), System.currentTimeMillis(), new byte[0]);
        this.kernel = new TestingState(block);
        this.avm = CommonAvmFactory.buildAvmInstanceForConfiguration(new EmptyCapabilities(), new AvmConfiguration());
        
        byte[] jar = UserlibJarBuilder.buildJarForMainAndClassesAndUserlib(ReentrantObjectArrayTestResource.class);
        Transaction tx = AvmTransactionUtil.create(deployer, kernel.getNonce(deployer), BigInteger.ZERO, new CodeAndArguments(jar, null).encodeToBytes(), energyLimit, energyPrice);
        TransactionResult txResult = avm.run(this.kernel, new Transaction[] {tx}, ExecutionType.ASSUME_MAINCHAIN, kernel.getBlockNumber()-1)[0].getResult();
        assertTrue(txResult.transactionStatus.isSuccess());
        dappAddress = new AionAddress(txResult.copyOfTransactionOutput().get());
    }

    @After
    public void tearDown() {
        this.avm.shutdown();
    }

    @Test
    public void testReentrantStringArray() {
        byte[] data = new ABIStreamingEncoder().encodeOneString("testString").toBytes();
        Transaction tx = AvmTransactionUtil.call(deployer, dappAddress, kernel.getNonce(deployer), BigInteger.ZERO, data, energyLimit, energyPrice);
        TransactionResult txResult = avm.run(this.kernel, new Transaction[] {tx}, ExecutionType.ASSUME_MAINCHAIN, kernel.getBlockNumber()-1)[0].getResult();

        assertTrue(txResult.transactionStatus.isSuccess());
    }
}
