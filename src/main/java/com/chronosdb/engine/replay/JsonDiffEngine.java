// src/main/java/com/chronosdb/engine/replay/JsonDiffEngine.java
package com.chronosdb.engine.replay;

import com.chronosdb.engine.replay.model.FieldDiff;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Computes a field-level diff between two JSON payloads.
 *
 * Algorithm:
 * - Parse both JSON strings into Map<String, Object>
 * - Walk the union of all keys from both maps
 * - For each key, classify: ADDED / REMOVED / MODIFIED / UNCHANGED
 * - For nested objects (value is a Map), recurse with dot-notation path prefix
 * - Arrays are treated as atomic values — element-level array diffing
 *   is a future enhancement requiring LCS (longest common subsequence)
 *
 * No Spring annotations — pure logic, unit-testable with plain strings.
 */
public class JsonDiffEngine {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    /**
     * Compute diff between oldJson and newJson.
     * Pass empty string or null for oldJson on the first version (all fields → ADDED).
     */
    public List<FieldDiff> diff(String oldJson, String newJson) {
        Map<String, Object> oldMap = parseOrEmpty(oldJson);
        Map<String, Object> newMap = parseOrEmpty(newJson);

        List<FieldDiff> results = new ArrayList<>();
        diffMaps(oldMap, newMap, "", results);
        return results;
    }

    // ------------------------------------------------------------------
    // Core recursive diff
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private void diffMaps(Map<String, Object> oldMap,
                          Map<String, Object> newMap,
                          String pathPrefix,
                          List<FieldDiff> results) {

        // Union of all keys from both maps — ensures we catch ADDs and REMOVEs
        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(oldMap.keySet());
        allKeys.addAll(newMap.keySet());

        for (String key : allKeys) {
            String fullPath = pathPrefix.isEmpty() ? key : pathPrefix + "." + key;

            boolean inOld = oldMap.containsKey(key);
            boolean inNew = newMap.containsKey(key);

            if (!inOld && inNew) {
                // Field appeared in the new version
                results.add(new FieldDiff(fullPath, FieldDiff.DiffType.ADDED,
                        null, newMap.get(key)));
                continue;
            }

            if (inOld && !inNew) {
                // Field was removed in the new version
                results.add(new FieldDiff(fullPath, FieldDiff.DiffType.REMOVED,
                        oldMap.get(key), null));
                continue;
            }

            // Both maps have this key — compare values
            Object oldVal = oldMap.get(key);
            Object newVal = newMap.get(key);

            // If both values are nested objects, recurse rather than treating
            // the whole object as a single atomic value. This gives us
            // "address.city MODIFIED" instead of "address MODIFIED".
            if (oldVal instanceof Map && newVal instanceof Map) {
                diffMaps(
                        (Map<String, Object>) oldVal,
                        (Map<String, Object>) newVal,
                        fullPath,
                        results
                );
                continue;
            }

            // One or both are scalars or arrays — atomic comparison
            if (valuesEqual(oldVal, newVal)) {
                results.add(new FieldDiff(fullPath, FieldDiff.DiffType.UNCHANGED,
                        oldVal, newVal));
            } else {
                results.add(new FieldDiff(fullPath, FieldDiff.DiffType.MODIFIED,
                        oldVal, newVal));
            }
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Null-safe equality. Uses toString() for numeric type normalisation —
     * Jackson parses 42 as Integer and 42.0 as Double; toString() equates them.
     * Arrays are compared by their JSON serialisation for structural equality.
     */
    private boolean valuesEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;

        // For lists (JSON arrays), compare via serialisation to handle
        // nested structure equality without element-by-element LCS
        if (a instanceof List && b instanceof List) {
            return serialise(a).equals(serialise(b));
        }

        return a.toString().equals(b.toString());
    }

    private Map<String, Object> parseOrEmpty(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON payload: " + json, e);
        }
    }

    private String serialise(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialise value for comparison", e);
        }
    }
}