package org.aion.avm.core.poc;

import org.aion.avm.api.ABIEncoder;
import org.aion.avm.api.Address;
import org.aion.avm.core.Avm;
import org.aion.avm.core.NodeEnvironment;
import org.aion.avm.core.TestingHelper;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.core.testExchange.CoinController;
import org.aion.avm.core.testExchange.ERC20;
import org.aion.avm.core.testExchange.ERC20Token;
import org.aion.avm.core.util.CodeAndArguments;
import org.aion.avm.core.util.Helpers;
import org.aion.avm.userlib.AionList;
import org.aion.avm.userlib.AionMap;
import org.aion.avm.userlib.AionSet;
import org.aion.kernel.Block;
import org.aion.kernel.KernelInterfaceImpl;
import org.aion.kernel.Transaction;
import org.aion.kernel.TransactionContext;
import org.aion.kernel.TransactionContextImpl;
import org.aion.kernel.TransactionResult;
import org.junit.Test;


public class Demo {

    private Block block = new Block(new byte[32], 1, Helpers.randomBytes(Address.LENGTH), System.currentTimeMillis(), new byte[0]);
    private long energyLimit = 5_000_000;
    private long energyPrice = 1;

    private byte[] pepeMinter = Helpers.randomBytes(Address.LENGTH);
    private byte[] deployer = Helpers.randomBytes(Address.LENGTH);
    private byte[] owner1 = Helpers.randomBytes(Address.LENGTH);
    private byte[] owner2 = Helpers.randomBytes(Address.LENGTH);
    private byte[] receiver = Helpers.randomBytes(Address.LENGTH);

