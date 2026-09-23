-- Speeds up the daily purge job, which bulk-deletes expired/revoked tokens by a created_at cutoff.
CREATE INDEX idx_tokens_created_at ON tokens (created_at);
