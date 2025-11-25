package test;

import benchmark.internal.Benchmark;
import benchmark.objects.A;
import benchmark.objects.B;

public class MultiDimArrayTest {
    public static void main(String[] args) {
        // 2D array
        A[][] arr2d = new A[3][3];
        
        // Create objects
        Benchmark.alloc(1);
        A a1 = new A();
        Benchmark.alloc(2);
        A a2 = new A();
        Benchmark.alloc(3);
        A a3 = new A();
        
        // Store to 2D array with constant indices
        arr2d[0][0] = a1;
        arr2d[1][1] = a2;
        arr2d[2][2] = a3;
        
        // Load from 2D array with constant indices
        A result1 = arr2d[0][0];
        Benchmark.test(1, result1); // Should point to {1}
        
        A result2 = arr2d[1][1];
        Benchmark.test(2, result2); // Should point to {2}
        
        // Load with one variable index
        int i = args.length % 3;
        A result3 = arr2d[i][i]; // Diagonal access with variable index
        Benchmark.test(3, result3); // Should point to {1, 2, 3}
        
        // Array of arrays - different from 2D array
        A[] inner1 = new A[2];
        A[] inner2 = new A[2];
        A[][] jagged = {inner1, inner2};
        
        Benchmark.alloc(4);
        A a4 = new A();
        Benchmark.alloc(5);
        A a5 = new A();
        
        inner1[0] = a4;
        inner2[1] = a5;
        
        A result4 = jagged[0][0];
        Benchmark.test(4, result4); // Should point to {4}
        
        A result5 = jagged[1][1];
        Benchmark.test(5, result5); // Should point to {5}
    }
}
/*
Expected:
  1 : 1
  2 : 2
  3 : 1 2 3
  4 : 4
  5 : 5
*/