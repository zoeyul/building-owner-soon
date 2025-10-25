-- Add expo_token column to user_devices table

ALTER TABLE user_devices
    ADD COLUMN expo_token VARCHAR(255) DEFAULT NULL AFTER fcm_token,
    ADD KEY idx_expo_token (expo_token);
