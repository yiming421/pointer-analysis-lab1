package test;

import benchmark.internal.Benchmark;
import benchmark.objects.A;
import benchmark.objects.B;
import java.util.*;

public class CollectionsTest {
    public static void main(String[] args) {
        // ArrayList operations
        List<A> list = new ArrayList<A>();
        
        Benchmark.alloc(1);
        A a1 = new A();
        Benchmark.alloc(2);
        A a2 = new A();
        
        list.add(a1);
        list.add(a2);
        
        // Get element by index
        A listResult1 = list.get(0);
        Benchmark.test(1, listResult1); // Should point to {1}
        
        A listResult2 = list.get(1);
        Benchmark.test(2, listResult2); // Should point to {2}
        
        // Iterator pattern
        A iterResult = null;
        for (A element : list) {
            iterResult = element; // Last element
        }
        Benchmark.test(3, iterResult); // Should point to {2}
        
        // HashMap operations
        Map<String, A> map = new HashMap<String, A>();
        
        Benchmark.alloc(3);
        A a3 = new A();
        Benchmark.alloc(4);
        A a4 = new A();
        
        map.put("key1", a3);
        map.put("key2", a4);
        
        A mapResult1 = map.get("key1");
        Benchmark.test(4, mapResult1); // Should point to {3}
        
        A mapResult2 = map.get("key2");
        Benchmark.test(5, mapResult2); // Should point to {4}
        
        // Unknown key lookup
        String unknownKey = args.length > 0 ? "key1" : "key2";
        A mapResult3 = map.get(unknownKey);
        Benchmark.test(6, mapResult3); // Should point to {3, 4}
        
        // HashSet operations
        Set<A> set = new HashSet<A>();
        set.add(a1);
        set.add(a2);
        
        A setResult = null;
        Iterator<A> iter = set.iterator();
        while (iter.hasNext()) {
            setResult = iter.next(); // Some element from set
        }
        Benchmark.test(7, setResult); // Should point to {1, 2} (set order is undefined)
        
        // Generic array (Object[] backing)
        A[] array = list.toArray(new A[0]);
        A arrayResult = array[0];
        Benchmark.test(8, arrayResult); // Should point to {1}
        
        // Collections utility methods
        Collections.shuffle(list);
        A shuffleResult = list.get(0); // First element after shuffle
        Benchmark.test(9, shuffleResult); // Should point to {1, 2} (unknown order)
    }
}
/*
Expected:
  1 : 1
  2 : 2
  3 : 2
  4 : 3
  5 : 4
  6 : 3 4
  7 : 1 2
  8 : 1
  9 : 1 2
  
This test checks:
1. ArrayList indexing and iteration
2. HashMap key-value operations
3. HashSet iteration (unordered)
4. Generic type erasure handling
5. Collections framework methods
6. Iterator pattern usage
*/