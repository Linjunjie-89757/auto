ALTER TABLE tb_api_definition
    ADD COLUMN IF NOT EXISTS preprocessors_json LONGTEXT;

ALTER TABLE tb_api_definition
    ADD COLUMN IF NOT EXISTS postprocessors_json LONGTEXT;

ALTER TABLE tb_api_run_step_result
    ADD COLUMN IF NOT EXISTS processor_results_json LONGTEXT;
