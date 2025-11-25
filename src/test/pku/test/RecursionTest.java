package test;

import benchmark.internal.Benchmark;
import benchmark.objects.A;

public class RecursionTest {
    
    static A globalVar;
    
    public static A fibonacci(int n) {
        if (n <= 1) {
            Benchmark.alloc(1);
            return new A();
        } else {
            A left = fibonacci(n - 1);
            A right = fibonacci(n - 2);
            // Recursive calls should merge results
            return left; // Return one of them
        }
    }
    
    public static void recursive(int depth, A accumulator) {
        if (depth <= 0) {
            globalVar = accumulator;
            return;
        }
        
        Benchmark.alloc(depth);
        A newObj = new A();
        recursive(depth - 1, newObj);
    }
    
    public static void main(String[] args) {
        // Test recursive function with merge
        A result1 = fibonacci(3);
        Benchmark.test(1, result1); // Should point to {1} (all recursive calls create same allocation)
        
        // Test recursive procedure with different allocations
        Benchmark.alloc(10);
        A initial = new A();
        recursive(3, initial);
        Benchmark.test(2, globalVar); // Should point to {1} (deepest recursive call)
        
        // Test with unknown recursion depth
        int depth = args.length;
        recursive(depth, initial);
        Benchmark.test(3, globalVar); // Should include multiple possible allocations
    }
}
/*
Expected:
  1 : 1
  2 : 1
  3 : (depends on analysis precision for dynamic recursion depth)
*/