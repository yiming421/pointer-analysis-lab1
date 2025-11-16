package pku;

import pascal.taie.language.classes.JField;
import java.util.Objects;

/**
 * Represents a field of a specific object instance.
 * Used as a key in the field points-to map.
 *
 * Example: If object O1 has a field 'f', this represents (O1, f)
 */
public class ObjectField {
    private final int objectId;
    private final JField field;

    public ObjectField(int objectId, JField field) {
        this.objectId = objectId;
        this.field = field;
    }

    public int getObjectId() {
        return objectId;
    }

    public JField getField() {
        return field;
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

    @Override
    public String toString() {
        return "(" + objectId + ", " + field.getName() + ")";
    }
}
