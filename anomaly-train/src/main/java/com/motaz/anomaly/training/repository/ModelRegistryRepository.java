package com.motaz.anomaly.training.repository;

import com.motaz.anomaly.training.model.ModelRegistryEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ModelRegistryRepository extends CrudRepository<ModelRegistryEntity, Long> {

    @Query("SELECT e FROM ModelRegistryEntity e ORDER BY e.createdAt DESC LIMIT 1")
    Optional<ModelRegistryEntity> findLatestIFModel();
}
