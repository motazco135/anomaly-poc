package com.motaz.anomaly.training.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "t_transaction_features", schema = "public")
public class TransactionFeatureEntity {

    @Id
    @Column(name = "id",nullable = false)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "transaction_features_entity_seq_generator")
    @SequenceGenerator(name = "transaction_features_entity_seq_generator", sequenceName = "transaction_features_id_seq", allocationSize = 100)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "txn_id")
    private TransactionEntity txn;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "ts_utc", nullable = false)
    private Instant tsUtc;

    @Column(name = "amount", nullable = false)
    private Double amount;

    @Column(name = "currency_code", nullable = false, length = 10)
    private String currencyCode;

    @Column(name = "amount_z_score", nullable = false)
    private Double amountZScore;

    @Column(name = "time_segment_ratio", nullable = false)
    private Double timeSegmentRatio;

    @Column(name = "velocity_ratio", nullable = false)
    private Double velocityRatio;

    @Column(name = "median_deviation", nullable = false)
    private Double medianDeviation;

    @Column(name = "baseline_n", nullable = false)
    private Long baselineN;

    @Column(name = "baseline_mean_amount", nullable = false)
    private Double baselineMeanAmount;

    @Column(name = "baseline_std_amount", nullable = false)
    private Double baselineStdAmount;

    @Column(name = "baseline_median_amount", nullable = false)
    private Double baselineMedianAmount;

    @Column(name = "baseline_seg_index", nullable = false)
    private Integer baselineSegIndex;

    @Column(name = "baseline_seg_mean", nullable = false)
    private Double baselineSegMean;

    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name="is_trainable")
    private Boolean isTrainable;

    @PrePersist
    public void setCreatedAt() {
        this.createdAt = Instant.now();
    }

}