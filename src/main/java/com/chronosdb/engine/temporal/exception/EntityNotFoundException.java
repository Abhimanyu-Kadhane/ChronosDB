package com.chronosdb.engine.temporal.exception;

public class EntityNotFoundException extends RuntimeException {
    public EntityNotFoundException(String entityId, Object at) {
        super("No version found for entity=" + entityId + " at=" + at);
    }
}