package test;

import benchmark.internal.Benchmark;
import benchmark.objects.A;


interface Shape {
    A getObject();
}

class Circle implements Shape {
    public A getObject() {
        Benchmark.alloc(100);
        return new A();
    }
}

class Rectangle implements Shape {
    public A getObject() {
        Benchmark.alloc(200);
        return new A();
    }
}

public class InterfaceTest {
    public static void main(String[] args) {
        // Interface variable pointing to different implementations
        Shape shape1 = new Circle();
        Shape shape2 = new Rectangle();

        // Virtual dispatch through interface
        A a1 = shape1.getObject();
        Benchmark.test(1, a1);  // Should point to {100}

        A a2 = shape2.getObject();
        Benchmark.test(2, a2);  // Should point to {200}

        // Polymorphic call - unknown which implementation
        Shape unknown = args.length > 0 ? shape1 : shape2;
        A a3 = unknown.getObject();
        Benchmark.test(3, a3);  // Should point to {100, 200}
    }
}
/*
Expected:
  1 : 100
  2 : 200
  3 : 100 200
*/
