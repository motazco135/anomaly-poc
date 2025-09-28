package com.motaz.anomaly.model.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "t_anomaly_alert", schema = "public")
public class AnomalyAlertEntity {

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "anomaly_alert_entity_seq_generator")
    @SequenceGenerator(name = "anomaly_alert_entity_seq_generator", sequenceName = "anomaly_alert_id_seq", allocationSize = 100)
    private Long id;

    @Column(name = "txn_id", length = 64)
    private String txnId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 10)
    private String currencyCode;

    @Column(name = "channel", nullable = false, length = 16)
    private String channel;

    @Column(name = "ts_utc", nullable = false)
    private Instant tsUtc;

    @Column(name = "score", nullable = false, precision = 6, scale = 4)
    private BigDecimal score;

    @Column(name = "severity", nullable = false, length = 8)
    private String severity;

    @Column(name = "facts_json", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> factsJson;

    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    private void setCreatedAt() {
        this.createdAt = Instant.now();
    }

}