package org.aion.avm.core;

import org.aion.avm.api.ABIDecoder;
import org.aion.avm.api.Address;
import org.aion.avm.arraywrapper.CharArray2D;
import org.aion.avm.arraywrapper.IntArray2D;
import org.aion.avm.arraywrapper.ObjectArray;
import org.aion.avm.internal.IHelper;
import org.aion.avm.internal.RuntimeAssertionError;
import org.aion.avm.shadow.java.lang.Class;
import org.aion.kernel.TransactionResult;


/**
 * Implements the IHelper interface for tests which need to create runtime objects or otherwise interact with the parts of the system
 * which assume that there is an IHelper installed.
 * It automatically installs itself as the helper and provides utilities to install and remove itself from IHelper.currentContractHelper.
 * Additionally, it provides some common static helpers for common cases of its use.
 */
public class TestingHelper implements IHelper {
    public static Address buildAddress(byte[] raw) {
        TestingHelper helper = new TestingHelper();
        Address data = new Address(raw);
        helper.remove();
        return data;
    }
    public static Object decodeResult(TransactionResult result) {
        return decodeResultRaw(result.getReturnData());
    }
    public static Object decodeResultRaw(byte[] returnData) {
        Object data = null;
        if (null != returnData) {
            TestingHelper helper = new TestingHelper();
            data = ABIDecoder.decodeOneObject(returnData);
            helper.remove();
        }
        return data;
    }
    public static ObjectArray construct2DWrappedArray(Object data) {
        TestingHelper helper = new TestingHelper();
        ObjectArray ret = null;
        if (data.getClass().getName() == "[[C") {
            ret = new CharArray2D((char[][]) data);
        }
        else if (data.getClass().getName() == "[[I") {
            ret = new IntArray2D((int[][]) data);
        } // add code for other 2D wrapped array when needed.
        helper.remove();
        return ret;
    }

    public TestingHelper() {
        install();
    }

    private void install() {
        RuntimeAssertionError.assertTrue(null == IHelper.currentContractHelper.get());
        IHelper.currentContractHelper.set(this);
    }
    private void remove() {
        RuntimeAssertionError.assertTrue(this == IHelper.currentContractHelper.get());
        IHelper.currentContractHelper.remove();
    }

    @Override
    public void externalChargeEnergy(long cost) {
        // Free!
    }

    @Override
    public void externalSetEnergy(long energy) {
        throw RuntimeAssertionError.unreachable("Shouldn't be called in the testing code");
    }

    @Override
    public long externalGetEnergyRemaining() {
        throw RuntimeAssertionError.unreachable("Shouldn't be called in the testing code");
    }

    @Override
    public Class<?> externalWrapAsClass(java.lang.Class<?> input) {
        throw RuntimeAssertionError.unreachable("Shouldn't be called in the testing code");
    }

    @Override
    public int externalGetNextHashCode() {
        // This is called pretty often when decoding data, etc, from outside the test so we want to just return anything.
        return 1;
    }

    @Override
    public int captureSnapshotAndNextHashCode() {
        throw RuntimeAssertionError.unreachable("Shouldn't be called in the testing code");
    }

    @Override
    public void applySnapshotAndNextHashCode(int nextHashCode) {
        throw RuntimeAssertionError.unreachable("Shouldn't be called in the testing code");
    }

    @Override
    public void externalBootstrapOnly() {
        throw RuntimeAssertionError.unreachable("Shouldn't be called in the testing code");
    }
}
