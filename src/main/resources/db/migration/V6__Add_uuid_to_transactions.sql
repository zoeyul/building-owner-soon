-- Add uuid column to transactions table

-- Add uuid column (nullable first to allow data migration)
ALTER TABLE transactions ADD COLUMN uuid VARCHAR(36) COMMENT 'UUID for secure external sharing';

-- Generate UUIDs for existing transactions
UPDATE transactions SET uuid = UUID() WHERE uuid IS NULL;

-- Now make it NOT NULL and add unique constraint
ALTER TABLE transactions MODIFY COLUMN uuid VARCHAR(36) NOT NULL;

-- Add unique index on uuid for fast lookups and uniqueness guarantee
CREATE UNIQUE INDEX idx_transactions_uuid ON transactions(uuid);
