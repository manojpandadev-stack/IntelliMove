-- Create databases for each microservice
-- This script runs when PostgreSQL first starts via docker-entrypoint-initdb.d

CREATE DATABASE intellimove_auth;
CREATE DATABASE intellimove_user;
CREATE DATABASE intellimove_driver;
CREATE DATABASE intellimove_ride;
CREATE DATABASE intellimove_payment;
CREATE DATABASE intellimove_notification;

-- Grant all privileges to intellimove user
GRANT ALL PRIVILEGES ON DATABASE intellimove_auth TO intellimove;
GRANT ALL PRIVILEGES ON DATABASE intellimove_user TO intellimove;
GRANT ALL PRIVILEGES ON DATABASE intellimove_driver TO intellimove;
GRANT ALL PRIVILEGES ON DATABASE intellimove_ride TO intellimove;
GRANT ALL PRIVILEGES ON DATABASE intellimove_payment TO intellimove;
GRANT ALL PRIVILEGES ON DATABASE intellimove_notification TO intellimove;
