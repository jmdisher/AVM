package org.aion.avm.core.rejection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.aion.avm.core.*;
import org.aion.kernel.AvmWrappedTransactionResult.AvmInternalError;
import org.aion.kernel.TestingState;
import org.aion.types.AionAddress;
import org.aion.types.Transaction;
import org.aion.avm.core.blockchainruntime.EmptyCapabilities;
import org.aion.avm.core.util.Helpers;
import org.aion.avm.userlib.CodeAndArguments;
import org.aion.avm.utilities.JarBuilder;
import org.aion.kernel.TestingBlock;
import org.aion.types.TransactionResult;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.math.BigInteger;


/**
 * Implemented as part of issue-305 to demonstrate how rejections are actually observed, within the transformation logic.
 */
public class RejectionIntegrationTest {
    private static AionAddress FROM = TestingState.PREMINED_ADDRESS;
    private static long ENERGY_LIMIT = 5_000_000L;
    private static long ENERGY_PRICE = 1L;

    private static TestingState kernel;
    private static AvmImpl avm;

    @BeforeClass
    public static void setup() {
        TestingBlock BLOCK = new TestingBlock(new byte[32], 1, Helpers.randomAddress(), System.currentTimeMillis(), new byte[0]);
        kernel = new TestingState(BLOCK);
        avm = CommonAvmFactory.buildAvmInstanceForConfiguration(new EmptyCapabilities(), new AvmConfiguration());
    }

    @AfterClass
    public static void tearDown() {
        avm.shutdown();
    }

    @Test
    public void rejectNonShadowJclSubclassError() throws Exception {
        kernel.generateBlock();
        byte[] jar = JarBuilder.buildJarForMainClassAndExplicitClassNamesAndBytecode(RejectNonShadowJclSubclassError.class, Collections.emptyMap());
        byte[] txData = new CodeAndArguments(jar, new byte[0]).encodeToBytes();

        Transaction transaction = AvmTransactionUtil.create(FROM, kernel.getNonce(FROM), BigInteger.ZERO, txData, ENERGY_LIMIT, ENERGY_PRICE);
        TransactionResult createResult = avm.run(RejectionIntegrationTest.kernel, new Transaction[] {transaction}, ExecutionType.ASSUME_MAINCHAIN, kernel.getBlockNumber()-1)[0].getResult();
        Assert.assertEquals(AvmInternalError.FAILED_REJECTED_CLASS.error, createResult.transactionStatus.causeOfError);
    }

    @Test
    public void rejectCorruptMainMethod() throws IOException {
        kernel.generateBlock();
        byte[] classBytes = Files.readAllBytes(Paths.get("test/resources/TestClassTemplate_corruptMainMethod.class"));
        byte[] jar = JarBuilder.buildJarForExplicitClassNamesAndBytecode("TestClassTemplate", classBytes, Collections.emptyMap());
        byte[] txData = new CodeAndArguments(jar, new byte[0]).encodeToBytes();

        Transaction transaction = AvmTransactionUtil.create(FROM, kernel.getNonce(FROM), BigInteger.ZERO, txData, ENERGY_LIMIT, ENERGY_PRICE);
        TransactionResult createResult = avm.run(RejectionIntegrationTest.kernel, new Transaction[] {transaction}, ExecutionType.ASSUME_MAINCHAIN, kernel.getBlockNumber()-1)[0].getResult();
        Assert.assertEquals(AvmInternalError.FAILED_REJECTED_CLASS.error, createResult.transactionStatus.causeOfError);
    }

    @Ignore
    @Test
    public void rejectCorruptMethod() throws IOException {
        kernel.generateBlock();
        byte[] classBytes = Files.readAllBytes(Paths.get("test/resources/TestClassTemplate_corruptMethod.class"));
        Map<String, byte[]> classMap = new HashMap<>();
        classMap.put("TestClassTemplate_corruptMethod", classBytes);
        byte[] jar = JarBuilder.buildJarForMainClassAndExplicitClassNamesAndBytecode(
            AvmImplTestResource.class, classMap);
        byte[] txData = new CodeAndArguments(jar, new byte[0]).encodeToBytes();

        Transaction transaction = AvmTransactionUtil.create(FROM, kernel.getNonce(FROM), BigInteger.ZERO, txData, ENERGY_LIMIT, ENERGY_PRICE);
        TransactionResult createResult = avm.run(RejectionIntegrationTest.kernel, new Transaction[] {transaction}, ExecutionType.ASSUME_MAINCHAIN, kernel.getBlockNumber()-1)[0].getResult();
        Assert.assertEquals(AvmInternalError.FAILED_REJECTED_CLASS.error, createResult.transactionStatus.causeOfError);
    }

