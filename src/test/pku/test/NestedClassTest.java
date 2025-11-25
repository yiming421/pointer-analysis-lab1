package test;

import benchmark.internal.Benchmark;
import benchmark.objects.A;

class OuterClass {
    A outerField;
    
    class InnerClass {
        A innerField;
        
        public A getOuter() {
            return outerField; // Access outer class field
        }
        
        public A getInner() {
            return innerField;
        }
    }
    
    static class StaticNestedClass {
        A staticNestedField;
        
        public A getField() {
            return staticNestedField;
        }
    }
}

public class NestedClassTest {
    public static void main(String[] args) {
        OuterClass outer = new OuterClass();
        
        Benchmark.alloc(1);
        A a1 = new A();
        outer.outerField = a1;
        
        // Inner class accessing outer field
        OuterClass.InnerClass inner = outer.new InnerClass();
        Benchmark.alloc(2);
        A a2 = new A();
        inner.innerField = a2;
        
        A result1 = inner.getOuter();
        Benchmark.test(1, result1); // Should point to {1} (outer field)
        
        A result2 = inner.getInner();
        Benchmark.test(2, result2); // Should point to {2} (inner field)
        
        // Static nested class
        OuterClass.StaticNestedClass nested = new OuterClass.StaticNestedClass();
        Benchmark.alloc(3);
        A a3 = new A();
        nested.staticNestedField = a3;
        
        A result3 = nested.getField();
        Benchmark.test(3, result3); // Should point to {3}
        
        // Anonymous inner class
        Runnable anonymousRunnable = new Runnable() {
            A anonymousField = outer.outerField; // Capture outer field
            
            public void run() {
                // Empty
            }
            
            public A getField() {
                return anonymousField;
            }
        };
        
        // Note: Testing anonymous class would require reflection or method calls
        // This is mainly to test that the analysis handles nested class definitions
    }
}
/*
Expected:
  1 : 1
  2 : 2
  3 : 3
*/