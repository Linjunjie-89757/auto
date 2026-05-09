ALTER TABLE tb_case_info ADD COLUMN review_status VARCHAR(32);
ALTER TABLE tb_case_info ADD COLUMN review_comment TEXT;
ALTER TABLE tb_case_info ADD COLUMN reviewed_by BIGINT;
ALTER TABLE tb_case_info ADD COLUMN reviewed_at TIMESTAMP;

UPDATE tb_case_info SET review_status = 'PENDING' WHERE review_status IS NULL;
