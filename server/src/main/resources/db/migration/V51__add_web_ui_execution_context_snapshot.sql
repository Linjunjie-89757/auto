ALTER TABLE tb_web_ui_run
    ADD COLUMN IF NOT EXISTS context_snapshot_json TEXT;
