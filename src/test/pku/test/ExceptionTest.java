package test;

import benchmark.internal.Benchmark;
import benchmark.objects.A;
import benchmark.objects.B;

public class ExceptionTest {
    
    public static A createA(boolean shouldThrow) throws RuntimeException {
        if (shouldThrow) {
            Benchmark.alloc(999);
            A error = new A();
            throw new RuntimeException();
        } else {
            Benchmark.alloc(1);
            return new A();
        }
    }
    
    public static void main(String[] args) {
        Benchmark.alloc(2);
        A a2 = new A();
        
        A result = a2; // Default value
        
        try {
            result = createA(false); // Should return normally
        } catch (RuntimeException e) {
            Benchmark.alloc(3);
            result = new A(); // Exception handler
        }
        
        Benchmark.test(1, result); // Should point to {1} (normal return)
        
        // Test exception path
        A result2 = a2; // Default value
        try {
            result2 = createA(true); // Should throw exception
        } catch (RuntimeException e) {
            Benchmark.alloc(4);
            result2 = new A(); // Exception handler
        }
        
        Benchmark.test(2, result2); // Should point to {4} (exception handler)
        
        // Test both paths merged
        A result3 = a2; // Default value
        try {
            result3 = createA(args.length > 0); // May or may not throw
        } catch (RuntimeException e) {
            Benchmark.alloc(5);
            result3 = new A(); // Exception handler
        }
        
        Benchmark.test(3, result3); // Should point to {1, 5} (both paths possible)
    }
}
/*
Expected:
  1 : 1
  2 : 4
  3 : 1 5
*/