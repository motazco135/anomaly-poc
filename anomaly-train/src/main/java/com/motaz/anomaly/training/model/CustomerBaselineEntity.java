package com.motaz.anomaly.training.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "t_customer_baseline_90d", schema = "public")
public class CustomerBaselineEntity {
    @Id
    @Column(name = "customer_id", nullable = false)
    private Long id;

    @Column(name = "n_tx", nullable = false)
    private Long nTx;

    @Column(name = "mean_amount", nullable = false)
    private Double meanAmount;

    @Column(name = "std_amount", nullable = false)
    private Double stdAmount;

    @Column(name = "median_amount", nullable = false)
    private Double medianAmount;

    @Column(name = "seg_mean_night", nullable = false)
    private Double segMeanNight;

    @Column(name = "seg_mean_morning", nullable = false)
    private Double segMeanMorning;

    @Column(name = "seg_mean_afternoon", nullable = false)
    private Double segMeanAfternoon;

    @Column(name = "seg_mean_evening", nullable = false)
    private Double segMeanEvening;

    @ColumnDefault("now()")
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    public void setUpdatedAt() {
        this.updatedAt = Instant.now();
    }

}