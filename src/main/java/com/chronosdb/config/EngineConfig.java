// src/main/java/com/chronosdb/config/EngineConfig.java
package com.chronosdb.config;

import com.chronosdb.engine.conflict.ConflictEngine;
import com.chronosdb.engine.temporal.ChecksumService;
import com.chronosdb.engine.temporal.DagService;
import com.chronosdb.engine.temporal.TemporalEngine;
import com.chronosdb.storage.repository.VersionRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * All engine beans are constructed here — manually, not via component scan.
 * Engine classes have no Spring annotations. This keeps them framework-agnostic
 * and unit-testable with plain `new`.
 */
@Configuration
public class EngineConfig {

    @Bean
    public ChecksumService checksumService() {
        return new ChecksumService();
    }

    @Bean
    public DagService dagService(VersionRepository versionRepository) {
        return new DagService(versionRepository);
    }

    @Bean
    public ConflictEngine conflictEngine(VersionRepository versionRepository) {
        return new ConflictEngine(versionRepository);
    }

    // Update temporalEngine bean to include conflictEngine:
    @Bean
    public TemporalEngine temporalEngine(VersionRepository versionRepository,
                                         DagService dagService,
                                         ChecksumService checksumService,
                                         ConflictEngine conflictEngine) {
        return new TemporalEngine(versionRepository, dagService, checksumService, conflictEngine);
    }
}