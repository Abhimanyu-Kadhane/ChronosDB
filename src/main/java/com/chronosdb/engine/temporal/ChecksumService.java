// src/main/java/com/chronosdb/engine/temporal/ChecksumService.java
package com.chronosdb.engine.temporal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

/**
 * Produces and verifies SHA-256 checksums over version content.
 * Input: entity_id + payload + valid_from + parent_version_id
 * This set is chosen because it covers both identity (entity_id),
 * content (payload), temporal position (valid_from), and lineage (parent).
 * Changing any of these changes the checksum — tamper detection catches all four.
 */
public class ChecksumService {

    public String compute(String entityId, String payload, Instant validFrom, String parentVersionId) {
        String input = entityId
                + "|" + payload
                + "|" + validFrom.toString()
                + "|" + (parentVersionId != null ? parentVersionId : "ROOT");
        return sha256Hex(input);
    }

    public boolean verify(String entityId, String payload, Instant validFrom,
                          String parentVersionId, String expectedChecksum) {
        return compute(entityId, payload, validFrom, parentVersionId).equals(expectedChecksum);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the Java spec — this cannot happen
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}