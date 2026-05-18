DELETE FROM tb_bug_attachment;
DELETE FROM tb_case_execution_attachment;
DELETE FROM tb_exec_report_attachment;
DELETE FROM tb_bug_flow_record;
DELETE FROM tb_bug_comment;
DELETE FROM tb_bug_info;
DELETE FROM tb_exec_report;
DELETE FROM tb_exec_task;
DELETE FROM tb_case_info;
DELETE FROM tb_case_directory;
DELETE FROM tb_env_config;
DELETE FROM tb_param_set;
DELETE FROM tb_ai_generation_task;
DELETE FROM tb_ai_requirement_asset;
DELETE FROM tb_ai_case_config;
DELETE FROM tb_sys_workspace_member;
DELETE FROM tb_sys_workspace;
DELETE FROM tb_sys_user WHERE role_code <> 'SUPER_ADMIN';

INSERT INTO tb_sys_workspace (
    id, workspace_code, workspace_name, description, status, created_at, updated_at
) VALUES
    (11, 'retail-onboarding', 'Retail Onboarding', 'Demo workspace for onboarding coverage', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12, 'payments-core', 'Payments Core', 'Demo workspace for payment execution and defects', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (13, 'risk-ops', 'Risk Ops', 'Demo workspace for rule review and regression checks', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO tb_sys_user (
    id, username, display_name, email, role_code, password, status, created_at, updated_at
) VALUES
    (11, 'zhangli', 'Zhang Li', 'zhangli@demo.local', 'PLATFORM_ADMIN', '{noop}123456', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12, 'chennan', 'Chen Nan', 'chennan@demo.local', 'MEMBER', '{noop}123456', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (13, 'liping', 'Li Ping', 'liping@demo.local', 'MEMBER', '{noop}123456', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (14, 'zhaofeng', 'Zhao Feng', 'zhaofeng@demo.local', 'MEMBER', '{noop}123456', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (15, 'wangxin', 'Wang Xin', 'wangxin@demo.local', 'VIEWER', '{noop}123456', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO tb_sys_workspace_member (
    id, workspace_id, user_id, role_code, status, created_at, updated_at
) VALUES
    (1101, 11, 11, 'ADMIN', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1102, 11, 12, 'MEMBER', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1103, 11, 13, 'MEMBER', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1104, 12, 11, 'ADMIN', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1105, 12, 14, 'MEMBER', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1106, 13, 11, 'ADMIN', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1107, 13, 15, 'VIEWER', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO tb_case_directory (
    id, workspace_id, parent_id, directory_name, created_at, updated_at
) VALUES
    (2101, 11, NULL, 'Account Registration', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2102, 11, 2101, 'Validation Rules', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2201, 12, NULL, 'Checkout', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2202, 12, 2201, 'Settlement', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2301, 13, NULL, 'Risk Review', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO tb_case_info (
    id, workspace_id, case_no, title, case_type, priority, source_type, case_status,
    owner_id, precondition, steps, expected_result, execution_note, case_directory_id,
    review_status, review_comment, reviewed_by, reviewed_at,
    execution_status, executor_id, execution_comment, executed_at,
    created_by, updated_by, created_at, updated_at
) VALUES
    (3101, 11, 'CASE-20260518-001', 'Open account with valid identity package', 'FUNCTION', 'P0', 'MANUAL', 'CONFIRMED',
     12, 'Applicant profile exists and sms channel is reachable', 'Submit the onboarding form with a valid id and mobile number',
     'Account is created and the welcome page is shown', 'Primary smoke case for onboarding', 2101,
     'APPROVED', 'Ready for regression', 11, CURRENT_TIMESTAMP,
     'PASSED', 12, 'Verified against latest form validation rules', CURRENT_TIMESTAMP,
     11, 12, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3102, 11, 'CASE-20260518-002', 'Reject expired sms verification code', 'EXCEPTION', 'P0', 'MANUAL', 'CONFIRMED',
     13, 'Verification code has expired before submission', 'Enter the expired code and submit onboarding',
     'The page blocks submission and shows a clear expiration hint', 'Boundary case linked to defect validation', 2102,
     'APPROVED', 'Expected result clarified', 11, CURRENT_TIMESTAMP,
     'FAILED', 13, 'Current build still returns a generic validation error', CURRENT_TIMESTAMP,
     11, 13, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3201, 12, 'CASE-20260518-003', 'Retry payment after gateway timeout', 'REGRESSION', 'P1', 'IMPORTED', 'CONFIRMED',
     14, 'A test order is ready and gateway timeout can be simulated', 'Submit payment, wait for timeout, then retry once',
     'Retry succeeds without duplicate charge', 'Used by payment regression run', 2202,
     'APPROVED', 'Regression ready', 11, CURRENT_TIMESTAMP,
     'FAILED', 14, 'Retry button stays disabled after timeout in current build', CURRENT_TIMESTAMP,
     11, 14, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3301, 13, 'CASE-20260518-004', 'Review high-risk transfer with manual approval', 'FUNCTION', 'P1', 'AI_GENERATED', 'DRAFT',
     11, 'A transfer is scored as high risk', 'Open the review queue and approve the transfer manually',
     'Transfer status changes to approved and audit log is recorded', 'Draft generated from current rule set', 2301,
     'PENDING', 'Awaiting product review', NULL, NULL,
     'NOT_EXECUTED', NULL, NULL, NULL,
     11, 11, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO tb_exec_task (
    id, workspace_id, task_name, engine_type, task_status, summary, created_at, updated_at
) VALUES
    (4101, 12, 'checkout-regression-nightly', 'WEB', 'FAILED', 'Nightly checkout pack with timeout retry scenario', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (4102, 13, 'risk-rule-regression', 'API', 'SUCCESS', 'Risk approval API verification suite', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO tb_exec_report (
    id, workspace_id, task_id, report_name, result, failure_summary, log_text, attachments_json, log_source, created_at, updated_at
) VALUES
    (4201, 12, 4101, 'checkout-regression-nightly-20260518', 'FAILED', 'Retry button remained disabled after gateway timeout',
     'Step 14 failed while waiting for retry control to become enabled.', '[]', 'INLINE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (4202, 13, 4102, 'risk-rule-regression-20260518', 'SUCCESS', 'All approval endpoints returned expected results',
     'All assertions passed.', '[]', 'INLINE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO tb_bug_info (
    id, workspace_id, bug_no, title, description, priority, severity, status, source_type,
    assignee_id, reporter_id, related_case_id, related_report_id, related_task_id, tags_json,
    created_at, updated_at
) VALUES
    (5101, 11, 'BUG-20260518-001', 'Expired verification code shows generic error', 'The onboarding page returns a generic validation hint instead of explaining that the sms code has expired.', 'P1', 'HIGH', 'ASSIGNED', 'CASE',
     13, 11, 3102, NULL, NULL, '["onboarding","sms","validation"]', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (5102, 12, 'BUG-20260518-002', 'Retry button stays disabled after gateway timeout', 'In the nightly checkout regression run, the retry action never re-enables after the first timeout, blocking payment recovery.', 'P0', 'CRITICAL', 'IN_PROGRESS', 'REPORT',
     14, 11, 3201, 4201, 4101, '["checkout","retry","timeout"]', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (5103, 13, 'BUG-20260518-003', 'Manual review queue defaults to stale filter', 'Opening the risk review queue restores an old filter set and hides the newest high-risk transfer until the page is refreshed.', 'P2', 'MEDIUM', 'TODO', 'MANUAL',
     NULL, 11, NULL, NULL, NULL, '["risk","queue","ux"]', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO tb_bug_flow_record (
    id, bug_id, from_status, to_status, operator_id, action_comment, created_at, updated_at
) VALUES
    (6101, 5101, 'TODO', 'ASSIGNED', 11, 'Assigned to Li Ping for validation copy update', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (6102, 5102, 'TODO', 'ASSIGNED', 11, 'Assigned to Zhao Feng after nightly run', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (6103, 5102, 'ASSIGNED', 'IN_PROGRESS', 14, 'Issue reproduced locally with gateway timeout mock', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO tb_bug_comment (
    id, bug_id, content, commenter_id, created_at, updated_at
) VALUES
    (7101, 5101, 'Reproduced with an expired code after waiting six minutes before submit.', 13, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (7102, 5102, 'Attached console trace shows the retry state never leaves loading.', 14, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (7103, 5102, 'Please verify whether the disable flag is cleared when timeout modal closes.', 11, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO tb_env_config (
    id, workspace_id, env_type, env_name, base_url, config_json, status, created_at, updated_at
) VALUES
    (8101, 11, 'WEB', 'onboarding-uat', 'https://uat-onboarding.demo.local', '{"browser":"chrome","region":"cn"}', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (8102, 12, 'API', 'payments-uat', 'https://uat-payments.demo.local', '{"auth":"bearer","region":"cn"}', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (8103, 13, 'API', 'risk-uat', 'https://uat-risk.demo.local', '{"auth":"bearer","region":"cn"}', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO tb_param_set (
    id, workspace_id, param_type, param_name, content_json, status, created_at, updated_at
) VALUES
    (9101, 11, 'HEADER', 'onboarding-common-header', '{"X-App":"retail-onboarding"}', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (9102, 12, 'TOKEN', 'payments-service-token', '{"token":"demo-payments-token"}', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (9103, 13, 'BODY', 'risk-default-request', '{"channel":"ops-console","priority":"high"}', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
