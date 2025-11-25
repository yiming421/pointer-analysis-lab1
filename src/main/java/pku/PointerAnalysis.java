package pku;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;

import pascal.taie.World;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.ir.IR;
import pascal.taie.ir.exp.FieldAccess;
import pascal.taie.ir.exp.InstanceFieldAccess;
import pascal.taie.ir.exp.IntLiteral;
import pascal.taie.ir.exp.InvokeExp;
import pascal.taie.ir.exp.InvokeInstanceExp;
import pascal.taie.ir.exp.Literal;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.AssignLiteral;
import pascal.taie.ir.stmt.Cast;
import pascal.taie.ir.stmt.Copy;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.ir.stmt.LoadArray;
import pascal.taie.ir.stmt.LoadField;
import pascal.taie.ir.stmt.New;
import pascal.taie.ir.stmt.Return;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.ir.stmt.StoreArray;
import pascal.taie.ir.stmt.StoreField;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.Type;

/**
 * Pointer Analysis Implementation
 * Supports: New, Copy, StoreField, LoadField, Invoke (inter-procedural)
 */
public class PointerAnalysis extends PointerAnalysisTrivial {
    public static final String ID = "pku-pta";
    
    // Map from allocation ID to object type
    private Map<Integer, Type> allocIdToType = new HashMap<>();

    public PointerAnalysis(AnalysisConfig config) {
        super(config);
    }

    @Override
    public PointerAnalysisResult analyze() {
        PointerAnalysisResult result = new PointerAnalysisResult();
        PreprocessResult preprocess = new PreprocessResult();
        World world = World.get();

        // Step 1: Preprocess all methods and build allocation ID to type mapping
        world.getClassHierarchy().applicationClasses().forEach(jclass -> {
            jclass.getDeclaredMethods().forEach(method -> {
                if (!method.isAbstract() && !method.isNative()) {
                    IR ir = method.getIR();
                    if (ir != null) {
                        preprocess.analysis(ir);
                    }
                }
            });
        });
        
        // Build allocation ID to type mapping
        preprocess.obj_ids.forEach((newStmt, objId) -> {
            Type objType = newStmt.getRValue().getType();
            allocIdToType.put(objId, objType);
        });
            // 打印对象分配信息
        //System.out.println("=== Object Allocation Info ===");
        //preprocess.obj_ids.forEach((stmt, objId) -> {
        //    if (stmt instanceof New) {
        //        New newStmt = (New) stmt;
        //        System.out.println("Object " + objId + " allocated at: " + newStmt);
        //    }
        //});
        //System.out.println("=== End Object Allocation Info ===");

        // Step 2: Create managers
        PointsToSetManager ptsManager = new PointsToSetManager();
        FieldPtsManager fieldPtsManager = new FieldPtsManager();

        // Step 3: Analyze each method
        boolean changed;
        int iterations = 0;
        final int MAX_ITERATIONS = 5000;

        do {
            changed = false;
            iterations++;

            // Analyze all methods
            for (JMethod method : getAllMethods()) {
                if (!method.isAbstract() && !method.isNative()) {
                    IR ir = method.getIR();
                    if (ir != null) {
                        changed |= analyzeMethod(ir, preprocess, ptsManager, fieldPtsManager);
                    }
                }
            }

        } while (changed && iterations < MAX_ITERATIONS);

        // Step 4: Extract results
        preprocess.test_pts.forEach((testId, var) -> {
            Set<Integer> pts = ptsManager.getPts(var);
            // Filter out auto-generated negative IDs (unlabeled objects)
            TreeSet<Integer> filteredPts = new TreeSet<>();
            for (Integer objId : pts) {
                if (objId > 0) {  // Only include explicitly labeled objects
                    filteredPts.add(objId);
                }
            }
            result.put(testId, filteredPts);
        });

        dump(result);
        return result;
    }

