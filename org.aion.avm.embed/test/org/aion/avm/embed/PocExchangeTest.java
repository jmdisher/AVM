package org.aion.avm.embed;

import org.aion.avm.core.*;
import org.aion.types.AionAddress;
import org.aion.types.Transaction;
import avm.Address;
import org.aion.avm.core.dappreading.UserlibJarBuilder;
import org.aion.avm.userlib.CodeAndArguments;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.avm.core.util.Helpers;
import org.aion.avm.embed.testExchange.*;
import org.aion.avm.tooling.ABIUtil;
import org.aion.kernel.*;
import org.aion.types.TransactionResult;
import org.junit.*;

import java.math.BigInteger;


public class PocExchangeTest {
    private static IExternalState kernel;
    private static AvmImpl avm;
    private static byte[] testERC20Jar;
    private static byte[] testExchangeJar;

    @BeforeClass
    public static void setup() {
        TestingBlock block = new TestingBlock(new byte[32], 1, Helpers.randomAddress(), System.currentTimeMillis(), new byte[0]);
        kernel = new TestingState(block);
        avm = CommonAvmFactory.buildAvmInstanceForConfiguration(new StandardCapabilities(), new AvmConfiguration());
        
        testERC20Jar = UserlibJarBuilder.buildJarForMainAndClassesAndUserlib(CoinController.class, ERC20Token.class);
        testExchangeJar = UserlibJarBuilder.buildJarForMainAndClassesAndUserlib(ExchangeController.class, Exchange.class, ExchangeTransaction.class, ERC20Token.class);
    }

    @AfterClass
    public static void tearDown() {
        avm.shutdown();
    }

    private long energyLimit = 6_000_0000;

    private AionAddress pepeMinter = Helpers.randomAddress();
    private AionAddress memeMinter = Helpers.randomAddress();
    private AionAddress exchangeOwner = Helpers.randomAddress();
    private AionAddress usr1 = Helpers.randomAddress();
    private AionAddress usr2 = Helpers.randomAddress();
    private AionAddress usr3 = Helpers.randomAddress();


    class CoinContract{
        private AionAddress addr;
        private AionAddress minter;

        CoinContract(AionAddress contractAddr, AionAddress minter, byte[] jar, byte[] arguments){
            kernel.adjustBalance(minter, BigInteger.valueOf(1_000_000_000L));
            kernel.adjustBalance(pepeMinter, BigInteger.valueOf(1_000_000_000L));
            kernel.adjustBalance(memeMinter, BigInteger.valueOf(1_000_000_000L));
            kernel.adjustBalance(exchangeOwner, BigInteger.valueOf(1_000_000_000L));
            kernel.adjustBalance(usr1, BigInteger.valueOf(1_000_000_000L));
            kernel.adjustBalance(usr2, BigInteger.valueOf(1_000_000_000L));
            kernel.adjustBalance(usr3, BigInteger.valueOf(1_000_000_000L));

            this.addr = contractAddr;
            this.minter = minter;
            this.addr = initCoin(jar, arguments);
        }

        private AionAddress initCoin(byte[] jar, byte[] arguments){
            Transaction createTransaction = AvmTransactionUtil.create(minter, kernel.getNonce(minter), BigInteger.ZERO, new CodeAndArguments(jar, arguments).encodeToBytes(), energyLimit, 1L);
            TransactionResult createResult = avm.run(kernel, new Transaction[] {createTransaction}, ExecutionType.ASSUME_MAINCHAIN, 0)[0].getResult();
            Assert.assertTrue(createResult.transactionStatus.isSuccess());
            return new AionAddress(createResult.copyOfTransactionOutput().get());
        }

        public TransactionResult callTotalSupply() {
            byte[] args = ABIUtil.encodeMethodArguments("totalSupply");
            return call(minter, args);
        }

        private TransactionResult callBalanceOf(AionAddress toQuery) {
            byte[] args = ABIUtil.encodeMethodArguments("balanceOf", new Address(toQuery.toByteArray()));
            return call(minter, args);
        }

        private TransactionResult callMint(AionAddress receiver, long amount) {
            byte[] args = ABIUtil.encodeMethodArguments("mint", new Address(receiver.toByteArray()), amount);
            return call(minter, args);
        }

        private TransactionResult callTransfer(AionAddress sender, AionAddress receiver, long amount) {
            byte[] args = ABIUtil.encodeMethodArguments("transfer", new Address(receiver.toByteArray()), amount);
            return call(sender, args);
        }

