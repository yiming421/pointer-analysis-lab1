`# Pointer Analysis - Data Structures & Algorithm Design

**Date**: November 16, 2024
**Team**: PKU Software Analysis Lab1
**Goal**: Implement sound pointer analysis for Java programs

---

## Table of Contents

1. [Overview](#overview)
2. [Key Concepts](#key-concepts)
3. [Core Data Structures](#core-data-structures)
4. [Algorithm Specification](#algorithm-specification)
5. [Implementation Plan](#implementation-plan)
6. [API Reference](#api-reference)

---

## Overview

### What We're Building

A **flow-insensitive, context-insensitive pointer analysis** that:
- Tracks which objects each variable can point to
- Handles: variables, fields, method calls, control flow
- Must be **sound** (no false negatives - never miss a possible points-to relation)
- Should be **precise** (minimize false positives when possible)

### Input/Output Format

**Input**: Java program with marked allocation/test points
```java
Benchmark.alloc(1);
A a = new A();        // This 'new' is marked as object 1
Benchmark.test(7, a); // Query: what can variable 'a' point to?
```

**Output**: Points-to sets for each test point
```
7 : 1
```
Meaning: At test point 7, variable `a` can point to object 1.

---

## Key Concepts

### 1. Objects vs Variables

- **Object** = A memory allocation site (`new A()`)
  - Identified by integer ID (from `Benchmark.alloc(id)`)
  - Multiple variables can point to the same object

- **Variable** = A program variable (local var, parameter, field)
  - Has a **points-to set**: set of object IDs it may point to
  - Represented by Tai-e's `Var` class

### 2. Flow-Insensitive vs Flow-Sensitive

**Flow-insensitive** (what we implement):
```java
A a = new A();  // obj 1
A b = new A();  // obj 2
a = b;
// Flow-insensitive: a points to {1, 2} (union of all assignments)
// Flow-sensitive: a points to {2} (only the last assignment)
```
We use flow-insensitive = easier, conservative (sound).

### 3. Field Sensitivity

Fields create **aliasing**:
```java
A a1 = new A();  // obj O1
A a2 = a1;       // a1 and a2 are ALIASES (point to same object)
B b1 = new B();  // obj O2
a1.f = b1;       // Field f of object O1 points to O2
B b2 = a2.f;     // Since a2 -> O1, and O1.f -> O2, then b2 -> O2
```

We need to track: **for each object's field, what can it point to?**

### 4. Soundness

**Sound** = Never say "NO" when the answer should be "YES"
- If variable `x` CAN point to object `o`, we MUST include `o` in pts(x)
- Over-approximation is OK (extra objects), but missing is NOT

**Examples**:
```java
A a = new A();  // obj 1
if (condition) {
    a = new A(); // obj 2
}
// Sound: pts(a) = {1, 2}  ✅
// Unsound: pts(a) = {1}   ❌ (missed obj 2)
```

---

## Core Data Structures

### 1. Points-To Sets for Variables

**Purpose**: Track which objects each variable can point to

**Type**: `Map<Var, Set<Integer>>`

```java
// Example state:
// Variable 'a' can point to objects {1, 2}
// Variable 'b' can point to objects {3}
Map<Var, Set<Integer>> varPointsTo = new HashMap<>();
```

**Operations**:
```java
// Initialize points-to set for a variable
Set<Integer> getPts(Var v) {
    return varPointsTo.computeIfAbsent(v, k -> new HashSet<>());
}

// Add an object to variable's points-to set
// Returns true if set changed (for worklist algorithm)
boolean addPointsTo(Var v, int objId) {
    return getPts(v).add(objId);
}

// Add multiple objects (for copy: a = b)
boolean addAllPointsTo(Var dest, Set<Integer> srcObjs) {
    return getPts(dest).addAll(srcObjs);
}
```

---

### 2. Points-To Sets for Object Fields

**Purpose**: Track what each object's field can point to

**Type**: `Map<ObjectField, Set<Integer>>`

We need a composite key: (objectId, fieldName)

```java
// Represents: object O's field F
class ObjectField {
    private final int objectId;
    private final JField field;

    public ObjectField(int objectId, JField field) {
        this.objectId = objectId;
        this.field = field;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ObjectField)) return false;
        ObjectField that = (ObjectField) o;
        return objectId == that.objectId &&
               Objects.equals(field, that.field);
    }

    @Override
    public int hashCode() {
        return Objects.hash(objectId, field);
    }
}

// The actual data structure
Map<ObjectField, Set<Integer>> fieldPointsTo = new HashMap<>();
```

**Operations**:
```java
// Get points-to set for an object's field
Set<Integer> getFieldPts(int objId, JField field) {
    ObjectField of = new ObjectField(objId, field);
    return fieldPointsTo.computeIfAbsent(of, k -> new HashSet<>());
}

