package org.aion.avm.embed;

import avm.Address;
import org.aion.types.AionAddress;
import org.aion.avm.core.util.Helpers;
import org.aion.avm.embed.poc.TRS;
import org.aion.avm.tooling.ABIUtil;
import org.aion.avm.userlib.AionMap;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.kernel.*;
import org.aion.types.TransactionResult;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.math.BigInteger;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

public class TrsTest {
    @ClassRule
    public static AvmRule avmRule = new AvmRule(true);

    private static AionAddress DEPLOYER;
    private static Address DEPLOYER_API;
    private static final long ENERGY_LIMIT = 100_000_000_000L;
    private static final long ENERGY_PRICE = 1;
    private static final int NUM_PERIODS = 12;

    private static TestingState kernel;

    @BeforeClass
    public static void setup() {
        DEPLOYER_API = avmRule.getPreminedAccount();
        DEPLOYER = new AionAddress(DEPLOYER_API.toByteArray());
        kernel = avmRule.kernel;
    }

    private AionAddress contract;

    @Test
    public void testDeployTrs() {
        assertTrue(deployContract().transactionStatus.isSuccess());
    }

    /**
     * Tests the typical basic use case of the TRS contract:
     *
     * 1. deploy contract
     * 2. initialize contract
     * 3. fund the contract
     * 4. mint an account to the contract
     * 5. lock the contract
     * 6. start the contract
     * 7. make withdrawals in each period
     */
    @Test
    public void testTrs() {
        BigInteger basicBalance = BigInteger.valueOf(1_000_000_000_000L);
        BigInteger trsFunds = BigInteger.valueOf(100_000);
        AionAddress account = Helpers.randomAddress();

        // Deploy and initialize the contract.
        assertTrue(deployContract().transactionStatus.isSuccess());
        assertTrue(initializeTrs().transactionStatus.isSuccess());

        // Send funds to the contract and verify they were received.
        assertTrue(sendFundsToTrs(trsFunds).transactionStatus.isSuccess());
        assertEquals(trsFunds, kernel.getBalance(contract));

        // Mint an account into the contract so that it gets all of the funds.
        assertTrue(mintAccountToTrs(account, trsFunds).transactionStatus.isSuccess());

        // Lock and start the contract.
        assertTrue(lockTrs().transactionStatus.isSuccess());
        assertTrue(startTrs().transactionStatus.isSuccess());

        // Give account some basic balance so that it is able to make withdrawals.
        assertTrue(sendFundsTo(account, basicBalance).transactionStatus.isSuccess());
        assertEquals(basicBalance, kernel.getBalance(account));

        // Step through each period in the contract and withdraw the funds.
        BigInteger accountBalance = basicBalance;

        for (int i = 0; i < NUM_PERIODS; i++) {
            TransactionResult result = withdrawFromTrs(account);

            // Check the transaction was successful.
            assertTrue(result.transactionStatus.isSuccess());

            // Check the return value is true, indicating a non-zero withdrawal amount.
            assertTrue(new ABIDecoder(result.copyOfTransactionOutput().get()).decodeOneBoolean());

            // Update the account balance by deducting the transaction cost from the previous balance.
            long callCost = result.energyUsed * ENERGY_PRICE;
            accountBalance = accountBalance.subtract(BigInteger.valueOf(callCost));

            // Move into next period.
            moveIntoNextPeriod();
        }

        // Check that the total amount of funds from the trs contract have been claimed.
        assertEquals(accountBalance.add(trsFunds), kernel.getBalance(account));
        assertEquals(BigInteger.ZERO, kernel.getBalance(contract));
    }

    /**
     * The period is determined by the current block timestamp. This method replaces block with a
     * new block whose timestamp has been moved ahead in time so that it is now in the next period.
     */
    private void moveIntoNextPeriod() {
        long previousBlockTime = avmRule.kernel.getBlockTimestamp();
        long secondsPerPeriod = TRS.intervalSecs;
        while(avmRule.kernel.getBlockTimestamp() < previousBlockTime + secondsPerPeriod) {
            avmRule.kernel.generateBlock();
        }
    }

    private TransactionResult sendFundsToTrs(BigInteger amount) {
        return sendFundsTo(contract, amount);
    }

    private TransactionResult sendFundsTo(AionAddress recipient, BigInteger amount) {
        Address recipientAddress = new Address(recipient.toByteArray());
        AvmRule.ResultWrapper result = avmRule.balanceTransfer(DEPLOYER_API, recipientAddress, amount, ENERGY_LIMIT, ENERGY_PRICE);
        return result.getTransactionResult();
    }

    private TransactionResult mintAccountToTrs(AionAddress account, BigInteger amount) {
        return callContract("mint", new Address(account.toByteArray()), amount.longValue());
    }

    private TransactionResult withdrawFromTrs(AionAddress recipient) {
        return callContract(recipient, "withdraw");
    }

    private TransactionResult startTrs() {
        return callContract("start", avmRule.kernel.getBlockTimestamp());
    }

    private TransactionResult lockTrs() {
        return callContract("lock");
    }

    private TransactionResult initializeTrs() {
        return callContract("init", NUM_PERIODS, 0);
    }

    private TransactionResult callContract(String method, Object... parameters) {
        return callContract(DEPLOYER, method, parameters);
    }

    private TransactionResult callContract(AionAddress sender, String method, Object... parameters) {
        byte[] callData = ABIUtil.encodeMethodArguments(method, parameters);
        Address contractAddress = new Address(contract.toByteArray());
        Address senderAddress = new Address(sender.toByteArray());
        AvmRule.ResultWrapper result = avmRule.call(senderAddress, contractAddress, BigInteger.ZERO, callData, ENERGY_LIMIT, ENERGY_PRICE);
        assertTrue(result.getReceiptStatus().isSuccess());
        return result.getTransactionResult();

    }

    private TransactionResult deployContract() {
        byte[] jarBytes = avmRule.getDappBytes(TRS.class, null, AionMap.class);

        AvmRule.ResultWrapper result = avmRule.deploy(DEPLOYER_API, BigInteger.ZERO, jarBytes, ENERGY_LIMIT, ENERGY_PRICE);
        assertTrue(result.getReceiptStatus().isSuccess());
        contract = new AionAddress(result.getDappAddress().toByteArray());
        return result.getTransactionResult();
    }

}
