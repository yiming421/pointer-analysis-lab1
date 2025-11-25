package test;

import benchmark.internal.Benchmark;
import benchmark.objects.A;

public class ArrayTest {
    public static void main(String[] args) {
        // Create array
        A[] arr = new A[10];

        // Create objects
        Benchmark.alloc(1);
        A a1 = new A();
        Benchmark.alloc(2);
        A a2 = new A();

        // Store to array with constant indices
        arr[0] = a1;
        arr[5] = a2;

        // Load from array with constant indices (precise!)
        A a3 = arr[0];
        Benchmark.test(1, a3);  // Should point to {1} with index-sensitive analysis

        A a4 = arr[5];
        Benchmark.test(2, a4);  // Should point to {2} with index-sensitive analysis

        // Load with variable index (must be sound - include all)
        int idx = args.length;
        A a5 = arr[idx];
        Benchmark.test(3, a5);  // Should point to {1, 2} - unknown index
    }
}
/*
Expected Answer with index-sensitive analysis:
  1 : 1
  2 : 2
  3 : 1 2

Reason: arr[0] and arr[5] are tracked separately (constant indices),
but arr[idx] with unknown index must include all elements.
*/
