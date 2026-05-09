// src/main/java/com/chronosdb/storage/repository/JdbcSnapshotRepository.java
package com.chronosdb.storage.repository;

import com.chronosdb.storage.model.SnapshotRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcSnapshotRepository implements SnapshotRepository {

    private final JdbcTemplate jdbc;

    public JdbcSnapshotRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(SnapshotRecord r) {
        jdbc.update("""
                INSERT INTO snapshots (snapshot_id, entity_id, version_id, snapshot_time, state)
                VALUES (?, ?, ?, ?, ?)
                """,
                r.snapshotId(),
                r.entityId(),
                r.versionId(),
                Timestamp.from(r.snapshotTime()),
                r.state()
        );
    }

    @Override
    public Optional<SnapshotRecord> findLatestAtOrBefore(String entityId, Instant at) {
        List<SnapshotRecord> results = jdbc.query("""
                SELECT * FROM snapshots
                WHERE entity_id = ?
                  AND snapshot_time <= ?
                ORDER BY snapshot_time DESC
                LIMIT 1
                """,
                ROW_MAPPER,
                entityId,
                Timestamp.from(at)
        );
        return results.stream().findFirst();
    }

    private static final RowMapper<SnapshotRecord> ROW_MAPPER = (rs, rowNum) ->
            new SnapshotRecord(
                    rs.getString("snapshot_id"),
                    rs.getString("entity_id"),
                    rs.getString("version_id"),
                    rs.getTimestamp("snapshot_time").toInstant(),
                    rs.getString("state")
            );
}