// src/main/java/com/chronosdb/application/VersioningService.java
package com.chronosdb.application;

import com.chronosdb.engine.conflict.exception.ConflictDetectedException;
import com.chronosdb.engine.conflict.model.ConflictRecord;
import com.chronosdb.engine.conflict.ConflictEngine;
import com.chronosdb.engine.temporal.TemporalEngine;
import com.chronosdb.engine.temporal.model.WriteCommand;
import com.chronosdb.storage.model.VersionRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class VersioningService {

    private final TemporalEngine temporalEngine;
    private final ConflictEngine conflictEngine;

    public VersioningService(TemporalEngine temporalEngine, ConflictEngine conflictEngine) {
        this.temporalEngine = temporalEngine;
        this.conflictEngine = conflictEngine;
    }

    /**
     * Write result — wraps either a clean version or a conflict notification.
     * The version is always present; the conflict is present only when branching occurred.
     */
    public record WriteResult(
            VersionRecord version,
            String conflictId,      // null = clean write
            boolean hasConflict
    ) {
        static WriteResult clean(VersionRecord v) {
            return new WriteResult(v, null, false);
        }
        static WriteResult conflicted(VersionRecord v, String conflictId) {
            return new WriteResult(v, conflictId, true);
        }
    }

    /**
     * Write with conflict awareness.
     * The transaction COMMITS even on conflict — the version and the conflict
     * flags are both durable. The caller receives a WriteResult describing
     * what happened rather than an exception propagating to the HTTP layer.
     *
     * noRollbackFor = ConflictDetectedException: Spring's default behaviour
     * rolls back on any RuntimeException. We explicitly override this because
     * a conflict is a valid business outcome, not an error requiring rollback.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ,
            noRollbackFor = ConflictDetectedException.class)
    public WriteResult write(WriteCommand cmd) {
        try {
            VersionRecord version = temporalEngine.write(cmd);
            return WriteResult.clean(version);
        } catch (ConflictDetectedException ex) {
            // Version was inserted and conflict flags were set — all within
            // this transaction. noRollbackFor ensures this commits.
            return WriteResult.conflicted(
                    temporalEngine.findCurrentHead(cmd.entityId()),
                    ex.getConflictId()
            );
        }
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

    @Transactional(readOnly = true)
    public ConflictRecord loadConflict(String conflictId) {
        return conflictEngine.loadConflict(conflictId);
    }
}