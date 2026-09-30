-- Story submissions are public, so they carry an optional author name. Nullable
-- like description; VARCHAR(255) matches the other string columns and the DTO cap.
ALTER TABLE story ADD COLUMN author VARCHAR(255);