    /** Get all application methods for inter-procedural analysis */
    private Set<JMethod> getAllMethods() {
        Set<JMethod> methods = new HashSet<>();
        World.get().getClassHierarchy().applicationClasses().forEach(jclass -> {
            methods.addAll(jclass.getDeclaredMethods());
        });
        return methods;
    }

    /** Analyze a method with worklist until fixed point */
    private boolean analyzeMethod(IR ir, PreprocessResult preprocess,
                                PointsToSetManager ptsManager, FieldPtsManager fieldPtsManager) {
        Queue<Stmt> worklist = new LinkedList<>(ir.getStmts());
        int iterationCount = 0;
        final int MAX_ITERATIONS = 5000;
        boolean changed = false;

        // Track constant values for array index analysis
        Map<Var, Literal> constantMap = new HashMap<>();

        while (!worklist.isEmpty() && iterationCount < MAX_ITERATIONS) {
            Stmt stmt = worklist.poll();
            boolean stmtChanged = false;
            
            // Debug: Log what statements we're processing
            // System.out.println("Processing: " + stmt.getClass().getSimpleName() + " - " + stmt);

            if (stmt instanceof New newStmt) {
                stmtChanged = handleNew(newStmt, preprocess, ptsManager);
            } else if (stmt instanceof AssignLiteral assignLit) {
                // Track literal assignments for constant propagation
                constantMap.put(assignLit.getLValue(), assignLit.getRValue());
            } else if (stmt instanceof Copy copyStmt) {
                stmtChanged = handleCopy(copyStmt, ptsManager, constantMap);
            } else if (stmt instanceof Cast castStmt) {
                // Handle type casts: x = (T) y
                stmtChanged = handleCast(castStmt, ptsManager);
            } else if (stmt instanceof StoreField storeStmt) {
                stmtChanged = handleStoreField(storeStmt, ptsManager, fieldPtsManager);
            } else if (stmt instanceof LoadField loadStmt) {
                stmtChanged = handleLoadField(loadStmt, ptsManager, fieldPtsManager);
            } else if (stmt instanceof StoreArray storeStmt) {
                stmtChanged = handleStoreArray(storeStmt, ptsManager, fieldPtsManager, constantMap);
            } else if (stmt instanceof LoadArray loadStmt) {
                stmtChanged = handleLoadArray(loadStmt, ptsManager, fieldPtsManager, constantMap);
            } else if (stmt instanceof Invoke invokeStmt) {
                stmtChanged = handleInvoke(invokeStmt, preprocess, ptsManager, fieldPtsManager);
            }

            if (stmtChanged) {
                changed = true;
                worklist.addAll(ir.getStmts());
            }
            iterationCount++;
        }

        return changed;
    }

    /** Handle method invocation with inter-procedural parameter and return value handling */
    private boolean handleInvoke(Invoke invoke, PreprocessResult preprocess,
                               PointsToSetManager ptsManager, FieldPtsManager fieldPtsManager) {
        boolean changed = false;

        // Process argument passing from caller to callee
        changed |= processArgumentPassing(invoke, ptsManager);

        // Process return value from callee to caller
        changed |= processReturnValue(invoke, ptsManager);

        return changed;
    }

