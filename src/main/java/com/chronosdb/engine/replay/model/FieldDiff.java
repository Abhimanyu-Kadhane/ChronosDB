// src/main/java/com/chronosdb/engine/replay/model/FieldDiff.java
package com.chronosdb.engine.replay.model;

/**
 * Represents the change status of a single field between two consecutive versions.
 *
 * ADDED:     field exists in newState, absent in oldState
 * REMOVED:   field exists in oldState, absent in newState
 * MODIFIED:  field exists in both, values differ
 * UNCHANGED: field exists in both, values identical
 */
public record FieldDiff(
        String fieldPath,       // dot-notation path: "address.city" for nested fields
        DiffType type,
        Object oldValue,        // null for ADDED
        Object newValue         // null for REMOVED
) {
    public enum DiffType {
        ADDED, REMOVED, MODIFIED, UNCHANGED
    }
}