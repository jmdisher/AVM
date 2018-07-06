package org.aion.avm.core.shadowing.misc;

import java.lang.reflect.Method;

import org.aion.avm.core.SimpleAvm;
import org.aion.avm.core.miscvisitors.UserClassMappingVisitor;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;


/**
 * Miscellaneous tests for our shadow implementation.
 */
public class MiscellaneousShadowTest {
    private Class<?> clazz;

    @Before
    public void setupClass() throws Exception {
        SimpleAvm avm = new SimpleAvm(1_000_000L, TestResource.class);
        this.clazz = avm.getClassLoader().loadUserClassByOriginalName(TestResource.class.getName());
    }

    /**
     * Checks that the java.lang.Class.cast() method works as expected on our shadow instance.
     */
    @Test
    public void testClassCast() throws Exception {
        Object string = this.clazz.getMethod(UserClassMappingVisitor.mapMethodName("returnString")).invoke(null);
        Object object = this.clazz.getMethod(UserClassMappingVisitor.mapMethodName("returnObject")).invoke(null);
        Object stringClass = this.clazz.getMethod(UserClassMappingVisitor.mapMethodName("returnClass")).invoke(null);
        Method cast = this.clazz.getMethod(UserClassMappingVisitor.mapMethodName("cast"), org.aion.avm.shadow.java.lang.Class.class ,org.aion.avm.internal.IObject.class);
        boolean didCastString = (Boolean)cast.invoke(null, stringClass, string);
        Assert.assertTrue(didCastString);
        boolean didCastObject = (Boolean)cast.invoke(null, stringClass, object);
        Assert.assertFalse(didCastObject);
    }
}