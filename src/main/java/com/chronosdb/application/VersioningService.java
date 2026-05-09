// src/main/java/com/chronosdb/application/VersioningService.java
package com.chronosdb.application;

import com.chronosdb.engine.temporal.TemporalEngine;
import com.chronosdb.engine.temporal.model.WriteCommand;
import com.chronosdb.storage.model.VersionRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Application service — the only layer where @Transactional lives.
 * Orchestrates engine calls. Contains zero business logic itself.
 *
 * Isolation.REPEATABLE_READ: ensures that within our write transaction,
 * re-reads of the same row return the same data. This prevents a
 * second read from seeing a commit made by a concurrent transaction
 * between our SELECT FOR UPDATE and our INSERT.
 * MySQL's default (REPEATABLE_READ) already provides this, but we
 * declare it explicitly to make the contract visible and enforced.
 */
@Service
public class VersioningService {

    private final TemporalEngine temporalEngine;

    public VersioningService(TemporalEngine temporalEngine) {
        this.temporalEngine = temporalEngine;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public VersionRecord write(WriteCommand cmd) {
        return temporalEngine.write(cmd);
    }

    @Transactional(readOnly = true)
    public VersionRecord findAtTime(String entityId, Instant at) {
        return temporalEngine.findAtTime(entityId, at);
    }

    @Transactional(readOnly = true)
    public VersionRecord findCurrentHead(String entityId) {
        return temporalEngine.findCurrentHead(entityId);
    }

    @Transactional(readOnly = true)
    public List<VersionRecord> findVersionsBetween(String entityId, Instant from, Instant to) {
        return temporalEngine.findVersionsBetween(entityId, from, to);
    }

    @Transactional(readOnly = true)
    public List<VersionRecord> findAllVersions(String entityId) {
        return temporalEngine.findAllVersions(entityId);
    }
}