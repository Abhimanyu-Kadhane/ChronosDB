// src/main/java/com/chronosdb/engine/conflict/resolution/PriorityBasedStrategy.java
package com.chronosdb.engine.conflict.resolution;

import com.chronosdb.engine.conflict.model.ConflictResolution;
import com.chronosdb.storage.model.VersionRecord;
import com.chronosdb.storage.repository.VersionRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Resolution strategy: the sibling written by the highest-priority author wins.
 *
 * Priority is defined by an ordered list of createdBy identifiers supplied
 * at construction time. Earlier position = higher priority.
 *
 * Example: ["system", "admin", "service-account", "user"]
 *   → a write by "system" always beats a write by "admin"
 *   → a write by "admin" beats "service-account"
 *
 * If no sibling matches any priority entry, falls back to LastWriteWins
 * to guarantee a deterministic outcome rather than throwing.
 *
 * Use case: multi-actor systems where writes from automated pipelines
 * (higher trust) should override writes from interactive users (lower trust).
 */
public class PriorityBasedStrategy implements ResolutionStrategy {

    private final List<String> priorityOrder;   // index 0 = highest priority
    private final VersionRepository versionRepository;

    public PriorityBasedStrategy(List<String> priorityOrder, VersionRepository versionRepository) {
        if (priorityOrder == null || priorityOrder.isEmpty()) {
            throw new IllegalArgumentException("priorityOrder must have at least one entry");
        }
        this.priorityOrder = List.copyOf(priorityOrder);
        this.versionRepository = versionRepository;
    }

    @Override
    public String resolve(ConflictResolution resolution) {
        // Load all sibling versions to access their createdBy field
        Map<String, String> payloads = resolution.siblingPayloads();

        // For each priority level (high to low), find the first sibling authored
        // by that actor. Return its payload immediately.
        for (String priorityActor : priorityOrder) {
            for (Map.Entry<String, String> entry : payloads.entrySet()) {
                String versionId = entry.getKey();
                VersionRecord version = versionRepository.findById(versionId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Sibling version not found: " + versionId));

                if (priorityActor.equals(version.createdBy())) {
                    return entry.getValue();
                }
            }
        }

        // No sibling matched any priority entry — fall back to latest created_at
        return payloads.entrySet().stream()
                .max(Comparator.comparing(entry ->
                        versionRepository.findById(entry.getKey())
                                .map(VersionRecord::createdAt)
                                .orElseThrow()))
                .map(Map.Entry::getValue)
                .orElseThrow(() -> new IllegalStateException(
                        "No sibling payloads in conflict: " + resolution.conflictId()));
    }

    @Override
    public String name() {
        return "PriorityBased";
    }
}