// Add to field points-to set
boolean addFieldPointsTo(int objId, JField field, int targetObjId) {
    return getFieldPts(objId, field).add(targetObjId);
}
```

**Example**:
```java
// A a = new A();  // obj 1
// a.f = new B();  // obj 2
//
// State:
// varPointsTo: {a -> {1}}
// fieldPointsTo: {(1, f) -> {2}}
```

---

### 3. Worklist for Fixed-Point Iteration

**Purpose**: Track statements that need reprocessing when points-to sets change

**Why needed**: Pointer analysis is iterative
```java
// Initially: pts(a) = {}, pts(b) = {}
b = a;        // stmt1: Nothing to propagate yet
a = new A();  // stmt2: pts(a) = {1}
// Now stmt1 needs to reprocess because pts(a) changed!
// After reprocessing: pts(b) = {1}
```

**Type**: `Queue<Stmt>` or `Set<Stmt>`

```java
// Option 1: Simple queue (may have duplicates)
Queue<Stmt> worklist = new LinkedList<>();

// Option 2: Set-based (no duplicates, more efficient)
Set<Stmt> worklist = new LinkedHashSet<>();

// Add statement to worklist
void addToWorklist(Stmt stmt) {
    worklist.add(stmt);
}

// Process until fixed point
while (!worklist.isEmpty()) {
    Stmt stmt = worklist.poll(); // or iterator.next() + remove
    boolean changed = processStmt(stmt);
    if (changed) {
        // Add dependent statements
        addDependents(stmt);
    }
}
```

---

### 4. Statement Dependencies (Optional Optimization)

**Purpose**: Know which statements to re-analyze when points-to sets change

**Type**: `Map<Var, Set<Stmt>>`

```java
// Which statements use each variable?
// var 'a' is used in: {stmt1, stmt3, stmt7}
Map<Var, Set<Stmt>> varUseStmts = new HashMap<>();

// When pts(a) changes, re-analyze all statements that use 'a'
void propagateChange(Var v) {
    Set<Stmt> users = varUseStmts.get(v);
    if (users != null) {
        worklist.addAll(users);
    }
}
```

**For first implementation**: Skip this optimization, just re-analyze all statements each iteration.

---

### 5. Preprocessing Results (Already Provided)

**Type**: `PreprocessResult` (already implemented)

```java
public class PreprocessResult {
    // Maps: New statement -> object ID
    public final Map<New, Integer> obj_ids;

    // Maps: test ID -> variable to query
    public final Map<Integer, Var> test_pts;
}
```

**Usage**:
```java
PreprocessResult preprocess = new PreprocessResult();
preprocess.analysis(method.getIR()); // Extract alloc/test markers

// Get object ID for a 'new' statement
int objId = preprocess.obj_ids.get(newStmt);

// Get variable for test point
Var queryVar = preprocess.test_pts.get(testId);
```

---

## Algorithm Specification

### High-Level Algorithm

```
Input: Program P with marked allocation and test points
Output: For each test point, set of objects it can point to

1. PREPROCESS: Extract all allocation IDs and test points
2. INITIALIZE: Create empty points-to sets for all variables
3. PROCESS ALLOCATIONS: For each "new" statement, add object to variable
4. ITERATE TO FIXED POINT:
   - Process each statement (assign, field store/load, method call)
   - Update points-to sets
   - Repeat until no changes
5. EXTRACT RESULTS: For each test point, get its variable's points-to set
```

---

### Statement Processing Rules

#### Rule 1: New Statement
```
Statement: var = new T();
Marked as: obj_id

Rule: pts(var) ∪= {obj_id}
```

**Implementation**:
```java
if (stmt instanceof New) {
    New newStmt = (New) stmt;
    Var lhs = newStmt.getLValue();
    Integer objId = preprocess.obj_ids.get(newStmt);
    if (objId != null) {
        changed |= addPointsTo(lhs, objId);
    }
}
```

---

#### Rule 2: Copy/Assignment
```
Statement: a = b;

Rule: pts(a) ∪= pts(b)
```

**Implementation**:
```java
if (stmt instanceof Copy) {
    Copy copy = (Copy) stmt;
    Var lhs = copy.getLValue();
    Var rhs = copy.getRValue();
    Set<Integer> rhsPts = getPts(rhs);
    changed |= addAllPointsTo(lhs, rhsPts);
}
```

---

#### Rule 3: Field Store (Store to field)
```
Statement: base.field = rhs;

Rule: For each object o in pts(base):
        pts(o.field) ∪= pts(rhs)
