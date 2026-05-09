-- ============================================================
-- ChronosDB Initial Schema
-- Immutable append-only. No UPDATE or DELETE on version data.
-- ============================================================

CREATE TABLE entity_versions (
                                 version_id        CHAR(36)      NOT NULL,
                                 entity_id         CHAR(36)      NOT NULL,
                                 entity_type       VARCHAR(64)   NOT NULL,
                                 parent_version_id CHAR(36)      NULL,
                                 valid_from        DATETIME(6)   NOT NULL,
                                 valid_to          DATETIME(6)   NULL,
                                 payload           JSON          NOT NULL,
                                 checksum          CHAR(64)      NOT NULL,
                                 created_at        DATETIME(6)   NOT NULL,
                                 created_by        VARCHAR(64)   NOT NULL,
                                 change_reason     VARCHAR(255)  NULL,
                                 is_conflicted     BOOLEAN       NOT NULL DEFAULT FALSE,
                                 conflict_id       CHAR(36)      NULL,
                                 is_merged         BOOLEAN       NOT NULL DEFAULT FALSE,

                                 PRIMARY KEY (version_id),
                                 INDEX idx_entity_time  (entity_id, valid_from, valid_to),
                                 INDEX idx_entity_type  (entity_type, valid_from),
                                 INDEX idx_parent       (parent_version_id),
                                 INDEX idx_conflict     (conflict_id)
);

CREATE TABLE snapshots (
                           snapshot_id   CHAR(36)     NOT NULL,
                           entity_id     CHAR(36)     NOT NULL,
                           version_id    CHAR(36)     NOT NULL,
                           snapshot_time DATETIME(6)  NOT NULL,
                           state         JSON         NOT NULL,
                           PRIMARY KEY (snapshot_id),
                           INDEX idx_snapshot_entity_time (entity_id, snapshot_time)
);

CREATE TABLE version_events (
                                event_id      CHAR(36)     NOT NULL,
                                entity_id     CHAR(36)     NOT NULL,
                                version_id    CHAR(36)     NOT NULL,
                                event_type    VARCHAR(64)  NOT NULL,
                                emitted_at    DATETIME(6)  NOT NULL,
                                payload       JSON         NOT NULL,
                                PRIMARY KEY (event_id),
                                INDEX idx_event_entity (entity_id, emitted_at)
);