    /** Process argument passing from caller to callee */
    private boolean processArgumentPassing(Invoke invoke, PointsToSetManager ptsManager) {
        boolean changed = false;

        // Get all possible target methods (handles virtual dispatch)
        Set<JMethod> targetMethods = resolveTargetMethods(invoke, ptsManager);
        InvokeExp invokeExp = invoke.getInvokeExp();
        
        for (JMethod targetMethod : targetMethods) {
            if (targetMethod == null || targetMethod.isNative()) {
                continue;
            }

            IR targetIR = targetMethod.getIR();
            if (targetIR == null) {
                continue;
            }
            List<Var> params = targetIR.getParams();

            if (!invoke.isStatic()) {
                // Instance method call: handle this parameter
                Var base = null;
                Var thisVar = targetIR.getThis();

                // Get base object for instance method call
                if (invokeExp instanceof InvokeInstanceExp) {
                    InvokeInstanceExp instanceExp = (InvokeInstanceExp) invokeExp;
                    base = instanceExp.getBase();
                }

                if (base != null && thisVar != null) {
                    Set<Integer> basePointsTo = ptsManager.getPts(base);
                    changed |= ptsManager.addAllPointsTo(thisVar, basePointsTo);
                }

                // Pass other arguments
                for (int i = 0; i < invokeExp.getArgs().size() && i < params.size(); i++) {
                    Var arg = invokeExp.getArg(i);
                    Var param = params.get(i);
                    Set<Integer> argPointsTo = ptsManager.getPts(arg);
                    changed |= ptsManager.addAllPointsTo(param, argPointsTo);
                }
            } else {
                // Static method call: direct argument passing
                for (int i = 0; i < invokeExp.getArgs().size() && i < params.size(); i++) {
                    Var arg = invokeExp.getArg(i);
                    Var param = params.get(i);
                    Set<Integer> argPointsTo = ptsManager.getPts(arg);
                    changed |= ptsManager.addAllPointsTo(param, argPointsTo);
                }
            }
        }

        return changed;
    }

    /** Process return value from callee to caller */
    private boolean processReturnValue(Invoke invoke, PointsToSetManager ptsManager) {
        Var lhs = invoke.getLValue();
        if (lhs == null) {
            return false; // No return value
        }

        // Get all possible target methods (handles virtual dispatch)
        Set<JMethod> targetMethods = resolveTargetMethods(invoke, ptsManager);
        Set<Integer> allReturnPts = new HashSet<>();
        
        for (JMethod targetMethod : targetMethods) {
            if (targetMethod == null || targetMethod.isNative()) {
                continue;
            }

            // Analyze the return value of the target method
            Set<Integer> returnPts = analyzeMethodReturnValue(targetMethod, ptsManager);

            // Special handling for methods that return 'this'
            if (returnPts.isEmpty() && !invoke.isStatic() && returnsThis(targetMethod)) {
                // Use the base object of the call as return value
                InvokeExp invokeExp = invoke.getInvokeExp();
                if (invokeExp instanceof InvokeInstanceExp) {
                    InvokeInstanceExp instanceExp = (InvokeInstanceExp) invokeExp;
                    Var base = instanceExp.getBase();
                    returnPts.addAll(ptsManager.getPts(base));
                }
            }
            
            allReturnPts.addAll(returnPts);
        }

        return ptsManager.addAllPointsTo(lhs, allReturnPts);
    }

    /** Analyze return value of a method */
    private Set<Integer> analyzeMethodReturnValue(JMethod method, PointsToSetManager ptsManager) {
        Set<Integer> returnPts = new HashSet<>();

        if (method.isAbstract() || method.isNative()) {
            return returnPts;
        }

        IR ir = method.getIR();
        if (ir == null) {
            return returnPts;
        }

        // Find return statements and collect return values
        for (Stmt stmt : ir.getStmts()) {
            if (stmt instanceof Return) {
                Return returnStmt = (Return) stmt;
                Var returnVar = returnStmt.getValue();
                if (returnVar != null) {
                    returnPts.addAll(ptsManager.getPts(returnVar));
                }
            }
        }

        return returnPts;
    }

