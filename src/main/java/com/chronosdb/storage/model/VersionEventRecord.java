package com.chronosdb.storage.model;

import java.time.Instant;

public record VersionEventRecord(
        String eventId,
        String entityId,
        String versionId,
        String eventType,        // e.g. VERSION_CREATED, CONFLICT_DETECTED, VERSION_MERGED
        Instant emittedAt,
        String payload           // raw JSON — event-specific data
) {}