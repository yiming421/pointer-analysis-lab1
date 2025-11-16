package pku;

import pascal.taie.World;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.ir.IR;
import pascal.taie.ir.stmt.*;
import pascal.taie.ir.exp.Var;

import java.util.*;

/**
 * Pointer Analysis Implementation - PKU Lab 1
 *
 * Phase 1 Goal: Pass Hello.java and Branch.java
 * Requirements: Only need to handle New and Copy statements
 */
public class PointerAnalysis extends PointerAnalysisTrivial {
    public static final String ID = "pku-pta";

    public PointerAnalysis(AnalysisConfig config) {
        super(config);
    }

    @Override
    public PointerAnalysisResult analyze() {
        var result = new PointerAnalysisResult();
        var preprocess = new PreprocessResult();
        var world = World.get();

        // Step 1: Collect allocation and test points from all methods
        world.getClassHierarchy().applicationClasses().forEach(jclass -> {
            jclass.getDeclaredMethods().forEach(method -> {
                if (!method.isAbstract()) {
                    preprocess.analysis(method.getIR());
                }
            });
        });

        // Step 2: Create points-to manager
        PointsToSetManager ptsManager = new PointsToSetManager();

        // Step 3: Analyze each method
        world.getClassHierarchy().applicationClasses().forEach(jclass -> {
            jclass.getDeclaredMethods().forEach(method -> {
                if (!method.isAbstract()) {
                    analyzeMethod(method.getIR(), preprocess, ptsManager);
                }
            });
        });

        // Step 4: Extract results for test points
        preprocess.test_pts.forEach((testId, var) -> {
            Set<Integer> pts = ptsManager.getPts(var);
            result.put(testId, new TreeSet<>(pts));
        });

        dump(result);
        return result;
    }

    /**
     * Analyze a method using simple iteration until fixed point.
     * Person A: Implement worklist algorithm here later.
     */
    private void analyzeMethod(IR ir, PreprocessResult preprocess, PointsToSetManager ptsManager) {
        // Simple approach: iterate until no changes
        boolean changed = true;
        int iteration = 0;

        while (changed && iteration < 100) {
            changed = false;
            iteration++;

            for (Stmt stmt : ir.getStmts()) {
                // Only need to handle New and Copy for Hello/Branch
                if (stmt instanceof New) {
                    changed |= handleNew((New) stmt, preprocess, ptsManager);
                } else if (stmt instanceof Copy) {
                    changed |= handleCopy((Copy) stmt, ptsManager);
                }
                // Add StoreField and LoadField later for Field1/Field2
                // else if (stmt instanceof StoreField) { ... }
                // else if (stmt instanceof LoadField) { ... }
            }
        }
    }

    /**
     * Handle: var = new T();  (marked with Benchmark.alloc(id))
     * Implement this first (Nov 18)
     *
     * Expected behavior:
     * - Get the variable being assigned: lhs
     * - Get the object ID from preprocess
     * - Add object ID to lhs's points-to set
     */
    private boolean handleNew(New stmt, PreprocessResult preprocess, PointsToSetManager ptsManager) {
        // TODO Implement this
        //
        // Var lhs = stmt.getLValue();
        // Integer objId = preprocess.obj_ids.get(stmt);
        // if (objId != null) {
        //     return ptsManager.addPointsTo(lhs, objId);
        // }
        return false;
    }

    /**
     * Handle: a = b;
     * Implement this second (Nov 18)
     *
     * Expected behavior:
     * - Get lhs variable (a) and rhs variable (b)
     * - Copy all objects from pts(b) to pts(a)
     */
    private boolean handleCopy(Copy stmt, PointsToSetManager ptsManager) {
        // TODO Person B: Implement this
        //
        // Var lhs = stmt.getLValue();
        // Var rhs = stmt.getRValue();
        // Set<Integer> rhsPts = ptsManager.getPts(rhs);
        // return ptsManager.addAllPointsTo(lhs, rhsPts);
        return false;
    }
}
