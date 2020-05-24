package org.aion.avm.embed.collection;

import java.math.BigInteger;

import avm.Address;

import org.aion.avm.embed.AvmRule;
import org.aion.avm.tooling.ABIUtil;
import org.aion.avm.userlib.AionList;
import org.aion.avm.userlib.AionMap;
import org.aion.avm.userlib.AionSet;
import org.aion.types.TransactionResult;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

public class AionCollectionInterfaceTest {

    @ClassRule
    public static AvmRule avmRule = new AvmRule(false);
    private Address from = avmRule.getPreminedAccount();
    private long energyLimit = 10_000_000L;
    private long energyPrice = 1;

    private TransactionResult deploy(){
        byte[] dappBytes = avmRule.getDappBytes(AionCollectionInterfaceContract.class, new byte[0], AionList.class, AionSet.class, AionMap.class);
        TransactionResult createResult = avmRule.deploy(from,  BigInteger.ZERO, dappBytes, energyLimit, energyPrice).getTransactionResult();
        Assert.assertTrue(createResult.transactionStatus.isSuccess());
        return createResult;
    }

    private TransactionResult call(Address contract, byte[] args) {
        TransactionResult callResult = avmRule.call(from, contract, BigInteger.ZERO, args, energyLimit, energyPrice).getTransactionResult();
        Assert.assertTrue(callResult.transactionStatus.isSuccess());
        return callResult;
    }

    @Test
    public void testList() {
        TransactionResult deployRes = deploy();
        Address contract = new Address(deployRes.copyOfTransactionOutput().get());

        byte[] args = ABIUtil.encodeMethodArguments("testList");
        TransactionResult testResult = call(contract, args);
	Assert.assertTrue(testResult.transactionStatus.isSuccess());
    }

    @Test
    public void testSet() {
        TransactionResult deployRes = deploy();
        Address contract = new Address(deployRes.copyOfTransactionOutput().get());

        byte[] args = ABIUtil.encodeMethodArguments("testSet");
        TransactionResult testResult = call(contract, args);
        Assert.assertTrue(testResult.transactionStatus.isSuccess());
    }

    @Test
    public void testMap() {
        TransactionResult deployRes = deploy();
        Address contract = new Address(deployRes.copyOfTransactionOutput().get());

        byte[] args = ABIUtil.encodeMethodArguments("testMap");
        TransactionResult testResult = call(contract, args);
        Assert.assertTrue(testResult.transactionStatus.isSuccess());
    }

}
