package org.aion.cli;

import java.math.BigInteger;

import org.aion.avm.core.*;
import org.aion.kernel.AvmWrappedTransactionResult.AvmInternalError;
import org.aion.types.AionAddress;
import org.aion.types.Transaction;
import org.aion.avm.core.util.Helpers;
import org.aion.avm.embed.StandardCapabilities;
import org.aion.avm.tooling.ABIUtil;
import org.aion.avm.userlib.CodeAndArguments;
import org.aion.cli.ArgumentParser.Action;
import org.aion.kernel.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.aion.types.TransactionResult;


public class AvmCLI {
    static TestingBlock block = new TestingBlock(new byte[32], 1, Helpers.randomAddress(), System.currentTimeMillis(), new byte[0]);

    public static Transaction setupOneDeploy(IEnvironment env, String storagePath, String jarPath, AionAddress sender, long energyLimit, BigInteger balance) {

        reportDeployRequest(env, storagePath, jarPath, sender);

        File storageFile = new File(storagePath);

        TestingState kernel = new TestingState(storageFile, block);

        Path path = Paths.get(jarPath);
        byte[] jar;
        try {
            jar = Files.readAllBytes(path);
        }catch (IOException e){
            throw env.fail("deploy : Invalid location of Dapp jar");
        }

        return AvmTransactionUtil.create(sender, kernel.getNonce(sender), balance, new CodeAndArguments(jar, null).encodeToBytes(), energyLimit, 1L);
    }

    public static void reportDeployRequest(IEnvironment env, String storagePath, String jarPath, AionAddress sender) {
        lineSeparator(env);
        env.logLine("DApp deployment request");
        env.logLine("Storage      : " + storagePath);
        env.logLine("Dapp Jar     : " + jarPath);
        env.logLine("Sender       : " + sender);
    }

    public static void reportDeployResult(IEnvironment env, FutureResult future){
        TransactionResult createResult = future.getResult();
        String dappAddress = Helpers.bytesToHexString(createResult.copyOfTransactionOutput().get());
        env.noteRelevantAddress(dappAddress);

        // In the case of SUCCESS, the causeOfError is the empty string. But we want to explicitly display SUCCESS.
        String status = createResult.transactionStatus.causeOfError;
        status = (status.isEmpty()) ? "SUCCESS" : status;
        
        lineSeparator(env);
        env.logLine("DApp deployment status");
        env.logLine("Result status: " + status);
        env.logLine("Dapp Address : " + dappAddress);
        env.logLine("Energy cost  : " + createResult.energyUsed);
    }

    public static Transaction setupOneCall(IEnvironment env, String storagePath, AionAddress contract, AionAddress sender, String method, Object[] args, long energyLimit, long nonceBias, BigInteger balance) {
        reportCallRequest(env, storagePath, contract, sender, method, args);

        byte[] arguments = ABIUtil.encodeMethodArguments(method, args);
        return commonSetupTransaction(env, storagePath, contract, sender, arguments, energyLimit, nonceBias, balance);
    }

    public static Transaction setupOneTransfer(IEnvironment env, String storagePath, AionAddress recipient, AionAddress sender, long energyLimit, long nonceBias, BigInteger balance) {
        reportTransferRequest(env, storagePath, recipient, sender, balance);

        return commonSetupTransaction(env, storagePath, recipient, sender, new byte[0], energyLimit, nonceBias, balance);
    }

    private static Transaction commonSetupTransaction(IEnvironment env, String storagePath, AionAddress target, AionAddress sender, byte[] data, long energyLimit, long nonceBias, BigInteger balance) {

        File storageFile = new File(storagePath);

        TestingState kernel = new TestingState(storageFile, block);

        // Note that we can remove this bias when/if we change this to no longer send all transactions from the same account.
        BigInteger biasedNonce = kernel.getNonce(sender).add(BigInteger.valueOf(nonceBias));
        return AvmTransactionUtil.call(sender, target, biasedNonce, balance, data, energyLimit, 1L);
    }

    private static void reportCallRequest(IEnvironment env, String storagePath, AionAddress contract, AionAddress sender, String method, Object[] args){
        lineSeparator(env);
        env.logLine("DApp call request");
        env.logLine("Storage      : " + storagePath);
        env.logLine("Dapp Address : " + contract);
        env.logLine("Sender       : " + sender);
        env.logLine("Method       : " + method);
        env.logLine("Arguments    : ");
        for (int i = 0; i < args.length; i += 2){
            env.logLine("             : " + args[i]);
        }
    }

