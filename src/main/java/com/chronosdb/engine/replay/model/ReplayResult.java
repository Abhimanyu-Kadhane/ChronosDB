// src/main/java/com/chronosdb/engine/replay/model/ReplayResult.java
package com.chronosdb.engine.replay.model;

import java.time.Instant;
import java.util.List;

/**
 * Complete replay output for a time window.
 */
public record ReplayResult(
        String entityId,
        Instant from,
        Instant to,
        List<VersionTransition> transitions,    // ordered oldest → newest
        int totalVersions
) {}