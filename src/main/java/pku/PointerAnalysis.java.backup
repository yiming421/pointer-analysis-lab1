package pku;

import pascal.taie.World;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.ir.IR;
import pascal.taie.ir.stmt.*;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.exp.FieldAccess;
import pascal.taie.ir.exp.InstanceFieldAccess;
import pascal.taie.ir.exp.StaticFieldAccess;

import java.util.*;

/**
 * Pointer Analysis Implementation - Phase 2
 * Supports: New, Copy, StoreField, LoadField
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

        // Step 2: Create managers
        PointsToSetManager ptsManager = new PointsToSetManager();
        FieldPtsManager fieldPtsManager = new FieldPtsManager();

        // Step 3: Analyze each method
        world.getClassHierarchy().applicationClasses().forEach(jclass -> {
            jclass.getDeclaredMethods().forEach(method -> {
                if (!method.isAbstract()) {
                    analyzeMethod(method.getIR(), preprocess, ptsManager, fieldPtsManager);
                }
            });
        });

        // Step 4: Extract results
        preprocess.test_pts.forEach((testId, var) -> {
            Set<Integer> pts = ptsManager.getPts(var);
            result.put(testId, new TreeSet<>(pts));
        });

        dump(result);
        return result;
    }

    /** Field-sensitive manager for object fields */
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
            return STATIC_OBJ_ID; // 所有静态字段统一一个 id
        }
    }

    /** Analyze a method with worklist until fixed point */
    private void analyzeMethod(IR ir, PreprocessResult preprocess, PointsToSetManager ptsManager, FieldPtsManager fieldPtsManager) {
        Queue<Stmt> worklist = new LinkedList<>(ir.getStmts());

        while (!worklist.isEmpty()) {
            Stmt stmt = worklist.poll();
            boolean changed = false;

            if (stmt instanceof New newStmt) {
                changed = handleNew(newStmt, preprocess, ptsManager);
            } else if (stmt instanceof Copy copyStmt) {
                changed = handleCopy(copyStmt, ptsManager);
            } else if (stmt instanceof StoreField storeStmt) {
                changed = handleStoreField(storeStmt, ptsManager, fieldPtsManager);
            } else if (stmt instanceof LoadField loadStmt) {
                changed = handleLoadField(loadStmt, ptsManager, fieldPtsManager);
            }

            if (changed) {
                // 简化处理：重新加入所有语句
                worklist.addAll(ir.getStmts());
            }
        }
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
        FieldAccess lvalue = stmt.getFieldAccess(); // 左值
        Var value = stmt.getRValue();               // 右值

        Var base = null;
        String fieldName = lvalue.getFieldRef().getName();

        if (lvalue instanceof InstanceFieldAccess instField) {
            base = instField.getBase();
        }

        Set<Integer> valuePts = ptsManager.getPts(value);
        boolean changed = false;

        if (base != null) { // 实例字段
            for (Integer objId : ptsManager.getPts(base)) {
                changed |= fieldPtsManager.addFieldPts(objId, fieldName, valuePts);
            }
        } else { // 静态字段
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

        if (base != null) { // 实例字段
            for (Integer objId : ptsManager.getPts(base)) {
                newPts.addAll(fieldPtsManager.getFieldPts(objId, fieldName));
            }
        } else { // 静态字段
            int staticObjId = FieldPtsManager.getStaticObjId(fieldName);
            newPts.addAll(fieldPtsManager.getFieldPts(staticObjId, fieldName));
        }

        return ptsManager.addAllPointsTo(lhs, newPts);
    }
}
