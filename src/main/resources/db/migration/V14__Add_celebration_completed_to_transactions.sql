ALTER TABLE transactions
ADD COLUMN celebration_completed BOOLEAN NOT NULL DEFAULT FALSE;

-- 기존 완료된 거래는 celebration_completed = true로 설정 (갑자기 셀레브레이션 뜨는 것 방지)
UPDATE transactions
SET celebration_completed = TRUE
WHERE deleted_at IS NULL
  AND completed_amount >= total_amount;
