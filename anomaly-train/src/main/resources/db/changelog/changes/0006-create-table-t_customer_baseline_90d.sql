-- Rolling baseline snapshot (latest per customer) — used to warm Redis on startup
CREATE TABLE IF NOT EXISTS t_customer_baseline_90d (
  customer_id            BIGINT PRIMARY KEY,
  n_tx                   BIGINT NOT NULL,
  mean_amount            DOUBLE PRECISION NOT NULL,
  std_amount             DOUBLE PRECISION NOT NULL,
  median_amount          DOUBLE PRECISION NOT NULL,
  seg_mean_night         DOUBLE PRECISION NOT NULL,
  seg_mean_morning       DOUBLE PRECISION NOT NULL,
  seg_mean_afternoon     DOUBLE PRECISION NOT NULL,
  seg_mean_evening       DOUBLE PRECISION NOT NULL,
  updated_at             TIMESTAMP NOT NULL DEFAULT now()
);