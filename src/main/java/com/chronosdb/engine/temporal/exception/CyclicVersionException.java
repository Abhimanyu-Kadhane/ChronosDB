package com.chronosdb.engine.temporal.exception;

public class CyclicVersionException extends RuntimeException {
    public CyclicVersionException(String versionId) {
        super("Cycle detected in version DAG at version_id=" + versionId);
    }
}