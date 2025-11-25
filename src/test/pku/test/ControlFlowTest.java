package test;

import benchmark.internal.Benchmark;
import benchmark.objects.A;

public class ControlFlowTest {
    public static void main(String[] args) {
        A result = null;
        
        // Switch statement with different cases
        int choice = args.length % 4;
        
        switch (choice) {
            case 0:
                Benchmark.alloc(1);
                result = new A();
                break;
            case 1:
                Benchmark.alloc(2);
                result = new A();
                break;
            case 2:
                Benchmark.alloc(3);
                result = new A();
                // Fall through (no break)
            case 3:
                Benchmark.alloc(4);
                A temp = new A();
                if (result == null) {
                    result = temp;
                }
                break;
            default:
                Benchmark.alloc(5);
                result = new A();
                break;
        }
        
        Benchmark.test(1, result); // Should point to {1, 2, 3, 4} depending on case
        
        // While loop with complex condition
        A accumulator = null;
        int count = 0;
        while (count < args.length && count < 3) {
            if (count == 0) {
                Benchmark.alloc(10);
                accumulator = new A();
            } else {
                Benchmark.alloc(11);
                accumulator = new A();
            }
            count++;
        }
        
        Benchmark.test(2, accumulator); // Depends on loop iterations
        
        // Do-while loop (executes at least once)
        A doResult = null;
        int i = 0;
        do {
            Benchmark.alloc(20);
            doResult = new A();
            i++;
        } while (i < args.length && i < 2);
        
        Benchmark.test(3, doResult); // Should point to {20}
        
        // Enhanced for loop (for-each)
        A[] array = new A[3];
        Benchmark.alloc(30);
        A a30 = new A();
        Benchmark.alloc(31);
        A a31 = new A();
        array[0] = a30;
        array[1] = a31;
        
        A forEachResult = null;
        for (A element : array) {
            if (element != null) {
                forEachResult = element; // Last non-null element
            }
        }
        
        Benchmark.test(4, forEachResult); // Should point to {30, 31}
    }
}
/*
Expected behavior depends on analysis precision:
  1 : 1 2 3 4 (all switch cases reachable with unknown input)
  2 : 10 11 (depends on loop execution)
  3 : 20 (do-while executes at least once)
  4 : 30 31 (for-each over array elements)
*/