-- Widen users.avatar_url so the in-app file uploader can store a small
-- base64-encoded data URL inline. Mirrors the same migration on the
-- community service so both sides of the shared users table agree.

ALTER TABLE users
    ALTER COLUMN avatar_url TYPE TEXT;