    /** Check if a method returns this */
    private boolean returnsThis(JMethod method) {
        if (method.isAbstract() || method.isNative()) {
            return false;
        }

        IR ir = method.getIR();
        if (ir == null) {
            return false;
        }
        Var thisVar = ir.getThis();

        if (thisVar == null) {
            return false; // Static methods don't have 'this'
        }

        // Check if method contains 'return this' statement
        for (Stmt stmt : ir.getStmts()) {
            if (stmt instanceof Return) {
                Return returnStmt = (Return) stmt;
                Var returnVar = returnStmt.getValue();
                if (returnVar != null && returnVar.equals(thisVar)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Resolve target methods for virtual dispatch
     * Handles interface calls and virtual method calls
     */
    private Set<JMethod> resolveTargetMethods(Invoke invoke, PointsToSetManager ptsManager) {
        Set<JMethod> targets = new HashSet<>();
        InvokeExp invokeExp = invoke.getInvokeExp();
        JMethod declaredMethod = invokeExp.getMethodRef().resolve();
        
        if (invoke.isStatic()) {
            // Static calls: only one target
            if (declaredMethod != null && !declaredMethod.isAbstract()) {
                targets.add(declaredMethod);
            }
            return targets;
        }
        
        // Instance calls: need virtual dispatch
        if (!(invokeExp instanceof InvokeInstanceExp)) {
            return targets;
        }
        
        InvokeInstanceExp instanceExp = (InvokeInstanceExp) invokeExp;
        Var receiver = instanceExp.getBase();
        
        // Get all possible receiver types from points-to set
        Set<Integer> receiverPts = ptsManager.getPts(receiver);
        
        for (Integer objId : receiverPts) {
            // Find the concrete type of this object
            Type objType = findObjectType(objId);
            if (objType != null) {
                JClass objClass = World.get().getClassHierarchy().getClass(objType.getName());
                if (objClass != null) {
                    // Find concrete implementation of the method in this class
                    JMethod concreteMethod = findConcreteMethod(objClass, declaredMethod);
                    if (concreteMethod != null && !concreteMethod.isAbstract()) {
                        targets.add(concreteMethod);
                    }
                }
            }
        }
        
        // If no receivers found or no concrete implementations, fall back to declared method
        if (targets.isEmpty() && declaredMethod != null && !declaredMethod.isAbstract()) {
            targets.add(declaredMethod);
        }
        
        return targets;
    }
    
    /**
     * Find concrete implementation of a method in a class hierarchy
     */
    private JMethod findConcreteMethod(JClass clazz, JMethod declaredMethod) {
        if (clazz == null || declaredMethod == null) {
            return null;
        }
        
        // Look for method in current class
        JMethod method = clazz.getDeclaredMethod(declaredMethod.getSubsignature());
        if (method != null && !method.isAbstract()) {
            return method;
        }
        
        // Look in superclass
        JClass superClass = clazz.getSuperClass();
        if (superClass != null) {
            JMethod superMethod = findConcreteMethod(superClass, declaredMethod);
            if (superMethod != null) {
                return superMethod;
            }
        }
        
        return null;
    }
    
    /**
     * Find the type of an object given its allocation ID
     * Uses the pre-built allocation ID to type mapping
     */
    private Type findObjectType(Integer objId) {
        return allocIdToType.get(objId);
    }

    /** Handle: var = new T() */
    private boolean handleNew(New stmt, PreprocessResult preprocess, PointsToSetManager ptsManager) {
        Var lhs = stmt.getLValue();
        Integer objId = preprocess.obj_ids.get(stmt);
        if (objId != null) {
            return ptsManager.addPointsTo(lhs, objId);
        }
        return false;
    }

    /** Handle: a = b (also track constant assignments) */
    private boolean handleCopy(Copy stmt, PointsToSetManager ptsManager, Map<Var, Literal> constantMap) {
        Var lhs = stmt.getLValue();
        Var rhs = stmt.getRValue();

        // Track constant propagation for array indices
        if (rhs.isConst()) {
            constantMap.put(lhs, rhs.getConstValue());
        } else if (constantMap.containsKey(rhs)) {
            constantMap.put(lhs, constantMap.get(rhs));
        }

        Set<Integer> rhsPts = ptsManager.getPts(rhs);
        return ptsManager.addAllPointsTo(lhs, rhsPts);
    }

    /** Handle: x = (T) y (type cast just propagates points-to set) */
    private boolean handleCast(Cast stmt, PointsToSetManager ptsManager) {
        Var lhs = stmt.getLValue();
        Var rhs = stmt.getRValue().getValue();
        Set<Integer> rhsPts = ptsManager.getPts(rhs);
        return ptsManager.addAllPointsTo(lhs, rhsPts);
    }

    /** Handle: x.f = y or T.f = y */
    private boolean handleStoreField(StoreField stmt, PointsToSetManager ptsManager, FieldPtsManager fieldPtsManager) {
        FieldAccess lvalue = stmt.getFieldAccess();
        Var value = stmt.getRValue();

        Var base = null;
        String fieldName = lvalue.getFieldRef().getName();

        if (lvalue instanceof InstanceFieldAccess instField) {
            base = instField.getBase();
        }

        Set<Integer> valuePts = ptsManager.getPts(value);
        boolean changed = false;

        // Field诊断：只对特定测试用例输出
        //if (stmt.toString().contains("Field")) {
            //System.out.println("FIELD_STORE: " + stmt);
            //System.out.println("  Base: " + base + " -> " + (base != null ? ptsManager.getPts(base) : "null"));
            //System.out.println("  Value: " + value + " -> " + valuePts);
            //System.out.println("  Field: " + fieldName);
        //}

        if (base != null) {
            Set<Integer> basePts = ptsManager.getPts(base);
            for (Integer objId : basePts) {
                changed |= fieldPtsManager.addFieldPts(objId, fieldName, valuePts);

                // Field诊断
                //if (stmt.toString().contains("Field")) {
                //    System.out.println("  Stored to obj " + objId + "." + fieldName + ": " + valuePts);
                //}
            }
        } else {
            // Static field - use declaring class name to avoid collisions
            String className = lvalue.getFieldRef().getDeclaringClass().getName();
            int staticObjId = FieldPtsManager.getStaticObjId(className);
            changed |= fieldPtsManager.addFieldPts(staticObjId, fieldName, valuePts);
        }

        return changed;
    }

    /** Handle: x = y.f or x = T.f */
    private boolean handleLoadField(LoadField stmt, PointsToSetManager ptsManager, FieldPtsManager fieldPtsManager) {
        Var lhs = stmt.getLValue();
        FieldAccess rvalue = stmt.getFieldAccess();

        Var base = null;
        String fieldName = rvalue.getFieldRef().getName();

        if (rvalue instanceof InstanceFieldAccess instField) {
            base = instField.getBase();
        }

        Set<Integer> newPts = new HashSet<>();

        // Field诊断：只对特定测试用例输出
       // if (stmt.toString().contains("Field")) {
        //    System.out.println("FIELD_LOAD: " + stmt);
        //    System.out.println("  Base: " + base + " -> " + (base != null ? ptsManager.getPts(base) : "null"));
        //    System.out.println("  LHS: " + lhs);
        //}

        if (base != null) {
            Set<Integer> basePts = ptsManager.getPts(base);
            for (Integer objId : basePts) {
                Set<Integer> fieldPts = fieldPtsManager.getFieldPts(objId, fieldName);
                newPts.addAll(fieldPts);

                // Field诊断
                //if (stmt.toString().contains("Field")) {
                //    System.out.println("  Loaded from obj " + objId + "." + fieldName + ": " + fieldPts);
                //}
            }
        } else {
            // Static field - use declaring class name to avoid collisions
            String className = rvalue.getFieldRef().getDeclaringClass().getName();
            int staticObjId = FieldPtsManager.getStaticObjId(className);
            newPts.addAll(fieldPtsManager.getFieldPts(staticObjId, fieldName));
        }

        // Field诊断
        //if (stmt.toString().contains("Field")) {
        //    System.out.println("  Result: " + lhs + " -> " + newPts);
        //}

        return ptsManager.addAllPointsTo(lhs, newPts);
    }

    /** Handle: arr[i] = x with index-sensitive analysis for constants */
    private boolean handleStoreArray(StoreArray stmt, PointsToSetManager ptsManager, FieldPtsManager fieldPtsManager, Map<Var, Literal> constantMap) {
        Var arrayVar = stmt.getArrayAccess().getBase();
        Var indexVar = stmt.getArrayAccess().getIndex();
        Var value = stmt.getRValue();

        Set<Integer> valuePts = ptsManager.getPts(value);
        Set<Integer> arrayPts = ptsManager.getPts(arrayVar);
        boolean changed = false;

        // Determine field name based on index
        String fieldName = getArrayFieldName(indexVar, constantMap);

        // Store to all possible array objects
        for (Integer objId : arrayPts) {
            changed |= fieldPtsManager.addFieldPts(objId, fieldName, valuePts);
        }

        return changed;
    }

    /** Handle: x = arr[i] with index-sensitive analysis for constants */
    private boolean handleLoadArray(LoadArray stmt, PointsToSetManager ptsManager, FieldPtsManager fieldPtsManager, Map<Var, Literal> constantMap) {
        Var lhs = stmt.getLValue();
        Var arrayVar = stmt.getArrayAccess().getBase();
        Var indexVar = stmt.getArrayAccess().getIndex();

        Set<Integer> newPts = new HashSet<>();
        Set<Integer> arrayPts = ptsManager.getPts(arrayVar);

        // Determine field name based on index
        String fieldName = getArrayFieldName(indexVar, constantMap);

        // Load from all possible array objects
        for (Integer objId : arrayPts) {
            if (fieldName.equals("[]")) {
                // Unknown index - must include ALL array elements to be sound
                newPts.addAll(fieldPtsManager.getAllArrayElements(objId));
            } else {
                // Constant index - only load that specific element
                Set<Integer> elementPts = fieldPtsManager.getFieldPts(objId, fieldName);
                newPts.addAll(elementPts);
            }
        }

        return ptsManager.addAllPointsTo(lhs, newPts);
    }

    /** Get array field name - use constant index if available, otherwise merge all */
    private String getArrayFieldName(Var indexVar, Map<Var, Literal> constantMap) {
        // Check if index is tracked as a constant in our map
        if (indexVar != null && constantMap.containsKey(indexVar)) {
            Literal constValue = constantMap.get(indexVar);
            if (constValue instanceof IntLiteral) {
                int intValue = ((IntLiteral) constValue).getValue();
                return "[" + intValue + "]";
            }
        }

        // Check if index variable itself is marked as const
        if (indexVar != null && indexVar.isConst()) {
            try {
                Object constValue = indexVar.getConstValue();
                if (constValue instanceof IntLiteral) {
                    int intValue = ((IntLiteral) constValue).getValue();
                    return "[" + intValue + "]";
                }
            } catch (Exception e) {
                // Not a constant, fall through to wildcard
            }
        }

        // Unknown or variable index - use wildcard to merge all elements
        return "[]";
    }

    //** Field-sensitive manager for object fields */
    static class FieldPtsManager {
        private final Map<Integer, Map<String, Set<Integer>>> fieldPts = new HashMap<>();
        private static final int STATIC_OBJ_ID = -1;

        public Set<Integer> getFieldPts(int objId, String field) {
            return fieldPts.computeIfAbsent(objId, k -> new HashMap<>())
                    .computeIfAbsent(field, k -> new HashSet<>());
        }

        public boolean addFieldPts(int objId, String field, Set<Integer> pts) {
            Set<Integer> set = getFieldPts(objId, field);
            return set.addAll(pts);
        }

        /** Get all array elements for an object (for sound handling of unknown indices) */
        public Set<Integer> getAllArrayElements(int objId) {
            Set<Integer> allElements = new HashSet<>();
            Map<String, Set<Integer>> fields = fieldPts.get(objId);
            if (fields != null) {
                for (Map.Entry<String, Set<Integer>> entry : fields.entrySet()) {
                    // Include [] and all [constant] fields
                    if (entry.getKey().startsWith("[") && entry.getKey().endsWith("]")) {
                        allElements.addAll(entry.getValue());
                    }
                }
            }
            return allElements;
        }

        public static int getStaticObjId(String className) {
            // Use class name hash to get unique static object ID per class
            return -1000000 - Math.abs(className.hashCode());
        }
    }
}
