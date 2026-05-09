// src/main/java/com/chronosdb/storage/repository/VersionEventRepository.java
package com.chronosdb.storage.repository;

import com.chronosdb.storage.model.VersionEventRecord;

import java.util.List;

public interface VersionEventRepository {

    void insert(VersionEventRecord record);

    List<VersionEventRecord> findByEntityId(String entityId);
}