```

**Why**: If `base` can point to multiple objects, storing to `base.field` affects ALL those objects' fields.

**Implementation**:
```java
if (stmt instanceof StoreField) {
    StoreField store = (StoreField) stmt;
    Var base = store.getFieldAccess().getBase();
    JField field = store.getFieldRef().resolve();
    Var rhs = store.getRValue();

    Set<Integer> basePts = getPts(base);
    Set<Integer> rhsPts = getPts(rhs);

    for (int objId : basePts) {
        for (int rhsObj : rhsPts) {
            changed |= addFieldPointsTo(objId, field, rhsObj);
        }
    }
}
```

---

#### Rule 4: Field Load (Load from field)
```
Statement: lhs = base.field;

Rule: For each object o in pts(base):
        pts(lhs) ∪= pts(o.field)
```

**Implementation**:
```java
if (stmt instanceof LoadField) {
    LoadField load = (LoadField) stmt;
    Var lhs = load.getLValue();
    Var base = load.getFieldAccess().getBase();
    JField field = load.getFieldRef().resolve();

    Set<Integer> basePts = getPts(base);

    for (int objId : basePts) {
        Set<Integer> fieldPts = getFieldPts(objId, field);
        changed |= addAllPointsTo(lhs, fieldPts);
    }
}
```

---

#### Rule 5: Method Invocation (Simplified)
```
Statement: ret = obj.method(arg1, arg2, ...);

Rule (simplified, ignoring polymorphism for now):
  1. Find target method
  2. Pass arguments: pts(param_i) ∪= pts(arg_i)
  3. Pass receiver: pts(this) ∪= pts(obj)
  4. Analyze target method body
  5. Get return value: pts(ret) ∪= pts(return_var)
```

**This is COMPLEX - will be handled in Phase 3 (Nov 21-22)**

For now (Phase 1-2), we can:
- Skip method calls (only handle intraprocedural)
- Or conservatively: `pts(ret) = all objects` (very imprecise but sound)

---

### Fixed-Point Iteration

**Why needed**: Updates propagate through the program

**Example**:
```java
// Iteration 1:
a = new A();  // pts(a) = {1}
b = a;        // pts(b) = {1}
c = b;        // pts(c) = {} (not updated yet!)

// Iteration 2:
c = b;        // pts(c) = {1} ✓
```

**Algorithm**:
```java
// Initialize worklist with all statements
Queue<Stmt> worklist = new LinkedList<>(ir.getStmts());

// Iterate until no changes
while (!worklist.isEmpty()) {
    Stmt stmt = worklist.poll();
    boolean changed = processStatement(stmt);

    if (changed) {
        // Some points-to set changed, need to re-analyze
        // Simple approach: add all statements back
        worklist.addAll(ir.getStmts());

        // Better approach: only add dependent statements
        // (implement later for optimization)
    }
}
```

**Termination**: Guaranteed because:
- Points-to sets can only grow (never shrink)
- Finite number of objects
- Therefore, must reach fixed point

---

## Implementation Plan

### Phase 1: Basic Infrastructure (Person A, Nov 16-17)

**Create utility classes in `src/main/java/pku/`**:

File: `ObjectField.java`
```java
package pku;

import pascal.taie.language.classes.JField;
import java.util.Objects;

public class ObjectField {
    private final int objectId;
    private final JField field;

    public ObjectField(int objectId, JField field) {
        this.objectId = objectId;
        this.field = field;
    }

    // equals, hashCode, toString...
}
```

File: `PointsToSetManager.java`
```java
package pku;

import pascal.taie.ir.exp.Var;
import pascal.taie.language.classes.JField;
import java.util.*;

public class PointsToSetManager {
    private final Map<Var, Set<Integer>> varPts;
    private final Map<ObjectField, Set<Integer>> fieldPts;

    public PointsToSetManager() {
        this.varPts = new HashMap<>();
        this.fieldPts = new HashMap<>();
    }

    public Set<Integer> getPts(Var v) {
        return varPts.computeIfAbsent(v, k -> new HashSet<>());
    }

    public boolean addPointsTo(Var v, int objId) {
        return getPts(v).add(objId);
    }

    // More methods...
}
```

---

### Phase 2: Intraprocedural Analysis (Person B, Nov 17-19)

**In `PointerAnalysis.java`, implement**:

1. **New statement handler** (Nov 17)
2. **Copy statement handler** (Nov 18)
3. **Basic iteration** (Nov 18)

Test: Should pass Hello.java, Branch.java

---

### Phase 3: Field Analysis (Person C, Nov 18-19)

**In `PointerAnalysis.java`, add**:

1. **StoreField handler** (Nov 18)
2. **LoadField handler** (Nov 18)
3. **Integration with Person B's code** (Nov 19)

Test: Should pass Field1.java, Field2.java

---

### Phase 4: Fixed-Point & Loops (Person A, Nov 19-20)

**Implement worklist algorithm**:

1. **Worklist data structure** (Nov 19)
2. **Iteration logic** (Nov 19)
3. **Convergence testing** (Nov 20)

Test: Should pass Loop.java

---

### Phase 5: Interprocedural (All Three, Nov 21-22)

**Complex - need team collaboration**:

1. **Call graph construction** (Nov 21)
2. **Parameter passing** (Nov 21)
3. **Return values** (Nov 22)
4. **Polymorphism** (Nov 22)

Test: Should pass Invocation.java

---

## API Reference

### Tai-e IR Classes We Use

#### Var (Variable)
```java
pascal.taie.ir.exp.Var

