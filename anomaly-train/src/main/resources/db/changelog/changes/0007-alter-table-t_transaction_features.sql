ALTER TABLE t_transaction_features
  ADD COLUMN IF NOT EXISTS is_trainable boolean DEFAULT false;