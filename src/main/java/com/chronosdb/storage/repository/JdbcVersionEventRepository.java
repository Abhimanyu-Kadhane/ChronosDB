// src/main/java/com/chronosdb/storage/repository/JdbcVersionEventRepository.java
package com.chronosdb.storage.repository;

import com.chronosdb.storage.model.VersionEventRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class JdbcVersionEventRepository implements VersionEventRepository {

    private final JdbcTemplate jdbc;

    public JdbcVersionEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(VersionEventRecord r) {
        jdbc.update("""
                INSERT INTO version_events (event_id, entity_id, version_id, event_type, emitted_at, payload)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                r.eventId(),
                r.entityId(),
                r.versionId(),
                r.eventType(),
                Timestamp.from(r.emittedAt()),
                r.payload()
        );
    }

    @Override
    public List<VersionEventRecord> findByEntityId(String entityId) {
        return jdbc.query(
                "SELECT * FROM version_events WHERE entity_id = ? ORDER BY emitted_at ASC",
                (rs, rowNum) -> new VersionEventRecord(
                        rs.getString("event_id"),
                        rs.getString("entity_id"),
                        rs.getString("version_id"),
                        rs.getString("event_type"),
                        rs.getTimestamp("emitted_at").toInstant(),
                        rs.getString("payload")
                ),
                entityId
        );
    }
}