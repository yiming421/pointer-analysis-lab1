package test;

import benchmark.internal.Benchmark;
import benchmark.objects.A;
import benchmark.objects.B;

class MyClass {
    A field1;
    A field2;
    
    public MyClass() {
        // Default constructor
        Benchmark.alloc(1);
        this.field1 = new A();
    }
    
    public MyClass(A param) {
        // Parameterized constructor
        this.field1 = param;
        Benchmark.alloc(2);
        this.field2 = new A();
    }
    
    public MyClass(boolean flag) {
        // Constructor with branching
        if (flag) {
            Benchmark.alloc(3);
            this.field1 = new A();
        } else {
            Benchmark.alloc(4);
            this.field1 = new A();
        }
    }
}

public class ConstructorTest {
    public static void main(String[] args) {
        // Default constructor
        MyClass obj1 = new MyClass();
        Benchmark.test(1, obj1.field1); // Should point to {1}
        
        // Parameterized constructor
        Benchmark.alloc(5);
        A param = new A();
        MyClass obj2 = new MyClass(param);
        Benchmark.test(2, obj2.field1); // Should point to {5}
        Benchmark.test(3, obj2.field2); // Should point to {2}
        
        // Constructor with branching - true branch
        MyClass obj3 = new MyClass(true);
        Benchmark.test(4, obj3.field1); // Should point to {3}
        
        // Constructor with branching - false branch
        MyClass obj4 = new MyClass(false);
        Benchmark.test(5, obj4.field1); // Should point to {4}
        
        // Constructor with branching - unknown branch
        MyClass obj5 = new MyClass(args.length > 0);
        Benchmark.test(6, obj5.field1); // Should point to {3, 4}
    }
}
/*
Expected:
  1 : 1
  2 : 5
  3 : 2
  4 : 3
  5 : 4
  6 : 3 4
*/