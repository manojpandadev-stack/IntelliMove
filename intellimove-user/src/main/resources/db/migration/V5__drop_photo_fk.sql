-- V4 created the table while the auth/user database sync gap still existed;
-- drop the foreign key so photos work for every authenticated JWT identity.
ALTER TABLE user_profile_photos DROP CONSTRAINT IF EXISTS user_profile_photos_user_id_fkey;