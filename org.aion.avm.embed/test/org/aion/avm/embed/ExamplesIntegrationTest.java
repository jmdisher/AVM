package org.aion.avm.embed;

import examples.BetaMapEvents;
import examples.HelloWorld;
import org.aion.avm.core.dappreading.UserlibJarBuilder;
import org.aion.avm.tooling.ABIUtil;
import org.aion.avm.tooling.deploy.JarOptimizer;
import avm.Address;
import org.aion.avm.userlib.AionMap;
import org.aion.avm.userlib.CodeAndArguments;
import org.aion.types.TransactionResult;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.math.BigInteger;


/**
 * Various tests to prove that our examples we build into our packages basically work.
 */
public class ExamplesIntegrationTest {
    // NOTE:  Output is ONLY produced if REPORT is set to true.
    private static final boolean REPORT = false;

    private boolean preserveDebugInfo = false;

    @Rule
    public AvmRule avmRule = new AvmRule(preserveDebugInfo).setBlockchainPrintlnEnabled(REPORT);

    private Address deployer = avmRule.getPreminedAccount();

    @Test
    public void test_BetaMapEvents() throws Exception {
        byte[] txData = avmRule.getDappBytes(BetaMapEvents.class, new byte[0], AionMap.class);
        
        // Deploy.
        long energyLimit = 10_000_000l;
        long energyPrice = 1l;
        TransactionResult createResult = avmRule.deploy(deployer, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
        Assert.assertTrue(createResult.transactionStatus.isSuccess());
        Address contractAddr = new Address(createResult.copyOfTransactionOutput().get());
        
        // We will just invoke a basic sequence of "PUT", "PUT", "GET" to make sure that we can call the main entry-points and execute the main paths.
        callStatic(contractAddr, "put", "key1", "value1");
        callStatic(contractAddr, "put", "key1", "value2");
        callStatic(contractAddr, "get", "key1");
    }

    @Test
    public void test_HelloWorld() throws Exception {
        JarOptimizer jarOptimizer = new JarOptimizer(preserveDebugInfo);
        byte[] jar = UserlibJarBuilder.buildJarForMainAndClassesAndUserlib(HelloWorld.class);
        byte[] optimizedDappBytes = jarOptimizer.optimize(jar);
        byte[] txData = new CodeAndArguments(optimizedDappBytes, new byte[0]).encodeToBytes();
        // Deploy.
        long energyLimit = 10_000_000l;
        long energyPrice = 1l;
        TransactionResult createResult = avmRule.deploy(deployer, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
        Assert.assertTrue(createResult.transactionStatus.isSuccess());
        Address contractAddr = new Address(createResult.copyOfTransactionOutput().get());
        
        // We only want to check that we can call it without issue (it only produces STDOUT).
        callStatic(contractAddr, "sayHello");
    }


    private void callStatic(Address contractAddr, String methodName, Object... args) {
        long energyLimit = 1_000_000l;
        byte[] argData = ABIUtil.encodeMethodArguments(methodName, args);
        TransactionResult result = avmRule.call(deployer, contractAddr, BigInteger.ZERO, argData, energyLimit, 1l).getTransactionResult();
        Assert.assertTrue(result.transactionStatus.isSuccess());
    }
}
