// src/main/java/com/chronosdb/engine/conflict/exception/ConflictDetectedException.java
package com.chronosdb.engine.conflict.exception;

public class ConflictDetectedException extends RuntimeException {

    private final String conflictId;
    private final String entityId;

    public ConflictDetectedException(String conflictId, String entityId, String parentVersionId) {
        super("Conflict detected for entity=" + entityId +
                " at parent=" + parentVersionId +
                ". Assigned conflict_id=" + conflictId +
                ". Resolve before further writes.");
        this.conflictId = conflictId;
        this.entityId = entityId;
    }

    public String getConflictId() { return conflictId; }
    public String getEntityId()   { return entityId; }
}