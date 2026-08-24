-- Driver Service: Initial Schema

CREATE TABLE IF NOT EXISTS drivers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    license_number VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL DEFAULT 'OFFLINE',
    vehicle_make VARCHAR(100) NOT NULL,
    vehicle_model VARCHAR(100) NOT NULL,
    vehicle_year INT NOT NULL,
    vehicle_color VARCHAR(50) NOT NULL,
    license_plate VARCHAR(20) NOT NULL,
    vehicle_type VARCHAR(50) NOT NULL DEFAULT 'ECONOMY',
    rating DOUBLE PRECISION NOT NULL DEFAULT 5.0,
    total_ratings INT NOT NULL DEFAULT 0,
    total_trips INT NOT NULL DEFAULT 0,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at TIMESTAMP WITH TIME ZONE,
    available BOOLEAN NOT NULL DEFAULT FALSE,
    last_location_update_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_driver_user_id ON drivers(user_id);
CREATE INDEX idx_driver_status ON drivers(status);
CREATE INDEX idx_driver_license ON drivers(license_number);
