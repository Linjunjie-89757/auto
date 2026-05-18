UPDATE tb_sys_workspace
SET workspace_name = 'Account Opening',
    description = 'Account opening test assets',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 1;

UPDATE tb_sys_workspace
SET workspace_name = 'Trade Core',
    description = 'Trade execution core flows',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 2;

UPDATE tb_sys_workspace
SET workspace_name = 'Risk Control',
    description = 'Risk validation and interception checks',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 3;

UPDATE tb_sys_user SET display_name = 'Zhang Li', updated_at = CURRENT_TIMESTAMP WHERE id = 1;
UPDATE tb_sys_user SET display_name = 'Chen Nan', updated_at = CURRENT_TIMESTAMP WHERE id = 2;
UPDATE tb_sys_user SET display_name = 'Li Ping', updated_at = CURRENT_TIMESTAMP WHERE id = 3;
UPDATE tb_sys_user SET display_name = 'Zhao Feng', updated_at = CURRENT_TIMESTAMP WHERE id = 4;
UPDATE tb_sys_user SET display_name = 'Wang Xin', updated_at = CURRENT_TIMESTAMP WHERE id = 5;
UPDATE tb_sys_user SET display_name = 'Super Admin', updated_at = CURRENT_TIMESTAMP WHERE username = 'superadmin';

UPDATE tb_case_info
SET title = 'Successful account opening main flow',
    source_type = 'AI_GENERATED',
    case_status = 'CONFIRMED',
    precondition = 'Customer identity data is complete',
    steps = 'Submit the account opening request',
    expected_result = 'Account opening succeeds',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 128;

UPDATE tb_case_info
SET title = 'Account field boundary length validation',
    source_type = 'AI_APPENDED',
    case_status = 'DRAFT',
    precondition = 'Verification code has been sent',
    steps = 'Enter an overlong field value',
    expected_result = 'The field length validation error is shown',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 129;

UPDATE tb_case_info
SET title = 'Handle invalid sms verification code',
    source_type = 'MANUAL',
    case_status = 'CONFIRMED',
    precondition = 'Verification code has expired',
    steps = 'Submit the verification code',
    expected_result = 'The invalid verification code prompt is shown',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 130;

UPDATE tb_case_info
SET title = 'Cross-browser login compatibility regression',
    source_type = 'IMPORTED',
    case_status = 'ARCHIVED',
    precondition = 'Chrome and Edge are available',
    steps = 'Run the login regression flow',
    expected_result = 'Compatibility check passes',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 131;

UPDATE tb_exec_task
SET summary = 'UAT trade placement and settlement regression',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 201;

UPDATE tb_exec_task
SET summary = 'Chrome headless payment regression',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 202;

UPDATE tb_exec_task
SET summary = 'iPhone 14 login and transfer smoke run',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 203;

UPDATE tb_exec_report
SET failure_summary = 'Authentication and settlement assertions all passed',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 301;

UPDATE tb_exec_report
SET failure_summary = 'Timed out while waiting for the pay button',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 302;

UPDATE tb_exec_report
SET failure_summary = 'The home screen header did not appear after login',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 303;

UPDATE tb_bug_info
SET title = 'Pay button becomes unclickable after the modal closes',
    description = 'During the Playwright regression run, the pay button remains covered after the modal closes and blocks the checkout flow.',
    tags_json = '["payment","web","regression"]',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 1001;

UPDATE tb_bug_info
SET title = 'Verification code boundary copy is inconsistent',
    description = 'In the boundary case set, expired verification code and invalid verification code return inconsistent frontend copy.',
    tags_json = '["account-opening","boundary"]',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 1002;

UPDATE tb_bug_flow_record
SET action_comment = 'Assigned to Zhao Feng for investigation',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 5001;

UPDATE tb_bug_flow_record
SET action_comment = 'Issue reproduced and fix work started',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 5002;

UPDATE tb_bug_comment
SET content = 'The issue disappears immediately after the modal closes, which suggests the overlay state is not cleared.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 8001;

UPDATE tb_bug_comment
SET content = 'Screenshot evidence has been added and the frontend owner has been notified.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 8002;

UPDATE tb_env_config
SET env_name = 'Account UAT',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 1;

UPDATE tb_env_config
SET env_name = 'Trade Web UAT',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 2;

UPDATE tb_param_set
SET param_name = 'Account UAT Token',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 1;

UPDATE tb_param_set
SET param_name = 'Trade Common Header',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 2;
