// src/main/java/com/chronosdb/engine/conflict/model/ConflictRecord.java
package com.chronosdb.engine.conflict.model;

import java.time.Instant;
import java.util.List;

/**
 * Represents a detected conflict — a set of sibling versions that
 * share the same parent_version_id and therefore represent divergent
 * writes from the same base state.
 *
 * This is an in-memory model. Conflict state is persisted via the
 * is_conflicted + conflict_id columns on entity_versions rows.
 * We do not have a separate conflicts table — the version rows ARE
 * the source of truth; this record is assembled on demand.
 */
public record ConflictRecord(
        String conflictId,          // shared UUID across all sibling versions
        String entityId,
        String parentVersionId,     // the common parent all siblings branched from
        List<String> siblingVersionIds,  // all version_ids in this conflict group
        Instant detectedAt
) {}