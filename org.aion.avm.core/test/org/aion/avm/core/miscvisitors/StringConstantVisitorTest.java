package org.aion.avm.core.miscvisitors;

import org.aion.avm.core.ClassToolchain;
import org.aion.avm.core.ConstantClassBuilder;
import org.aion.avm.core.NodeEnvironment;
import org.aion.avm.core.classloading.AvmClassLoader;
import org.aion.avm.core.util.DebugNameResolver;
import org.aion.avm.core.util.Helpers;
import org.aion.avm.utilities.Utilities;

import i.CommonInstrumentation;
import i.IInstrumentation;
import i.IRuntimeSetup;
import i.InstrumentationHelpers;
import i.InternedClasses;
import i.PackageConstants;
import jdk.nashorn.internal.ir.SetSplitState;

import org.junit.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;


public class StringConstantVisitorTest {
    private IInstrumentation instrumentation;
    private IRuntimeSetup runtimeSetup;
    private Class<?> clazz;
    private Class<?> clazzNoStatic;

    @Before
    public void setup() throws Exception {
        String targetTestName = StringConstantVisitorTestTarget.class.getName();
        byte[] targetTestBytes = Utilities.loadRequiredResourceAsBytes(targetTestName.replaceAll("\\.", "/") + ".class");
        String targetNoStaticName = StringConstantVisitorTestTargetNoStatic.class.getName();
        byte[] targetNoStaticBytes = Utilities.loadRequiredResourceAsBytes(targetNoStaticName.replaceAll("\\.", "/") + ".class");
        
        // WARNING:  We are providing the class set as both the "classes only" and "classes plus interfaces" sets.
        // This works for this test but, in general, is not correct.
        Set<String> userClassDotNameSet = Arrays.stream(new String[] { targetTestName, targetNoStaticName }).collect(Collectors.toSet());
        PreRenameClassAccessRules classAccessRules = new PreRenameClassAccessRules(userClassDotNameSet, userClassDotNameSet);
        
        // We will need to produce the constant class.
        Set<byte[]> inputClasses = Arrays.stream(new byte[][] { targetTestBytes, targetNoStaticBytes }).collect(Collectors.toSet());
        ConstantClassBuilder.ConstantClassInfo constantClass = ConstantClassBuilder.buildConstantClassBytecodeForClasses(PackageConstants.kConstantClassName, inputClasses);
        
        Function<byte[], byte[]> transformer = (inputBytes) ->
                new ClassToolchain.Builder(inputBytes, ClassReader.SKIP_DEBUG)
                        .addNextVisitor(new UserClassMappingVisitor(new NamespaceMapper(classAccessRules), false))
                        .addNextVisitor(new ConstantVisitor(PackageConstants.kConstantClassName, constantClass.constantToFieldMap))
                        .addWriter(new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS))
                        .build()
                        .runAndGetBytecode();
        Map<String, byte[]> classes = new HashMap<>();
        classes.put(DebugNameResolver.getUserPackageDotPrefix(targetTestName, false), transformer.apply(targetTestBytes));
        classes.put(DebugNameResolver.getUserPackageDotPrefix(targetNoStaticName, false), transformer.apply(targetNoStaticBytes));
        classes.put(PackageConstants.kConstantClassName, constantClass.bytecode);

        Map<String, byte[]> classAndHelper = Helpers.mapIncludingHelperBytecode(classes, Helpers.loadDefaultHelperBytecode());
        AvmClassLoader loader = NodeEnvironment.singleton.createInvocationClassLoader(classAndHelper);
        
        this.instrumentation = new CommonInstrumentation();
        InstrumentationHelpers.attachThread(this.instrumentation);
        this.runtimeSetup = Helpers.getSetupForLoader(loader);
        InstrumentationHelpers.pushNewStackFrame(this.runtimeSetup, loader, 1_000_000L, 1, new InternedClasses());
        
        this.clazz = loader.loadUserClassByOriginalName(targetTestName, false);
        this.clazzNoStatic = loader.loadUserClassByOriginalName(targetNoStaticName, false);
    }

    @After
    public void clearTestingState() {
        InstrumentationHelpers.popExistingStackFrame(this.runtimeSetup);
        InstrumentationHelpers.detachThread(this.instrumentation);
    }

    @Test
    public void testLoadStringConstant() throws Exception {
        Object obj = this.clazz.getConstructor().newInstance();
        
        // Get the constant via the method.
        Method method = this.clazz.getMethod(NamespaceMapper.mapMethodName("returnStaticStringConstant"));
        Object ret = method.invoke(obj);
        Assert.assertEquals(StringConstantVisitorTestTarget.kStringConstant, ret.toString());
        
        // Get the constant directly from the static field.
        Object direct = this.clazz.getField(NamespaceMapper.mapFieldName("kStringConstant")).get(null);
        Assert.assertEquals(StringConstantVisitorTestTarget.kStringConstant, direct.toString());
        
        // They should also be the same instance.
        Assert.assertTrue(ret == direct);
    }

    @Test
    public void testLoadStringConstantNoStatic() throws Exception {
        Object obj = this.clazzNoStatic.getConstructor().newInstance();
        
        // Get the constant via the method.
        Method method = this.clazzNoStatic.getMethod(NamespaceMapper.mapMethodName("returnStaticStringConstant"));
        Object ret = method.invoke(obj);
        Assert.assertEquals(StringConstantVisitorTestTarget.kStringConstant, ret.toString());
        
        // Get the constant directly from the static field.
        Object direct = this.clazzNoStatic.getField(NamespaceMapper.mapFieldName("kStringConstant")).get(null);
        Assert.assertEquals(StringConstantVisitorTestTarget.kStringConstant, direct.toString());
        
        // They should also be the same instance.
        Assert.assertTrue(ret == direct);
    }
}