// Get variable name
String name = var.getName();

// Compare variables
boolean same = var1.equals(var2);
```

#### Stmt (Statement) - Base interface
```java
pascal.taie.ir.stmt.Stmt

// Get statement index
int idx = stmt.getIndex();
```

#### New (Allocation statement)
```java
pascal.taie.ir.stmt.New extends Stmt

// Get variable being assigned
Var lhs = newStmt.getLValue();

// Get type being allocated
Type type = newStmt.getRValue().getType();
```

#### Copy (Assignment: a = b)
```java
pascal.taie.ir.stmt.Copy extends Stmt

Var lhs = copy.getLValue();  // a
Var rhs = copy.getRValue();  // b
```

#### StoreField (a.f = b)
```java
pascal.taie.ir.stmt.StoreField extends Stmt

FieldAccess fa = store.getFieldAccess();
Var base = fa.getBase();  // a
JField field = store.getFieldRef().resolve();  // f
Var rhs = store.getRValue();  // b
```

#### LoadField (a = b.f)
```java
pascal.taie.ir.stmt.LoadField extends Stmt

Var lhs = load.getLValue();  // a
FieldAccess fa = load.getFieldAccess();
Var base = fa.getBase();  // b
JField field = load.getFieldRef().resolve();  // f
```

#### Invoke (Method call)
```java
pascal.taie.ir.stmt.Invoke extends Stmt

InvokeExp callExp = invoke.getInvokeExp();
Var lhs = invoke.getLValue();  // Return variable (can be null)

// Get arguments
List<Var> args = callExp.getArgs();

// Get method reference
MethodRef methodRef = callExp.getMethodRef();
```

---

## Example: Trace Through Field1.java

**Code**:
```java
A a1 = new A();        // line 9
A a2 = a1;             // line 10
Benchmark.alloc(1);
B b1 = new B();        // line 12
Benchmark.alloc(2);
B b2 = new B();        // line 14
a1.f = b1;             // line 17
if(...) a2.f = b2;     // line 18
B b4 = a1.f;           // line 19
Benchmark.test(1, b4);
B b5 = a2.f;           // line 21
Benchmark.test(2, b5);
```

**Analysis Trace**:

1. **After preprocessing**:
   - `obj_ids = {newStmt@line12 -> 1, newStmt@line14 -> 2}`
   - `test_pts = {1 -> b4, 2 -> b5}`

2. **Process allocations**:
   - Line 9: `pts(a1) = {O1}` (unlabeled, internal object)
   - Line 10: `pts(a2) = pts(a1) = {O1}` (aliasing!)
   - Line 12: `pts(b1) = {1}`
   - Line 14: `pts(b2) = {2}`

3. **Process field store (line 17): `a1.f = b1`**:
   - `basePts = pts(a1) = {O1}`
   - `rhsPts = pts(b1) = {1}`
   - Update: `fieldPts[(O1, f)] = {1}`

4. **Process field store (line 18): `a2.f = b2`**:
   - `basePts = pts(a2) = {O1}` (same as a1!)
   - `rhsPts = pts(b2) = {2}`
   - Update: `fieldPts[(O1, f)] = {1, 2}` (union!)

5. **Process field load (line 19): `b4 = a1.f`**:
   - `basePts = pts(a1) = {O1}`
   - `fieldPts[(O1, f)] = {1, 2}`
   - Update: `pts(b4) = {1, 2}`

6. **Process field load (line 21): `b5 = a2.f`**:
   - `basePts = pts(a2) = {O1}` (same object as a1)
   - `fieldPts[(O1, f)] = {1, 2}`
   - Update: `pts(b5) = {1, 2}`

**Final Result**:
```
1 : 1 2
2 : 1 2
```

**Key insight**: a1 and a2 are aliases (point to same object O1), so updates through either variable affect the same field!

---

## Next Steps

1. **Review this document with team** (Nov 16 evening)
2. **Start implementation** (Nov 17 morning)
3. **Test incrementally** - don't write all code before testing!
4. **Ask questions early** - don't stay stuck

---

**Remember**: Sound > Precise > Fast

Good luck! 🚀