    private static void reportCallResult(IEnvironment env, FutureResult future){
        TransactionResult callResult = future.getResult();

        lineSeparator(env);
        env.logLine("DApp call result");
        env.logLine("Result status: " + callResult.transactionStatus);
        env.logLine("Return value : " + Helpers.bytesToHexString(callResult.copyOfTransactionOutput().get()));
        env.logLine("Energy cost  : " + callResult.energyUsed);

        if (AvmInternalError.FAILED_EXCEPTION.error.equals(callResult.transactionStatus.causeOfError)) {
            env.dumpThrowable(future.getException());
        }
    }

    private static void reportTransferRequest(IEnvironment env, String storagePath, AionAddress address, AionAddress sender, BigInteger balance){
        lineSeparator(env);
        env.logLine("Balance transfer request");
        env.logLine("Storage      : " + storagePath);
        env.logLine("address      : " + address);
        env.logLine("Sender       : " + sender);
        env.logLine("Balance      : " + balance);
    }

    private static void reportTransferResult(IEnvironment env, FutureResult future){
        TransactionResult transferResult = future.getResult();

        lineSeparator(env);
        env.logLine("DApp balance transfer result");
        env.logLine("Result status: " + transferResult.transactionStatus);
        if (!transferResult.copyOfTransactionOutput().isPresent()){
            env.logLine("Return value : " + "void");
        } else {
            env.logLine("Return value : " + Helpers.bytesToHexString(transferResult.copyOfTransactionOutput().get()));
        }
        env.logLine("Energy cost  : " + transferResult.energyUsed);

        if (AvmInternalError.FAILED_EXCEPTION.error.equals(transferResult.transactionStatus.causeOfError)) {
            env.dumpThrowable(future.getException());
        }
    }

    private static void lineSeparator(IEnvironment env){
        env.logLine("*******************************************************************************************");
    }

    public static void openAccount(IEnvironment env, String storagePath, AionAddress toOpen){
        lineSeparator(env);

        env.logLine("Creating Account " + toOpen);

        File storageFile = new File(storagePath);
        TestingState kernel = new TestingState(storageFile, block);

        kernel.createAccount(toOpen);
        kernel.adjustBalance(toOpen, BigInteger.valueOf(100000000000L));

        env.logLine("Account Balance : " + kernel.getBalance(toOpen));
    }

    public static void testingMain(IEnvironment env, String[] args) {
        internalMain(env, args);
    }

    static public void main(String[] args) {
        IEnvironment env = new IEnvironment() {
            @Override
            public RuntimeException fail(String message) {
                if (null != message) {
                    System.err.println(message);
                }
                System.exit(1);
                throw new RuntimeException();
            }
            @Override
            public void noteRelevantAddress(String address) {
                // This implementation doesn't care.
            }
            @Override
            public void logLine(String line) {
                System.out.println(line);
            }

            @Override
            public void dumpThrowable(Throwable throwable) {
                throwable.printStackTrace();
            }
        };
        internalMain(env, args);
    }

