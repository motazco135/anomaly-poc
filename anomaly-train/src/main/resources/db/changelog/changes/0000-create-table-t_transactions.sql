CREATE TABLE IF NOT EXISTS t_transactions (
  id            BIGSERIAL PRIMARY KEY,
  customer_id   BIGINT NOT NULL,
  amount        NUMERIC(18,2) NOT NULL,
  currency_code VARCHAR(10) NOT NULL,
  channel       VARCHAR(16) NOT NULL,     -- POS | ATM | ONLINE | WIRE ..
  ts_utc        TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_trx_cust_ts ON t_transactions(customer_id, ts_utc);