    @Test
    public void accept31Variables() throws Exception {
        byte[] jar = JarBuilder.buildJarForMainClassAndExplicitClassNamesAndBytecode(RejectClass31Variables.class, Collections.emptyMap());
        byte[] txData = new CodeAndArguments(jar, new byte[0]).encodeToBytes();
        
        Transaction transaction = AvmTransactionUtil.create(FROM, kernel.getNonce(FROM), BigInteger.ZERO, txData, ENERGY_LIMIT, ENERGY_PRICE);
        TransactionResult createResult = avm.run(RejectionIntegrationTest.kernel, new Transaction[] {transaction}, ExecutionType.ASSUME_MAINCHAIN, RejectionIntegrationTest.kernel.getBlockNumber() -1)[0].getResult();
        Assert.assertTrue(createResult.transactionStatus.isSuccess());
    }

    @Test
    public void reject32Variables() throws Exception {
        byte[] jar = JarBuilder.buildJarForMainClassAndExplicitClassNamesAndBytecode(RejectClass32Variables.class, Collections.emptyMap());
        byte[] txData = new CodeAndArguments(jar, new byte[0]).encodeToBytes();
        
        Transaction transaction = AvmTransactionUtil.create(FROM, kernel.getNonce(FROM), BigInteger.ZERO, txData, ENERGY_LIMIT, ENERGY_PRICE);
        TransactionResult createResult = avm.run(RejectionIntegrationTest.kernel, new Transaction[] {transaction}, ExecutionType.ASSUME_MAINCHAIN, RejectionIntegrationTest.kernel.getBlockNumber() -1)[0].getResult();
        Assert.assertEquals(AvmInternalError.FAILED_REJECTED_CLASS.error, createResult.transactionStatus.causeOfError);
    }

    @Test
    public void reject32VariablesSubclass() throws Exception {
        byte[] jar = JarBuilder.buildJarForMainClassAndExplicitClassNamesAndBytecode(RejectClassExtend31Variables.class, Collections.emptyMap(), RejectClass31Variables.class);
        byte[] txData = new CodeAndArguments(jar, new byte[0]).encodeToBytes();
        
        Transaction transaction = AvmTransactionUtil.create(FROM, kernel.getNonce(FROM), BigInteger.ZERO, txData, ENERGY_LIMIT, ENERGY_PRICE);
        TransactionResult createResult = avm.run(RejectionIntegrationTest.kernel, new Transaction[] {transaction}, ExecutionType.ASSUME_MAINCHAIN, RejectionIntegrationTest.kernel.getBlockNumber() -1)[0].getResult();
        Assert.assertEquals(AvmInternalError.FAILED_REJECTED_CLASS.error, createResult.transactionStatus.causeOfError);
    }

    @Test
    public void accept32VariablesSubclass() throws Exception {
        byte[] jar = JarBuilder.buildJarForMainClassAndExplicitClassNamesAndBytecode(RejectClassExtend31VariablesSafe.class, Collections.emptyMap(), RejectClass31Variables.class);
        byte[] txData = new CodeAndArguments(jar, new byte[0]).encodeToBytes();
        
        Transaction transaction = AvmTransactionUtil.create(FROM, kernel.getNonce(FROM), BigInteger.ZERO, txData, ENERGY_LIMIT, ENERGY_PRICE);
        TransactionResult createResult = avm.run(RejectionIntegrationTest.kernel, new Transaction[] {transaction}, ExecutionType.ASSUME_MAINCHAIN, RejectionIntegrationTest.kernel.getBlockNumber() -1)[0].getResult();
        Assert.assertTrue(createResult.transactionStatus.isSuccess());
    }
}
