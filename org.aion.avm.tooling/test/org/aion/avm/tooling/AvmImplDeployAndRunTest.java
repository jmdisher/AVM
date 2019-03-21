package org.aion.avm.tooling;

import legacy_examples.deployAndRunTest.DeployAndRunTarget;
import legacy_examples.helloworld.HelloWorld;
import org.aion.avm.api.ABIDecoder;
import org.aion.avm.api.ABIEncoder;
import org.aion.avm.api.Address;
import org.aion.kernel.AvmTransactionResult;
import org.aion.kernel.TestingKernel;
import org.aion.vm.api.interfaces.TransactionResult;
import org.junit.Rule;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;


public class AvmImplDeployAndRunTest {
    @Rule
    public AvmRule avmRule = new AvmRule(false);

    private Address from = avmRule.getPreminedAccount();
    private long energyLimit = 10_000_000L;
    private long energyPrice = 1;

    public TransactionResult deployHelloWorld() {
        byte[] txData = avmRule.getDappBytes(HelloWorld.class, null);
        return avmRule.deploy(from, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    @Test
    public void testDeployWithClinitCall() {
        byte[] arguments = ABIEncoder.encodeMethodArguments("", 100);
        byte[] txData = avmRule.getDappBytes(HelloWorld.class, arguments);

        TransactionResult result = avmRule.deploy(from, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();

        assertEquals(AvmTransactionResult.Code.SUCCESS, result.getResultCode());
    }

    @Test
    public void testDeployAndMethodCalls() {
        TransactionResult deployResult = deployHelloWorld();
        assertEquals(AvmTransactionResult.Code.SUCCESS, deployResult.getResultCode());

        // call the "run" method
        byte[] txData = ABIEncoder.encodeMethodArguments("run");
        TransactionResult result = avmRule.call(from, new Address(deployResult.getReturnData()), BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();

        assertEquals(AvmTransactionResult.Code.SUCCESS, result.getResultCode());
        assertEquals("Hello, world!", new String((byte[]) ABIDecoder.decodeOneObject(result.getReturnData())));

        // test another method call, "add" with arguments
        txData = ABIEncoder.encodeMethodArguments("add", 123, 1);
        result = avmRule.call(from, new Address(deployResult.getReturnData()), BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();


        assertEquals(AvmTransactionResult.Code.SUCCESS, result.getResultCode());
        assertEquals(124, ABIDecoder.decodeOneObject(result.getReturnData()));
    }

    public TransactionResult deployTheDeployAndRunTest() {
        byte[] txData = avmRule.getDappBytes(DeployAndRunTarget.class, null);
        return avmRule.deploy(from, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    @Test
    public void testDeployAndRunTest() {
        TransactionResult deployResult = deployTheDeployAndRunTest();
        assertEquals(AvmTransactionResult.Code.SUCCESS, deployResult.getResultCode());

        // test encode method arguments with "encodeArgs"
        byte[] txData = ABIEncoder.encodeMethodArguments("encodeArgs");
        TransactionResult result = avmRule.call(from, new Address(deployResult.getReturnData()), BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();


        assertEquals(AvmTransactionResult.Code.SUCCESS, result.getResultCode());
        byte[] expected = ABIEncoder.encodeMethodArguments("addArray", new int[]{123, 1}, 5);
        boolean correct = Arrays.equals((byte[])(ABIDecoder.decodeOneObject(result.getReturnData())), expected);
        assertEquals(true, correct);

        // test another method call, "addArray" with 1D array arguments
        result = avmRule.call(from, new Address(deployResult.getReturnData()), BigInteger.ZERO, expected, energyLimit, energyPrice).getTransactionResult();


        assertEquals(AvmTransactionResult.Code.SUCCESS, result.getResultCode());
        assertEquals(129, ABIDecoder.decodeOneObject(result.getReturnData()));

        // test another method call, "addArray2" with 2D array arguments
        int[][] a = new int[2][];
        a[0] = new int[]{123, 4};
        a[1] = new int[]{1, 2};
        txData = ABIEncoder.encodeMethodArguments("addArray2", (Object) a);
        result = avmRule.call(from, new Address(deployResult.getReturnData()), BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();

        assertEquals(AvmTransactionResult.Code.SUCCESS, result.getResultCode());
        assertEquals(124, ABIDecoder.decodeOneObject(result.getReturnData()));

        // test another method call, "concatenate" with 2D array arguments and 1D array return data
        char[][] chars = new char[2][];
        chars[0] = "cat".toCharArray();
        chars[1] = "dog".toCharArray();
        txData = ABIEncoder.encodeMethodArguments("concatenate", (Object) chars);
        result = avmRule.call(from, new Address(deployResult.getReturnData()), BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();

        assertEquals(AvmTransactionResult.Code.SUCCESS, result.getResultCode());
        assertEquals("catdog", new String((char[]) ABIDecoder.decodeOneObject(result.getReturnData())));

        // test another method call, "concatString" with String array arguments and String return data
        txData = ABIEncoder.encodeMethodArguments("concatString", "cat", "dog"); // Note - need to cast String[] into Object, to pass it as one argument to the varargs method
        result = avmRule.call(from, new Address(deployResult.getReturnData()), BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();

        assertEquals(AvmTransactionResult.Code.SUCCESS, result.getResultCode());
        assertEquals("catdog", ABIDecoder.decodeOneObject(result.getReturnData()));

        // test another method call, "concatStringArray" with String array arguments and String return data
        txData = ABIEncoder.encodeMethodArguments("concatStringArray", (Object) new String[]{"cat", "dog"}); // Note - need to cast String[] into Object, to pass it as one argument to the varargs method
        result = avmRule.call(from, new Address(deployResult.getReturnData()), BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();

        assertEquals(AvmTransactionResult.Code.SUCCESS, result.getResultCode());
        assertEquals("catdog", ((String[])ABIDecoder.decodeOneObject(result.getReturnData()))[0]);
        assertEquals("perfect", ((String[])ABIDecoder.decodeOneObject(result.getReturnData()))[1]);

        // test another method call, "swap" with 2D array arguments and 2D array return data
        txData = ABIEncoder.encodeMethodArguments("swap", (Object) chars);
        result = avmRule.call(from, new Address(deployResult.getReturnData()), BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();


        assertEquals(AvmTransactionResult.Code.SUCCESS, result.getResultCode());
        assertEquals("dog", new String(((char[][]) ABIDecoder.decodeOneObject(result.getReturnData()))[0]));
        assertEquals("cat", new String(((char[][]) ABIDecoder.decodeOneObject(result.getReturnData()))[1]));

        // test a method call to "setBar", which does not have a return type (void)
        txData = ABIEncoder.encodeMethodArguments("setBar", 20);
        result = avmRule.call(from, new Address(deployResult.getReturnData()), BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();

        assertEquals(AvmTransactionResult.Code.SUCCESS, result.getResultCode());
    }

    @Test
    public void testBalanceTransfer() {
        assertEquals(TestingKernel.PREMINED_AMOUNT, avmRule.kernel.getBalance(org.aion.types.Address.wrap(from.unwrap())));

        // account1 get 10000
        Address account1 = avmRule.getRandomAddress(BigInteger.ZERO);
        TransactionResult result = avmRule.balanceTransfer(from, account1, BigInteger.valueOf(100000L), 21000, energyPrice).getTransactionResult();

        assertEquals(AvmTransactionResult.Code.SUCCESS, result.getResultCode());
        assertEquals(BigInteger.valueOf(100000L), avmRule.kernel.getBalance(org.aion.types.Address.wrap(account1.unwrap())));

        // account1 transfers 1000 to account2
        Address account2 = avmRule.getRandomAddress(BigInteger.ZERO);
        result = avmRule.balanceTransfer(account1, account2, BigInteger.valueOf(1000L), 21000, energyPrice).getTransactionResult();

        assertEquals(AvmTransactionResult.Code.SUCCESS, result.getResultCode());
        // AKI-33: Our userlib is receiving a lot of changes while our tooling to prune on deployment is not yet ready so we
        // TEMPORARILY reduce this cost in order to improve testability.
        long basicCost = 21000L / 10L;
        assertEquals(BigInteger.valueOf(100000L - 1000L - energyPrice * basicCost), avmRule.kernel.getBalance(org.aion.types.Address.wrap(account1.unwrap())));
        assertEquals(BigInteger.valueOf(1000L), avmRule.kernel.getBalance(org.aion.types.Address.wrap(account2.unwrap())));
    }

    @Test
    public void testCreateAndCallWithBalanceTransfer() {
        // deploy the Dapp with 100000 value transfer; create with balance transfer
        byte[] txData = avmRule.getDappBytes(DeployAndRunTarget.class, null);
        TransactionResult deployResult = avmRule.deploy(from, BigInteger.valueOf(100000L), txData, energyLimit, energyPrice).getTransactionResult();
        assertEquals(AvmTransactionResult.Code.SUCCESS, deployResult.getResultCode());
        assertEquals(BigInteger.valueOf(100000L), avmRule.kernel.getBalance(org.aion.types.Address.wrap(deployResult.getReturnData())));

        // account1 get 1000000; pure balance transfer
        BigInteger accountBalance = BigInteger.valueOf(1000000L);

        Address account1 = avmRule.getRandomAddress(BigInteger.ZERO);
        TransactionResult result = avmRule.balanceTransfer(from, account1, accountBalance, 21000, energyPrice).getTransactionResult();

        assertEquals(AvmTransactionResult.Code.SUCCESS, result.getResultCode());
        assertEquals(accountBalance, avmRule.kernel.getBalance(org.aion.types.Address.wrap(account1.unwrap())));

        // account1 to call the Dapp and transfer 50000 to it; call with balance transfer
        long energyLimit = 500000L;
        BigInteger value = BigInteger.valueOf(50000L);

        txData = ABIEncoder.encodeMethodArguments("encodeArgs");
        result = avmRule.call(account1, new Address(deployResult.getReturnData()), value, txData, energyLimit, energyPrice).getTransactionResult();


        BigInteger accountBalanceAfterValueTransfer = accountBalance.subtract(value);

        assertEquals(AvmTransactionResult.Code.SUCCESS, result.getResultCode());
        assertEquals(BigInteger.valueOf(150000L), avmRule.kernel.getBalance(org.aion.types.Address.wrap(deployResult.getReturnData())));
        assertEquals(accountBalanceAfterValueTransfer.subtract(BigInteger.valueOf(((AvmTransactionResult) result).getEnergyUsed())), avmRule.kernel.getBalance(org.aion.types.Address.wrap(account1.unwrap())));
        assertEquals(accountBalanceAfterValueTransfer.subtract(BigInteger.valueOf(energyLimit - result.getEnergyRemaining())), avmRule.kernel.getBalance(org.aion.types.Address.wrap(account1.unwrap())));
    }
}
