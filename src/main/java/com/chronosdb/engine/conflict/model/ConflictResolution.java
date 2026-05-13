// src/main/java/com/chronosdb/engine/conflict/model/ConflictResolution.java
package com.chronosdb.engine.conflict.model;

import java.util.Map;

/**
 * Instructions passed to a resolution strategy.
 * Contains the payloads of all siblings so the strategy can inspect
 * and merge field values.
 */
public record ConflictResolution(
        String conflictId,
        String entityId,
        String parentVersionId,
        Map<String, String> siblingPayloads,  // versionId → raw JSON payload
        String resolvedBy,
        String resolutionStrategy             // label: "LastWriteWins", "FieldMerge", etc.
) {}