        private TransactionResult callAllowance(AionAddress owner, AionAddress spender) {
            byte[] args = ABIUtil.encodeMethodArguments("allowance", new Address(owner.toByteArray()), new Address(spender.toByteArray()));
            return call(minter, args);
        }

        private TransactionResult callApprove(AionAddress owner, AionAddress spender, long amount) {
            byte[] args = ABIUtil.encodeMethodArguments("approve", new Address(spender.toByteArray()), amount);
            return call(owner, args);
        }

        private TransactionResult callTransferFrom(AionAddress executor, AionAddress from, AionAddress to, long amount) {
            byte[] args = ABIUtil.encodeMethodArguments("transferFrom", new Address(from.toByteArray()), new Address(to.toByteArray()), amount);
            return call(executor, args);
        }

        private TransactionResult call(AionAddress sender, byte[] args) {
            Transaction callTransaction = AvmTransactionUtil.call(sender, addr, kernel.getNonce(sender), BigInteger.ZERO, args, energyLimit, 1l);
            TransactionResult callResult = avm.run(kernel, new Transaction[] {callTransaction}, ExecutionType.ASSUME_MAINCHAIN, 0)[0].getResult();
            Assert.assertTrue(callResult.transactionStatus.isSuccess());
            return callResult;
        }
    }

    class ExchangeContract{
        private AionAddress addr;
        private AionAddress owner;

        ExchangeContract(AionAddress contractAddr, AionAddress owner, byte[] jar){
            this.addr = contractAddr;
            this.owner = owner;
            this.addr = initExchange(jar, null);
        }

        private AionAddress initExchange(byte[] jar, byte[] arguments){
            Transaction createTransaction = AvmTransactionUtil.create(owner, kernel.getNonce(owner), BigInteger.ZERO, new CodeAndArguments(jar, arguments).encodeToBytes(), energyLimit, 1L);
            TransactionResult createResult = avm.run(kernel, new Transaction[] {createTransaction}, ExecutionType.ASSUME_MAINCHAIN, 0)[0].getResult();
            Assert.assertTrue(createResult.transactionStatus.isSuccess());
            return new AionAddress(createResult.copyOfTransactionOutput().get());
        }

        public TransactionResult callListCoin(String name, AionAddress coinAddr) {
            byte[] args = ABIUtil.encodeMethodArguments("listCoin", name.toCharArray(), new Address(coinAddr.toByteArray()));
            return call(owner,args);
        }

        public TransactionResult callRequestTransfer(String name, AionAddress from,  AionAddress to, long amount) {
            byte[] args = ABIUtil.encodeMethodArguments("requestTransfer", name.toCharArray(), new Address(to.toByteArray()), amount);
            return call(from,args);
        }

        public TransactionResult callProcessExchangeTransaction(AionAddress sender) {
            byte[] args = ABIUtil.encodeMethodArguments("processExchangeTransaction");
            return call(sender,args);
        }

        private TransactionResult call(AionAddress sender, byte[] args) {
            Transaction callTransaction = AvmTransactionUtil.call(sender, addr, kernel.getNonce(sender), BigInteger.ZERO, args, energyLimit, 1l);
            TransactionResult callResult = avm.run(kernel, new Transaction[] {callTransaction}, ExecutionType.ASSUME_MAINCHAIN, 0)[0].getResult();
            Assert.assertTrue(callResult.transactionStatus.isSuccess());
            return callResult;
        }
    }