    @Test
    public void testWallet() {
        Avm avm = NodeEnvironment.singleton.buildAvmInstance(new KernelInterfaceImpl());

        //================
        // DEPLOY
        //================

        System.out.println(">> Deploy \"PEPE\" ERC20 token Dapp...");
        byte[] jar = JarBuilder.buildJarForMainAndClasses(CoinController.class, ERC20.class, ERC20Token.class, AionList.class, AionSet.class, AionMap.class);
        byte[] arguments = ABIEncoder.encodeMethodArguments("", "Pepe".toCharArray(), "PEPE".toCharArray(), 8);
        //CoinContract pepe = new CoinContract(null, pepeMinter, testERC20Jar, arguments);
        Transaction createTransaction = new Transaction(Transaction.Type.CREATE, pepeMinter, null, 0, new CodeAndArguments(jar, arguments).encodeToBytes(), energyLimit, energyPrice);
        TransactionContext txContext = new TransactionContextImpl(createTransaction, block);
        TransactionResult txResult = avm.run(txContext);
        Address tokenDapp = TestingHelper.buildAddress(txResult.getReturnData());
        System.out.println(">> \"PEPE\" ERC20 token Dapp is deployed. (Address " + Helpers.toHexString(txResult.getReturnData()) + ")");

        System.out.println("\n>> Deploy the Multi-sig Wallet Dapp...");
        jar = JarBuilder.buildJarForMainAndClasses(Main.class, Wallet.class, Bytes32.class, AionList.class, AionSet.class, AionMap.class);
        int confirmationsRequired = 2;
        arguments = ABIEncoder.encodeMethodArguments("", TestingHelper.buildAddress(owner1), TestingHelper.buildAddress(owner2), confirmationsRequired);
        Transaction tx = new Transaction(Transaction.Type.CREATE, deployer, null, 0L, new CodeAndArguments(jar, arguments).encodeToBytes(), energyLimit, energyPrice);
        txContext = new TransactionContextImpl(tx, block);
        txResult = avm.run(txContext);
        Address walletDapp = TestingHelper.buildAddress(txResult.getReturnData());
        System.out.println(">> Wallet Dapp is deployed. (Address " + Helpers.toHexString(txResult.getReturnData()) + ")");
        System.out.println(">> Owners List:");
        System.out.println(">>   Deployer - (Address " + Helpers.toHexString(deployer) + ")");
        System.out.println(">>   Owner 1  - (Address " + Helpers.toHexString(owner1) + ")");
        System.out.println(">>   Owner 2  - (Address " + Helpers.toHexString(owner2) + ")");
        System.out.println(">> Minimum number of owners to approve a transaction: " + confirmationsRequired);

        //================
        // FUNDING and CHECK BALANCE
        //================
        arguments = ABIEncoder.encodeMethodArguments("mint", walletDapp, 5000L);
        tx = new Transaction(Transaction.Type.CALL, pepeMinter, tokenDapp.unwrap(), 0, arguments, energyLimit, energyPrice);
        txContext = new TransactionContextImpl(tx, block);
        txResult = avm.run(txContext);
        System.out.println("\n>> PEPE Mint to deliver 5000 tokens to the wallet: " + TestingHelper.decodeResult(txResult));

        arguments = ABIEncoder.encodeMethodArguments("balanceOf", walletDapp);
        tx = new Transaction(Transaction.Type.CALL, pepeMinter, tokenDapp.unwrap(), 0, arguments, energyLimit, energyPrice);
        txContext = new TransactionContextImpl(tx, block);
        txResult = avm.run(txContext);
        System.out.println(">> balance of wallet: " + TestingHelper.decodeResult(txResult));

        arguments = ABIEncoder.encodeMethodArguments("balanceOf", TestingHelper.buildAddress(receiver));
        tx = new Transaction(Transaction.Type.CALL, pepeMinter, tokenDapp.unwrap(), 0, arguments, energyLimit, energyPrice);
        txContext = new TransactionContextImpl(tx, block);
        txResult = avm.run(txContext);
        System.out.println(">> balance of receiver: " + TestingHelper.decodeResult(txResult));

        //================
        // PROPOSE
        //================
        byte[] data = ABIEncoder.encodeMethodArguments("transfer", TestingHelper.buildAddress(receiver), 3000L);
        arguments = ABIEncoder.encodeMethodArguments("propose", tokenDapp, 0L, data, energyLimit);
        tx = new Transaction(Transaction.Type.CALL, deployer, walletDapp.unwrap(), 0L, arguments, 2_000_000L, energyPrice);
        txContext = new TransactionContextImpl(tx, block);
        txResult = avm.run(txContext);
        System.out.println("\n>> Deployer to propose a transaction of 3000 PEPE tokens to Receiver. (Tx ID " + Helpers.toHexString((byte[]) TestingHelper.decodeResult(txResult)) + ")");
        byte[] pendingTx = (byte[]) TestingHelper.decodeResult(txResult);

        //================
        // CONFIRM #1
        //================
        arguments = ABIEncoder.encodeMethodArguments("confirm", pendingTx);
        tx = new Transaction(Transaction.Type.CALL, owner1, walletDapp.unwrap(), 0L, arguments, energyLimit, energyPrice);
        txContext = new TransactionContextImpl(tx, block);
        txResult = avm.run(txContext);
        System.out.println(">> Transaction confirmed by Owner 1: " + TestingHelper.decodeResult(txResult));

        //================
        // CONFIRM #2
        //================
        arguments = ABIEncoder.encodeMethodArguments("confirm", pendingTx);
        tx = new Transaction(Transaction.Type.CALL, owner2, walletDapp.unwrap(), 0L, arguments, energyLimit, energyPrice);
        txContext = new TransactionContextImpl(tx, block);
        txResult = avm.run(txContext);
        System.out.println(">> Transaction confirmed by Owner 2: " + TestingHelper.decodeResult(txResult));

        System.out.println("\n>> Number of confirmations reach to " + confirmationsRequired + ". Transaction is processed.");

        //================
        // CHECK BALANCE
        //================
        arguments = ABIEncoder.encodeMethodArguments("balanceOf", walletDapp);
        tx = new Transaction(Transaction.Type.CALL, pepeMinter, tokenDapp.unwrap(), 0, arguments, energyLimit, energyPrice);
        txContext = new TransactionContextImpl(tx, block);
        txResult = avm.run(txContext);
        System.out.println("\n>> balance of wallet: " + TestingHelper.decodeResult(txResult));

        arguments = ABIEncoder.encodeMethodArguments("balanceOf", TestingHelper.buildAddress(receiver));
        tx = new Transaction(Transaction.Type.CALL, pepeMinter, tokenDapp.unwrap(), 0, arguments, energyLimit, energyPrice);
        txContext = new TransactionContextImpl(tx, block);
        txResult = avm.run(txContext);
        System.out.println(">> balance of receiver: " + TestingHelper.decodeResult(txResult));
    }
}
