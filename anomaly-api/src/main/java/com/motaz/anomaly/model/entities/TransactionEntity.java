package com.motaz.anomaly.model.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "t_transactions",schema = "public")
public class TransactionEntity {

    @Id
    @Column(name = "id", columnDefinition = "serial",nullable = false)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "transactions_entity_seq_generator")
    @SequenceGenerator(name = "transactions_entity_seq_generator", sequenceName = "transactions_id_seq", allocationSize = 100)
    private Long id;

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

}