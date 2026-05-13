// src/main/java/com/chronosdb/engine/conflict/resolution/FieldMergeStrategy.java
package com.chronosdb.engine.conflict.resolution;

import com.chronosdb.engine.conflict.model.ConflictResolution;
import com.chronosdb.storage.model.VersionRecord;
import com.chronosdb.storage.repository.VersionRepository;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolution strategy: merge at the JSON field level.
 *
 * Algorithm:
 * 1. Start with the parent payload as the base (all fields at their last
 *    agreed-upon value before the branch).
 * 2. For each sibling, ordered oldest-first by created_at:
 *    - For every field in the sibling payload:
 *      - If the field was NOT changed from the parent value → skip it
 *        (this sibling didn't intend to change this field).
 *      - If the field WAS changed from the parent value → apply it
 *        (this sibling has an intentional update to this field).
 * 3. When two siblings both changed the same field, the later sibling wins
 *    for that specific field (LWW at field granularity, not document granularity).
 *
 * This preserves independent changes across siblings. If sibling A updated
 * "name" and sibling B updated "status", the merge retains both updates.
 * Only fields changed by multiple siblings need a tiebreak.
 *
 * Limitation: does not handle nested objects recursively — nested objects
 * are treated as atomic values. Deep merge would require recursive traversal
 * and is a Phase 5+ enhancement.
 */
public class FieldMergeStrategy implements ResolutionStrategy {

    private final VersionRepository versionRepository;

    public FieldMergeStrategy(VersionRepository versionRepository) {
        this.versionRepository = versionRepository;
    }

    @Override
    public String resolve(ConflictResolution resolution) {
        // Load parent payload as merge base
        Map<String, Object> base = versionRepository
                .findById(resolution.parentVersionId())
                .map(v -> PayloadMergeUtil.parse(v.payload()))
                .orElseThrow(() -> new IllegalStateException(
                        "Parent version not found: " + resolution.parentVersionId()));

        // Start merged state from the base
        Map<String, Object> merged = new LinkedHashMap<>(base);

        // Sort siblings oldest-first so later siblings win field-level conflicts
        resolution.siblingPayloads().entrySet().stream()
                .sorted(Comparator.comparing(entry ->
                        versionRepository.findById(entry.getKey())
                                .map(VersionRecord::createdAt)
                                .orElseThrow()))
                .forEach(entry -> {
                    Map<String, Object> siblingFields = PayloadMergeUtil.parse(entry.getValue());

                    siblingFields.forEach((field, siblingValue) -> {
                        Object baseValue = base.get(field);

                        boolean siblingChangedThisField = !objectEquals(siblingValue, baseValue);

                        if (siblingChangedThisField) {
                            // This sibling intentionally updated this field — apply it.
                            // If a later sibling also changes this field, it will overwrite
                            // in a subsequent iteration (LWW at field level).
                            merged.put(field, siblingValue);
                        }
                        // If unchanged from base, leave merged value as-is
                    });
                });

        return PayloadMergeUtil.serialise(merged);
    }

    @Override
    public String name() {
        return "FieldMerge";
    }

    /**
     * Null-safe structural equality for JSON-parsed values.
     * Jackson parses numbers as Integer/Long/Double depending on magnitude —
     * we use toString() comparison to avoid type mismatch false-negatives.
     */
    private boolean objectEquals(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.toString().equals(b.toString());
    }
}