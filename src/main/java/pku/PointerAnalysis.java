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
import pascal.taie.ir.exp.InvokeExp;
import pascal.taie.ir.exp.InvokeInstanceExp;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Copy;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.ir.stmt.LoadField;
import pascal.taie.ir.stmt.New;
import pascal.taie.ir.stmt.Return;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.ir.stmt.StoreField;
import pascal.taie.language.classes.JMethod;

/**
 * Pointer Analysis Implementation
 * Supports: New, Copy, StoreField, LoadField, Invoke (inter-procedural)
 */
public class PointerAnalysis extends PointerAnalysisTrivial {
    public static final String ID = "pku-pta";

    public PointerAnalysis(AnalysisConfig config) {
        super(config);
    }

    @Override
    public PointerAnalysisResult analyze() {
        PointerAnalysisResult result = new PointerAnalysisResult();
        PreprocessResult preprocess = new PreprocessResult();
        World world = World.get();

        // Step 1: Preprocess all methods
        world.getClassHierarchy().applicationClasses().forEach(jclass -> {
            jclass.getDeclaredMethods().forEach(method -> {
                if (!method.isAbstract()) {
                    preprocess.analysis(method.getIR());
                }
            });
        });
            // 打印对象分配信息
        System.out.println("=== Object Allocation Info ===");
        preprocess.obj_ids.forEach((stmt, objId) -> {
            if (stmt instanceof New) {
                New newStmt = (New) stmt;
                System.out.println("Object " + objId + " allocated at: " + newStmt);
            }
        });
        System.out.println("=== End Object Allocation Info ===");

        // Step 2: Create managers
        PointsToSetManager ptsManager = new PointsToSetManager();
        FieldPtsManager fieldPtsManager = new FieldPtsManager();

        // Step 3: Analyze each method
        boolean changed;
        int iterations = 0;
        final int MAX_ITERATIONS = 10;
        
        do {
            changed = false;
            iterations++;
            
            // Analyze all methods
            for (JMethod method : getAllMethods()) {
                if (!method.isAbstract()) {
                    changed |= analyzeMethod(method.getIR(), preprocess, ptsManager, fieldPtsManager);
                }
            }
            
        } while (changed && iterations < MAX_ITERATIONS);

        // Step 4: Extract results
        preprocess.test_pts.forEach((testId, var) -> {
            Set<Integer> pts = ptsManager.getPts(var);
            result.put(testId, new TreeSet<>(pts));
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
        final int MAX_ITERATIONS = 50;
        boolean changed = false;
        
        while (!worklist.isEmpty() && iterationCount < MAX_ITERATIONS) {
            Stmt stmt = worklist.poll();
            boolean stmtChanged = false;

            if (stmt instanceof New newStmt) {
                stmtChanged = handleNew(newStmt, preprocess, ptsManager);
            } else if (stmt instanceof Copy copyStmt) {
                stmtChanged = handleCopy(copyStmt, ptsManager);
            } else if (stmt instanceof StoreField storeStmt) {
                stmtChanged = handleStoreField(storeStmt, ptsManager, fieldPtsManager);
            } else if (stmt instanceof LoadField loadStmt) {
                stmtChanged = handleLoadField(loadStmt, ptsManager, fieldPtsManager);
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
        
        InvokeExp invokeExp = invoke.getInvokeExp();
        JMethod targetMethod = invokeExp.getMethodRef().resolve();
        
        if (targetMethod == null || targetMethod.isAbstract()) {
            return false;
        }
        
        IR targetIR = targetMethod.getIR();
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
        
        return changed;
    }

    /** Process return value from callee to caller */
    private boolean processReturnValue(Invoke invoke, PointsToSetManager ptsManager) {
        Var lhs = invoke.getLValue();
        if (lhs == null) {
            return false; // No return value
        }
        
        JMethod targetMethod = invoke.getInvokeExp().getMethodRef().resolve();
        if (targetMethod == null || targetMethod.isAbstract()) {
            return false;
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
        
        return ptsManager.addAllPointsTo(lhs, returnPts);
    }

    /** Analyze return value of a method */
    private Set<Integer> analyzeMethodReturnValue(JMethod method, PointsToSetManager ptsManager) {
        Set<Integer> returnPts = new HashSet<>();
        
        if (method.isAbstract()) {
            return returnPts;
        }
        
        IR ir = method.getIR();
        
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
        if (method.isAbstract()) {
            return false;
        }
        
        IR ir = method.getIR();
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

    /** Handle: var = new T() */
    private boolean handleNew(New stmt, PreprocessResult preprocess, PointsToSetManager ptsManager) {
        Var lhs = stmt.getLValue();
        Integer objId = preprocess.obj_ids.get(stmt);
        if (objId != null) {
            return ptsManager.addPointsTo(lhs, objId);
        }
        return false;
    }

    /** Handle: a = b */
    private boolean handleCopy(Copy stmt, PointsToSetManager ptsManager) {
        Var lhs = stmt.getLValue();
        Var rhs = stmt.getRValue();
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
        if (stmt.toString().contains("Field")) {
            System.out.println("FIELD_STORE: " + stmt);
            System.out.println("  Base: " + base + " -> " + (base != null ? ptsManager.getPts(base) : "null"));
            System.out.println("  Value: " + value + " -> " + valuePts);
            System.out.println("  Field: " + fieldName);
        }

        if (base != null) {
            Set<Integer> basePts = ptsManager.getPts(base);
            for (Integer objId : basePts) {
                changed |= fieldPtsManager.addFieldPts(objId, fieldName, valuePts);
                
                // Field诊断
                if (stmt.toString().contains("Field")) {
                    System.out.println("  Stored to obj " + objId + "." + fieldName + ": " + valuePts);
                }
            }
        } else {
            int staticObjId = FieldPtsManager.getStaticObjId(fieldName);
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
        if (stmt.toString().contains("Field")) {
            System.out.println("FIELD_LOAD: " + stmt);
            System.out.println("  Base: " + base + " -> " + (base != null ? ptsManager.getPts(base) : "null"));
            System.out.println("  LHS: " + lhs);
        }

        if (base != null) {
            Set<Integer> basePts = ptsManager.getPts(base);
            for (Integer objId : basePts) {
                Set<Integer> fieldPts = fieldPtsManager.getFieldPts(objId, fieldName);
                newPts.addAll(fieldPts);
                
                // Field诊断
                if (stmt.toString().contains("Field")) {
                    System.out.println("  Loaded from obj " + objId + "." + fieldName + ": " + fieldPts);
                }
            }
        } else {
            int staticObjId = FieldPtsManager.getStaticObjId(fieldName);
            newPts.addAll(fieldPtsManager.getFieldPts(staticObjId, fieldName));
        }

        // Field诊断
        if (stmt.toString().contains("Field")) {
            System.out.println("  Result: " + lhs + " -> " + newPts);
        }

        return ptsManager.addAllPointsTo(lhs, newPts);
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

        public static int getStaticObjId(String fieldName) {
            return STATIC_OBJ_ID;
        }
    }
}