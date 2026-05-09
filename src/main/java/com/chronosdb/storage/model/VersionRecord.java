package com.chronosdb.storage.model;

import java.time.Instant;

/**
 * Exact in-memory mirror of one row in entity_versions.
 * Immutable by design — no setters. Engine always constructs new instances.
 */
public record VersionRecord(
        String versionId,
        String entityId,
        String entityType,
        String parentVersionId,      // null = genesis version
        Instant validFrom,
        Instant validTo,             // null = current head
        String payload,              // raw JSON string
        String checksum,             // SHA-256 hex
        Instant createdAt,
        String createdBy,
        String changeReason,
        boolean isConflicted,
        String conflictId,           // null unless this version is part of a conflict group
        boolean isMerged
) {}