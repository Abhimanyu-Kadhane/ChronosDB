package com.chronosdb.storage.model;

import java.time.Instant;

public record SnapshotRecord(
        String snapshotId,
        String entityId,
        String versionId,        // the version this snapshot was taken at
        Instant snapshotTime,
        String state             // raw JSON string — full entity state at this point
) {}