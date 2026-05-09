ALTER TABLE tb_sys_user ADD COLUMN email VARCHAR(128);

UPDATE tb_sys_user SET email = 'zhangli@example.com' WHERE username = 'zhangli' AND (email IS NULL OR email = '');
UPDATE tb_sys_user SET email = 'chennan@example.com' WHERE username = 'chennan' AND (email IS NULL OR email = '');
UPDATE tb_sys_user SET email = 'liping@example.com' WHERE username = 'liping' AND (email IS NULL OR email = '');
UPDATE tb_sys_user SET email = 'zhaofeng@example.com' WHERE username = 'zhaofeng' AND (email IS NULL OR email = '');
UPDATE tb_sys_user SET email = 'wangxin@example.com' WHERE username = 'wangxin' AND (email IS NULL OR email = '');

CREATE UNIQUE INDEX uq_tb_sys_user_email ON tb_sys_user(email);
