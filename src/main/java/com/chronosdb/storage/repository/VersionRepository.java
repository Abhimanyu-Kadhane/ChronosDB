// src/main/java/com/chronosdb/storage/repository/VersionRepository.java
package com.chronosdb.storage.repository;

import com.chronosdb.storage.model.VersionRecord;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VersionRepository {

    /**
     * Append a new version row. Must never issue UPDATE or DELETE.
     */
    void insert(VersionRecord record);

    /**
     * Close the current head version by setting its valid_to.
     * This is the ONLY mutation allowed — closing an open interval.
     * It does not modify payload, checksum, or any business data.
     */
    void closeVersion(String versionId, Instant validTo);

    /**
     * Resolve entity state at time T.
     * Returns the version where valid_from <= T AND (valid_to IS NULL OR valid_to > T).
     */
    Optional<VersionRecord> findAtTime(String entityId, Instant at);

    /**
     * Fetch the current head version (valid_to IS NULL).
     * Used by the write protocol to identify what needs closing.
     */
    Optional<VersionRecord> findCurrentHead(String entityId);

    /**
     * All versions for an entity within [from, to], ordered by valid_from ASC.
     * Used by the replay engine.
     */
    List<VersionRecord> findVersionsBetween(String entityId, Instant from, Instant to);

    /**
     * All direct children of a given version (same parent_version_id).
     * Used for sibling detection in conflict engine.
     */
    List<VersionRecord> findChildren(String parentVersionId);

    /**
     * Full version history for an entity, oldest first.
     * Used for DAG export and integrity verification.
     */
    List<VersionRecord> findAllByEntityId(String entityId);

    /**
     * Lock the current head row for update within a transaction.
     * Returns the locked row. Used in the write protocol to prevent lost updates.
     */
    Optional<VersionRecord> findCurrentHeadForUpdate(String entityId);

    /**
     * Mark a set of versions as conflicted and assign them a shared conflict_id.
     */
    void markAsConflicted(List<String> versionIds, String conflictId);

    /**
     * Mark a version as merged.
     */
    void markAsMerged(String versionId);

    /**
     * Fetch a version by its exact ID.
     */
    Optional<VersionRecord> findById(String versionId);


    /**
     * Find all versions that share a conflict_id.
     * Backed by idx_conflict index.
     */
    List<VersionRecord> findByConflictId(String conflictId);
}