package org.aion.avm.core.collection;

import avm.Blockchain;
import org.aion.avm.userlib.AionSet;
import org.aion.avm.userlib.abi.ABIDecoder;

public class AionSetPerfContract {

    public static int SIZE = 500;

    public static AionSet<Integer> target;


    static{
        target = new AionSet<>();
    }

    public static byte[] main() {
        ABIDecoder decoder = new ABIDecoder(Blockchain.getData());
        String methodName = decoder.decodeMethodName();
        if (methodName == null) {
            return new byte[0];
        } else {
            if (methodName.equals("callInit")) {
                callInit();
                return new byte[0];
            } else if (methodName.equals("callAdd")) {
                callAdd();
                return new byte[0];
            } else if (methodName.equals("callContains")) {
                callContains();
                return new byte[0];
            } else if (methodName.equals("callRemove")) {
                callRemove();
                return new byte[0];
            } else {
                return new byte[0];
            }
        }
    }

    public static void callInit(){
        for (int i = 0; i < SIZE; i++){
            target.add(Integer.valueOf(i));
        }
    }

    public static void callAdd(){
        for (int i = 0; i < SIZE; i++){
            target.add(Integer.valueOf(SIZE + 1));
        }
    }

    public static void callContains(){
        for (int i = 0; i < SIZE; i++){
            target.contains(Integer.valueOf(i));
        }
    }

    public static void callRemove(){
        for (int i = 0; i < SIZE; i++){
            target.remove(Integer.valueOf(i));
        }
    }
}
