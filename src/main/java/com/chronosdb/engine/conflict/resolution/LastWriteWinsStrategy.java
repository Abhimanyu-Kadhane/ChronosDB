// src/main/java/com/chronosdb/engine/conflict/resolution/LastWriteWinsStrategy.java
package com.chronosdb.engine.conflict.resolution;

import com.chronosdb.engine.conflict.model.ConflictResolution;
import com.chronosdb.storage.model.VersionRecord;
import com.chronosdb.storage.repository.VersionRepository;

import java.util.Comparator;
import java.util.Map;

/**
 * Resolution strategy: the sibling with the latest created_at timestamp wins.
 * All other sibling payloads are discarded.
 *
 * Use case: low-stakes fields where the most recent intent is always correct
 * (e.g. a UI preference, a non-critical status flag).
 *
 * Trade-off: simple and deterministic, but loses data from earlier siblings.
 * Any field updated only in an earlier sibling is silently dropped.
 */
public class LastWriteWinsStrategy implements ResolutionStrategy {

    private final VersionRepository versionRepository;

    public LastWriteWinsStrategy(VersionRepository versionRepository) {
        this.versionRepository = versionRepository;
    }

    @Override
    public String resolve(ConflictResolution resolution) {
        // Find the sibling version with the latest created_at
        return resolution.siblingPayloads().entrySet().stream()
                .max(Comparator.comparing(entry -> {
                    // Load the version to get its created_at timestamp
                    return versionRepository.findById(entry.getKey())
                            .map(VersionRecord::createdAt)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Sibling version not found: " + entry.getKey()));
                }))
                .map(Map.Entry::getValue)
                .orElseThrow(() -> new IllegalStateException(
                        "No sibling payloads in conflict resolution: " + resolution.conflictId()));
    }

    @Override
    public String name() {
        return "LastWriteWins";
    }
}