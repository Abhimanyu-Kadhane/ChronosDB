// src/main/java/com/chronosdb/engine/conflict/resolution/PayloadMergeUtil.java
package com.chronosdb.engine.conflict.resolution;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

/**
 * Utility for parsing and serialising JSON payloads within resolution strategies.
 * ObjectMapper is thread-safe after construction — safe to share as a static instance.
 */
class PayloadMergeUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<>() {};

    static Map<String, Object> parse(String json) {
        try {
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse payload as JSON: " + json, e);
        }
    }

    static String serialise(Map<String, Object> map) {
        try {
            return MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialise merged payload", e);
        }
    }

    static ObjectNode emptyNode() {
        return MAPPER.createObjectNode();
    }

    private PayloadMergeUtil() {}
}