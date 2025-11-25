package test;

import benchmark.internal.Benchmark;
import benchmark.objects.A;
import benchmark.objects.B;

class Parent {
    public A field;
    
    public A getField() {
        return field;
    }
    
    public A virtualMethod() {
        Benchmark.alloc(10);
        return new A();
    }
}

class Child extends Parent {
    @Override
    public A virtualMethod() {
        Benchmark.alloc(20);
        return new A();
    }
}

public class InheritanceTest {
    public static void main(String[] args) {
        Benchmark.alloc(1);
        A a1 = new A();
        
        // Parent object
        Parent parent = new Parent();
        parent.field = a1;
        
        // Child object
        Child child = new Child();
        Benchmark.alloc(2);
        A a2 = new A();
        child.field = a2;
        
        // Polymorphic call - static type Parent, runtime type Child
        Parent poly = new Child();
        A a3 = poly.virtualMethod();  // Should call Child.virtualMethod()
        Benchmark.test(1, a3);  // Should point to {20}
        
        // Direct parent call
        A a4 = parent.virtualMethod();
        Benchmark.test(2, a4);  // Should point to {10}
        
        // Field access through inheritance
        A a5 = child.field;
        Benchmark.test(3, a5);  // Should point to {2}
        
        A a6 = parent.field;
        Benchmark.test(4, a6);  // Should point to {1}
    }
}
/*
Expected:
  1 : 20
  2 : 10
  3 : 2
  4 : 1
*/