// src/main/java/com/chronosdb/storage/repository/JdbcVersionRepository.java
package com.chronosdb.storage.repository;

import com.chronosdb.storage.model.VersionRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcVersionRepository implements VersionRepository {

    private final JdbcTemplate jdbc;

    public JdbcVersionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------
    // Write operations
    // ------------------------------------------------------------------

    @Override
    public void insert(VersionRecord r) {
        jdbc.update("""
                INSERT INTO entity_versions (
                    version_id, entity_id, entity_type, parent_version_id,
                    valid_from, valid_to, payload, checksum,
                    created_at, created_by, change_reason,
                    is_conflicted, conflict_id, is_merged
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                r.versionId(),
                r.entityId(),
                r.entityType(),
                r.parentVersionId(),
                Timestamp.from(r.validFrom()),
                r.validTo() != null ? Timestamp.from(r.validTo()) : null,
                r.payload(),
                r.checksum(),
                Timestamp.from(r.createdAt()),
                r.createdBy(),
                r.changeReason(),
                r.isConflicted(),
                r.conflictId(),
                r.isMerged()
        );
    }

    @Override
    public void closeVersion(String versionId, Instant validTo) {
        // The ONLY UPDATE in the system. It closes an open time interval.
        // It does not touch payload, checksum, or any business field.
        int rows = jdbc.update(
                "UPDATE entity_versions SET valid_to = ? WHERE version_id = ? AND valid_to IS NULL",
                Timestamp.from(validTo),
                versionId
        );
        if (rows != 1) {
            throw new IllegalStateException(
                    "closeVersion affected " + rows + " rows for version_id=" + versionId +
                            ". Expected exactly 1. Version may already be closed or does not exist."
            );
        }
    }

    @Override
    public void markAsConflicted(List<String> versionIds, String conflictId) {
        for (String id : versionIds) {
            jdbc.update(
                    "UPDATE entity_versions SET is_conflicted = TRUE, conflict_id = ? WHERE version_id = ?",
                    conflictId, id
            );
        }
    }

    @Override
    public void markAsMerged(String versionId) {
        jdbc.update(
                "UPDATE entity_versions SET is_merged = TRUE WHERE version_id = ?",
                versionId
        );
    }

    // ------------------------------------------------------------------
    // Read operations
    // ------------------------------------------------------------------

    @Override
    public Optional<VersionRecord> findAtTime(String entityId, Instant at) {
        List<VersionRecord> results = jdbc.query("""
                SELECT * FROM entity_versions
                WHERE entity_id = ?
                  AND valid_from <= ?
                  AND (valid_to IS NULL OR valid_to > ?)
                ORDER BY valid_from DESC
                LIMIT 1
                """,
                ROW_MAPPER,
                entityId,
                Timestamp.from(at),
                Timestamp.from(at)
        );
        return results.stream().findFirst();
    }

    @Override
    public Optional<VersionRecord> findCurrentHead(String entityId) {
        List<VersionRecord> results = jdbc.query(
                "SELECT * FROM entity_versions WHERE entity_id = ? AND valid_to IS NULL",
                ROW_MAPPER,
                entityId
        );
        // In a healthy system there is exactly 0 or 1 head per entity.
        // More than 1 indicates a write protocol bug — surface it loudly.
        if (results.size() > 1) {
            throw new IllegalStateException(
                    "Entity " + entityId + " has " + results.size() + " open head versions. " +
                            "This indicates a write protocol failure."
            );
        }
        return results.stream().findFirst();
    }

    @Override
    public Optional<VersionRecord> findCurrentHeadForUpdate(String entityId) {
        // SELECT FOR UPDATE acquires a row-level lock within the calling transaction.
        // No other transaction can close or read-lock this row until we commit/rollback.
        List<VersionRecord> results = jdbc.query(
                "SELECT * FROM entity_versions WHERE entity_id = ? AND valid_to IS NULL FOR UPDATE",
                ROW_MAPPER,
                entityId
        );
        if (results.size() > 1) {
            throw new IllegalStateException(
                    "Entity " + entityId + " has " + results.size() + " open head versions under lock."
            );
        }
        return results.stream().findFirst();
    }

    @Override
    public List<VersionRecord> findVersionsBetween(String entityId, Instant from, Instant to) {
        return jdbc.query("""
                SELECT * FROM entity_versions
                WHERE entity_id = ?
                  AND valid_from >= ?
                  AND valid_from <= ?
                ORDER BY valid_from ASC
                """,
                ROW_MAPPER,
                entityId,
                Timestamp.from(from),
                Timestamp.from(to)
        );
    }

    @Override
    public List<VersionRecord> findChildren(String parentVersionId) {
        return jdbc.query(
                "SELECT * FROM entity_versions WHERE parent_version_id = ? ORDER BY valid_from ASC",
                ROW_MAPPER,
                parentVersionId
        );
    }

    @Override
    public List<VersionRecord> findAllByEntityId(String entityId) {
        return jdbc.query(
                "SELECT * FROM entity_versions WHERE entity_id = ? ORDER BY valid_from ASC",
                ROW_MAPPER,
                entityId
        );
    }

    @Override
    public Optional<VersionRecord> findById(String versionId) {
        List<VersionRecord> results = jdbc.query(
                "SELECT * FROM entity_versions WHERE version_id = ?",
                ROW_MAPPER,
                versionId
        );
        return results.stream().findFirst();
    }

    // ------------------------------------------------------------------
    // RowMapper
    // ------------------------------------------------------------------

    private static final RowMapper<VersionRecord> ROW_MAPPER = (rs, rowNum) -> mapRow(rs);

    private static VersionRecord mapRow(ResultSet rs) throws SQLException {
        return new VersionRecord(
                rs.getString("version_id"),
                rs.getString("entity_id"),
                rs.getString("entity_type"),
                rs.getString("parent_version_id"),          // may be null
                toInstant(rs, "valid_from"),
                toInstantNullable(rs, "valid_to"),          // null = current head
                rs.getString("payload"),
                rs.getString("checksum"),
                toInstant(rs, "created_at"),
                rs.getString("created_by"),
                rs.getString("change_reason"),
                rs.getBoolean("is_conflicted"),
                rs.getString("conflict_id"),                // may be null
                rs.getBoolean("is_merged")
        );
    }

    private static Instant toInstant(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        if (ts == null) {
            throw new IllegalStateException("Non-nullable column " + col + " was null");
        }
        return ts.toInstant();
    }

    private static Instant toInstantNullable(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts != null ? ts.toInstant() : null;
    }
}