// src/main/java/com/chronosdb/engine/conflict/ConflictResolver.java
package com.chronosdb.engine.conflict;

import com.chronosdb.engine.conflict.model.ConflictRecord;
import com.chronosdb.engine.conflict.model.ConflictResolution;
import com.chronosdb.engine.conflict.resolution.ResolutionStrategyRegistry;
import com.chronosdb.engine.temporal.TemporalEngine;
import com.chronosdb.engine.temporal.model.WriteCommand;
import com.chronosdb.storage.model.VersionRecord;
import com.chronosdb.storage.repository.VersionRepository;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Orchestrates conflict resolution end-to-end:
 * 1. Load all sibling payloads for the conflict group.
 * 2. Delegate to the selected strategy to produce a merged payload.
 * 3. Write the merged payload as a new version via TemporalEngine.
 * 4. Mark all sibling versions as merged.
 *
 * The resolved version's parent is the COMMON PARENT of all siblings —
 * not any one sibling. This keeps the DAG semantically correct:
 * the merge node represents "we reconciled the branch that started at parent P."
 *
 *        [parent P]
 *       /    \
 *    [V2a]  [V2b]   ← both is_conflicted = true, is_merged = true after resolution
 *       \    /
 *       [V3]         ← merged version, parent = P, is_conflicted = false
 *
 * Why parent = P and not V2a or V2b?
 * Either choice would make the merge version a linear successor of one sibling,
 * implying it "extends" that branch. The merge actually supersedes both branches
 * from their common origin. Using P as parent reflects this semantically.
 */
public class ConflictResolver {

    private final ConflictEngine conflictEngine;
    private final TemporalEngine temporalEngine;
    private final VersionRepository versionRepository;
    private final ResolutionStrategyRegistry registry;

    public ConflictResolver(ConflictEngine conflictEngine,
                            TemporalEngine temporalEngine,
                            VersionRepository versionRepository,
                            ResolutionStrategyRegistry registry) {
        this.conflictEngine = conflictEngine;
        this.temporalEngine = temporalEngine;
        this.versionRepository = versionRepository;
        this.registry = registry;
    }

    /**
     * Resolve a conflict group using the named strategy.
     *
     * @param conflictId       the conflict group to resolve
     * @param strategyName     "LastWriteWins" | "FieldMerge" | "PriorityBased"
     * @param resolvedBy       actor performing the resolution (for audit)
     * @param entityType       required for the WriteCommand
     * @return the newly written merged VersionRecord
     */
    public VersionRecord resolve(String conflictId,
                                 String strategyName,
                                 String resolvedBy,
                                 String entityType) {

        // Step 1 — Load conflict metadata
        ConflictRecord conflict = conflictEngine.loadConflict(conflictId);

        // Step 2 — Load sibling payloads: versionId → raw JSON
        Map<String, String> siblingPayloads = new LinkedHashMap<>();
        for (String versionId : conflict.siblingVersionIds()) {
            versionRepository.findById(versionId).ifPresent(v ->
                    siblingPayloads.put(versionId, v.payload()));
        }

        // Step 3 — Build resolution context and delegate to strategy
        ConflictResolution resolution = new ConflictResolution(
                conflictId,
                conflict.entityId(),
                conflict.parentVersionId(),
                siblingPayloads,
                resolvedBy,
                strategyName
        );

        String mergedPayload = registry.get(strategyName).resolve(resolution);

        // Step 4 — Write merged version.
        // expectedVersionId = null: we bypass optimistic lock check here because
        // the sibling versions are conflicted — there is no single clean head.
        // The write protocol's SELECT FOR UPDATE still serialises concurrent resolvers.
        WriteCommand mergeCmd = new WriteCommand(
                conflict.entityId(),
                entityType,
                mergedPayload,
                resolvedBy,
                "Conflict resolved via strategy=" + strategyName +
                        " for conflict_id=" + conflictId,
                null
        );

        VersionRecord mergedVersion = temporalEngine.write(mergeCmd);

        // Step 5 — Mark all siblings as merged
        for (String siblingId : conflict.siblingVersionIds()) {
            versionRepository.markAsMerged(siblingId);
        }

        return mergedVersion;
    }
}