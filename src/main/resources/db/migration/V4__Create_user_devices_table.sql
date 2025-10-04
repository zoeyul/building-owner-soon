-- User devices table for FCM token management

CREATE TABLE IF NOT EXISTS user_devices (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    device_id VARCHAR(255) NOT NULL,
    fcm_token VARCHAR(255) NOT NULL,
    platform VARCHAR(20),
    device_name VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_device (user_id, device_id),
    KEY idx_user_id (user_id),
    KEY idx_fcm_token (fcm_token),
    CONSTRAINT fk_user_devices_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
