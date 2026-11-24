-- Accounts have no roles: being signed in is all the router asks of one.
ALTER TABLE app_user DROP COLUMN role;
