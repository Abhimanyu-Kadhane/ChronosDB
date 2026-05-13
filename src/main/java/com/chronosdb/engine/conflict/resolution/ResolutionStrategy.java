package com.chronosdb.engine.conflict.resolution;

import com.chronosdb.engine.conflict.model.ConflictResolution;

/**
 * Contract for all conflict resolution strategies.
 *
 * A strategy receives the full conflict context — all sibling payloads,
 * the common parent, the entity — and returns a single merged JSON payload
 * string. The engine writes this as a new version.
 *
 * Strategies are stateless. They may be singletons.
 * They must not write to the database directly — they return a payload
 * and the ConflictResolver handles the write.
 */
public interface ResolutionStrategy {

    /**
     * Produce a merged payload from the sibling payloads in the resolution context.
     *
     * @param resolution full conflict context including all sibling payloads
     * @return raw JSON string representing the resolved state
     */
    String resolve(ConflictResolution resolution);

    /**
     * Canonical name used for routing and audit trail.
     */
    String name();
}