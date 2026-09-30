-- Story ownership: the account that submitted a story, as opposed to `author`,
-- which stays free text used only for display credit. Nullable on purpose: a
-- submission with no session has no owner and is by definition community content.
ALTER TABLE story ADD COLUMN owner_id BIGINT;

ALTER TABLE story ADD CONSTRAINT fk_story_owner FOREIGN KEY (owner_id) REFERENCES users(id);

-- Every listing filters by owner, so an unindexed owner_id is a table scan.
CREATE INDEX idx_story_owner ON story(owner_id);

-- Backfill: /upload-story required a session until this migration, and
-- POST /auth/register is denyAll, so every pre-existing story belongs to the
-- site owner. A missing account leaves owner_id NULL instead of violating the
-- FK, which is the same state an anonymous submission lands in.
UPDATE story
SET owner_id = (SELECT id FROM users WHERE email = 'juli.agustin.perez@gmail.com')
WHERE owner_id IS NULL;
