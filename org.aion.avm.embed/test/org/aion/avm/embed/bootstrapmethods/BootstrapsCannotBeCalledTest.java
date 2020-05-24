package org.aion.avm.embed.bootstrapmethods;

import avm.Address;

import org.aion.avm.embed.AvmRule;
import org.aion.types.TransactionResult;
import org.junit.ClassRule;
import org.junit.Test;

import java.math.BigInteger;

import static junit.framework.TestCase.assertTrue;

public class BootstrapsCannotBeCalledTest {
    @ClassRule
    public static AvmRule avmRule = new AvmRule(false);
    private Address deployer = avmRule.getPreminedAccount();

    @Test
    public void testLambdaMetaFactory() {
        TransactionResult result = deployContract(MetaFactoryTarget.class);
        assertTrue(result.transactionStatus.isFailed());
    }

    private TransactionResult deployContract(Class<?> contract) {
        return avmRule.deploy(deployer, BigInteger.ZERO, avmRule.getDappBytesWithoutOptimization(contract, null)).getTransactionResult();
    }

}
