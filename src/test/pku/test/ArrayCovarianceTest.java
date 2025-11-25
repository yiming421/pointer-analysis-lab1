package test;

import benchmark.internal.Benchmark;

public class ArrayCovarianceTest {
    
    static class Animal { }
    static class Dog extends Animal { }
    static class Cat extends Animal { }
    
    public static void main(String[] args) {
        // Java allows this due to array covariance
        Animal[] animals = new Dog[3];  // Dog[] is subtype of Animal[]
        
        try {
            Benchmark.alloc(1);
            animals[0] = new Dog();  // OK at runtime
            
            Benchmark.alloc(2); 
            animals[1] = new Cat();  // ArrayStoreException at runtime!
        } catch (ArrayStoreException e) {
            // Exception caught, animals[1] remains null
        }
        
        Benchmark.alloc(3);
        Animal[] realAnimals = new Animal[3];
        realAnimals[0] = new Dog();
        realAnimals[1] = new Cat();  // OK now
        
        // What's in each array?
        Benchmark.test(1, animals[0]);    // Dog object
        Benchmark.test(2, animals[1]);    // null (exception prevented assignment)
        Benchmark.test(3, realAnimals[0]); // Dog object  
        Benchmark.test(4, realAnimals[1]); // Cat object
    }
}
/*
Answer:
  1 : 1
  2 : 
  3 : 3
  4 : 3
*/