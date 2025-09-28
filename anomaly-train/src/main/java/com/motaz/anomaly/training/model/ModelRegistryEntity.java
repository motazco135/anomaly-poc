package com.motaz.anomaly.training.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "t_model_registry", schema = "public")
public class ModelRegistryEntity {

    @Id
    @Column(name = "model_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "model_registry_entity_seq_generator")
    @SequenceGenerator(name = "model_registry_entity_seq_generator", sequenceName = "model_registry_id_seq",allocationSize = 100)
    private Long id;

    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "trees", nullable = false)
    private Integer trees;

    @Column(name = "subsample", nullable = false)
    private Integer subsample;

    @Column(name = "feature_schema", nullable = false, length = Integer.MAX_VALUE)
    private String featureSchema;

    @Column(name = "schema_hash", nullable = false, length = 64)
    private String schemaHash;

    @Column(name = "trained_rows", nullable = false)
    private Long trainedRows;

    @Column(name = "notes", length = Integer.MAX_VALUE)
    private String notes;

    @Column(name = "model_bytes", nullable = false)
    private byte[] modelBytes;


    @PrePersist
    public void setCreatedAt() {
        this.createdAt = Instant.now();
    }

}