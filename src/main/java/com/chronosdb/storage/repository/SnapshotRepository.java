// src/main/java/com/chronosdb/storage/repository/SnapshotRepository.java
package com.chronosdb.storage.repository;

import com.chronosdb.storage.model.SnapshotRecord;

import java.time.Instant;
import java.util.Optional;

public interface SnapshotRepository {

    void insert(SnapshotRecord record);

    /**
     * Find the most recent snapshot at or before time T.
     * The snapshot engine replays forward from this point instead of from V1.
     */
    Optional<SnapshotRecord> findLatestAtOrBefore(String entityId, Instant at);
}