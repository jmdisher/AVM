package org.aion.avm.core.miscvisitors.interfaceVisitor;

public class ClassWithNoInterfaceFields {
    interface InnerInterface {
        static void f(){}
        static int f2(){ return 2;}
    }
}

interface outerInterfaceNoFields{
    static void f(){}
}
