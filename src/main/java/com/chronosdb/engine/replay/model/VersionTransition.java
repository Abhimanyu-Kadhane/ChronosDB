// src/main/java/com/chronosdb/engine/replay/model/VersionTransition.java
package com.chronosdb.engine.replay.model;

import java.time.Instant;
import java.util.List;

/**
 * Represents a single step in the entity's version history.
 * Contains the full state at that version plus the diff from the previous state.
 *
 * For the first version in a replay window, fromVersionId is null
 * and all fields are ADDED (nothing existed before).
 */
public record VersionTransition(
        String versionId,
        String fromVersionId,       // null for the first version in the window
        Instant validFrom,
        Instant validTo,            // null if this is the current head
        String payload,             // full JSON state at this version
        List<FieldDiff> diff,       // field-level changes from previous state
        String createdBy,
        String changeReason
) {}