-- Computer players have accounts too, so the hands they play are recorded like anyone else's.
-- bot_level is NULL for a person. A bot's password_hash is not a real hash, so nobody can log in as one.
ALTER TABLE users ADD COLUMN bot_level VARCHAR(10);
