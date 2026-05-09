package com.chronosdb.engine.temporal.exception;

public class StaleWriteException extends RuntimeException {
    public StaleWriteException(String entityId, String expectedVersionId, String actualVersionId) {
        super("Stale write rejected for entity=" + entityId +
                ". Expected head=" + expectedVersionId +
                " but found=" + actualVersionId);
    }
}