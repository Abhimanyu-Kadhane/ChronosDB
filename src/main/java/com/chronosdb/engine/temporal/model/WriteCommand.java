// src/main/java/com/chronosdb/engine/temporal/model/WriteCommand.java
package com.chronosdb.engine.temporal.model;

/**
 * Everything the engine needs to create a new version.
 * The engine produces the version_id, checksum, timestamps — not the caller.
 */
public record WriteCommand(
        String entityId,
        String entityType,
        String payload,             // raw JSON string
        String createdBy,
        String changeReason,
        String expectedVersionId    // null = first write; non-null = optimistic lock check (Phase 6)
) {}