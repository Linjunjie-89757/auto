ALTER TABLE tb_case_info ADD COLUMN review_status VARCHAR(32) NULL;
ALTER TABLE tb_case_info ADD COLUMN review_comment TEXT NULL;
ALTER TABLE tb_case_info ADD COLUMN reviewed_by BIGINT NULL;
ALTER TABLE tb_case_info ADD COLUMN reviewed_at TIMESTAMP NULL;

UPDATE tb_case_info SET review_status = 'PENDING' WHERE review_status IS NULL;
