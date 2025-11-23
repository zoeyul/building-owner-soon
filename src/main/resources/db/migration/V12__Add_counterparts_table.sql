-- Add counterparts table to manage counterparty information separately
-- This allows name and character changes to be reflected across all transactions
-- This script is idempotent and safe to run multiple times

-- Create counterparts table if it doesn't exist
CREATE TABLE IF NOT EXISTS counterparts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT 'User who owns this counterpart relationship',
    name VARCHAR(12) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Counterpart name (max 12 chars)',
    `character` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL
        COMMENT 'JSON format character data',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_user_name (user_id, name),

    CONSTRAINT fk_counterparts_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT 'Counterpart management table - allows duplicate names for separate entities';

-- Add counterpart_id to transactions table (idempotent)
SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'transactions'
      AND COLUMN_NAME = 'counterpart_id'
);

SET @sql = IF(@column_exists = 0,
    'ALTER TABLE transactions ADD COLUMN counterpart_id BIGINT NULL COMMENT ''Reference to counterpart entity'' AFTER user_id',
    'SELECT "counterpart_id column already exists, skipping" AS info');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add foreign key constraint (idempotent)
SET @fk_exists = (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'transactions'
      AND CONSTRAINT_NAME = 'fk_transactions_counterpart'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);

SET @sql = IF(@fk_exists = 0,
    'ALTER TABLE transactions ADD CONSTRAINT fk_transactions_counterpart FOREIGN KEY (counterpart_id) REFERENCES counterparts(id) ON DELETE RESTRICT',
    'SELECT "fk_transactions_counterpart already exists, skipping" AS info');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add index for better JOIN performance (idempotent)
SET @index_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'transactions'
      AND INDEX_NAME = 'idx_counterpart_id'
);

SET @sql = IF(@index_exists = 0,
    'ALTER TABLE transactions ADD KEY idx_counterpart_id (counterpart_id)',
    'SELECT "idx_counterpart_id already exists, skipping" AS info');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Note: counterpart_name and counterpart_character columns remain for backward compatibility
-- They will be removed in a future migration after data migration is complete
