-- Add initial_completed_amount column to transactions table
-- This column stores the completed amount set at transaction creation time
-- to distinguish it from amounts completed through repayment schedules

ALTER TABLE transactions
ADD COLUMN initial_completed_amount DECIMAL(19,2) NOT NULL DEFAULT 0 COMMENT 'Completed amount set at transaction creation'
AFTER completed_amount;

-- Migrate existing data: set initial_completed_amount to current completed_amount
UPDATE transactions
SET initial_completed_amount = completed_amount;
