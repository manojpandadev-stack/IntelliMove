-- Ride Service: Initial Schema

CREATE TABLE IF NOT EXISTS rides (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL,
    driver_id UUID,
    status VARCHAR(50) NOT NULL,
    ride_type VARCHAR(50) NOT NULL,
    pickup_latitude DOUBLE PRECISION NOT NULL,
    pickup_longitude DOUBLE PRECISION NOT NULL,
    pickup_address TEXT,
    dropoff_latitude DOUBLE PRECISION NOT NULL,
    dropoff_longitude DOUBLE PRECISION NOT NULL,
    dropoff_address TEXT,
    estimated_fare DECIMAL(10,2),
    final_fare DECIMAL(10,2),
    currency VARCHAR(3) DEFAULT 'USD',
    distance_km DOUBLE PRECISION DEFAULT 0,
    duration_minutes BIGINT DEFAULT 0,
    driver_assigned_at TIMESTAMP WITH TIME ZONE,
    driver_accepted_at TIMESTAMP WITH TIME ZONE,
    driver_arriving_at TIMESTAMP WITH TIME ZONE,
    trip_started_at TIMESTAMP WITH TIME ZONE,
    trip_completed_at TIMESTAMP WITH TIME ZONE,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    cancellation_reason VARCHAR(50),
    cancellation_note TEXT,
    cancelled_by VARCHAR(50),
    payment_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_ride_customer ON rides(customer_id);
CREATE INDEX idx_ride_driver ON rides(driver_id);
CREATE INDEX idx_ride_status ON rides(status);
CREATE INDEX idx_ride_created ON rides(created_at);

-- Outbox events table (shared across services via migration)
CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    topic VARCHAR(100) NOT NULL,
    message_key VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP WITH TIME ZONE,
    retry_count INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_outbox_status ON outbox_events(status);
CREATE INDEX idx_outbox_created ON outbox_events(created_at);
