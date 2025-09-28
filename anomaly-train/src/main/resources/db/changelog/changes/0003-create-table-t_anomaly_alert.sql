CREATE TABLE IF NOT EXISTS t_anomaly_alert (
  id            BIGSERIAL PRIMARY KEY,
  txn_id        VARCHAR(64) UNIQUE,
  customer_id   BIGINT NOT NULL,
  amount    NUMERIC(18,2) NOT NULL,
  currency_code VARCHAR(10) NOT NULL,
  channel       VARCHAR(16) NOT NULL,
  ts_utc        TIMESTAMP NOT NULL,
  score         NUMERIC(6,4) NOT NULL,
  severity      VARCHAR(50) NOT NULL,      -- low|med|high
  facts_json    JSONB NOT NULL,           -- {amount_z, time_ratio, velocity_ratio, median_dev, means...}
  created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);