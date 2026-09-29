-- Email identity lookups ignore case. Enforce that rule in PostgreSQL too so two
-- concurrent registrations with different letter case cannot create duplicate users.
-- Stop and reconcile historical collisions before applying this migration.
CREATE UNIQUE INDEX uq_users_email_ci ON users (lower(email)) WHERE email <> '';
