package pku;

import pascal.taie.ir.exp.Var;
import pascal.taie.language.classes.JField;

import java.util.*;

/**
 * Manages points-to sets for variables and object fields.
 *
 * Person A should implement this class as infrastructure.
 */
public class PointsToSetManager {

    // Points-to sets for variables: var -> {obj1, obj2, ...}
    private final Map<Var, Set<Integer>> varPointsTo;

    // Points-to sets for object fields: (objId, field) -> {obj1, obj2, ...}
    private final Map<ObjectField, Set<Integer>> fieldPointsTo;

    public PointsToSetManager() {
        this.varPointsTo = new HashMap<>();
        this.fieldPointsTo = new HashMap<>();
    }

    // ========== Variable Points-To Operations ==========

    /**
     * Get the points-to set for a variable.
     * Creates an empty set if variable not seen before.
     */
    public Set<Integer> getPts(Var var) {
        return varPointsTo.computeIfAbsent(var, k -> new HashSet<>());
    }

    /**
     * Add a single object to a variable's points-to set.
     * @return true if the set changed (for worklist algorithm)
     */
    public boolean addPointsTo(Var var, int objId) {
        return getPts(var).add(objId);
    }

    /**
     * Add multiple objects to a variable's points-to set.
     * Used for: a = b (add all of b's objects to a)
     * @return true if the set changed
     */
    public boolean addAllPointsTo(Var dest, Set<Integer> srcObjs) {
        if (srcObjs.isEmpty()) {
            return false;
        }
        return getPts(dest).addAll(srcObjs);
    }

    // ========== Field Points-To Operations ==========

    /**
     * Get the points-to set for an object's field.
     * Example: Object 1's field 'f' -> {2, 3}
     */
    public Set<Integer> getFieldPts(int objId, JField field) {
        ObjectField of = new ObjectField(objId, field);
        return fieldPointsTo.computeIfAbsent(of, k -> new HashSet<>());
    }

    /**
     * Add a single object to a field's points-to set.
     * Used for: obj.field = rhs
     * @return true if the set changed
     */
    public boolean addFieldPointsTo(int objId, JField field, int targetObjId) {
        return getFieldPts(objId, field).add(targetObjId);
    }

    /**
     * Add multiple objects to a field's points-to set.
     * @return true if the set changed
     */
    public boolean addAllFieldPointsTo(int objId, JField field, Set<Integer> targetObjs) {
        if (targetObjs.isEmpty()) {
            return false;
        }
        return getFieldPts(objId, field).addAll(targetObjs);
    }

    // ========== Utility Methods ==========

    /**
     * Get all variables we're tracking.
     */
    public Set<Var> getAllVars() {
        return varPointsTo.keySet();
    }

    /**
     * Print debug information (for debugging).
     */
    public void dump() {
        System.out.println("=== Variable Points-To Sets ===");
        for (Map.Entry<Var, Set<Integer>> entry : varPointsTo.entrySet()) {
            System.out.println(entry.getKey().getName() + " -> " + entry.getValue());
        }

        System.out.println("\n=== Field Points-To Sets ===");
        for (Map.Entry<ObjectField, Set<Integer>> entry : fieldPointsTo.entrySet()) {
            System.out.println(entry.getKey() + " -> " + entry.getValue());
        }
    }
}
