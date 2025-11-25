package test;

import benchmark.internal.Benchmark;
import benchmark.objects.A;

public class PrimitiveWrapperTest {
    public static void main(String[] args) {
        // Primitive wrapper boxing and unboxing
        
        // Autoboxing: primitive to wrapper
        Integer i1 = 42;          // new Integer(42) or Integer.valueOf(42)
        Integer i2 = 42;          // May reuse cached instance
        Boolean b1 = true;        // Boolean.TRUE
        Boolean b2 = false;       // Boolean.FALSE
        
        // Integer cache for small values (-128 to 127)
        Integer small1 = 1;
        Integer small2 = 1;       // Same cached instance
        Integer large1 = 1000;
        Integer large2 = 1000;    // Different instances
        
        // Explicit boxing
        Integer i3 = Integer.valueOf(42);
        Integer i4 = new Integer(42);  // Always new instance (deprecated)
        
        // Unboxing: wrapper to primitive
        int val1 = i1;            // i1.intValue()
        int val2 = i2;            // i2.intValue()
        
        // For pointer analysis testing, track objects
        Object obj1 = i1;
        Object obj2 = i2;
        Object obj3 = small1;
        Object obj4 = small2;
        Object obj5 = large1;
        Object obj6 = large2;
        
        Benchmark.test(1, obj1);  // Integer object for 42
        Benchmark.test(2, obj2);  // May be same as obj1 (cached)
        Benchmark.test(3, obj3);  // Integer object for 1 (cached)
        Benchmark.test(4, obj4);  // Same as obj3 (cached)
        Benchmark.test(5, obj5);  // Integer object for 1000
        Benchmark.test(6, obj6);  // Different from obj5
        
        // String conversion
        String s1 = i1.toString();
        String s2 = String.valueOf(val1);
        
        Object objS1 = s1;
        Object objS2 = s2;
        Benchmark.test(7, objS1);  // String "42"
        Benchmark.test(8, objS2);  // String "42" (may be different object)
    }
}
/*
This test checks how the pointer analysis handles:
1. Autoboxing/unboxing operations
2. Integer caching behavior
3. Wrapper object creation and reuse
4. String conversion from primitives

Results depend on how the analysis models:
- Integer.valueOf() caching
- Autoboxing implementation
- String interning
*/