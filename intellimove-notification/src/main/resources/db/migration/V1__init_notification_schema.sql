-- Notification Service: Initial Schema

CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id VARCHAR(255) NOT NULL,
    recipient_type VARCHAR(50) NOT NULL,
    notification_type VARCHAR(100) NOT NULL,
    channel VARCHAR(50) NOT NULL DEFAULT 'IN_APP',
    title VARCHAR(255),
    message TEXT,
    sent BOOLEAN NOT NULL DEFAULT FALSE,
    sent_at TIMESTAMP WITH TIME ZONE,
    read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_notif_recipient ON notifications(recipient_id);
CREATE INDEX idx_notif_type ON notifications(notification_type);
CREATE INDEX idx_notif_created ON notifications(created_at);
