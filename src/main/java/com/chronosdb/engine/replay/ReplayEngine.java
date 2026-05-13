// src/main/java/com/chronosdb/engine/replay/ReplayEngine.java
package com.chronosdb.engine.replay;

import com.chronosdb.engine.replay.model.FieldDiff;
import com.chronosdb.engine.replay.model.ReplayResult;
import com.chronosdb.engine.replay.model.VersionTransition;
import com.chronosdb.storage.model.VersionRecord;
import com.chronosdb.storage.repository.VersionRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Traverses the version chain between two timestamps and emits
 * a VersionTransition for each step, including field-level diffs.
 *
 * Traversal strategy:
 * - Query all versions where valid_from falls within [from, to], ordered ASC.
 * - This gives us the versions that STARTED within the window.
 * - We also need the version active at the START of the window (if any)
 *   as the baseline for the first diff — it may have started before `from`.
 *
 * Why valid_from ordering and not valid_to?
 * valid_from is when the version became active — it is the canonical event time.
 * valid_to is a derived closure time set by the NEXT write. Ordering by valid_from
 * gives us the sequence of writes in the order they were committed.
 *
 * No Spring annotations — pure logic.
 */
public class ReplayEngine {

    private final VersionRepository versionRepository;
    private final JsonDiffEngine diffEngine;

    public ReplayEngine(VersionRepository versionRepository, JsonDiffEngine diffEngine) {
        this.versionRepository = versionRepository;
        this.diffEngine = diffEngine;
    }

    /**
     * Replay entity state transitions between [from, to].
     *
     * @param entityId  entity to replay
     * @param from      start of window (inclusive)
     * @param to        end of window (inclusive)
     * @return ReplayResult containing all transitions with diffs
     */
    public ReplayResult replay(String entityId, Instant from, Instant to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "Replay window invalid: from=" + from + " is after to=" + to);
        }

        // Fetch the version active just before the window opens.
        // This is the baseline state — the "previous" for the first diff.
        // It may not appear in the transition list, but we need its payload.
        VersionRecord baseline = findBaseline(entityId, from);

        // Fetch all versions whose valid_from falls within [from, to]
        List<VersionRecord> windowVersions =
                versionRepository.findVersionsBetween(entityId, from, to);

        if (windowVersions.isEmpty() && baseline == null) {
            // Entity did not exist in or before this window
            return new ReplayResult(entityId, from, to, List.of(), 0);
        }

        List<VersionTransition> transitions = new ArrayList<>();
        String previousPayload = baseline != null ? baseline.payload() : null;
        String previousVersionId = baseline != null ? baseline.versionId() : null;

        for (VersionRecord version : windowVersions) {
            List<FieldDiff> diff = diffEngine.diff(previousPayload, version.payload());

            transitions.add(new VersionTransition(
                    version.versionId(),
                    previousVersionId,
                    version.validFrom(),
                    version.validTo(),
                    version.payload(),
                    diff,
                    version.createdBy(),
                    version.changeReason()
            ));

            previousPayload = version.payload();
            previousVersionId = version.versionId();
        }

        return new ReplayResult(entityId, from, to, transitions, transitions.size());
    }

    /**
     * Replay the full history of an entity from the very first version.
     */
    public ReplayResult replayAll(String entityId) {
        List<VersionRecord> all = versionRepository.findAllByEntityId(entityId);
        if (all.isEmpty()) {
            return new ReplayResult(entityId, Instant.EPOCH, Instant.now(), List.of(), 0);
        }

        Instant first = all.get(0).validFrom();
        Instant last  = all.get(all.size() - 1).validFrom();

        return replay(entityId, first, last);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Find the version active at the moment just before `from`.
     * This is the state the entity was in when the replay window opens —
     * our baseline for computing the first diff.
     *
     * We use findAtTime(from) rather than findAtTime(from - 1ns) because
     * findAtTime uses valid_from <= T, so a version that starts exactly
     * at `from` is included in the window itself, not the baseline.
     *
     * If no version existed before `from`, returns null (entity was created
     * within or after the window).
     */
    private VersionRecord findBaseline(String entityId, Instant from) {
        // Look for a version strictly before the window start
        // We subtract 1 microsecond to get "just before from"
        Instant justBefore = from.minusNanos(1000);

        return versionRepository.findAtTime(entityId, justBefore).orElse(null);
    }
}