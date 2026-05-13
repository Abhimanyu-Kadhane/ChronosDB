// src/main/java/com/chronosdb/engine/temporal/TemporalEngine.java
package com.chronosdb.engine.temporal;

import com.chronosdb.engine.conflict.ConflictEngine;
import com.chronosdb.engine.temporal.exception.EntityNotFoundException;
import com.chronosdb.engine.temporal.exception.StaleWriteException;
import com.chronosdb.engine.temporal.model.WriteCommand;
import com.chronosdb.storage.model.VersionRecord;
import com.chronosdb.storage.repository.VersionRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Core temporal engine. No Spring annotations.
 * All transactional boundaries are enforced by the Application Service layer above.
 * This class assumes it is always called within an active transaction.
 */
public class TemporalEngine {

    private final VersionRepository versionRepository;
    private final DagService dagService;
    private final ChecksumService checksumService;

    private final ConflictEngine conflictEngine;

    public TemporalEngine(VersionRepository versionRepository,
                          DagService dagService,
                          ChecksumService checksumService,
                          ConflictEngine conflictEngine) {
        this.versionRepository = versionRepository;
        this.dagService = dagService;
        this.checksumService = checksumService;
        this.conflictEngine = conflictEngine;
    }

    // ------------------------------------------------------------------
    // Write Protocol
    // ------------------------------------------------------------------

    /**
     * Atomic close-then-append write protocol.
     *
     * Step 1: SELECT FOR UPDATE on the current head row — acquires row lock.
     *         No concurrent transaction can proceed past this point for the same entity.
     * Step 2: Optimistic lock check — if caller supplied expectedVersionId,
     *         verify the locked head matches it. Reject if stale.
     * Step 3: Close the current head by stamping valid_to = now.
     * Step 4: Cycle check on the new parent link.
     * Step 5: Compute checksum over the new version's content.
     * Step 6: INSERT the new version with valid_from = same instant as the old valid_to.
     *
     * Why valid_from == old valid_to?
     * This creates a gapless, non-overlapping time interval model.
     * At any instant T, exactly one version satisfies:
     *   valid_from <= T AND (valid_to IS NULL OR valid_to > T)
     * If we used valid_from = now() independently, clock drift between
     * the close and the insert could create a gap where no version exists.
     */
    public VersionRecord write(WriteCommand cmd) {
        Instant now = Instant.now();
        String newVersionId = UUID.randomUUID().toString();

        // Step 1 — Lock current head
        Optional<VersionRecord> existingHead =
                versionRepository.findCurrentHeadForUpdate(cmd.entityId());

        // Step 2 — Optimistic lock check (skip if first write or no expectation given)
        if (cmd.expectedVersionId() != null) {
            String actualHeadId = existingHead
                    .map(VersionRecord::versionId)
                    .orElse(null);
            if (!cmd.expectedVersionId().equals(actualHeadId)) {
                throw new StaleWriteException(cmd.entityId(), cmd.expectedVersionId(), actualHeadId);
            }
        }

        // Step 3 — Close existing head
        String parentVersionId = null;
        if (existingHead.isPresent()) {
            versionRepository.closeVersion(existingHead.get().versionId(), now);
            parentVersionId = existingHead.get().versionId();
        }

        // Step 4 — Cycle guard (belt-and-suspenders; cannot form cycle in normal operation)
        dagService.assertNoCycle(newVersionId, parentVersionId);

        // Step 5 — Compute checksum
        String checksum = checksumService.compute(
                cmd.entityId(), cmd.payload(), now, parentVersionId);

        // Step 6 — Build and insert new version
        VersionRecord newVersion = new VersionRecord(
                newVersionId,
                cmd.entityId(),
                cmd.entityType(),
                parentVersionId,
                now,           // valid_from
                null,          // valid_to = null → this is the new head
                cmd.payload(),
                checksum,
                now,
                cmd.createdBy(),
                cmd.changeReason(),
                false,         // is_conflicted — set later by ConflictEngine if needed
                null,          // conflict_id
                false          // is_merged
        );

        versionRepository.insert(newVersion);
        conflictEngine.checkAndRegister(newVersion);

        return newVersion;
    }

    // ------------------------------------------------------------------
    // Time-Travel Query
    // ------------------------------------------------------------------

    /**
     * Resolve the entity's state at an arbitrary point in time T.
     *
     * The SQL in findAtTime uses interval containment:
     *   valid_from <= T AND (valid_to IS NULL OR valid_to > T)
     *
     * This handles all three cases:
     *   - Historical version: valid_from <= T < valid_to
     *   - Current head:       valid_from <= T AND valid_to IS NULL
     *   - Future query:       no row matches → EntityNotFoundException
     *
     * The composite index on (entity_id, valid_from, valid_to) makes this
     * a single index seek + range scan — O(log n) not O(n).
     */
    public VersionRecord findAtTime(String entityId, Instant at) {
        return versionRepository.findAtTime(entityId, at)
                .orElseThrow(() -> new EntityNotFoundException(entityId, at));
    }

    /**
     * Return the current head version of an entity.
     */
    public VersionRecord findCurrentHead(String entityId) {
        return versionRepository.findCurrentHead(entityId)
                .orElseThrow(() -> new EntityNotFoundException(entityId, "HEAD"));
    }

    /**
     * Return all versions of an entity in [from, to], ordered by valid_from ASC.
     * Used by replay engine and DAG export.
     */
    public List<VersionRecord> findVersionsBetween(String entityId, Instant from, Instant to) {
        return versionRepository.findVersionsBetween(entityId, from, to);
    }

    /**
     * Return all versions of an entity ever, ordered oldest first.
     */
    public List<VersionRecord> findAllVersions(String entityId) {
        return versionRepository.findAllByEntityId(entityId);
    }
}