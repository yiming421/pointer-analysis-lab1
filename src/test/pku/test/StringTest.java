package test;

import benchmark.internal.Benchmark;
import benchmark.objects.A;

public class StringTest {
    public static void main(String[] args) {
        // String literals (interned)
        String s1 = "hello";
        String s2 = "hello";
        String s3 = "world";

        // String concatenation creates new objects
        String s4 = s1 + s3;
        String s5 = "hello" + "world";

        // String.valueOf() calls
        int x = 42;
        String s6 = String.valueOf(x);

        // StringBuilder usage
        StringBuilder sb = new StringBuilder();
        sb.append(s1);
        sb.append(s3);
        String s7 = sb.toString();

        // These tests focus on object identity in pointer analysis
        // String comparison by reference (should be false unless interned)
        boolean same = (s1 == s2); // true for literals (interned)
        boolean different = (s4 == s5); // may be false (different objects)

        // For pointer analysis testing, we need to track objects
        // Use Object type to avoid string optimization
        Object obj1 = s1;
        Object obj2 = s2;
        Object obj3 = s4;Give

        Benchmark.test(1, obj1); // String literal object
        Benchmark.test(2, obj2); // Same string literal (should be same object)
        Benchmark.test(3, obj3); // Concatenated string (new object)
    }
}
/*
Note: This test focuses on string object identity for pointer analysis.
Results may vary based on JVM string optimization and interning behavior.
Expected behavior depends on how the analysis models string constants.
*/
