// src/main/java/com/chronosdb/config/EngineConfig.java
package com.chronosdb.config;

import com.chronosdb.engine.conflict.ConflictEngine;
import com.chronosdb.engine.conflict.ConflictResolver;
import com.chronosdb.engine.conflict.resolution.FieldMergeStrategy;
import com.chronosdb.engine.conflict.resolution.LastWriteWinsStrategy;
import com.chronosdb.engine.conflict.resolution.PriorityBasedStrategy;
import com.chronosdb.engine.conflict.resolution.ResolutionStrategyRegistry;
import com.chronosdb.engine.replay.JsonDiffEngine;
import com.chronosdb.engine.replay.ReplayEngine;
import com.chronosdb.engine.temporal.ChecksumService;
import com.chronosdb.engine.temporal.DagService;
import com.chronosdb.engine.temporal.TemporalEngine;
import com.chronosdb.storage.repository.VersionRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

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

    @Bean
    public ResolutionStrategyRegistry resolutionStrategyRegistry(
            VersionRepository versionRepository) {

        ResolutionStrategyRegistry registry = new ResolutionStrategyRegistry();

        registry.register(new LastWriteWinsStrategy(versionRepository));
        registry.register(new FieldMergeStrategy(versionRepository));
        registry.register(new PriorityBasedStrategy(
                List.of("system", "admin", "service-account", "user"),
                versionRepository));

        return registry;
    }

    @Bean
    public ConflictResolver conflictResolver(ConflictEngine conflictEngine,
                                             TemporalEngine temporalEngine,
                                             VersionRepository versionRepository,
                                             ResolutionStrategyRegistry registry) {
        return new ConflictResolver(conflictEngine, temporalEngine, versionRepository, registry);
    }


    @Bean
    public JsonDiffEngine jsonDiffEngine() {
        return new JsonDiffEngine();
    }

    @Bean
    public ReplayEngine replayEngine(VersionRepository versionRepository,
                                     JsonDiffEngine jsonDiffEngine) {
        return new ReplayEngine(versionRepository, jsonDiffEngine);
    }
}