    @Test
    public void testERC20() {
        TransactionResult res;
        byte[] arguments = ABIUtil.encodeDeploymentArguments("Pepe", "PEPE", 8);
        CoinContract pepe = new CoinContract(null, pepeMinter, testERC20Jar, arguments);

        res = pepe.callTotalSupply();
        Assert.assertEquals(0L, new ABIDecoder(res.copyOfTransactionOutput().get()).decodeOneLong());

        res = pepe.callBalanceOf(usr1);
        Assert.assertEquals(0L, new ABIDecoder(res.copyOfTransactionOutput().get()).decodeOneLong());

        res = pepe.callBalanceOf(usr2);
        Assert.assertEquals(0L, new ABIDecoder(res.copyOfTransactionOutput().get()).decodeOneLong());

        res = pepe.callMint(usr1, 5000L);
        Assert.assertEquals(true, new ABIDecoder(res.copyOfTransactionOutput().get()).decodeOneBoolean());

        res = pepe.callBalanceOf(usr1);
        Assert.assertEquals(5000L, new ABIDecoder(res.copyOfTransactionOutput().get()).decodeOneLong());

        res = pepe.callMint(usr2, 10000L);
        Assert.assertEquals(true, new ABIDecoder(res.copyOfTransactionOutput().get()).decodeOneBoolean());

        res = pepe.callBalanceOf(usr2);
        Assert.assertEquals(10000L, new ABIDecoder(res.copyOfTransactionOutput().get()).decodeOneLong());

        res = pepe.callTransfer(usr1, usr2, 2000L);
        Assert.assertEquals(true, new ABIDecoder(res.copyOfTransactionOutput().get()).decodeOneBoolean());

        res = pepe.callBalanceOf(usr1);
        Assert.assertEquals(3000L, new ABIDecoder(res.copyOfTransactionOutput().get()).decodeOneLong());

        res = pepe.callBalanceOf(usr2);
        Assert.assertEquals(12000L, new ABIDecoder(res.copyOfTransactionOutput().get()).decodeOneLong());

        res = pepe.callAllowance(usr1, usr2);
        Assert.assertEquals(0L, new ABIDecoder(res.copyOfTransactionOutput().get()).decodeOneLong());

        res = pepe.callApprove(usr1, usr3, 1000L);
        Assert.assertEquals(true, new ABIDecoder(res.copyOfTransactionOutput().get()).decodeOneBoolean());

        res = pepe.callAllowance(usr1, usr3);
        Assert.assertEquals(1000L, new ABIDecoder(res.copyOfTransactionOutput().get()).decodeOneLong());

        res = pepe.callTransferFrom(usr3, usr1, usr2, 500L);
        Assert.assertEquals(true, new ABIDecoder(res.copyOfTransactionOutput().get()).decodeOneBoolean());

        res = pepe.callAllowance(usr1, usr3);
        Assert.assertEquals(500L, new ABIDecoder(res.copyOfTransactionOutput().get()).decodeOneLong());

        res = pepe.callBalanceOf(usr1);
        Assert.assertEquals(2500L, new ABIDecoder(res.copyOfTransactionOutput().get()).decodeOneLong());

        res = pepe.callBalanceOf(usr2);
        Assert.assertEquals(12500L, new ABIDecoder(res.copyOfTransactionOutput().get()).decodeOneLong());
    }

    @Test
    public void testExchange() {
        byte[] arguments = ABIUtil.encodeDeploymentArguments("Pepe", "PEPE", 8);
        CoinContract pepe = new CoinContract(null, pepeMinter, testERC20Jar, arguments);

        arguments = ABIUtil.encodeDeploymentArguments("Meme", "MEME", 8);
        CoinContract meme = new CoinContract(null, memeMinter, testERC20Jar, arguments);

        ExchangeContract ex = new ExchangeContract(null, exchangeOwner, testExchangeJar);

        TransactionResult res;

        res = ex.callListCoin("PEPE", pepe.addr);
        Assert.assertEquals(true, new ABIDecoder(res.copyOfTransactionOutput().get()).decodeOneBoolean());

        res = ex.callListCoin("MEME", meme.addr);
        Assert.assertEquals(true, new ABIDecoder(res.copyOfTransactionOutput().get()).decodeOneBoolean());

        res = pepe.callMint(usr1, 5000L);
        res = pepe.callMint(usr2, 5000L);

        res = pepe.callApprove(usr1, ex.addr, 2000L);
        Assert.assertEquals(true, new ABIDecoder(res.copyOfTransactionOutput().get()).decodeOneBoolean());

        res = ex.callRequestTransfer("PEPE", usr1, usr2, 1000L);
        Assert.assertEquals(true, new ABIDecoder(res.copyOfTransactionOutput().get()).decodeOneBoolean());

        res = ex.callProcessExchangeTransaction(exchangeOwner);
        Assert.assertEquals(true, new ABIDecoder(res.copyOfTransactionOutput().get()).decodeOneBoolean());

        res = pepe.callBalanceOf(usr1);
        Assert.assertEquals(4000L, new ABIDecoder(res.copyOfTransactionOutput().get()).decodeOneLong());

        res = pepe.callBalanceOf(usr2);
        Assert.assertEquals(6000L, new ABIDecoder(res.copyOfTransactionOutput().get()).decodeOneLong());

        res = pepe.callAllowance(usr1, ex.addr);
        Assert.assertEquals(1000L, new ABIDecoder(res.copyOfTransactionOutput().get()).decodeOneLong());
    }
}
