// src/main/java/com/chronosdb/engine/temporal/DagService.java
package com.chronosdb.engine.temporal;

import com.chronosdb.engine.temporal.exception.CyclicVersionException;
import com.chronosdb.storage.model.VersionRecord;
import com.chronosdb.storage.repository.VersionRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates and traverses the version DAG.
 * No Spring annotations — pure logic, fully unit-testable without a container.
 */
public class DagService {

    private final VersionRepository versionRepository;

    public DagService(VersionRepository versionRepository) {
        this.versionRepository = versionRepository;
    }

    /**
     * Verify that inserting a new version with the given parentVersionId
     * would not introduce a cycle. Called before every write.
     *
     * Strategy: walk UP the ancestor chain from parentVersionId.
     * If we ever encounter newVersionId, a cycle would be formed.
     * In practice, newVersionId is brand-new and cannot appear in any
     * existing ancestor chain — this guard exists to catch bugs where
     * the engine accidentally reuses a version_id.
     */
    public void assertNoCycle(String newVersionId, String parentVersionId) {
        if (parentVersionId == null) return; // genesis node — no parent to traverse

        Set<String> visited = new HashSet<>();
        String cursor = parentVersionId;

        while (cursor != null) {
            if (!visited.add(cursor)) {
                // visited.add returns false when element already present
                throw new CyclicVersionException(cursor);
            }
            if (cursor.equals(newVersionId)) {
                throw new CyclicVersionException(newVersionId);
            }

            String finalCursor = cursor;
            cursor = versionRepository.findById(cursor)
                    .map(VersionRecord::parentVersionId)
                    .orElseThrow(() -> new IllegalStateException(
                            "DAG integrity broken: version " + finalCursor + " references " +
                                    "non-existent parent. Chain is corrupt."
                    ));
        }
    }

    /**
     * Return the full ancestor chain from a given version back to genesis,
     * ordered from the given version UP to root. Used by replay engine.
     */
    public List<String> ancestorChain(String versionId) {
        java.util.ArrayList<String> chain = new java.util.ArrayList<>();
        Set<String> visited = new HashSet<>();
        String cursor = versionId;

        while (cursor != null) {
            if (!visited.add(cursor)) {
                throw new CyclicVersionException(cursor);
            }
            chain.add(cursor);
            String finalCursor = cursor;
            cursor = versionRepository.findById(cursor)
                    .map(VersionRecord::parentVersionId)
                    .orElseThrow(() -> new IllegalStateException(
                            "DAG broken at version " + finalCursor
                    ));
            // cursor is now the parentVersionId — null means we've reached genesis
        }

        return chain;
    }
}