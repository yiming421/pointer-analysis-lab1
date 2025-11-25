package test;

import benchmark.internal.Benchmark;
import benchmark.objects.A;
import benchmark.objects.B;

public class CastTest {
    public static void main(String[] args) {
        Benchmark.alloc(1);
        A a1 = new A();

        Benchmark.alloc(2);
        B b1 = new B();

        // Upcast (B to Object)
        Object obj = (Object) b1;

        // Downcast (Object to B)
        B b2 = (B) obj;
        Benchmark.test(1, b2);  // Should point to {2}

        // Another cast
        Object obj2 = (Object) a1;
        A a2 = (A) obj2;
        Benchmark.test(2, a2);  // Should point to {1}
    }
}
/*
Expected:
  1 : 2
  2 : 1
*/
