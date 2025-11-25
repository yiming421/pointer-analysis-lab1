package test;

import benchmark.internal.Benchmark;
import benchmark.objects.A;

public class NativeMethodTest {
    public static void main(String[] args) {
        Benchmark.alloc(1);
        A a1 = new A();

        Benchmark.alloc(2);
        A a2 = new A();

        // Call native methods that don't affect pointer analysis
        // (they return primitives or don't return objects we care about)

        // hashCode() may be native - returns int (primitive)
        int hash1 = a1.hashCode();

        // System.identityHashCode() is definitely native - returns int
        int hash2 = System.identityHashCode(a2);

        // Object.getClass() may call native code - but returns Class object
        // we don't track Class objects in typical pointer analysis
        Class<?> clazz = a1.getClass();

        // After calling native methods, verify pointer analysis still works
        A a3 = a1;
        Benchmark.test(1, a3);  // Should point to {1}

        A a4 = a2;
        Benchmark.test(2, a4);  // Should point to {2}

        // Test assignment after native calls
        A a5 = a1;
        A a6 = a5;
        Benchmark.test(3, a6);  // Should point to {1}
    }
}
/*
Expected:
  1 : 1
  2 : 2
  3 : 1

Purpose:
1. Verify analysis doesn't crash when native methods are called
2. Verify pointer analysis results are correct even when native methods
   are interspersed in the code
3. The native methods here (hashCode, identityHashCode, getClass) don't
   return tracked objects, so they don't affect points-to results

Before native method fix:
- Would crash with NullPointerException when trying to get IR of native methods
- Analysis would fail completely

After native method fix:
- Native methods are detected with isNative() check
- getIR() results are null-checked
- Native methods are safely skipped
- Analysis completes successfully with correct results
*/
