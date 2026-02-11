package io.github.nbauma109.decompiler.util;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class ReflectionUtilsTest {

    private TestSubClass subObj;
    private TestSuperClass superObj;

    // Helper classes to test inheritance and access levels
    class TestSuperClass {
        private String superField = "initialSuper";
        private String superMethod(String input) { return "super:" + input; }
    }

    class TestSubClass extends TestSuperClass {
        private String subField = "initialSub";
        private void noArgMethod() { this.subField = "noArgCalled"; }
        private String singleArgMethod(Integer val) { return "val:" + val; }
    }

    @Before
    public void setUp() {
        subObj = new TestSubClass();
        superObj = new TestSuperClass();
    }

    /**
     * Tests the private constructor for 100% coverage.
     */
    @Test
    public void testConstructorIsPrivate() throws Exception {
        Constructor<ReflectionUtils> constructor = ReflectionUtils.class.getDeclaredConstructor();
        assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        constructor.newInstance();
    }

    // --- FIELD TESTS ---

    @Test
    public void testSetAndGetFieldValue() {
        // Test SubClass field
        ReflectionUtils.setFieldValue(subObj, "subField", "changedSub");
        assertEquals("changedSub", ReflectionUtils.getFieldValue(subObj, "subField"));

        // Test SuperClass field (traversal)
        ReflectionUtils.setFieldValue(subObj, "superField", "changedSuper");
        assertEquals("changedSuper", ReflectionUtils.getFieldValue(subObj, "superField"));
    }

    @Test
    public void testFieldEdgeCases() {
        assertNull(ReflectionUtils.getFieldValue(null, "any"));
        assertNull(ReflectionUtils.getFieldValue(subObj, null));
        assertNull(ReflectionUtils.getFieldValue(subObj, "nonExistentField"));
        
        // Ensure setFieldValue handles nulls/missing fields gracefully
        ReflectionUtils.setFieldValue(null, "field", "value");
        ReflectionUtils.setFieldValue(subObj, null, "value");
        ReflectionUtils.setFieldValue(subObj, "nonExistentField", "value");
    }

    // --- METHOD TESTS ---

    @Test
    public void testInvokeMethodNoArgs() {
        // Package-private method test
        ReflectionUtils.invokeMethod(subObj, "noArgMethod");
        assertEquals("noArgCalled", ReflectionUtils.getFieldValue(subObj, "subField"));
    }

    @Test
    public void testInvokeMethodSingleArg() {
        Object result = ReflectionUtils.invokeMethod(subObj, "singleArgMethod", Integer.class, 123);
        assertEquals("val:123", result);
    }

    @Test
    public void testInvokeMethodNullArg() {
        assertNull(ReflectionUtils.invokeMethod(null, "singleArgMethod", Integer.class, 123));
        assertNull(ReflectionUtils.invokeMethod(subObj, null, Integer.class, 123));
        assertNull(ReflectionUtils.invokeMethod(null, null, Integer.class, 123));
    }
    
    @Test
    public void testInvokeMethodMultiArgs() {
        Class<?>[] types = new Class<?>[]{ String.class };
        Object[] args = new Object[]{ "hello" };
        
        // Test inheritance traversal for methods
        Object result = ReflectionUtils.invokeMethod(subObj, "superMethod", types, args);
        assertEquals("super:hello", result);
    }

    @Test
    public void testInvokeMethodNullArgs() {
        Class<?>[] types = new Class<?>[]{ String.class };
        Object[] args = new Object[]{ "hello" };
        assertNull(ReflectionUtils.invokeMethod(null, "superMethod", types, args));
        assertNull(ReflectionUtils.invokeMethod(subObj, null, types, args));
        assertNull(ReflectionUtils.invokeMethod(null, null, types, args));
    }
    
    @Test
    public void testInvokeMethodDirect() throws Exception {
        Method m = subObj.getClass().getDeclaredMethod("singleArgMethod", Integer.class);
        Object result = ReflectionUtils.invokeMethod(m, subObj, new Object[]{ 456 });
        assertEquals("val:456", result);
    }

    @Test
    public void testMethodEdgeCases() {
        // Test null inputs
        assertNull(ReflectionUtils.invokeMethod(null, "method"));
        assertNull(ReflectionUtils.invokeMethod(subObj, null));
        assertNull(ReflectionUtils.getDeclaredMethod(subObj, "nonExistent", null));
        
        // Test invalid method call (wrong params)
        assertNull(ReflectionUtils.invokeMethod(subObj, "singleArgMethod", String.class, "wrongType"));
    }

    @Test
    public void testGetDeclaredMethodTraversal() {
        // Test that it stops at Object.class and doesn't crash
        Method m = ReflectionUtils.getDeclaredMethod(subObj, "toString", new Class[0]);
        // Note: The logic in ReflectionUtils excludes Object.class in the loop: 
        // clazz != Object.class. So toString() might return null depending on intent.
        assertNull("Should return null as loop stops before Object.class", m);
    }
    
    @Test
    public void testGetDeclaredMethodNullArg() {
        assertNull(ReflectionUtils.getDeclaredMethod(null, "toString", new Class[0]));
        assertNull(ReflectionUtils.getDeclaredMethod(subObj, null, new Class[0]));
        assertNull(ReflectionUtils.getDeclaredMethod(null, "null", new Class[0]));
    }
}