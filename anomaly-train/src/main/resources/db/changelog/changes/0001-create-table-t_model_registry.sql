CREATE TABLE IF NOT EXISTS t_model_registry (
  model_id         BIGSERIAL PRIMARY KEY,
  created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
  trees            INT NOT NULL,
  subsample        INT NOT NULL,
  feature_schema   TEXT NOT NULL,         -- e.g. "[amount_z,time_segment_ratio,velocity_ratio,median_dev]"
  schema_hash      VARCHAR(64) NOT NULL,  -- e.g. SHA-256 of feature_schema
  trained_rows     BIGINT NOT NULL,
  notes            TEXT,
  model_bytes      BYTEA NOT NULL
);