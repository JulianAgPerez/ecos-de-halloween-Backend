-- Speeds up lookups of a user's valid tokens on login/refresh/logout.
CREATE INDEX idx_tokens_user_id ON tokens (user_id);