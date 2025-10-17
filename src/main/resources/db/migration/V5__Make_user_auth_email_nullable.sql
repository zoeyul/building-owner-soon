-- Make email column nullable in user_auths table
-- This allows non-BOS providers (e.g., Kakao) to have users without email

ALTER TABLE user_auths
    MODIFY COLUMN email VARCHAR(255) NULL;
