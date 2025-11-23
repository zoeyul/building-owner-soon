-- Migrate existing transaction data to use counterparts table
-- Creates a separate counterpart entity for each existing transaction
-- This script is idempotent and safe to run multiple times

-- Process transactions one by one to ensure correct counterpart mapping
-- This approach is slower but guarantees data integrity

DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS migrate_transactions_to_counterparts()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE trans_id BIGINT;
    DECLARE trans_user_id BIGINT;
    DECLARE trans_name VARCHAR(12) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
    DECLARE trans_character LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
    DECLARE trans_created_at TIMESTAMP;
    DECLARE new_counterpart_id BIGINT;

    -- Cursor to iterate through transactions that need migration
    DECLARE cur CURSOR FOR
        SELECT id, user_id, counterpart_name, counterpart_character, created_at
        FROM transactions
        WHERE deleted_at IS NULL
          AND counterpart_name IS NOT NULL
          AND counterpart_character IS NOT NULL
          AND counterpart_id IS NULL;

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    OPEN cur;

    read_loop: LOOP
        FETCH cur INTO trans_id, trans_user_id, trans_name, trans_character, trans_created_at;
        IF done THEN
            LEAVE read_loop;
        END IF;

        -- Create a new counterpart for this transaction
        INSERT INTO counterparts (user_id, name, `character`, created_at, updated_at)
        VALUES (trans_user_id, trans_name, trans_character, trans_created_at, trans_created_at);

        -- Get the newly created counterpart ID
        SET new_counterpart_id = LAST_INSERT_ID();

        -- Update the transaction with the counterpart ID
        UPDATE transactions
        SET counterpart_id = new_counterpart_id
        WHERE id = trans_id;

    END LOOP;

    CLOSE cur;
END$$

DELIMITER ;

-- Execute the migration procedure
CALL migrate_transactions_to_counterparts();

-- Clean up the procedure
DROP PROCEDURE IF EXISTS migrate_transactions_to_counterparts;

-- Note: counterpart_name and counterpart_character columns are kept in Transaction table (denormalized)
-- counterpart_id is also kept nullable for backward compatibility
-- Both are maintained in sync during create/update operations
-- Future migration (V14) may remove denormalized columns after full migration to JOIN queries
