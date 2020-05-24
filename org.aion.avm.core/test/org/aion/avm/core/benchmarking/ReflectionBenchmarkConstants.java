package org.aion.avm.core.benchmarking;

import java.io.File;
import java.util.Random;

/**
 * This class is here so that we can ensure the benchmarks that are comparing the same operations for
 * reflection and MethodHandle will use the same field/method and supply it with the same arguments.
 *
 * In addition to constants, the shared printTime() method is here as well.
 */
class ReflectionBenchmarkConstants {

    // Spins are set to 1 for testing purposes. Recommended spin values:
    // sameSpins ~15,000,000
    // uniqueSpins ~3,000,000

    static int sameSpins = 1;
    static int uniqueSpins = 1;

    static String targetClassName = ReflectionTarget.class.getCanonicalName();
    static File classpathDirectory = new File(System.getProperty("user.dir") + "/" + ReflectionTarget.class.getPackage().getName() + "/");

    static String staticField = "staticField3";
    static String instanceField = "instanceField2";
    static String staticMethod = "staticMethod4";
    static String instanceMethod = "instanceMethod4";

    static String constructorArg1 = "";
    static Object constructorArg2 = new Object();
    static Character constructorArg3 = 'a';
    static Float[] constructorArg4 = new Float[]{ 1.0f, 2.0f };

    static Boolean booleanValue = Boolean.TRUE;

    static Integer instanceMethodArg1 = 0;
    static Object instanceMethodArg2 = new Object();
    static float instanceMethodArg3 = 1.0f;

    static Number staticMethodArg1 = 1;
    static Random staticMethodArg2 = new Random();
}