    private static void internalMain(IEnvironment env, String[] args) {
        ArgumentParser.Invocation invocation = ArgumentParser.parseArgs(args);

        if (null == invocation.errorString) {
            // There must be at least one command or there should have been a parse error (usually just defaulting to usage).
            assertTrue(invocation.commands.size() > 0);

            if (!invocation.commands.get(0).action.equals(Action.BYTES) && !invocation.commands.get(0).action.equals(Action.ENCODE_CALL)) {
                // This logging line is largely just for test verification so it might be removed in the future.
                env.logLine("Running block with " + invocation.commands.size() + " transactions");
            }
            
            // Do the thing.
            // Before we run any command, make sure that the specified storage directory exists.
            // (we want the underlying storage engine to remain very passive so it should always expect that the directory was created for it).
            verifyStorageExists(env, invocation.storagePath);

            IExternalCapabilities capabilities = new StandardCapabilities();

            // See if this is a non-batching case or if we are just going to roll these into an AVM invocation.
            if (null != invocation.nonBatchingAction) {
                ArgumentParser.Command command = invocation.commands.get(0);
                switch (command.action) {
                case CALL:
                case DEPLOY:
                case TRANSFER:
                    // This should be in the batching path.
                    unreachable("This should be in the batching path");
                    break;
                case OPEN:
                    openAccount(env, invocation.storagePath, new AionAddress(Helpers.hexStringToBytes(command.contractAddress)));
                    break;
                case BYTES:
                    try {
                        Path path = Paths.get(command.jarPath);
                        byte[] jar = Files.readAllBytes(path);
                        System.out.println(Helpers.bytesToHexString(
                            new CodeAndArguments(jar, new byte[0]).encodeToBytes()));
                    } catch (IOException e) {
                        System.out.println(e.toString());
                        System.exit(1);
                    }
                    break;
                case ENCODE_CALL:
                    Object[] callArgs = new Object[command.args.size()];
                    command.args.toArray(callArgs);
                    System.out.println(Helpers.bytesToHexString(ABIUtil.encodeMethodArguments(command.method, callArgs)));
                    break;
                default:
                    throw new AssertionError("Unknown option");
                }
            } else {
                // Setup the transactions.
                Transaction[] transactions = new Transaction[invocation.commands.size()];
                for (int i = 0; i < invocation.commands.size(); ++i) {
                    ArgumentParser.Command command = invocation.commands.get(i);
                    switch (command.action) {
                    case CALL:
                        Object[] callArgs = new Object[command.args.size()];
                        command.args.toArray(callArgs);
                        transactions[i] = setupOneCall(
                            env,
                            invocation.storagePath,
                            new AionAddress(Helpers.hexStringToBytes(command.contractAddress)),
                            new AionAddress(Helpers.hexStringToBytes(command.senderAddress)),
                            command.method, callArgs,
                            command.energyLimit,
                            i,
                            command.balance);
                        break;
                    case DEPLOY:
                        transactions[i] = setupOneDeploy(
                            env,
                            invocation.storagePath,
                            command.jarPath,
                            new AionAddress(Helpers.hexStringToBytes(command.senderAddress)),
                            command.energyLimit,
                            command.balance);
                        break;
                    case TRANSFER:
                        Object[] transferArgs = new Object[command.args.size()];
                        command.args.toArray(transferArgs);
                        transactions[i] = setupOneTransfer(
                                env,
                                invocation.storagePath,
                                new AionAddress(Helpers.hexStringToBytes(command.contractAddress)),
                                new AionAddress(Helpers.hexStringToBytes(command.senderAddress)),
                                command.energyLimit,
                                i,
                                command.balance);
                        break;
                    case OPEN:
                        // This should be in the non-batching path.
                        unreachable("This should be in the batching path");
                        break;
                    default:
                        throw new AssertionError("Unknown option");
                    }
                }
                
                // Run them in a single batch.
                File storageFile = new File(invocation.storagePath);
                TestingState kernel = new TestingState(storageFile, block);
                AvmImpl avm = CommonAvmFactory.buildAvmInstanceForConfiguration(capabilities, new AvmConfiguration());
                FutureResult[] futures = avm.run(kernel, transactions, ExecutionType.ASSUME_MAINCHAIN, 0);
                
                // Finish up with reporting.
                for (int i = 0; i < invocation.commands.size(); ++i) {
                    ArgumentParser.Command command = invocation.commands.get(i);
                    switch (command.action) {
                    case CALL:
                        reportCallResult(env, futures[i]);
                        break;
                    case DEPLOY:
                        reportDeployResult(env, futures[i]);
                        break;
                    case TRANSFER:
                        reportTransferResult(env, futures[i]);
                        break;
                    case OPEN:
                        // This should be in the non-batching path.
                        unreachable("This should be in the batching path");
                        break;
                    default:
                        throw new AssertionError("Unknown option");
                    }
                }

                avm.shutdown();
            }
        } else {
            env.fail(invocation.errorString);
        }
    }

    private static void verifyStorageExists(IEnvironment env, String storageRoot) {
        File directory = new File(storageRoot);
        if (!directory.isDirectory()) {
            boolean didCreate = directory.mkdirs();
            // Is this the best way to handle this failure?
            if (!didCreate) {
                // System.exit isn't ideal but we are very near the top of the entry-point so this shouldn't cause confusion.
                throw env.fail("Failed to create storage root: \"" + storageRoot + "\"");
            }
        }
    }

    private static void assertTrue(boolean flag) {
        // We use a private helper to manage the assertions since the JDK default disables them.
        if (!flag) {
            throw new AssertionError("Case must be true");
        }
    }

    private static void unreachable(String message) {
        throw new AssertionError(message);
    }
}
