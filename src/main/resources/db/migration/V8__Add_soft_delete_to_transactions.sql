-- Add soft delete column to transactions table
ALTER TABLE transactions ADD COLUMN deleted_at TIMESTAMP NULL;

-- Add index for soft delete queries
CREATE INDEX idx_transactions_deleted_at ON transactions(deleted_at);
