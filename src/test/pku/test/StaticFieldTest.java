package test;

import benchmark.internal.Benchmark;
import benchmark.objects.A;
import benchmark.objects.B;

class ClassA {
    static A staticField;
}

class ClassB {
    static A staticField;  // Same field name, different class
}

public class StaticFieldTest {
    public static void main(String[] args) {
        Benchmark.alloc(1);
        A a1 = new A();

        Benchmark.alloc(2);
        A a2 = new A();

        // Store to different classes' static fields
        ClassA.staticField = a1;
        ClassB.staticField = a2;

        // Load from ClassA's static field
        A a3 = ClassA.staticField;
        Benchmark.test(1, a3);  // Should point to {1} only

        // Load from ClassB's static field
        A a4 = ClassB.staticField;
        Benchmark.test(2, a4);  // Should point to {2} only
    }
}
/*
Expected (with correct per-class static field handling):
  1 : 1
  2 : 2

If static fields collide (buggy):
  1 : 1 2
  2 : 1 2
*/
