-- Add user_device_id foreign key to refresh_tokens table for device-specific token management

ALTER TABLE refresh_tokens
    ADD COLUMN user_device_id BIGINT NULL AFTER user_id,
    ADD KEY idx_user_device_id (user_device_id),
    ADD CONSTRAINT fk_refresh_tokens_user_device
        FOREIGN KEY (user_device_id)
        REFERENCES user_devices(id)
        ON DELETE SET NULL;
