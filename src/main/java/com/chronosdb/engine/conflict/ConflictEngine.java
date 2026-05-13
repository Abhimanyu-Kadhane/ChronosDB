// src/main/java/com/chronosdb/engine/conflict/ConflictEngine.java
package com.chronosdb.engine.conflict;

import com.chronosdb.engine.conflict.exception.ConflictDetectedException;
import com.chronosdb.engine.conflict.model.ConflictRecord;
import com.chronosdb.storage.model.VersionRecord;
import com.chronosdb.storage.repository.VersionRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Detects and registers conflicts.
 * No Spring annotations — pure logic.
 *
 * Branching model:
 * A conflict exists when two or more versions share the same parent_version_id
 * for the same entity. Visually:
 *
 *        [V1]
 *       /    \
 *    [V2a]  [V2b]   ← siblings — both wrote from V1 as parent
 *
 * V2a arrived first and closed V1 normally.
 * V2b arrived concurrently — the write protocol's SELECT FOR UPDATE
 * serialised the DB writes, so V2b was inserted AFTER V2a closed V1.
 * But V2b still declares V1 as its parent (from the client's perspective,
 * they both branched from V1).
 *
 * How does this happen if SELECT FOR UPDATE serialises writes?
 * Two scenarios:
 *
 * 1. The client explicitly sends parent_version_id = V1 on both requests
 *    (optimistic locking disabled). The engine inserts both, detects
 *    the sibling on the second insert's post-write check.
 *
 * 2. Optimistic locking is enabled but the client sends expectedVersionId = V1
 *    on both — one wins, one is rejected via StaleWriteException before reaching
 *    conflict detection. This is the preferred mode.
 *
 * Without optimistic locking, both writes succeed and the conflict engine
 * is the safety net that ensures the branching is recorded rather than lost.
 */
public class ConflictEngine {

    private final VersionRepository versionRepository;

    public ConflictEngine(VersionRepository versionRepository) {
        this.versionRepository = versionRepository;
    }

    /**
     * Called immediately after a successful version insert.
     * Checks whether the newly written version has siblings (other versions
     * sharing the same parent). If siblings exist, registers the conflict.
     *
     * @param newVersion the version that was just inserted
     * @throws ConflictDetectedException if siblings were found and conflict was registered
     */
    public void checkAndRegister(VersionRecord newVersion) {
        String parentId = newVersion.parentVersionId();
        if (parentId == null) {
            // Genesis version — no parent, no possible siblings
            return;
        }

        // Find all versions that share this parent (siblings of newVersion)
        List<VersionRecord> children = versionRepository.findChildren(parentId);

        // Filter to same entity — paranoia check; parent_version_id is globally unique
        // (UUID), so cross-entity collision is impossible, but we make the intent explicit
        List<VersionRecord> siblings = children.stream()
                .filter(v -> v.entityId().equals(newVersion.entityId()))
                .filter(v -> !v.versionId().equals(newVersion.versionId()))
                .toList();

        if (siblings.isEmpty()) {
            // No siblings — clean linear write, nothing to do
            return;
        }

        // Determine if a conflict_id already exists for this parent
        // (a third sibling arriving after the first conflict was registered)
        String conflictId = siblings.stream()
                .map(VersionRecord::conflictId)
                .filter(id -> id != null)
                .findFirst()
                .orElse(UUID.randomUUID().toString());

        // Collect all version IDs in this conflict group, including the new one
        List<String> allConflictedIds = new java.util.ArrayList<>();
        allConflictedIds.add(newVersion.versionId());
        siblings.stream()
                .filter(v -> v.conflictId() == null) // only mark those not yet marked
                .map(VersionRecord::versionId)
                .forEach(allConflictedIds::add);

        // Persist the conflict flags
        versionRepository.markAsConflicted(allConflictedIds, conflictId);

        throw new ConflictDetectedException(conflictId, newVersion.entityId(), parentId);
    }

    /**
     * Reconstruct a ConflictRecord for a known conflict_id.
     * Used by resolution strategies and the API layer.
     */
    public ConflictRecord loadConflict(String conflictId) {
        // Find all versions bearing this conflict_id
        // We query via findChildren on the parent — but we need the parent first.
        // Use findAllByEntityId is too broad; instead we rely on the conflict_id index.
        List<VersionRecord> conflicted = versionRepository
                .findByConflictId(conflictId);  // new method — added below

        if (conflicted.isEmpty()) {
            throw new IllegalArgumentException("No versions found for conflict_id=" + conflictId);
        }

        // All siblings share the same parent and entity
        String parentVersionId = conflicted.get(0).parentVersionId();
        String entityId        = conflicted.get(0).entityId();

        List<String> siblingIds = conflicted.stream()
                .map(VersionRecord::versionId)
                .toList();

        return new ConflictRecord(
                conflictId,
                entityId,
                parentVersionId,
                siblingIds,
                conflicted.stream()
                        .map(VersionRecord::createdAt)
                        .min(Instant::compareTo)
                        .orElse(Instant.now())
        );
    }
}