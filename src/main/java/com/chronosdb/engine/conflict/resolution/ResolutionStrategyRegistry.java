// src/main/java/com/chronosdb/engine/conflict/resolution/ResolutionStrategyRegistry.java
package com.chronosdb.engine.conflict.resolution;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry that maps strategy names to strategy instances.
 * Populated at startup in EngineConfig.
 *
 * Why a registry instead of Spring @Qualifier injection?
 * The strategy is selected at RUNTIME based on the client's request —
 * we don't know at wiring time which strategy a caller will choose.
 * A registry lookup by name gives us runtime polymorphism cleanly.
 */
public class ResolutionStrategyRegistry {

    private final Map<String, ResolutionStrategy> strategies = new HashMap<>();

    public void register(ResolutionStrategy strategy) {
        strategies.put(strategy.name(), strategy);
    }

    public ResolutionStrategy get(String name) {
        ResolutionStrategy strategy = strategies.get(name);
        if (strategy == null) {
            throw new IllegalArgumentException(
                    "Unknown resolution strategy: '" + name + "'. " +
                            "Available: " + strategies.keySet());
        }
        return strategy;
    }
}