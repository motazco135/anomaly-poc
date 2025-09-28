package com.motaz.anomaly.repositories;


import com.motaz.anomaly.model.entities.ModelRegistryEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ModelRegistryRepository extends CrudRepository<ModelRegistryEntity, Long> {

    @Query("SELECT e FROM ModelRegistryEntity e ORDER BY e.createdAt DESC LIMIT 1")
    Optional<ModelRegistryEntity> findLatestIFModel();
}
