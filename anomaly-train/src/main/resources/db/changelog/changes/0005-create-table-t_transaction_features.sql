-- Per-transaction feature record (one row per txn). Baseline* columns capture the
-- PRIOR baseline used for that txn's features (so it's fully reproducible/debuggable).
CREATE TABLE IF NOT EXISTS t_transaction_features (
  id                    BIGSERIAL PRIMARY KEY,
  txn_id                BIGINT UNIQUE REFERENCES t_transactions(id) ON DELETE CASCADE,
  customer_id           BIGINT NOT NULL,
  ts_utc                TIMESTAMP NOT NULL,
  amount                DOUBLE PRECISION NOT NULL,
  currency_code         VARCHAR(10) NOT NULL,

  -- 4 features (fixed order)
  amount_z_score        DOUBLE PRECISION NOT NULL,
  time_segment_ratio    DOUBLE PRECISION NOT NULL,
  velocity_ratio        DOUBLE PRECISION NOT NULL,
  median_deviation      DOUBLE PRECISION NOT NULL,

  -- snapshot of the PRIOR baseline used to compute features
  baseline_n            BIGINT NOT NULL,             -- number of prior txns
  baseline_mean_amount  DOUBLE PRECISION NOT NULL,
  baseline_std_amount   DOUBLE PRECISION NOT NULL,
  baseline_median_amount DOUBLE PRECISION NOT NULL,
  baseline_seg_index    INT NOT NULL,           -- 0..3 (Night, Morning, Afternoon, Evening)
  baseline_seg_mean     DOUBLE PRECISION NOT NULL,

  created_at            TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_tf_cust_ts ON t_transaction_features(customer_id, ts_utc);

CREATE SEQUENCE transaction_features_id_seq
    INCREMENT BY 100
    START WITH 1
    cache 100
    OWNED BY t_transaction_features.id;
