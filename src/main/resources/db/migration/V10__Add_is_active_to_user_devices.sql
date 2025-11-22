-- Add is_active column to user_devices table for logout functionality

ALTER TABLE user_devices
    ADD COLUMN is_active TINYINT(1) NOT NULL DEFAULT 1 AFTER device_name,
    ADD KEY idx_is_active (is_active);
